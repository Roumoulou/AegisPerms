// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.ConfigurationWrite
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.validate.ConfigurationFile
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Le cycle de vie du mod sur le serveur (cahier des charges, 7.5) : l'adoption de l'index au démarrage et après un `/reload` réussi, le renvoi des arbres à
 * la fin du tick, le contrôle de l'enveloppe, les messages aux ops et le renommage à la connexion (6.6, 4.9, 4.4), l'oubli du cache à la déconnexion, le
 * résultat de la surveillance aux ops (4.8), et l'arrêt du fil des fichiers.
 */
object Lifecycle {

    private const val HOOK = "lifecycle"

    /** Vrai dès qu'un joueur s'est connecté avec une autorité qui n'est pas l'enveloppe (6.6) : un pare-feu qui ne juge plus doit le dire, et `/aegis status` le lira. */
    @Volatile
    var foreignAuthoritySeen: Boolean = false
        private set

    /** Le serveur en service, de `SERVER_STARTING` à `SERVER_STOPPING` : ce que les messages aux ops ont à connaître. */
    @Volatile
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTING.register { server -> guarded("starting") { onServerStarting(server) } }
        ServerLifecycleEvents.SERVER_STARTED.register { server -> guarded("started") { onServerStarted(server) } }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success -> guarded("reload") { onDataPackReloaded(server, success) } }
        ServerLifecycleEvents.SERVER_STOPPING.register { guarded("stopping") { onServerStopping() } }
        ServerTickEvents.END_SERVER_TICK.register(TreeResend::flush)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> guarded("join") { onJoin(handler.player) } }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> guarded("disconnect") { onDisconnect(handler.player) } }
        FileThread.watchListener = { outcome -> server?.let { current -> current.execute { guarded("watch") { Notices.watch(current, outcome) } } } }
    }

    /** Une erreur interne dans un événement du cycle de vie se journalise une fois et ne fait pas tomber le serveur : ce qui n'a pas été fait laisse l'arbre fermé ou le sujet non-op. */
    private inline fun guarded(event: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            ModLog.once(HOOK, event, error)
        }
    }

    /**
     * Sur le fil serveur, avant toute connexion : l'index du premier `Commands` est adopté, et le fil des fichiers fait le démarrage, que ce fil attend (D9).
     * La liste des ops n'existe pas encore : l'événement part juste avant `initServer()`, qui crée la liste des joueurs, et c'est `SERVER_STARTED` qui la lit.
     */
    private fun onServerStarting(server: MinecraftServer) {
        this.server = server
        FileThread.startup(indexOf(server))
    }

    /**
     * À `SERVER_STARTED`, la liste des joueurs créée : la liste des ops se lit (6.3), et un serveur qui n'authentifie pas les joueurs le dit en avertissement
     * (2.3, 5.4), parce que quiconque se connecte sous le pseudo d'un administrateur reçoit alors ses règles.
     */
    private fun onServerStarted(server: MinecraftServer) {
        OpLevels.readOps(server)
        if (!server.usesAuthentication()) ModLog.warn(ModLog.OFFLINE_MODE)
    }

    /** À la fin d'un `/reload` : l'index du nouveau `Commands` devient courant, et tous les arbres repartent. Un `/reload` raté garde l'ancien dispatcher et son index. */
    private fun onDataPackReloaded(server: MinecraftServer, success: Boolean) {
        if (!success) return
        FileThread.adopt(indexOf(server))
        TreeResend.requestAll()
    }

    private fun onServerStopping() {
        server = null
        FileThread.stop()
    }

    /**
     * À la connexion : l'autorité du joueur est relue, et une autorité qui n'est pas l'enveloppe est une erreur au journal, avec le joueur et la classe
     * trouvée (6.6) ; un op d'au moins `admin-level` reçoit le repli du démarrage et les avertissements renforcés (4.9, 4.5) ; un joueur du fichier qui
     * revient sous un nouveau pseudo voit sa forme lisible réécrite (4.4).
     */
    private fun onJoin(player: ServerPlayer) {
        val authority = player.permissions()
        if (authority is Envelope) {
            val level = authority.level().id()
            OpLevels.record(player.uuid, level)
            Notices.onJoin(player, level)
        } else {
            foreignAuthoritySeen = true
            ModLog.error(ModLog.FOREIGN_AUTHORITY, player.nameAndId().name(), player.uuid, authority.javaClass.name)
        }
        renameIfNeeded(player)
    }

    /**
     * Un joueur du fichier qui se connecte sous un nouveau pseudo : sa forme lisible se réécrit, par le fil des fichiers, comme toute écriture du mod (4.4,
     * 4.8). Le journal le dit quand c'est écrit ; un fichier en lecture seule ou qui a bougé garde l'ancienne forme, et la connexion suivante réessaiera.
     */
    private fun renameIfNeeded(player: ServerPlayer) {
        val state = Publisher.currentOrNull()?.state ?: return
        val known = state.players[player.uuid] ?: return
        val name = player.nameAndId().name()
        if (known.name == name) return
        val renamed = ReadableForm.of(name, player.uuid)
        FileThread.write(Renaming.renamed(state.permissions, player.uuid, name), PublicationSource.COMMAND).thenAccept { outcome ->
            when (val write = outcome.result) {
                is ConfigurationWrite.Written -> ModLog.info(ModLog.PLAYER_RENAMED, known.readableForm, renamed)
                is ConfigurationWrite.Failed -> ModLog.error(ModLog.WRITE_FAILED, write.cause, ConfigurationFile.PERMISSIONS.fileName)
                else -> {}
            }
        }
    }

    private fun onDisconnect(player: ServerPlayer) {
        Publisher.currentOrNull()?.forget(player.uuid)
    }

    /** L'index attaché au `Commands` en service, ou `null` quand il a manqué : l'arbre est alors fermé, et le journal le dit. */
    private fun indexOf(server: MinecraftServer): CommandIndex<CommandSourceStack>? {
        val index = (server.commands as CommandsIndexHolder).`aegisperms$getIndex`()
        if (index == null) ModLog.error(ModLog.INDEX_MISSING)
        return index
    }
}
