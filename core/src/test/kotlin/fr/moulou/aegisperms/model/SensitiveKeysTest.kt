// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les clés sensibles du cahier des charges (2.9). */
class SensitiveKeysTest {

    @Test
    fun `les dix-huit clés sensibles sont celles du tableau de 2 point 9`() {
        val expected = mapOf(
            "minecraft:command.function" to SensitiveReason.FUNCTIONS,
            "minecraft:command.schedule.function" to SensitiveReason.FUNCTIONS,
            "minecraft:command.execute.if.function" to SensitiveReason.FUNCTIONS,
            "minecraft:command.execute.unless.function" to SensitiveReason.FUNCTIONS,
            "minecraft:command.reload" to SensitiveReason.FUNCTIONS,
            "minecraft:command.setblock" to SensitiveReason.COMMAND_BLOCKS,
            "minecraft:command.fill" to SensitiveReason.COMMAND_BLOCKS,
            "minecraft:command.clone" to SensitiveReason.COMMAND_BLOCKS,
            "minecraft:command.data" to SensitiveReason.COMMAND_BLOCKS,
            "minecraft:command.summon" to SensitiveReason.COMMAND_MINECART,
            "minecraft:command.execute.summon" to SensitiveReason.COMMAND_MINECART,
            "minecraft:command.give" to SensitiveReason.LECTERN_BOOK,
            "minecraft:command.item" to SensitiveReason.LECTERN_BOOK,
            "minecraft:command.execute.store" to SensitiveReason.DATA_WRITING,
            "minecraft:command.execute.as" to SensitiveReason.IMPERSONATION,
            "minecraft:command.execute.on" to SensitiveReason.IMPERSONATION,
            "minecraft:command.op" to SensitiveReason.OP_CHANGE,
            "minecraft:command.deop" to SensitiveReason.OP_CHANGE,
        )

        assertEquals(18, SensitiveKeys.all.size)
        assertEquals(expected, SensitiveKeys.all.associate { it.key.text to it.reason })
    }

    @Test
    fun `une clé sensible dit sa raison, une autre clé n'en a pas`() {
        assertEquals(SensitiveReason.OP_CHANGE, SensitiveKeys.reasonFor(Keys.parse("minecraft:command.op") as CommandKey))
        assertNull(SensitiveKeys.reasonFor(Keys.parse("minecraft:command.help") as CommandKey))
        assertNull(SensitiveKeys.reasonFor(Keys.parse("minecraft:command.execute") as CommandKey))
    }

    @Test
    fun `chaque raison a sa clé de traduction et rend au moins une clé sensible`() {
        assertEquals("aegisperms.issue.allow_sensitive.op_change", SensitiveReason.OP_CHANGE.translationKey)
        assertEquals(SensitiveReason.entries.toSet(), SensitiveKeys.all.map { it.reason }.toSet())
    }
}
