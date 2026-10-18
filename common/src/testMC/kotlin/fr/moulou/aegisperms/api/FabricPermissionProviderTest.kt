// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.api

import fr.moulou.aegisperms.hook.AllRights
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.HookFixtures
import fr.moulou.aegisperms.hook.HookFixtures.personas
import fr.moulou.aegisperms.hook.HookFixtures.readableForm
import fr.moulou.aegisperms.hook.HookFixtures.state
import fr.moulou.aegisperms.hook.HookFixtures.uuidOf
import fr.moulou.aegisperms.hook.LogWriter
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.hook.OpLevels
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import net.fabricmc.fabric.api.permission.v1.MutablePermissionContext
import net.fabricmc.fabric.api.permission.v1.PermissionContext
import net.fabricmc.fabric.api.permission.v1.PermissionNode
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Le fournisseur de l'API de permissions de Fabric (cahier des charges, 6.3), face à ses contextes et à ses nœuds, sans serveur. */
class FabricPermissionProviderTest {

    companion object {
        private val NON_OP: LevelBasedPermissionSet = LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)

        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        /** La clé de l'atome des sélecteurs, demandée comme un nœud booléen de l'API de Fabric. */
        private val SELECTORS: PermissionNode<Boolean> = PermissionNode.of("minecraft", "commands/entity_selectors")

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    private val lines = ArrayList<String>()

    @BeforeEach
    fun publishPersonas() {
        ModLog.writer = LogWriter { lines += it.keyAndArguments() }
        ModLog.forgetReported()
        ApiKeyRequests.clear()
        OpLevels.clear()
        Publisher.publish(state(personas))
    }

    @AfterEach
    fun releaseLog() {
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `une demande booléenne reçoit true, false ou null selon la décision du sujet désigné par UUID`() {
        // Bob : moderateur ouvre les sélecteurs. Joe : aucune règle ne les nomme.
        assertEquals(true, FabricPermissionProvider.answer(playerContext("Bob"), SELECTORS))
        assertNull(FabricPermissionProvider.answer(playerContext("Joe"), SELECTORS))

        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group(rules = mapOf(Keys.ENTITY_SELECTORS.text to Rule.DENY)))), generation = 2))
        assertEquals(false, FabricPermissionProvider.answer(playerContext("Nina"), SELECTORS))
        assertEquals(mapOf(Keys.ENTITY_SELECTORS.text to "fabric"), ApiKeyRequests.types())
    }

    @Test
    fun `le niveau d'op retenu d'un sujet désigné par UUID ouvre ses groupes d'office`() {
        val staff = Group(includeOps = OpLevel.GAMEMASTERS, rules = mapOf(Keys.ENTITY_SELECTORS.text to Rule.ALLOW))
        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group(), "staff" to staff)), generation = 2))
        OpLevels.record(uuidOf("Olga"), 4)

        // Le niveau d'op du contexte, que le consommateur a pu fixer lui-même, n'est pas lu (6.3) : seul le niveau d'op retenu compte.
        assertEquals(true, FabricPermissionProvider.answer(playerContext("Olga", PermissionLevel.ALL), SELECTORS))
        assertNull(FabricPermissionProvider.answer(playerContext("Nina", PermissionLevel.OWNERS), SELECTORS))
    }

    @Test
    fun `le sujet d'une source est son autorité, jamais l'entité, et la source à tous droits dit oui`() {
        val judged = playerContext("Nina").set(PermissionContext.COMMAND_SOURCE_STACK, Commands.createCompilationContext(Envelope(uuidOf("Bob"), NON_OP)))
        val console = playerContext("Bob").set(PermissionContext.COMMAND_SOURCE_STACK, Commands.createCompilationContext(LevelBasedPermissionSet.OWNER))
        val allRights = playerContext("Nina").set(PermissionContext.COMMAND_SOURCE_STACK, Commands.createCompilationContext(AllRights))

        // La source porte l'enveloppe de Bob : c'est lui le sujet, pas l'UUID du contexte.
        assertEquals(true, FabricPermissionProvider.answer(judged, SELECTORS))
        assertNull(FabricPermissionProvider.answer(console, SELECTORS))
        assertEquals(true, FabricPermissionProvider.answer(allRights, SELECTORS))
        assertNull(FabricPermissionProvider.answer(PermissionContext.create(uuidOf("Bob"), PermissionContext.Type.ENTITY, PermissionLevel.ALL), SELECTORS))
        assertNull(FabricPermissionProvider.answer(PermissionContext.create(uuidOf("Bob"), PermissionContext.Type.SYSTEM, PermissionLevel.OWNERS), SELECTORS))
    }

    @Test
    fun `un codec autre que booléen reçoit null et sa clé entre au catalogue, un chemin de commande ne répond pas`() {
        assertNull(FabricPermissionProvider.answer(playerContext("Bob"), PermissionNode.ofInteger("homes", "home/limit")))
        assertNull(FabricPermissionProvider.answer(playerContext("Bob"), PermissionNode.of("minecraft", "command.teleport")))

        assertEquals(mapOf("homes:home/limit" to "fabric"), ApiKeyRequests.types())
    }

    @Test
    fun `une erreur interne répond null et se journalise une fois`() {
        val broken = HookFixtures.permissions(mapOf(Group.DEFAULT to Group()), mapOf(readableForm("Joe") to Player(groups = listOf("absent"))))
        Publisher.publish(state(broken, generation = 2))

        assertNull(FabricPermissionProvider.answer(playerContext("Joe"), SELECTORS))
        assertNull(FabricPermissionProvider.answer(playerContext("Joe"), SELECTORS))
        assertEquals(listOf("aegisperms.log.internal_error fabricApi ${Keys.ENTITY_SELECTORS.text} 2"), lines)
    }

    /** Un contexte de joueur sans source de commande, tel qu'un consommateur le bâtit pour un joueur hors ligne. */
    private fun playerContext(name: String, level: PermissionLevel = PermissionLevel.ALL): MutablePermissionContext = PermissionContext.create(uuidOf(name), PermissionContext.Type.PLAYER, level)
}
