// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.hook.HookFixtures.personas
import fr.moulou.aegisperms.hook.HookFixtures.readableForm
import fr.moulou.aegisperms.hook.HookFixtures.state
import fr.moulou.aegisperms.hook.HookFixtures.uuidOf
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.DeniedCommands
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.tree.NodeEntry
import fr.moulou.aegisperms.tree.NodeMark
import fr.moulou.aegisperms.tree.Unindexed
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.Permissions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

/**
 * Les accroches face aux classes du jeu (cahier des charges, 7.5 et 8.4), sans mixin : les marques viennent d'une table, et une source jugée est une source
 * de compilation qui porte une enveloppe.
 */
class HooksTest {

    companion object {
        private val NON_OP: LevelBasedPermissionSet = LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)

        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        private val TELEPORT: CommandKey = Keys.parse("minecraft:command.teleport") as CommandKey
        private val TP: CommandKey = Keys.parse("minecraft:command.tp") as CommandKey
        private val KICK: CommandKey = Keys.parse("minecraft:command.kick") as CommandKey

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    private val lines = ArrayList<String>()

    /** Les marques des nœuds, à la place de celles qu'un mixin porterait. */
    private val marks = IdentityHashMap<CommandNode<*>, NodeMark>()
    private val markOf: (CommandNode<*>) -> NodeMark? = { marks[it] }

