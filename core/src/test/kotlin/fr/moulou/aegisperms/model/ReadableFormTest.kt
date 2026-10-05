// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** La forme lisible d'un joueur dans le fichier des droits (cahier des charges, 4.4). */
class ReadableFormTest {

    private val uuidText = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val uuid = UUID.fromString(uuidText)

    @Test
    fun `la forme lisible s'écrit pseudo, croisillon, UUID`() {
        assertEquals("Joe#$uuidText", ReadableForm.of("Joe", uuid))
        assertEquals("Joe" to uuidText, ReadableForm.split("Joe#$uuidText"))
    }

    @Test
    fun `elle se découpe à son dernier croisillon`() {
        assertEquals("Jo#e" to uuidText, ReadableForm.split("Jo#e#$uuidText"))
    }

    @Test
    fun `un pseudo inconnu s'écrit par un point d'interrogation`() {
        assertEquals("?#$uuidText", ReadableForm.of(null, uuid))
        assertEquals(null to uuidText, ReadableForm.split("?#$uuidText"))
    }

    @Test
    fun `sans croisillon, sans pseudo ou sans UUID, la forme n'a pas ses deux parts`() {
        assertNull(ReadableForm.split("Joe"))
        assertNull(ReadableForm.split("#$uuidText"))
        assertNull(ReadableForm.split("Joe#"))
        assertNull(ReadableForm.split(""))
    }

    @Test
    fun `un UUID canonique s'écrit en minuscules avec ses tirets`() {
        assertTrue(ReadableForm.isCanonicalUuid(uuidText))
        assertEquals(uuid, ReadableForm.parseUuid(uuidText))

        // En majuscules, l'UUID se lit, mais il n'est pas canonique.
        assertEquals(uuid, ReadableForm.parseUuid(uuidText.uppercase()))
        assertFalse(ReadableForm.isCanonicalUuid(uuidText.uppercase()))

        assertNull(ReadableForm.parseUuid(uuidText.replace("-", "")))
        assertNull(ReadableForm.parseUuid("pas-un-uuid"))
        assertNull(ReadableForm.parseUuid("f81d4fae-7dec-11d0-a765-00a0c91e6bfz"))
        assertFalse(ReadableForm.isCanonicalUuid("pas-un-uuid"))
    }
}
