// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.key
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.state
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.Fixtures.uuidOf
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.DeniedCommands
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Des invariants du cahier des charges (2.2), éprouvés sur des configurations tirées au hasard : cinq groupes, quatre joueurs écrits et un joueur que le
 * fichier ne connaît pas, chacun jugé à tous les niveaux d'op. La graine est fixée, pour qu'un échec se rejoue à l'identique.
 */
class PropertiesTest {

    private val nodes: List<CommandKey> = listOf(
        "minecraft:command.teleport", "minecraft:command.teleport.targets", "minecraft:command.teleport.targets.location", "minecraft:command.teleport.targets.location.rotation",
        "minecraft:command.teleport.location", "minecraft:command.kick", "minecraft:command.kick.targets", "minecraft:command.kick.targets.reason",
        "minecraft:command.gamemode", "minecraft:command.gamemode.gamemode", "homes:command.home", "homes:command.home.name",
        "aegisperms:command.aegis", "aegisperms:command.aegis.reload",
    ).map(::commandKey)

    /** Les clés que les règles tirées au hasard portent : celles des nœuds, les clés générales, et des clés d'API. */
    private val keys: List<Key> = nodes + listOf("command", "minecraft:command", "homes:command", "aegisperms:command", "minecraft:commands", "minecraft:commands/entity_selectors", "homes:home/limit").map(::key)

    private val groupNames = listOf("a", "b", "c", "d", "e")
    private val playerNames = listOf("p1", "p2", "p3", "p4")

    /** Les joueurs écrits et Nina, que le fichier ne connaît pas, chacun du niveau d'op 0 au niveau 4. */
    private val subjects: List<Subject> = (playerNames + "Nina").flatMap { name -> (0..OpLevel.OWNERS.level).map { subject(name, it) } }

    @Test
    fun `les décisions ne dépendent pas de l'ordre des entrées de la configuration`() {
        val random = Random(20261005)
        val decisionsSeen = HashSet<Decision>()
        repeat(300) { draw ->
            val permissions = randomPermissions(random)
            val written = resolver(permissions)
            val reversed = resolver(reversed(permissions))
            for (subject in subjects) {
                for (key in keys) {
                    val decision = written.decision(subject, key)
                    decisionsSeen += decision
                    assertEquals(decision, reversed.decision(subject, key)) { "tirage $draw, $subject, décision de $key" }
                }
                for (node in nodes) assertEquals(written.judgment(subject, node), reversed.judgment(subject, node)) { "tirage $draw, $subject, jugement de $node" }
            }
        }
        // Les tirages couvrent les quatre décisions : la propriété n'est pas tenue à vide.
        assertEquals(Decision.entries.toSet(), decisionsSeen)
    }

    @Test
    fun `un refus ferme tout ce qui descend de sa clé`() {
        val random = Random(1123)
        val judgmentsSeen = HashSet<NodeJudgment>()
        repeat(300) { draw ->
            val deniedCommands = if (random.nextBoolean()) DeniedCommands.VISIBLE else DeniedCommands.HIDDEN
            val resolver = resolver(randomPermissions(random), Settings.DELIVERED.copy(deniedCommands = deniedCommands))
            for (subject in subjects) {
                for (node in nodes) {
                    val judgment = resolver.judgment(subject, node)
                    val decisions = node.pathKeys.map { resolver.decision(subject, it) }
                    judgmentsSeen += judgment
                    if (decisions.any { it.isRefusal }) assertFalse(judgment.hasNoRefusal) { "tirage $draw, $subject : $node est sans refus sous un refus" }
                    if (decisions.any { it == Decision.DENY_HIDDEN }) assertEquals(NodeJudgment.HIDDEN, judgment) { "tirage $draw, $subject : $node n'est pas masqué sous un refus masqué" }
                    if (decisions.none { it.isRefusal }) assertTrue(judgment.hasNoRefusal) { "tirage $draw, $subject : $node porte un refus que son chemin n'a pas" }
                }
            }
        }
        // Les tirages couvrent les cinq jugements : la propriété n'est pas tenue à vide.
        assertEquals(NodeJudgment.entries.toSet(), judgmentsSeen)
    }

