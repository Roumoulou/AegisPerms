// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le groupe du cahier des charges (2.5), sous la forme que le fichier des droits lui donne (4.4). */
class GroupTest {

    @Test
    fun `un nom de groupe s'écrit en minuscules, chiffres, tiret bas et trait d'union`() {
        for (name in listOf("default", "moderateur", "vip_2", "a-b", "0", "x".repeat(100))) assertTrue(Group.isValidName(name), name)
        for (name in listOf("", "Admin", "mal nommé", "modérateur", "a.b", "a:b")) assertFalse(Group.isValidName(name), name)
    }

    @Test
    fun `un groupe sans rien d'écrit a un poids de 0, ni parent, ni membre d'office, ni règle`() {
        val group = Group()
        assertEquals(0, group.weight)
        assertTrue(group.parents.isEmpty())
        assertNull(group.includeOps)
        assertTrue(group.rules.isEmpty())
        assertNull(group.note)
    }
}
