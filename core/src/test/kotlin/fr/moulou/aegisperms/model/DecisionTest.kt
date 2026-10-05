// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La décision et le refus du cahier des charges (2.1). */
class DecisionTest {

    @Test
    fun `un refus est une décision deny, visible ou masquée`() {
        assertTrue(Decision.DENY_VISIBLE.isRefusal)
        assertTrue(Decision.DENY_HIDDEN.isRefusal)
        assertFalse(Decision.ALLOW.isRefusal)
        assertFalse(Decision.UNSET.isRefusal)
    }
}
