// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.hook.HookFixtures.personas
import fr.moulou.aegisperms.hook.HookFixtures.readableForm
import fr.moulou.aegisperms.hook.HookFixtures.state
import fr.moulou.aegisperms.hook.HookFixtures.uuidOf
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import net.fabricmc.fabric.impl.permission.CommandPermissionContext
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.Permissions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** L'enveloppe face aux classes du jeu et à l'API de Fabric (cahier des charges, 6.2 et 8.4) : l'étage 1, rejoué par chaque module de version. */
class EnvelopeTest {

    companion object {
        /** L'ensemble vanilla d'un non-op : le champ `ALL` est déprécié dans les trois versions, et le jeu le rend pourtant à tout non-op (`getProfilePermissions`). */
        private val NON_OP: LevelBasedPermissionSet = LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)

        private val SINGLETONS: List<LevelBasedPermissionSet> = listOf(NON_OP, LevelBasedPermissionSet.MODERATOR, LevelBasedPermissionSet.GAMEMASTER, LevelBasedPermissionSet.ADMIN, LevelBasedPermissionSet.OWNER)

        /** Un atome qu'aucun mod ne demande et qu'aucune règle ne nomme. */
        private val UNKNOWN_ATOM: Permission = Permission.Atom.create(Identifier.fromNamespaceAndPath("aegisperms_test", "unknown"))

        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    /** Les lignes du journal pendant le test. */
    private val lines = ArrayList<String>()

    @BeforeEach
    fun publishPersonas() {
        ModLog.writer = LogWriter { lines += it.keyAndArguments() }
        ModLog.forgetReported()
        ApiKeyRequests.clear()
        Publisher.publish(state(personas))
    }

    @AfterEach
    fun releaseLog() {
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `un niveau de commande reçoit la réponse de l'ensemble vanilla`() {
        val moderator = Envelope(uuidOf("Bob"), LevelBasedPermissionSet.MODERATOR)
        val admin = Envelope(uuidOf("Alice"), LevelBasedPermissionSet.ADMIN)

        assertTrue(moderator.hasPermission(Permissions.COMMANDS_MODERATOR))
        assertFalse(moderator.hasPermission(Permissions.COMMANDS_GAMEMASTER))
        assertTrue(admin.hasPermission(Permissions.COMMANDS_GAMEMASTER))
        assertTrue(admin.hasPermission(Permission.HasCommandLevel(PermissionLevel.ADMINS)))
        assertFalse(admin.hasPermission(Permissions.COMMANDS_OWNER))
    }

    @Test
    fun `un atome reçoit la décision du sujet, et la réponse de l'ensemble vanilla sous unset`() {
        // Bob : moderateur ouvre les sélecteurs à un non-op. Joe : aucune règle ne les nomme, vanilla décide par le niveau.
        assertTrue(Envelope(uuidOf("Bob"), NON_OP).hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertFalse(Envelope(uuidOf("Joe"), NON_OP).hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertTrue(Envelope(uuidOf("Joe"), LevelBasedPermissionSet.GAMEMASTER).hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))

        // Un refus sur default ferme les sélecteurs à un op à qui vanilla les accorderait.
        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group(rules = mapOf(Keys.ENTITY_SELECTORS.text to Rule.DENY)))), generation = 2))
        assertFalse(Envelope(uuidOf("Nina"), LevelBasedPermissionSet.GAMEMASTER).hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
    }

    @Test
    fun `un atome inconnu sans règle est refusé, comme en vanilla`() {
        // Alice a `command` à allow : une clé de commande ne couvre pas une clé d'API (2.4).
        assertFalse(Envelope(uuidOf("Alice"), LevelBasedPermissionSet.OWNER).hasPermission(UNKNOWN_ATOM))
        assertFalse(LevelBasedPermissionSet.OWNER.hasPermission(UNKNOWN_ATOM))
    }

    @Test
    fun `union avec chacun des cinq singletons rend ce que vanilla rend, jamais l'enveloppe`() {
        for (vanilla in SINGLETONS) {
            val envelope = Envelope(uuidOf("Joe"), vanilla)
            for (other in SINGLETONS) {
                val union = envelope.union(other)
                assertSame(vanilla.union(other), union)
                assertFalse(union is Envelope)
            }
        }
    }

    @Test
    fun `l'enveloppe est un LevelBasedPermissionSet de son niveau, que l'API de Fabric lit`() {
        val admin: PermissionSet = Envelope(uuidOf("Alice"), LevelBasedPermissionSet.ADMIN)
        val nonOp: PermissionSet = Envelope(uuidOf("Joe"), NON_OP)

        assertTrue(admin is LevelBasedPermissionSet)
        assertEquals(PermissionLevel.ADMINS, (admin as LevelBasedPermissionSet).level())
        assertEquals(PermissionLevel.ADMINS, CommandPermissionContext.extractPermissionLevel(admin))
        assertEquals(PermissionLevel.ALL, CommandPermissionContext.extractPermissionLevel(nonOp))
    }

    @Test
    fun `AllRights répond oui à un niveau comme à un atome`() {
        assertEquals(PermissionLevel.OWNERS, AllRights.level())
        assertTrue(AllRights.hasPermission(Permissions.COMMANDS_OWNER))
        assertTrue(AllRights.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertTrue(AllRights.hasPermission(UNKNOWN_ATOM))
        assertSame(AllRights, AllRights.union(NON_OP))
    }

    @Test
    fun `un atome demandé s'inscrit sous la voie atom, une seule fois`() {
        val bob = Envelope(uuidOf("Bob"), NON_OP)
        bob.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)
        bob.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)
        Envelope(uuidOf("Joe"), LevelBasedPermissionSet.OWNER).hasPermission(UNKNOWN_ATOM)

        assertEquals(mapOf(Keys.ENTITY_SELECTORS.text to "atom", "aegisperms_test:unknown" to "atom"), ApiKeyRequests.types())
        assertFalse(ApiKeyRequests.request(Keys.ENTITY_SELECTORS, ApiRoute.ATOM))
    }

    @Test
    fun `une erreur interne vaut un refus et se journalise une fois par génération`() {
        // Un état qu'aucune validation n'a admis : Joe nomme un groupe absent, et son ordre d'examen lève.
        val broken = HookFixtures.permissions(mapOf(Group.DEFAULT to Group()), mapOf(readableForm("Joe") to Player(groups = listOf("absent"))))
        Publisher.publish(state(broken, generation = 2))
        val joe = Envelope(uuidOf("Joe"), LevelBasedPermissionSet.GAMEMASTER)

        assertFalse(joe.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertFalse(joe.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertEquals(listOf("aegisperms.log.internal_error envelope ${Keys.ENTITY_SELECTORS.text} 2"), lines)

        Publisher.publish(state(broken, generation = 3))
        assertFalse(joe.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertEquals(listOf("aegisperms.log.internal_error envelope ${Keys.ENTITY_SELECTORS.text} 2", "aegisperms.log.internal_error envelope ${Keys.ENTITY_SELECTORS.text} 3"), lines)
    }

    @Test
    fun `sans état publié, un atome est refusé et l'erreur se journalise`() {
        Publisher.clear()
        val joe = Envelope(uuidOf("Joe"), LevelBasedPermissionSet.GAMEMASTER)

        assertFalse(joe.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS))
        assertTrue(joe.hasPermission(Permissions.COMMANDS_GAMEMASTER))
        assertEquals(listOf("aegisperms.log.internal_error envelope ${Keys.ENTITY_SELECTORS.text} 0"), lines)
    }
}
