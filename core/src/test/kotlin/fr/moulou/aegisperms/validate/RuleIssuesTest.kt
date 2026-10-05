// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.Fixtures.issues
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les avertissements que la résolution fait naître (cahier des charges, 4.5) : l'`allow` sans effet, la clé sensible, l'égalité de poids. */
class RuleIssuesTest {

    private val delivered = Permissions.DELIVERED.groups

    private val teleport = "minecraft:command.teleport"
    private val targets = "minecraft:command.teleport.targets"
    private val list = "minecraft:command.list"

    @Test
    fun `un allow sous un refus du même ensemble est un avertissement`() {
        val vip = "vip" to Group(rules = mapOf(teleport to Rule.DENY, targets to Rule.ALLOW))

        assertEquals(
            listOf(Issue(IssueKind.ALLOW_UNDER_REFUSAL, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", targets), literals(targets, teleport))),
            issues(permissions(delivered + vip)),
        )
    }

    @Test
    fun `un allow sous le refus d'un parent est un avertissement`() {
        val base = "base" to Group(rules = mapOf(teleport to Rule.DENY))
        val heir = "heir" to Group(parents = listOf("base"), rules = mapOf(targets to Rule.ALLOW))

        assertEquals(
            listOf(Issue(IssueKind.ALLOW_UNDER_REFUSAL, ConfigurationFile.PERMISSIONS, listOf("groups", "heir", "rules", targets), literals(targets, teleport))),
            issues(permissions(delivered + base + heir)),
        )
    }

    @Test
    fun `un allow propre à un joueur, sous un refus de sa décision, est un avertissement`() {
        val coordinates = "minecraft:command.teleport.targets.location"
        val rotation = "minecraft:command.teleport.targets.location.rotation"

        // Jogger : sa règle propre sur rotation, sous le refus que membre pose sur les coordonnées.
        assertEquals(
            listOf(Issue(IssueKind.ALLOW_UNDER_REFUSAL, ConfigurationFile.PERMISSIONS, listOf("players", readableForm("Jogger"), "rules", rotation), literals(rotation, coordinates))),
            issues(personas),
        )
    }

    @Test
    fun `un refus hors de la lignée d'un groupe ne donne aucun constat sur lui`() {
        val vip = "vip" to Group(rules = mapOf(targets to Rule.ALLOW))
        val sanction = "sanction" to Group(weight = 1000, rules = mapOf(teleport to Rule.DENY))

        assertTrue(issues(permissions(delivered + vip + sanction)).isEmpty())
    }

    @Test
    fun `un allow sur une clé sensible est un avertissement, avec sa raison`() {
        val op = "minecraft:command.op"

        assertEquals(
            listOf(
                Issue(
                    IssueKind.ALLOW_SENSITIVE, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", op),
                    listOf(IssueArgument.Literal(op), IssueArgument.Literal(op), IssueArgument.Translatable("aegisperms.issue.allow_sensitive.op_change")),
                ),
            ),
            issues(permissions(delivered + ("vip" to Group(rules = mapOf(op to Rule.ALLOW))))),
        )
    }

    @Test
    fun `un allow sur une clé de nœud qui couvre des clés sensibles en dit une ligne par raison`() {
        val execute = "minecraft:command.execute"
        val rulePath = listOf("groups", "vip", "rules", execute)
        fun opened(keys: String, reason: String) =
            Issue(IssueKind.ALLOW_SENSITIVE, ConfigurationFile.PERMISSIONS, rulePath, listOf(IssueArgument.Literal(execute), IssueArgument.Literal(keys), IssueArgument.Translatable("aegisperms.issue.allow_sensitive.$reason")))

        assertEquals(
            listOf(
                opened("minecraft:command.execute.if.function, minecraft:command.execute.unless.function", "functions"),
                opened("minecraft:command.execute.summon", "command_minecart"),
                opened("minecraft:command.execute.store", "data_writing"),
                opened("minecraft:command.execute.as, minecraft:command.execute.on", "impersonation"),
            ),
            issues(permissions(delivered + ("vip" to Group(rules = mapOf(execute to Rule.ALLOW))))),
        )
    }

    @Test
    fun `un allow sur une clé générale n'avertit pas des clés sensibles, qu'elle ouvre par définition`() {
        val wide = "wide" to Group(rules = mapOf("command" to Rule.ALLOW, "minecraft:command" to Rule.ALLOW))

        assertTrue(issues(permissions(delivered + wide)).isEmpty())
    }

    @Test
    fun `un deny sur une clé sensible ne donne aucun constat`() {
        assertTrue(issues(permissions(delivered + ("vip" to Group(rules = mapOf("minecraft:command.op" to Rule.DENY))))).isEmpty())
    }

    @Test
    fun `deux groupes de même poids en désaccord chez un joueur sont un avertissement, dit une fois par paire`() {
        val tie = readableForm("Tie")
        val groups = delivered +
            ("alpha" to Group(weight = 5, rules = mapOf(list to Rule.ALLOW, "minecraft:command.seed" to Rule.ALLOW))) +
            ("beta" to Group(weight = 5, rules = mapOf(list to Rule.DENY, "minecraft:command.seed" to Rule.DENY)))

        assertEquals(
            listOf(Issue(IssueKind.WEIGHT_TIE, ConfigurationFile.PERMISSIONS, listOf("players", tie, "groups"), literals("alpha", "beta", "5", list))),
            issues(permissions(groups, mapOf(tie to Player(groups = listOf("beta", "alpha"))))),
        )
    }

    @Test
    fun `l'égalité de poids ne se dit ni pour default, ni quand l'héritage a tranché`() {
        val besideDefault = permissions(delivered + ("vip" to Group(rules = mapOf(list to Rule.ALLOW))), mapOf(readableForm("Joe") to Player(groups = listOf("vip"))))
        val inherited = permissions(
            delivered + ("a" to Group(weight = 10, parents = listOf("b"), rules = mapOf(list to Rule.ALLOW))) + ("b" to Group(weight = 10, rules = mapOf(list to Rule.DENY))),
            mapOf(readableForm("Heir") to Player(groups = listOf("a", "b"))),
        )

        assertTrue(issues(besideDefault).isEmpty())
        assertTrue(issues(inherited).isEmpty())
    }
}
