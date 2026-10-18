// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.PermissionsChanges
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.Subject
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le renvoi des arbres de commandes (cahier des charges, 7.4 et 7.6) : les demandes s'accumulent et partent à la fin du tick, sur le fil serveur, une fois
 * par joueur, pour les seuls joueurs concernés. Vanilla ne renvoie pas les arbres après un `/reload`.
 */
object TreeResend {

    private const val HOOK = "treeResend"

    /** Tout le monde est demandé ; lu et remis à zéro d'un seul geste au renvoi, pour qu'une demande posée entre les deux par un autre fil ne se perde pas. */
    private val everyone = AtomicBoolean(false)

    private val players = ConcurrentHashMap.newKeySet<UUID>()

    /** Les changements des publications depuis le dernier envoi : leurs joueurs concernés se décident à la fin du tick, parmi les joueurs en ligne. */
    private val pending = ConcurrentLinkedQueue<PermissionsChanges>()

    /** Demande l'arbre de tous les joueurs en ligne : après un `/reload`, un changement des réglages ou un premier état. */
    fun requestAll() {
        everyone.set(true)
    }

    /** Ajoute [player] aux joueurs dont l'arbre part à la fin du tick. */
    fun request(player: UUID) {
        players += player
    }

    /**
     * Ce qu'une publication demande : tout le monde quand les réglages ou la structure des groupes changent, parce que l'ordre d'examen de chacun peut
     * changer avec eux ; sinon les seuls joueurs que les règles changées touchent, décidés à la fin du tick.
     */
    fun afterPublication(previous: EffectiveState?, next: EffectiveState) {
        if (previous == null || previous.settings != next.settings) return requestAll()
        val changes = PermissionsChanges.between(previous, next)
        when {
            changes.isEmpty -> {}
            changes.groupsCreated.isNotEmpty() || changes.groupsDeleted.isNotEmpty() || changes.groupsChanged.isNotEmpty() -> requestAll()
            else -> pending += changes
        }
    }

    /** À la fin du tick, sur le fil serveur : l'arbre des joueurs demandés et des joueurs concernés, une fois chacun. */
    fun flush(server: MinecraftServer) {
        if (!everyone.get() && players.isEmpty() && pending.isEmpty()) return
        val all = everyone.getAndSet(false)
        val requested = HashSet(players)
        players.removeAll(requested)
        val changes = generateSequence { pending.poll() }.toList()
        val resolver = Publisher.currentOrNull()
        for (player in server.playerList.players) {
            val uuid = player.uuid
            val concerned = all || uuid in requested || (resolver != null && changes.isNotEmpty() && concerns(Subject(uuid, levelOf(player)), resolver, changes))
            if (!concerned) continue
            try {
                server.commands.sendCommands(player)
            } catch (error: Throwable) {
                ModLog.once(HOOK, uuid.toString(), error)
            }
        }
    }

    /** Vrai si [subject] est touché par [changes] : son entrée de joueur, ses règles propres, ou les règles d'un groupe de son ordre d'examen sous [resolver]. */
    internal fun concerns(subject: Subject, resolver: Resolver, changes: Collection<PermissionsChanges>): Boolean {
        val suffix = "#${subject.uuid}"
        val groups: Set<String> by lazy { resolver.order(subject).groups.mapTo(HashSet()) { it.name } }
        for (change in changes) {
            if ((change.playersAdded + change.playersRemoved + change.playersChanged).any { it.endsWith(suffix) }) return true
            for (rule in change.rules) {
                val touched = when (val owner = rule.owner) {
                    is RuleSetOwner.OfPlayer -> owner.readableForm.endsWith(suffix)
                    is RuleSetOwner.OfGroup -> owner.name in groups
                }
                if (touched) return true
            }
        }
        return false
    }

    private fun levelOf(player: ServerPlayer): Int = (player.permissions() as? LevelBasedPermissionSet)?.level()?.id() ?: 0

    internal fun pending(): List<PermissionsChanges> = pending.toList()

    internal fun wantsEveryone(): Boolean = everyone.get()

    /** Oublie toute demande. Réservé aux tests. */
    internal fun clear() {
        everyone.set(false)
        players.clear()
        pending.clear()
    }
}
