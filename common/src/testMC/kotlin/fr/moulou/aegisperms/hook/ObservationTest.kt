// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.api.RequestSubject
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.tree.NodeEntry
import fr.moulou.aegisperms.tree.NodeMark
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

/** Le mode observation (cahier des charges, 5.8 et 6.5) : vanilla répond, la décision se calcule, et l'écart se note à l'exécution seulement. */
class ObservationTest {

    companion object {
        private val NON_OP: LevelBasedPermissionSet = LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)
        private val DEFAULT_WRITER: LogWriter = ModLog.writer
        private val SEED: CommandKey = Keys.parse("minecraft:command.seed") as CommandKey
        private val SELECTORS: Permission = Permission.Atom.create(Identifier.fromNamespaceAndPath("minecraft", "commands/entity_selectors"))

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    private val lines = ArrayList<String>()
    private val marks = IdentityHashMap<CommandNode<*>, NodeMark>()
    private val dispatcher = CommandDispatcher<CommandSourceStack>()
    private val seed: CommandNode<CommandSourceStack> = dispatcher.register(Commands.literal("seed").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes { 1 })

    @BeforeEach
    fun observe() {
        ModLog.writer = LogWriter { lines += it.keyAndArguments() }
        ModLog.forgetReported()
        ObservationLog.clear()
        ApiKeyRequests.clear()
        Publisher.publish(HookFixtures.state(HookFixtures.personas))
        Publisher.setMode(Mode.OBSERVE)
        marks[seed] = NodeEntry(SEED, null, CommandRequirement.GAMEMASTERS, isRoot = true, inheritsKey = false, shared = false)
    }

    @AfterEach
    fun release() {
        ModLog.writer = DEFAULT_WRITER
        ObservationLog.clear()
        Publisher.clear()
    }

    @Test
    fun `en observation, canUse répond comme vanilla et ne note rien`() {
        val (nina, source) = judged("Nina")
        assertTrue(Hooks.decide(marks[seed], seed, nina, source, true))
        assertFalse(Hooks.decide(marks[seed], seed, nina, source, false))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `en observation, la porte laisse passer et note l'écart une fois par sujet et par clé`() {
        val (nina, source) = judged("Nina", LevelBasedPermissionSet.GAMEMASTER)
        val parse = dispatcher.parse("seed", source)
        assertTrue(Hooks.allowsExecution(parse) { marks[it] })
        assertTrue(Hooks.allowsExecution(parse) { marks[it] })
        assertEquals(listOf("aegisperms.log.observe_deviation ${nina.subject} ${SEED.text} deny_hidden allow"), lines)
    }

    @Test
    fun `en observation, l'enveloppe répond aux atomes comme l'ensemble vanilla et note l'écart`() {
        val (bob, _) = judged("Bob")
        assertFalse(bob.hasPermission(SELECTORS), "moderateur ouvre les sélecteurs, mais vanilla les refuse à un non-op")
        assertEquals(listOf("aegisperms.log.observe_deviation ${bob.subject} ${Keys.ENTITY_SELECTORS.text} allow deny"), lines)
    }

    @Test
    fun `en observation, les fournisseurs ne répondent pas et notent toute décision autre qu'unset`() {
        val (bob, _) = judged("Bob")
        assertNull(RequestSubject.of(bob).decision(Keys.ENTITY_SELECTORS))
        assertEquals(1, lines.size)
        assertNull(RequestSubject.of(judged("Nina").first).decision(Keys.parse("homes:home/set") as ApiKey))
        assertEquals(1, lines.size, "unset n'est pas un écart")
    }

    @Test
    fun `hors observation, la décision s'applique et le mode posé s'efface à la publication suivante`() {
        Publisher.setMode(Mode.ENFORCE)
        val (nina, source) = judged("Nina")
        assertFalse(Hooks.decide(marks[seed], seed, nina, source, true))
        Publisher.setMode(Mode.OBSERVE)
        assertEquals(Mode.OBSERVE, Publisher.mode())
        Publisher.publish(HookFixtures.state(HookFixtures.personas, generation = 2))
        assertEquals(Mode.ENFORCE, Publisher.mode())
    }

    private fun judged(name: String, vanilla: LevelBasedPermissionSet = NON_OP): Pair<Envelope, CommandSourceStack> {
        val envelope = Envelope(HookFixtures.uuidOf(name), vanilla)
        return envelope to Commands.createCompilationContext(envelope)
    }
}
