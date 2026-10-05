// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les niveaux d'op du cahier des charges (2.5), ceux de `admin-level` et d'`include-ops`. */
class OpLevelTest {

    @Test
    fun `les niveaux d'op vont de moderators à owners, de 1 à 4`() {
        assertEquals(listOf(OpLevel.MODERATORS, OpLevel.GAMEMASTERS, OpLevel.ADMINS, OpLevel.OWNERS), OpLevel.entries)
        assertEquals(listOf(1, 2, 3, 4), OpLevel.entries.map { it.level })
    }

    @Test
    fun `un op atteint un niveau quand le sien est au moins égal`() {
        assertTrue(OpLevel.OWNERS.isReachedBy(4))
        assertFalse(OpLevel.OWNERS.isReachedBy(3))
        assertTrue(OpLevel.MODERATORS.isReachedBy(4))
        assertTrue(OpLevel.MODERATORS.isReachedBy(1))
        assertFalse(OpLevel.MODERATORS.isReachedBy(0))
    }
}
