// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.key
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.silentDefault
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.RuleValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La trace d'une décision et celle d'un nœud, ce que `/aegis why` et `/aegis check` racontent (cahier des charges, 5.5). */
class DecisionTraceTest {

    private val coordinates = commandKey("minecraft:command.teleport.targets.location")
    private val rotation = commandKey("minecraft:command.teleport.targets.location.rotation")
    private val list = key("minecraft:command.list")

    @Test
    fun `la trace nomme la règle qui l'emporte et l'ensemble qui la porte`() {
        val trace = resolver(personas).explain(subject("Joe"), coordinates)

        assertSame(coordinates, trace.key)
        assertEquals(Decision.ALLOW, trace.decision)
        assertFalse(trace.step0)
        assertEquals(RuleHit(RuleSetOwner.OfGroup("vip"), coordinates, Rule.ALLOW), trace.winner)
    }

    @Test
    fun `une règle propre se dit par la forme lisible de son joueur`() {
        val trace = resolver(personas).explain(subject("Jogger"), rotation)

        assertEquals(RuleHit(RuleSetOwner.OfPlayer(readableForm("Jogger")), rotation, Rule.ALLOW), trace.winner)
    }

    @Test
    fun `une règle qui couvre par une clé ancêtre se dit par sa propre clé`() {
        val targets = commandKey("minecraft:command.teleport.targets")
        val trace = resolver(personas).explain(subject("Jogger"), targets)

        assertEquals(RuleHit(RuleSetOwner.OfGroup("membre"), commandKey("minecraft:command.teleport"), Rule.ALLOW), trace.winner)
    }

    @Test
    fun `les ensembles écartés suivent celui qui l'emporte, dans l'ordre d'examen`() {
        val teleport = commandKey("minecraft:command.teleport")
        val trace = resolver(personas).explain(subject("Alice"), teleport)

        assertEquals(RuleHit(RuleSetOwner.OfGroup("admin"), Keys.ALL_COMMANDS, Rule.ALLOW), trace.winner)
        assertEquals(
            listOf(
                RuleHit(RuleSetOwner.OfGroup("moderateur"), teleport, Rule.DENY),
                RuleHit(RuleSetOwner.OfGroup("membre"), teleport, Rule.ALLOW),
                RuleHit(RuleSetOwner.OfGroup("default"), Keys.ALL_COMMANDS, Rule.DENY),
            ),
            trace.setAside,
        )
        assertEquals(listOf("admin", "moderateur", "membre", "default"), trace.order.groups.map { it.name })
    }

    @Test
    fun `sans règle qui réponde, la trace n'a ni règle qui l'emporte ni ensemble écarté`() {
        val trace = resolver(personas).explain(subject("Alice"), key("aegisperms:command.aegis.reload"))

        assertEquals(Decision.UNSET, trace.decision)
        assertNull(trace.winner)
        assertTrue(trace.setAside.isEmpty())
    }

    @Test
    fun `sous l'étape 0, la trace le dit, et toutes les règles qui répondraient sont écartées`() {
        val defaultGroup = Permissions.DELIVERED.groups.getValue("default")
        val permissions = permissions(Permissions.DELIVERED.groups + ("default" to defaultGroup.copy(rules = defaultGroup.rules + ("aegisperms:command.aegis" to Rule.DENY))))
        val trace = resolver(permissions).explain(subject("Olga", opLevel = 4), key("aegisperms:command.aegis.reload"))

        assertTrue(trace.step0)
        assertEquals(Decision.ALLOW, trace.decision)
        assertNull(trace.winner)
        assertEquals(listOf(RuleHit(RuleSetOwner.OfGroup("default"), Keys.ADMINISTRATION, Rule.DENY)), trace.setAside)
        assertTrue(trace.weightTies.isEmpty())
    }

    @Test
    fun `à poids égal, la trace dit l'égalité quand c'est le nom qui a tranché`() {
        val permissions = permissions(
            groups = mapOf(silentDefault, "beta" to Group(weight = 5, rules = mapOf("minecraft:command.list" to Rule.DENY)), "alpha" to Group(weight = 5, rules = mapOf("minecraft:command.list" to Rule.ALLOW))),
            players = mapOf(readableForm("Tie") to Player(groups = listOf("beta", "alpha"))),
        )
        val trace = resolver(permissions).explain(subject("Tie"), list)

        assertEquals(Decision.ALLOW, trace.decision)
        assertEquals(listOf(WeightTie("alpha", "beta", 5)), trace.weightTies)
    }