    /** Un arbre réduit : `teleport <targets>`, réservé aux ops de niveau 2, et `tp` qui y redirige, comme en vanilla. */
    private val dispatcher = CommandDispatcher<CommandSourceStack>()
    private val teleport: LiteralCommandNode<CommandSourceStack> = dispatcher.register(Commands.literal("teleport").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("targets", StringArgumentType.greedyString()).executes { 1 }))
    private val targets: CommandNode<CommandSourceStack> = teleport.getChild("targets")
    private val tp: LiteralCommandNode<CommandSourceStack> = dispatcher.register(Commands.literal("tp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).redirect(teleport))
    private val kick: CommandNode<CommandSourceStack> = Commands.literal("kick").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).build()

    @BeforeEach
    fun publishPersonas() {
        ModLog.writer = LogWriter { _, line, _ -> lines += line }
        ModLog.forgetReported()
        Publisher.publish(state(personas))
        marks[teleport] = entry(TELEPORT, CommandRequirement.GAMEMASTERS)
        marks[targets] = entry(TELEPORT.descendant("targets"), isRoot = false)
        marks[tp] = entry(TELEPORT, CommandRequirement.GAMEMASTERS, ownKey = TP)
    }

    @AfterEach
    fun releaseLog() {
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `canUse tolère la source de compilation et la source sans droits, sans lire l'état`() {
        Publisher.clear()
        val compilation = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)
        val noPermissions = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS)

        assertTrue(Hooks.canUse(kick, compilation, true))
        assertFalse(Hooks.canUse(kick, compilation, false))
        assertTrue(Hooks.canUse(kick, noPermissions, true))
        assertFalse(Hooks.canUse(kick, noPermissions, false))
        assertTrue(Hooks.canUse(kick, 42, true))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `un nœud sans marque suit vanilla`() {
        val (envelope, source) = judged("Bob")

        assertTrue(Hooks.decide(null, kick, envelope, source, true))
        assertFalse(Hooks.decide(null, kick, envelope, source, false))
    }

    @Test
    fun `un arbre sans index est fermé à une source jugée`() {
        val (envelope, source) = judged("Alice", LevelBasedPermissionSet.OWNER)

        assertFalse(Hooks.decide(Unindexed, kick, envelope, source, true))
    }

    @Test
    fun `un deny masqué retire le nœud, un allow lève le niveau, unset garde vanilla`() {
        val (bob, bobSource) = judged("Bob")
        val (joe, joeSource) = judged("Joe")
        val adminsOnly = entry(KICK, CommandRequirement.ADMINS)

        // Bob : moderateur ouvre kick à un non-op que vanilla refuse. Joe : le command à deny de default le masque, quoi que vanilla dise.
        assertTrue(Hooks.decide(adminsOnly, kick, bob, bobSource, false))
        assertFalse(Hooks.decide(adminsOnly, kick, joe, joeSource, true))

        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group())), generation = 2))
        val (nina, ninaSource) = judged("Nina")
        assertTrue(Hooks.decide(adminsOnly, kick, nina, ninaSource, true))
        assertFalse(Hooks.decide(adminsOnly, kick, nina, ninaSource, false))
    }

    @Test
    fun `un refus visible garde le nœud vu`() {
        Publisher.publish(state(personas, Settings.DELIVERED.copy(deniedCommands = DeniedCommands.VISIBLE), generation = 2))
        val (joe, source) = judged("Joe")

        assertTrue(Hooks.decide(entry(KICK, CommandRequirement.ADMINS), kick, joe, source, false))
    }

    @Test
    fun `sous allow, un prédicat de mod s'évalue avec la source à tous droits et garde ses autres conditions`() {
        // Joe ouvre home par une règle propre, sans règle sur les sélecteurs, que son ensemble vanilla refuse.
        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group()), mapOf(readableForm("Joe") to Player(rules = mapOf("homes:command.home" to Rule.ALLOW)))), generation = 2))
        val (joe, source) = judged("Joe")
        val selectors = Commands.literal("home").requires(Commands.hasPermission(PermissionCheck.Require(Permissions.COMMANDS_ENTITY_SELECTORS))).build()
        val never = Commands.literal("home").requires { false }.build()
        val home = entry(Keys.parse("homes:command.home") as CommandKey, CommandRequirement.MOD_PREDICATE)

        assertFalse(joe.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertTrue(Hooks.decide(home, selectors, joe, source, false))
        assertFalse(Hooks.decide(home, never, joe, source, false))
    }

    @Test
    fun `la forme exigée retire la forme nue à une source jugée`() {
        val (joe, source) = judged("Joe")
        val bareForm = entry(TELEPORT, CommandRequirement.GAMEMASTERS, hidden = true)

        assertFalse(Hooks.decide(bareForm, teleport, joe, source, true))

        Publisher.publish(state(personas, Settings.DELIVERED.copy(namespaces = NamespaceSettings(requirePrefix = false)), generation = 2))
        assertTrue(Hooks.decide(bareForm, teleport, joe, source, true))
    }

    @Test
    fun `une erreur interne de canUse vaut un refus masqué, journalisé une fois`() {
        val broken = HookFixtures.permissions(mapOf(Group.DEFAULT to Group()), mapOf(readableForm("Joe") to Player(groups = listOf("absent"))))
        Publisher.publish(state(broken, generation = 2))
        val (joe, source) = judged("Joe", LevelBasedPermissionSet.OWNER)

        assertFalse(Hooks.decide(entry(KICK), kick, joe, source, true))
        assertFalse(Hooks.decide(entry(KICK), kick, joe, source, true))
        assertEquals(listOf("aegisperms.log.internal_error canUse ${KICK.text} 2"), lines)
    }

    @Test
    fun `la porte laisse passer une source non jugée sans lire l'état`() {
        Publisher.clear()
        val console = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER)

        assertTrue(Hooks.allowsExecution(dispatcher.parse("teleport Joe", console)))
        assertTrue(Hooks.allowsExecution(dispatcher.parse("tp Joe", console)))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `la porte lit les clés du chemin, la cible d'une redirection comprise`() {
        val (bob, bobSource) = judged("Bob", LevelBasedPermissionSet.GAMEMASTER)
        val (joe, joeSource) = judged("Joe", LevelBasedPermissionSet.GAMEMASTER)

        // Bob : moderateur refuse teleport ; tp se juge sur la clé de sa cible. Joe : membre ouvre teleport.
        assertHidden(Hooks.gateRefusal(parse("teleport Joe", bobSource), bob, markOf), deep = false)
        assertHidden(Hooks.gateRefusal(parse("tp Joe", bobSource), bob, markOf), deep = false)
        assertNull(Hooks.gateRefusal(parse("teleport Joe", joeSource), joe, markOf))
        assertNull(Hooks.gateRefusal(parse("tp Joe", joeSource), joe, markOf))

        // Un refus plus bas que la commande racine est un argument inconnu.
        val deeper = HookFixtures.permissions(mapOf(Group.DEFAULT to Group()), mapOf(readableForm("Joe") to Player(rules = mapOf(TELEPORT.text to Rule.ALLOW, TELEPORT.descendant("targets").text to Rule.DENY))))
        Publisher.publish(state(deeper, generation = 2))
        assertHidden(Hooks.gateRefusal(parse("teleport Joe", joeSource), joe, markOf), deep = true)
        assertHidden(Hooks.gateRefusal(parse("tp Joe", joeSource), joe, markOf), deep = true)

        Publisher.publish(state(personas, Settings.DELIVERED.copy(deniedCommands = DeniedCommands.VISIBLE), generation = 3))
        val refusal = Hooks.gateRefusal(parse("teleport Joe", bobSource), bob, markOf)
        assertTrue(refusal != null && refusal.visible && !refusal.deep)
    }

    @Test
    fun `la forme exigée ne vise pas la cible d'une redirection`() {
        val (joe, source) = judged("Joe", LevelBasedPermissionSet.GAMEMASTER)
        marks[teleport] = entry(TELEPORT, CommandRequirement.GAMEMASTERS, hidden = true)

        assertHidden(Hooks.gateRefusal(parse("teleport Joe", source), joe, markOf), deep = false)
        assertNull(Hooks.gateRefusal(parse("tp Joe", source), joe, markOf))
    }

    @Test
    fun `l'arbre du client remplace une redirection vers une forme nue masquée par la forme préfixée`() {
        val prefixedForm = Commands.literal("mc:teleport").build()
        val copy = Commands.literal("mc:teleport").build()
        marks[teleport] = entry(TELEPORT, CommandRequirement.GAMEMASTERS, forms = listOf(prefixedForm))
        val copies = IdentityHashMap<Any?, Any?>()

        assertNull(Hooks.clientRedirect(copies, teleport, markOf))
        copies[prefixedForm] = copy
        assertSame(copy, Hooks.clientRedirect(copies, teleport, markOf))
        // Une redirection vers tp suit sa redirection simple jusqu'à teleport, dont la forme est dans l'arbre.
        assertSame(copy, Hooks.clientRedirect(copies, tp, markOf))
        copies[teleport] = Commands.literal("teleport").build()
        assertSame(copies[teleport], Hooks.clientRedirect(copies, tp, markOf))
        assertEquals(emptyList<String>(), lines)
    }

    /** Une source jugée : une source de compilation qui porte l'enveloppe de [name], et cette enveloppe. */
    private fun judged(name: String, vanilla: LevelBasedPermissionSet = NON_OP): Pair<Envelope, CommandSourceStack> {
        val envelope = Envelope(uuidOf(name), vanilla)
        return envelope to Commands.createCompilationContext(envelope)
    }

    private fun parse(command: String, source: CommandSourceStack): ParseResults<CommandSourceStack> = dispatcher.parse(command, source)

    private fun entry(key: CommandKey, requirement: CommandRequirement = CommandRequirement.NONE, isRoot: Boolean = true, ownKey: CommandKey? = null, hidden: Boolean = false, forms: List<CommandNode<*>> = emptyList()): NodeEntry =
        NodeEntry(key, ownKey, requirement, isRoot, inheritsKey = false, shared = false, isForm = false, hiddenUnderRequirePrefix = hidden, forms = forms)

    private fun assertHidden(refusal: Hooks.GateRefusal?, deep: Boolean) {
        assertTrue(refusal != null && !refusal.visible && refusal.deep == deep, "refus masqué attendu, ${if (deep) "profond" else "à la racine"}")
    }
}
