// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.api

import fr.moulou.aegisperms.hook.AllRights
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.HookFixtures
import fr.moulou.aegisperms.hook.HookFixtures.personas
import fr.moulou.aegisperms.hook.HookFixtures.state
import fr.moulou.aegisperms.hook.HookFixtures.uuidOf
import fr.moulou.aegisperms.hook.LogWriter
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.hook.OpLevels
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Rule
import me.lucko.fabric.api.permissions.v0.OfflinePermissionCheckEvent
import me.lucko.fabric.api.permissions.v0.OptionRequestEvent
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

/** Le fournisseur de l'API de lucko (cahier des charges, 6.4), face à ses trois événements, sans serveur. */
class LuckoPermissionProviderTest {

    companion object {
        private val NON_OP: LevelBasedPermissionSet = LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)

        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        /** L'atome des sélecteurs, tel qu'un consommateur de l'API de lucko l'écrit. */
        private const val SELECTORS = "minecraft.commands.entity_selectors"

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
    fun `une chaîne à points se lit comme un identifiant, et la décision répond TRUE, FALSE ou DEFAULT`() {
        assertEquals(TriState.TRUE, LuckoPermissionProvider.check(RequestSubject.of(Envelope(uuidOf("Bob"), NON_OP)), SELECTORS))
        assertEquals(TriState.DEFAULT, LuckoPermissionProvider.check(RequestSubject.of(Envelope(uuidOf("Joe"), NON_OP)), SELECTORS))
        assertEquals(TriState.DEFAULT, LuckoPermissionProvider.check(RequestSubject.of(LevelBasedPermissionSet.OWNER), SELECTORS))
        assertEquals(TriState.TRUE, LuckoPermissionProvider.check(RequestSubject.of(AllRights), "homes.home.set"))

        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group(rules = mapOf(Keys.ENTITY_SELECTORS.text to Rule.DENY)))), generation = 2))
        assertEquals(TriState.FALSE, LuckoPermissionProvider.check(RequestSubject.of(Envelope(uuidOf("Nina"), LevelBasedPermissionSet.GAMEMASTER)), SELECTORS))
        assertEquals(mapOf(Keys.ENTITY_SELECTORS.text to "lucko", "homes:home/set" to "lucko"), ApiKeyRequests.types())
    }

    @Test
    fun `une chaîne qui ne fait pas une clé reçoit DEFAULT et entre au catalogue comme refusée`() {
        val bob = RequestSubject.of(Envelope(uuidOf("Bob"), NON_OP))

        assertEquals(TriState.DEFAULT, LuckoPermissionProvider.check(bob, "essentialcommands"))
        assertEquals(TriState.DEFAULT, LuckoPermissionProvider.check(bob, "Homes.set"))
        assertEquals(mapOf("essentialcommands" to "lucko", "Homes.set" to "lucko"), ApiKeyRequests.refusedTypes())
        assertTrue(ApiKeyRequests.types().isEmpty())
    }

    @Test
    fun `hors ligne, le sujet est l'UUID, et son niveau d'op retenu ouvre ses groupes d'office`() {
        val staff = Group(includeOps = OpLevel.GAMEMASTERS, rules = mapOf(Keys.ENTITY_SELECTORS.text to Rule.ALLOW))
        Publisher.publish(state(HookFixtures.permissions(mapOf(Group.DEFAULT to Group(), "staff" to staff)), generation = 2))
        OpLevels.record(uuidOf("Olga"), 4)

        assertEquals(TriState.TRUE, LuckoPermissionProvider.check(RequestSubject.of(uuidOf("Olga")), SELECTORS))
        assertEquals(TriState.DEFAULT, LuckoPermissionProvider.check(RequestSubject.of(uuidOf("Nina")), SELECTORS))
    }

    @Test
    fun `les trois événements répondent, la source, l'UUID en future, et l'option vide`() {
        LuckoPermissionProvider.register()
        val bob = Commands.createCompilationContext(Envelope(uuidOf("Bob"), NON_OP))

        assertEquals(TriState.TRUE, PermissionCheckEvent.EVENT.invoker().onPermissionCheck(bob, SELECTORS))
        assertEquals(TriState.TRUE, OfflinePermissionCheckEvent.EVENT.invoker().onPermissionCheck(uuidOf("Bob"), SELECTORS).get())
        assertEquals(Optional.empty<String>(), OptionRequestEvent.EVENT.invoker().onOptionRequest(bob, "prefix"))
    }
}
