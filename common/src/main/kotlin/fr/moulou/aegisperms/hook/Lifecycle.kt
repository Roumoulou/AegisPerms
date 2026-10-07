// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.tree.CommandIndex
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Le cycle de vie du mod sur le serveur (cahier des charges, 7.5) : l'adoption de l'index au démarrage et après un `/reload` réussi, le renvoi des arbres à
 * la fin du tick, le contrôle de l'enveloppe à la connexion (6.6), l'oubli du cache à la déconnexion, et l'arrêt du fil des fichiers.
 */
object Lifecycle {

    /** Vrai dès qu'un joueur s'est connecté avec une autorité qui n'est pas l'enveloppe (D20) : un pare-feu qui ne juge plus doit le dire, et `/aegis status` le lira. */
    @Volatile
    var foreignAuthoritySeen: Boolean = false
        private set

    private const val HOOK = "lifecycle"

    fun register() {
        ServerLifecycleEvents.SERVER_STARTING.register { server -> guarded("starting") { onServerStarting(server) } }
        ServerLifecycleEvents.SERVER_STARTED.register { server -> guarded("started") { OpLevels.readOps(server) } }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success -> guarded("reload") { onDataPackReloaded(server, success) } }
        ServerLifecycleEvents.SERVER_STOPPING.register { guarded("stopping") { FileThread.stop() } }
        ServerTickEvents.END_SERVER_TICK.register(TreeResend::flush)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> guarded("join") { onJoin(handler.player) } }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> guarded("disconnect") { onDisconnect(handler.player) } }
    }

    /**
     * Sur le fil serveur, avant toute connexion : l'index du premier `Commands` est adopté, et le fil des fichiers fait le démarrage, que ce fil attend (D9).
     * La liste des ops n'existe pas encore : l'événement part juste avant `initServer()`, qui crée la liste des joueurs, et c'est `SERVER_STARTED` qui la lit.
     */
    private fun onServerStarting(server: MinecraftServer) {
        FileThread.startup(indexOf(server))
    }

    /** À la fin d'un `/reload` : l'index du nouveau `Commands` devient courant, et tous les arbres repartent. Un `/reload` raté garde l'ancien dispatcher et son index. */
    private fun onDataPackReloaded(server: MinecraftServer, success: Boolean) {
        if (!success) return
        FileThread.adopt(indexOf(server))
        TreeResend.requestAll()
    }

    /** Une erreur interne dans un événement du cycle de vie se journalise une fois et ne fait pas tomber le serveur : ce qui n'a pas été fait laisse l'arbre fermé ou le sujet non-op. */
    private inline fun guarded(event: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            ModLog.once(HOOK, event, error)
        }
    }

    /** À la connexion : l'autorité du joueur est relue, et une autorité qui n'est pas l'enveloppe est une erreur au journal, avec le joueur et la classe trouvée (6.6). */
    private fun onJoin(player: ServerPlayer) {
        val authority = player.permissions()
        if (authority is Envelope) {
            OpLevels.record(player.uuid, authority.level().id())
            return
        }
        foreignAuthoritySeen = true
        ModLog.error(ModLog.FOREIGN_AUTHORITY, player.nameAndId().name(), player.uuid, authority.javaClass.name)
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