    @Test
    fun `aucun nœud n'est sans règle sous un refus, qui couvre aussi ses descendantes`() {
        val random = Random(505)
        var underRefusal = 0
        repeat(300) { draw ->
            val resolver = resolver(randomPermissions(random))
            for (subject in subjects) {
                for (node in nodes) {
                    val decisions = node.pathKeys.map { resolver.decision(subject, it) }
                    if (decisions.dropLast(1).none { it.isRefusal }) continue
                    underRefusal++
                    assertNotEquals(Decision.UNSET, decisions.last()) { "tirage $draw, $subject : $node est sans règle sous un refus" }
                }
            }
        }
        // Des milliers de nœuds se trouvent sous un refus au fil des tirages : la propriété n'est pas tenue à vide.
        assertTrue(underRefusal > 1000) { "$underRefusal nœuds seulement sous un refus" }
    }

    @Test
    fun `un résolveur qui a déjà servi répond comme un résolveur neuf`() {
        val random = Random(77)
        repeat(300) { draw ->
            val state = state(randomPermissions(random))
            val used = Resolver(state)
            // Les sujets et les nœuds mêlés : les cases du cache se remplissent dans le désordre, niveaux d'op confondus.
            val questions = subjects.flatMap { subject -> nodes.map { subject to it } }.shuffled(random)

            val first = questions.map { (subject, node) -> used.judgment(subject, node) }
            used.forget(uuidOf(playerNames.random(random)))
            val second = questions.map { (subject, node) -> used.judgment(subject, node) }
            val fresh = questions.map { (subject, node) -> Resolver(state).judgment(subject, node) }

            assertEquals(first, second) { "tirage $draw : le cache change une réponse" }
            assertEquals(first, fresh) { "tirage $draw : un résolveur neuf répond autrement" }
        }
    }

    private fun randomPermissions(random: Random): Permissions {
        val groups = groupNames.mapIndexed { index, name ->
            name to Group(
                weight = random.nextInt(4) * 10,
                // Des parents pris parmi les groupes suivants seulement : jamais de cycle.
                parents = groupNames.drop(index + 1).filter { random.nextInt(3) == 0 },
                includeOps = if (random.nextInt(4) == 0) OpLevel.entries.random(random) else null,
                rules = randomRules(random, oneIn = 4) { Rule(if (random.nextBoolean()) RuleValue.ALLOW else RuleValue.DENY, visible = listOf(null, true, false).random(random)) },
            )
        }
        val players = playerNames.map { name ->
            readableForm(name) to Player(groups = groupNames.filter { random.nextInt(3) == 0 }, rules = randomRules(random, oneIn = 8) { Rule.ALLOW })
        }
        return permissions(groups.toMap() + (Group.DEFAULT to Group(rules = randomRules(random, oneIn = 6) { Rule.DENY })), players.toMap())
    }

    private fun randomRules(random: Random, oneIn: Int, rule: () -> Rule): Map<String, Rule> = keys.filter { random.nextInt(oneIn) == 0 }.associate { it.text to rule() }

    /** La même configuration, écrite à l'envers : ses groupes, ses joueurs, leurs règles et les groupes de chaque joueur. Seul l'ordre des parents, qui compte, est gardé. */
    private fun reversed(permissions: Permissions): Permissions = permissions(
        groups = permissions.groups.reversedEntries().mapValues { it.value.copy(rules = it.value.rules.reversedEntries()) },
        players = permissions.players.reversedEntries().mapValues { it.value.copy(groups = it.value.groups.reversed(), rules = it.value.rules.reversedEntries()) },
    )

    private fun <V> Map<String, V>.reversedEntries(): Map<String, V> = entries.reversed().associate { it.key to it.value }
}