    @Test
    fun `à poids égal, aucune égalité ne se dit quand les deux groupes décident de même`() {
        val permissions = permissions(
            groups = mapOf(silentDefault, "beta" to Group(weight = 5, rules = mapOf("minecraft:command.list" to Rule.ALLOW)), "alpha" to Group(weight = 5, rules = mapOf("minecraft:command.list" to Rule.ALLOW))),
            players = mapOf(readableForm("Tie") to Player(groups = listOf("beta", "alpha"))),
        )

        assertTrue(resolver(permissions).explain(subject("Tie"), list).weightTies.isEmpty())
    }

    @Test
    fun `quand l'héritage a tranché, l'égalité de poids ne se dit pas`() {
        // a et b ont le même poids et décident différemment, mais a hérite de b : b passe après a quel que soit son nom.
        val permissions = permissions(
            groups = mapOf(
                silentDefault,
                "a" to Group(weight = 10, parents = listOf("b"), rules = mapOf("minecraft:command.list" to Rule.ALLOW)),
                "b" to Group(weight = 10, rules = mapOf("minecraft:command.list" to Rule.DENY)),
            ),
            players = mapOf(readableForm("Heir") to Player(groups = listOf("a", "b"))),
        )
        val trace = resolver(permissions).explain(subject("Heir"), list)

        assertEquals(Decision.ALLOW, trace.decision)
        assertTrue(trace.weightTies.isEmpty())
    }

    @Test
    fun `default rangé après un groupe de même poids n'est pas une égalité de poids`() {
        val permissions = permissions(
            groups = Permissions.DELIVERED.groups + ("vip" to Group(rules = mapOf("minecraft:command.list" to Rule.ALLOW))),
            players = mapOf(readableForm("Joe") to Player(groups = listOf("vip"))),
        )
        val trace = resolver(permissions).explain(subject("Joe"), list)

        assertEquals(Decision.ALLOW, trace.decision)
        assertEquals(listOf(RuleHit(RuleSetOwner.OfGroup("default"), Keys.ALL_COMMANDS, Rule.DENY)), trace.setAside)
        assertTrue(trace.weightTies.isEmpty())
    }

    @Test
    fun `la trace d'un nœud dit la clé de son chemin qui le ferme`() {
        val trace = resolver(personas).explainNode(subject("Jogger"), rotation)

        assertEquals(NodeJudgment.HIDDEN, trace.judgment)
        assertEquals(Decision.ALLOW, trace.own.decision)
        assertSame(coordinates, trace.closing?.key)
        assertEquals(RuleHit(RuleSetOwner.OfGroup("membre"), coordinates, Rule.DENY), trace.closing?.winner)
    }

    @Test
    fun `un nœud refusé par sa propre clé se ferme lui-même`() {
        val trace = resolver(personas).explainNode(subject("Jogger"), coordinates)

        assertEquals(NodeJudgment.HIDDEN, trace.judgment)
        assertSame(trace.own, trace.closing)
    }

    @Test
    fun `un nœud sans refus sur son chemin n'a pas de clé qui le ferme`() {
        val trace = resolver(personas).explainNode(subject("Joe"), coordinates)

        assertEquals(NodeJudgment.ALLOWED, trace.judgment)
        assertNull(trace.closing)
    }

    @Test
    fun `un allow sous un refus du chemin est sans effet, dans les règles d'un groupe comme dans celles d'un joueur`() {
        val kill = commandKey("minecraft:command.kill")
        val targets = commandKey("minecraft:command.kill.targets")
        val permissions = permissions(mapOf("default" to Group(rules = mapOf("minecraft:command.kill" to Rule(RuleValue.DENY, visible = true), "minecraft:command.kill.targets" to Rule.ALLOW))))
        val resolver = resolver(permissions)

        assertEquals(listOf(IneffectiveRule(RuleHit(RuleSetOwner.OfGroup("default"), targets, Rule.ALLOW), kill)), resolver.ineffectiveRules(subject("Nina")))
        assertSame(kill, resolver.explainNode(subject("Nina"), targets).closing?.key)
        assertTrue(resolver(personas).ineffectiveRules(subject("Joe")).isEmpty())
    }
}
