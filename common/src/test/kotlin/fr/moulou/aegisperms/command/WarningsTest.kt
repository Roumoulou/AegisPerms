// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les avertissements nouveaux d'une modification (cahier des charges, 5.3) : ceux que l'écriture fait naître, pas ceux qu'elle déplace. */
class WarningsTest {

    private val unknownKey = Issue(IssueKind.UNKNOWN_COMMAND_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", "minecraft:command.telport"), listOf(IssueArgument.Literal("minecraft:command.telport")), line = 12)
    private val sensitive = Issue(IssueKind.ALLOW_SENSITIVE, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", "minecraft:command.op"), listOf(IssueArgument.Literal("minecraft:command.op")), line = 14)

    @Test
    fun `un constat qui n'a bougé que de ligne n'est pas nouveau, un constat d'une autre nature ou d'un autre chemin l'est`() {
        val moved = unknownKey.copy(line = 15)
        assertEquals(emptyList<Issue>(), Warnings.newSince(listOf(unknownKey), listOf(moved)))
        assertEquals(listOf(sensitive), Warnings.newSince(listOf(unknownKey), listOf(moved, sensitive)))
        assertEquals(listOf(unknownKey.copy(path = listOf("groups", "staff", "rules", "minecraft:command.telport"))), Warnings.newSince(listOf(unknownKey), listOf(unknownKey.copy(path = listOf("groups", "staff", "rules", "minecraft:command.telport")))))
        assertEquals(listOf(unknownKey, sensitive), Warnings.newSince(emptyList(), listOf(unknownKey, sensitive)))
    }
}
