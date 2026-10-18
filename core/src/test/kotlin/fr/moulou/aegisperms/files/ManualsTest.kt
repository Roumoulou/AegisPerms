// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Les manuels des commandes (cahier des charges, 5.10) : le fichier, son nom, et le refus d'écraser. */
class ManualsTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("aegisperms")

    private val manuals: Path get() = config.resolve("manuals")

    /** Une horloge arrêtée à 23:30 UTC, sur un serveur à l'heure de Paris, où le jour suivant a commencé. */
    private val clock = Clock.fixed(Instant.parse("2026-10-18T23:30:00Z"), ZoneId.of("Europe/Paris"))

    @Test
    fun `un manuel s'écrit en UTF-8 sans BOM et en LF, sous la date locale et la langue, et n'en écrase jamais un autre`() {
        ConfigurationFiles(config, clock).use { files ->
            assertEquals(ManualWrite.Done("commands-2026-10-19-fr.md"), files.writeManual("fr", "# Été\n\nUne ligne.\n"))
            assertArrayEquals("# Été\n\nUne ligne.\n".toByteArray(Charsets.UTF_8), Files.readAllBytes(manuals.resolve("commands-2026-10-19-fr.md")))
            assertEquals(ManualWrite.Done("commands-2026-10-19-fr-2.md"), files.writeManual("fr", "# Deux\n"))
            assertEquals(ManualWrite.Done("commands-2026-10-19-fr-3.md"), files.writeManual("fr", "# Trois\n"))
            assertEquals(ManualWrite.Done("commands-2026-10-19-en.md"), files.writeManual("en", "# One\n"))
        }
        assertEquals("# Été\n\nUne ligne.\n", Files.readString(manuals.resolve("commands-2026-10-19-fr.md")))
        assertEquals("# Deux\n", Files.readString(manuals.resolve("commands-2026-10-19-fr-2.md")))
        val names = Files.list(manuals).use { found -> found.map { it.fileName.toString() }.sorted().toList() }
        assertEquals(listOf("commands-2026-10-19-en.md", "commands-2026-10-19-fr-2.md", "commands-2026-10-19-fr-3.md", "commands-2026-10-19-fr.md"), names, "aucun fichier temporaire ne reste")
    }

    @Test
    fun `une panne d'écriture rend sa cause au lieu de remonter`() {
        Files.createDirectories(config)
        Files.writeString(config.resolve("manuals"), "un fichier à la place du dossier")
        ConfigurationFiles(config, clock).use { files ->
            val result = files.writeManual("fr", "# Rien\n")
            assertTrue(result is ManualWrite.Failed, result.toString())
        }
    }
}
