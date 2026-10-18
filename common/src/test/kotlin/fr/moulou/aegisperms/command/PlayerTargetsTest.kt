// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** La résolution d'un `<joueur>` (cahier des charges, 5.4), sur des sources simulées. */
class PlayerTargetsTest {

    companion object {
        private val BOB_ONLINE: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000001")
        private val BOB_CACHED: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000002")
        private val BOB_IN_FILE: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000003")
        private val JOE: UUID = UUID.fromString("0e000000-0000-4000-8000-000000000001")
    }

    @Test
    fun `un UUID écrit tel quel désigne ce joueur, venu ou jamais venu, avec le pseudo connu`() {
        val lookup = FakeLookup(names = mapOf(JOE to "Joe"))
        assertEquals(TargetResolution.Found(PlayerTarget(JOE, "Joe")), PlayerTargets.resolve(JOE.toString(), lookup))
        assertEquals(TargetResolution.Found(PlayerTarget(BOB_ONLINE, null)), PlayerTargets.resolve(" $BOB_ONLINE ", lookup))
    }

    @Test
    fun `un UUID sans tirets vaut sa forme canonique`() {
        assertEquals(TargetResolution.Found(PlayerTarget(JOE, "Joe")), PlayerTargets.resolve(JOE.toString().replace("-", ""), FakeLookup(names = mapOf(JOE to "Joe"))))
        assertEquals(JOE, PlayerTargets.parseUuid(JOE.toString().replace("-", "").uppercase()))
    }

    @Test
    fun `un pseudo se cherche chez le joueur en ligne, au cache de profils et dans le fichier, sans la casse`() {
        assertEquals(TargetResolution.Found(PlayerTarget(BOB_ONLINE, "Bob")), PlayerTargets.resolve("Bob", FakeLookup(online = mapOf("Bob" to PlayerTarget(BOB_ONLINE, "Bob")))))
        assertEquals(TargetResolution.Found(PlayerTarget(BOB_CACHED, "Bob")), PlayerTargets.resolve("Bob", FakeLookup(cached = mapOf("Bob" to PlayerTarget(BOB_CACHED, "Bob")))))
        assertEquals(TargetResolution.Found(PlayerTarget(BOB_IN_FILE, "Bob")), PlayerTargets.resolve("bob", FakeLookup(known = listOf(PlayerTarget(BOB_IN_FILE, "Bob"), PlayerTarget(JOE, "Joe")))))
    }

    @Test
    fun `deux UUID différents entre les sources font refuser, avec les UUID en présence`() {
        val disagreeing = FakeLookup(online = mapOf("Bob" to PlayerTarget(BOB_ONLINE, "Bob")), known = listOf(PlayerTarget(BOB_IN_FILE, "Bob")))
        assertEquals(TargetResolution.Ambiguous("Bob", listOf(BOB_ONLINE, BOB_IN_FILE)), PlayerTargets.resolve("Bob", disagreeing))
        val twiceInFile = FakeLookup(cached = mapOf("Bob" to PlayerTarget(BOB_CACHED, "Bob")), known = listOf(PlayerTarget(BOB_IN_FILE, "Bob"), PlayerTarget(BOB_ONLINE, "bob")))
        assertEquals(TargetResolution.Ambiguous("Bob", listOf(BOB_CACHED, BOB_IN_FILE, BOB_ONLINE)), PlayerTargets.resolve("Bob", twiceInFile))
        // Le même UUID chez plusieurs sources ne fait pas un désaccord.
        assertEquals(TargetResolution.Found(PlayerTarget(BOB_ONLINE, "Bob")), PlayerTargets.resolve("Bob", FakeLookup(online = mapOf("Bob" to PlayerTarget(BOB_ONLINE, "Bob")), known = listOf(PlayerTarget(BOB_ONLINE, "Bob")))))
    }

    @Test
    fun `un pseudo que rien ne connaît est inconnu`() {
        assertEquals(TargetResolution.Unknown("Nina"), PlayerTargets.resolve("Nina", FakeLookup()))
    }

    /** Des sources qui ne connaissent que ce qu'on leur donne. */
    private class FakeLookup(
        private val online: Map<String, PlayerTarget> = emptyMap(),
        private val cached: Map<String, PlayerTarget> = emptyMap(),
        private val names: Map<UUID, String> = emptyMap(),
        private val known: List<PlayerTarget> = emptyList(),
    ) : PlayerLookup {
        override fun online(name: String): PlayerTarget? = online[name]
        override fun cached(name: String): PlayerTarget? = cached[name]
        override fun nameOf(uuid: UUID): String? = names[uuid]
        override fun knownPlayers(): Collection<PlayerTarget> = known
    }
}
