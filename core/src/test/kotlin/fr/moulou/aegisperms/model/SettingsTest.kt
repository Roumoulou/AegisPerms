// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les réglages livrés au premier démarrage (cahier des charges, 4.3). */
class SettingsTest {

    @Test
    fun `les réglages livrés sont ceux du fichier du premier démarrage`() {
        val delivered = Settings.DELIVERED
        assertEquals(1, delivered.schemaVersion)
        assertEquals(OpLevel.OWNERS, delivered.adminLevel)
        assertEquals(DeniedCommands.HIDDEN, delivered.deniedCommands)
        assertNull(delivered.refusalMessage)
        assertEquals(Language.AUTO, delivered.language)
        assertEquals(Mode.ENFORCE, delivered.mode)
        assertTrue(delivered.namespaces.enabled)
        assertTrue(delivered.namespaces.requirePrefix)
        assertEquals(mapOf("minecraft" to "mc"), delivered.namespaces.prefixes)
        assertTrue(delivered.namespaces.owners.isEmpty())
        assertTrue(delivered.aliases.isEmpty())
        assertTrue(delivered.watchFiles)
    }
}
