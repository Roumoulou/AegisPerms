// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** L'ordre des noms, point de code par point de code (cahier des charges, 2.6). */
class CodePointOrderTest {

    @Test
    fun `les chaînes se comparent point de code par point de code, pas par unité UTF-16`() {
        val lastOfBasicPlane = "￿"
        val beyondBasicPlane = "😀"

        // L'ordre naturel de String compare les unités UTF-16 : la paire de substitution, 0xD83D, y passe avant 0xFFFF.
        assertTrue(lastOfBasicPlane > beyondBasicPlane)
        assertTrue(CodePointOrder.compare(lastOfBasicPlane, beyondBasicPlane) < 0)
        assertTrue(CodePointOrder.compare(beyondBasicPlane, lastOfBasicPlane) > 0)
    }

    @Test
    fun `une chaîne passe avant celles qu'elle commence, et aucune langue ne s'en mêle`() {
        assertEquals(listOf("admin", "default", "membre", "moderateur", "vip"), listOf("vip", "membre", "admin", "moderateur", "default").sortedWith(CodePointOrder))
        assertTrue(CodePointOrder.compare("vip", "vip2") < 0)
        assertEquals(0, CodePointOrder.compare("vip", "vip"))
        assertTrue(CodePointOrder.compare("Z", "a") < 0)
        assertTrue(CodePointOrder.compare("z", "é") < 0)
    }
}
