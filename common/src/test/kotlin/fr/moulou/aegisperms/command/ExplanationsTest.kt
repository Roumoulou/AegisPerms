// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.command.Explanations.Evaluation
import fr.moulou.aegisperms.command.Explanations.NodeStatus
import fr.moulou.aegisperms.command.Explanations.Verdict
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.Subject
import fr.moulou.aegisperms.tree.ADMINS
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.FakeMods
import fr.moulou.aegisperms.tree.FakeRequirements
import fr.moulou.aegisperms.tree.GAMEMASTERS
import fr.moulou.aegisperms.tree.VanillaCommand
import fr.moulou.aegisperms.tree.AegisCommand as AegisCommandFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.function.Predicate

/** Le récit de `why` (cahier des charges, 5.5), sur un arbre synthétique et les personnages de 2.11 ; la source qui analyse voit tout, la sonde juge. */
class ExplanationsTest {

    companion object {
        private const val CONSOLE_LEVEL = 4

        private fun uuidOf(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

        private fun readableForm(name: String): String = ReadableForm.of(name, uuidOf(name))

        private val PERSONAS = Permissions(
            Permissions.SCHEMA_VERSION,
            groups = mapOf(
                "admin" to Group(weight = 100, parents = listOf("moderateur"), includeOps = OpLevel.OWNERS, rules = mapOf("command" to Rule.ALLOW)),
                Group.DEFAULT to Permissions.DELIVERED.groups.getValue(Group.DEFAULT),
                "membre" to Group(weight = 10, rules = mapOf("minecraft:command.teleport" to Rule.ALLOW, "minecraft:command.teleport.location" to Rule.DENY, "minecraft:command.teleport.targets.location" to Rule.DENY)),
                "moderateur" to Group(weight = 50, parents = listOf("membre"), rules = mapOf("minecraft:command.kick" to Rule.ALLOW, "minecraft:command.teleport" to Rule.DENY, "minecraft:commands/entity_selectors" to Rule.ALLOW)),
                "vip" to Group(weight = 80, rules = mapOf("minecraft:command.teleport.targets.location" to Rule.ALLOW, "minecraft:command.datapack" to Rule.ALLOW)),
            ),
            players = mapOf(
                readableForm("Alice") to Player(groups = listOf("admin")),
                readableForm("Bob") to Player(groups = listOf("moderateur")),
                readableForm("Joe") to Player(groups = listOf("membre", "vip")),
                readableForm("Jogger") to Player(groups = listOf("membre"), rules = mapOf("minecraft:command.teleport.targets.location.rotation" to Rule.ALLOW)),
            ),
        )
    }

    /** Les réglages livrés, plus un alias vers un littéral profond (3.6). */
    private val settings = Settings.DELIVERED.copy(aliases = mapOf("wla" to "minecraft:whitelist add"))

    private val dispatcher: CommandDispatcher<Any> = CommandDispatcher<Any>().apply {
        register(
            literal<Any>("teleport").requires(GAMEMASTERS).then(
                argument<Any, String>("targets", StringArgumentType.word()).then(
                    argument<Any, String>("location", StringArgumentType.word()).executes(VanillaCommand()).then(argument<Any, String>("rotation", StringArgumentType.word()).executes(VanillaCommand())),
                ),
            ),
        )
        register(literal<Any>("help").executes(VanillaCommand()))
        register(literal<Any>("seed").requires(GAMEMASTERS).executes(VanillaCommand()))
        register(literal<Any>("aegis").requires(ADMINS).executes(AegisCommandFixture()))
        // `datapack create` exige plus que `datapack` (2.8) ; `whitelist add <targets>` est la cible de l'alias `wla`.
        register(literal<Any>("datapack").requires(GAMEMASTERS).then(literal<Any>("create").requires(ADMINS).executes(VanillaCommand())))
        register(literal<Any>("whitelist").requires(ADMINS).then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand()))))
    }

    private val index: CommandIndex<Any> = CommandIndex.build(dispatcher.root, settings, FakeMods(), FakeRequirements)

    private val resolver = Resolver(EffectiveState(1, Configuration(settings, PERSONAS)))

    @Test
    fun `Jogger lit sa téléportation nœud par nœud, la forme, la clé ancêtre, le refus masqué et son allow sans effet`() {
        val report = report("Jogger", 0, "mc:teleport Jogger 10 90")

        val headline = requireNotNull(report.headline)
        assertEquals(listOf("mc:teleport", "teleport", "minecraft"), listOf(headline.typed, headline.target, headline.owner))
        assertEquals(listOf("mc:teleport", "targets", "location", "rotation"), report.rows.map { it.name })
        assertEquals(listOf(NodeStatus.SEEN, NodeStatus.SEEN, NodeStatus.HIDDEN, NodeStatus.INEFFECTIVE), report.rows.map { it.status })
        assertEquals(listOf(Decision.ALLOW, Decision.ALLOW, Decision.DENY_HIDDEN, Decision.ALLOW), report.rows.map { it.decision })
        assertEquals(listOf(false, true, false, false), report.rows.map { it.byAncestorKey })
        assertEquals(RuleSetOwner.OfGroup("membre"), report.rows[2].trace?.own?.winner?.owner)
        assertEquals(RuleSetOwner.OfPlayer(readableForm("Jogger")), report.rows[3].trace?.own?.winner?.owner)
        assertEquals(Verdict.REFUSED_HIDDEN, report.verdict)
        assertEquals("location", report.deciding?.name)
        assertEquals(listOf(RuleSetOwner.OfGroup(Group.DEFAULT)), report.deciding?.trace?.own?.setAside?.map { it.owner })
    }

    @Test
    fun `une forme nue sous la forme exigée est refusée, masquée, dès son premier nœud`() {
        val report = report("Jogger", 0, "teleport Jogger 10 90")

        assertNull(report.headline)
        assertEquals(NodeStatus.BARE_FORM, report.rows.first().status)
        assertEquals(Verdict.REFUSED_HIDDEN, report.verdict)
        assertEquals("teleport", report.deciding?.name)
    }

    @Test
    fun `Joe passe par vip et Bob par default, permises`() {
        val joe = report("Joe", 0, "mc:teleport Joe 10 90")
        assertEquals(listOf(NodeStatus.SEEN, NodeStatus.SEEN, NodeStatus.SEEN, NodeStatus.SEEN), joe.rows.map { it.status })
        assertEquals(RuleSetOwner.OfGroup("vip"), joe.rows[2].trace?.own?.winner?.owner)
        assertEquals(Verdict.PERMITTED, joe.verdict)

        val bob = report("Bob", 0, "mc:help")
        assertEquals(listOf(NodeStatus.SEEN), bob.rows.map { it.status })
        assertEquals(RuleSetOwner.OfGroup(Group.DEFAULT), bob.rows.single().trace?.own?.winner?.owner)
        assertEquals(Verdict.PERMITTED, bob.verdict)
    }

    @Test
    fun `sous unset, l'exigence vanilla décide, Bob ne voit pas aegis et Olga l'a par l'étape 0`() {
        val bob = report("Bob", 0, "aegis")
        assertEquals(NodeStatus.UNSEEN_VANILLA, bob.rows.single().status)
        assertEquals(Decision.UNSET, bob.rows.single().decision)
        assertEquals(Verdict.REFUSED_BY_VANILLA, bob.verdict)

        val olga = report("Olga", 4, "aegis")
        assertEquals(NodeStatus.SEEN, olga.rows.single().status)
        assertTrue(olga.rows.single().trace?.own?.step0 == true)
        assertEquals(Verdict.PERMITTED, olga.verdict)
    }

    @Test
    fun `Nina retombe sur le command à deny de default, par une clé ancêtre`() {
        val report = report("Nina", 0, "mc:seed")
        val row = report.rows.single()
        assertEquals(NodeStatus.HIDDEN, row.status)
        assertEquals(Decision.DENY_HIDDEN, row.decision)
        assertTrue(row.byAncestorKey)
        assertEquals(Verdict.REFUSED_HIDDEN, report.verdict)
    }

    @Test
    fun `Joe ouvre datapack create par l'allow de datapack, et le récit dit le nœud plus exigeant que son ancêtre`() {
        val report = report("Joe", 0, "mc:datapack create")

        assertEquals(Verdict.PERMITTED, report.verdict, "l'allow répond de l'exigence des deux nœuds")
        assertEquals(listOf("mc:datapack", "create"), report.rows.map { it.name })
        assertFalse(report.rows[0].moreDemanding, "datapack porte l'allow lui-même")
        assertTrue(report.rows[1].byAncestorKey)
        assertTrue(report.rows[1].moreDemanding, "create exige le niveau admins, que datapack n'exige pas")
        assertTrue(Explanations.isMoreDemanding(CommandRequirement.ADMINS, CommandRequirement.GAMEMASTERS))
        assertFalse(Explanations.isMoreDemanding(CommandRequirement.GAMEMASTERS, CommandRequirement.ADMINS))
        assertTrue(Explanations.isMoreDemanding(CommandRequirement.MODERATORS, CommandRequirement.NONE))
        assertFalse(Explanations.isMoreDemanding(CommandRequirement.ADMINS, CommandRequirement.MOD_PREDICATE))
        assertFalse(Explanations.isMoreDemanding(CommandRequirement.MOD_PREDICATE, CommandRequirement.NONE))
    }

    @Test
    fun `la première ligne ramène un alias vers un littéral profond à sa cible, écrite comme on la tape`() {
        val alice = report("Alice", 0, "wla Alice")

        val headline = requireNotNull(alice.headline)
        assertEquals(listOf("wla", "whitelist add", "minecraft"), listOf(headline.typed, headline.target, headline.owner))
        assertEquals(listOf("wla", "targets"), alice.rows.map { it.name }, "l'alias remplace whitelist et add, puis l'argument de add")
        assertEquals(Verdict.PERMITTED, alice.verdict, "command à allow répond de l'exigence de whitelist, que l'alias garde")

        val joe = report("Joe", 0, "wla Joe")
        assertEquals("whitelist add", joe.headline?.target, "la première ligne ne dépend pas du verdict")
        assertEquals(Verdict.REFUSED_HIDDEN, joe.verdict, "command à deny ferme whitelist add à Joe")
    }

    @Test
    fun `une ligne qui s'arrête avant un nœud qui s'exécute est incomplète`() {
        val report = report("Joe", 0, "mc:teleport Joe")
        assertEquals(listOf(NodeStatus.SEEN, NodeStatus.SEEN), report.rows.map { it.status })
        assertEquals(Verdict.INCOMPLETE, report.verdict)
    }

    @Test
    fun `un nœud sans fiche est hors catalogue, et vanilla décide`() {
        dispatcher.register(literal<Any>("late").executes(VanillaCommand()))
        val report = report("Nina", 0, "late")
        val row = report.rows.single()
        assertEquals(NodeStatus.OUT_OF_CATALOG, row.status)
        assertNull(row.decision)
        assertFalse(row.byAncestorKey)
        assertEquals(Verdict.PERMITTED, report.verdict)
    }

    private fun report(name: String, opLevel: Int, line: String): Explanations.Report {
        val parse = dispatcher.parse(line, CONSOLE_LEVEL)
        return Explanations.why(
            steps = Explanations.steps(parse),
            complete = Explanations.isComplete(parse),
            resolver = resolver,
            subject = Subject(uuidOf(name), opLevel),
            requirePrefix = settings.namespaces.requirePrefix,
            markOf = { node -> @Suppress("UNCHECKED_CAST") index.markOf(node as CommandNode<Any>) },
            evaluate = { node -> evaluate(node, opLevel) },
            evaluateWithAllRights = { node -> evaluate(node, CONSOLE_LEVEL) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluate(node: CommandNode<*>, level: Int): Evaluation = if ((node.requirement as Predicate<Any>).test(level)) Evaluation.PASSES else Evaluation.FAILS
}
