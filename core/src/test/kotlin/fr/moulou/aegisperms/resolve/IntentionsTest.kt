// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les intentions courantes du cahier des charges (2.12), chacune posée sur les deux groupes du premier démarrage et jugée pour un joueur non-op. */
class IntentionsTest {

    private val player = subject("Paul")

    @Test
    fun `ouvrir une commande d'op à un groupe`() {
        val resolver = resolverWith("builders" to Group(rules = mapOf("minecraft:command.gamemode" to Rule.ALLOW)))

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.gamemode")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.gamemode.gamemode")))
    }

    @Test
    fun `une commande sans l'une de ses branches`() {
        val resolver = resolverWith("hunters" to Group(rules = mapOf("minecraft:command.kill" to Rule.ALLOW, "minecraft:command.kill.targets" to Rule.DENY)))

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.kill")))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("minecraft:command.kill.targets")))
    }

    @Test
    fun `tout, sauf quelques commandes, ce qui donne op et deop mais pas aegis`() {
        val resolver = resolverWith("staff" to Group(rules = mapOf("command" to Rule.ALLOW, "minecraft:command.stop" to Rule.DENY)))

        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("minecraft:command.stop")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.gamemode")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.op")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.deop")))
        assertEquals(Decision.UNSET, resolver.decision(player, commandKey("aegisperms:command.aegis")))
    }

    @Test
    fun `une sanction qui prime, par un groupe de poids élevé`() {
        val staff = "staff" to Group(weight = 10, rules = mapOf("command" to Rule.ALLOW))
        val muted = resolverWith(staff, "sanction" to Group(weight = 1000, rules = mapOf("minecraft:command.msg" to Rule.DENY)))
        val banished = resolverWith(staff, "sanction" to Group(weight = 1000, rules = mapOf("command" to Rule.DENY)))

        assertEquals(NodeJudgment.HIDDEN, muted.judgment(player, commandKey("minecraft:command.msg")))
        assertEquals(NodeJudgment.ALLOWED, muted.judgment(player, commandKey("minecraft:command.gamemode")))
        // Avec command à deny, la sanction retire tout.
        assertEquals(NodeJudgment.HIDDEN, banished.judgment(player, commandKey("minecraft:command.gamemode")))
        assertEquals(NodeJudgment.HIDDEN, banished.judgment(player, commandKey("minecraft:command.help")))
    }

    @Test
    fun `tenir un modérateur à un niveau bas, non op, avec ce qu'un allow lui ouvre`() {
        val resolver = resolverWith("moderators" to Group(rules = mapOf("minecraft:command.kick" to Rule.ALLOW)))

        assertEquals(0, player.opLevel)
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.kick.targets")))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("minecraft:command.ban")))
    }

    @Test
    fun `confier aegis à un joueur qui n'est pas op, et refermer ce qu'on garde pour soi`() {
        val resolver = resolverWith("helpers" to Group(rules = mapOf("aegisperms:command.aegis" to Rule.ALLOW, "aegisperms:command.aegis.reload" to Rule.DENY)))

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("aegisperms:command.aegis")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("aegisperms:command.aegis.why")))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("aegisperms:command.aegis.reload")))
    }

    /** Les groupes du premier démarrage, [groups] en plus, et le joueur inscrit dans chacun d'eux. */
    private fun resolverWith(vararg groups: Pair<String, Group>): Resolver =
        resolver(permissions(Permissions.DELIVERED.groups + groups, mapOf(readableForm("Paul") to Player(groups = groups.map { it.first }))))
}
