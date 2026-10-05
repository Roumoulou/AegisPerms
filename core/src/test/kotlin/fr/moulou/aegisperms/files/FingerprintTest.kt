// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** L'empreinte d'un fichier, sa taille et sa date de modification (cahier des charges, 4.1). */
class FingerprintTest {

    @TempDir
    lateinit var dir: Path

    private val file: Path get() = dir.resolve("dossier à accents").resolve("permissions.json")

    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    private fun put(text: String, modified: FileTime = stamp): Fingerprint? {
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        Files.setLastModifiedTime(file, modified)
        return Fingerprint.of(file)
    }

    @Test
    fun `un fichier absent n'a pas d'empreinte`() {
        assertNull(Fingerprint.of(file))
        put("{}")
        Files.delete(file)
        assertNull(Fingerprint.of(file))
    }

    @Test
    fun `l'empreinte est la taille du fichier en octets et sa date de modification`() {
        assertEquals(Fingerprint(size = 2, modified = stamp), put("{}"))
        // Un accent pèse deux octets en UTF-8.
        assertEquals(Fingerprint(size = 3, modified = stamp), put("{é"))
    }

    @Test
    fun `la taille seule ou la date seule suffisent à changer l'empreinte, et le contenu seul ne la change pas`() {
        val first = put("{ \"a\": 1 }")
        assertEquals(first, Fingerprint.of(file))
        // La limite assumée : à taille et à date égales, un autre contenu garde la même empreinte.
        assertEquals(first, put("{ \"b\": 2 }"))

        assertNotEquals(first, put("{ \"a\": 10 }"))
        assertNotEquals(first, put("{ \"a\": 1 }", modified = FileTime.fromMillis(stamp.toMillis() + 2_000)))
    }
}
