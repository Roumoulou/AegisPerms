// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Les écarts du mode observation (cahier des charges, 5.8) : une fois par sujet, par clé et par génération, soixante par minute, le compte à la minute suivante. */
class ObservationLogTest {

    private val events = ArrayList<Pair<String, List<Any?>>>()
    private var now = 0L
    private val bob: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000001")

    @BeforeEach
    fun capture() {
        ObservationLog.clear()
        ObservationLog.sink = { event, arguments -> events += event to arguments }
        ObservationLog.clock = { now }
        Publisher.publish(state(1))
    }

    @AfterEach
    fun release() {
        ObservationLog.sink = null
        ObservationLog.clock = System::currentTimeMillis
        ObservationLog.clear()
        Publisher.clear()
    }

    @Test
    fun `un écart se dit une fois par sujet, par clé et par génération`() {
        ObservationLog.deviation(bob, "minecraft:command.seed", Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
        ObservationLog.deviation(bob, "minecraft:command.seed", Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
        ObservationLog.deviation(bob, "minecraft:command.kick", Decision.ALLOW, ObservationLog.VANILLA_REFUSES)
        assertEquals(
            listOf(
                ModLog.OBSERVE_DEVIATION to listOf<Any?>(bob, "minecraft:command.seed", "deny_hidden", "allow"),
                ModLog.OBSERVE_DEVIATION to listOf<Any?>(bob, "minecraft:command.kick", "allow", "deny"),
            ),
            events,
        )

        Publisher.publish(state(2))
        ObservationLog.deviation(bob, "minecraft:command.seed", Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
        assertEquals(3, events.size)
    }

    @Test
    fun `au-delà de soixante lignes dans la minute, le journal compte et dit le total à la minute suivante`() {
        for (i in 1..70) ObservationLog.deviation(bob, "minecraft:command.k$i", Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
        assertEquals(ObservationLog.MAX_LINES_PER_MINUTE, events.size)

        now += 60_000
        ObservationLog.deviation(bob, "minecraft:command.next", Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
        assertEquals(ModLog.OBSERVE_SUPPRESSED to listOf<Any?>(10), events[ObservationLog.MAX_LINES_PER_MINUTE])
        assertEquals(ModLog.OBSERVE_DEVIATION, events.last().first)
        assertEquals(ObservationLog.MAX_LINES_PER_MINUTE + 2, events.size)
    }

    private fun state(generation: Long): EffectiveState = EffectiveState(generation, Configuration(Settings.DELIVERED, Permissions.DELIVERED))
}
