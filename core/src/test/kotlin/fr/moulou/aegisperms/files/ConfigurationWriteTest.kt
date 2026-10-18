// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name

/** L'écriture du fichier des droits, et la protection des écritures croisées par les empreintes (cahier des charges, 4.8). */
class ConfigurationWriteTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val settingsPath: Path get() = config.resolve("settings.json")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    /** Une date ancienne, celle d'une édition faite par un autre. */
    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    private val personasText = JsonFileFormat.PERMISSIONS.encodeToText(Permissions.serializer(), Fixtures.personas)

    /** Des droits valides, écrits à la main par un autre que le mod. */
    private val byHand = "{ \"schema-version\": 1, \"groups\": { \"default\": {}, \"vip\": { \"weight\": 80 } } }"

    @Test
    fun `une écriture écrit le fichier des droits entier, à l'octet près, sans rien laisser à côté`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            val written = files.write(Fixtures.personas).written()
            assertEquals(Configuration(Settings.DELIVERED, Fixtures.personas), written.configuration)
            assertTrue(written.report.isValid)
        }
        assertArrayEquals(personasText.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
        assertEquals(setOf("permissions.json", "settings.json"), Files.list(config).use { found -> found.map { it.name }.toList().toSet() })
    }

    @Test
    fun `les avertissements d'une écriture portent la ligne du fichier écrit`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            // La règle propre de Jogger sur `rotation` est sans effet, sous le refus de `location` (2.11) : la validation la signale.
            val warning = files.write(Fixtures.personas).written().report.issues.single { it.kind == IssueKind.ALLOW_UNDER_REFUSAL && it.path.first() == "players" }
            val expectedLine = personasText.lines().indexOfFirst { it.contains("\"minecraft:command.teleport.targets.location.rotation\"") } + 1
            assertTrue(expectedLine > 1)
            assertEquals(expectedLine, warning.line)
        }
    }

    @Test
    fun `une écriture du mod n'est pas un changement extérieur, et un rechargement la relit`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())

            val written = files.write(Fixtures.personas).written()
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())
            assertEquals(written.configuration, files.reload().valid().configuration)
            assertEquals(written.configuration, files.write(Fixtures.personas).written().configuration)
        }
    }

    @Test
    fun `le mod refuse d'écrire sur un fichier qui a bougé, et un rechargement rouvre l'écriture`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            edit(permissionsPath, byHand)
            assertEquals(setOf(ConfigurationFile.PERMISSIONS), files.changedOutside())

            assertEquals(ConfigurationWrite.FileMoved, files.write(Fixtures.personas))
            assertArrayEquals(byHand.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
            assertEquals(stamp, Files.getLastModifiedTime(permissionsPath))

            assertEquals(setOf("default", "vip"), files.reload().valid().configuration.permissions.groups.keys)
            files.write(Fixtures.personas).written()
            assertArrayEquals(personasText.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
        }
    }

    @Test
    fun `la date seule, ou la taille seule, font un fichier qui a bougé`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            Files.setLastModifiedTime(permissionsPath, stamp)
            assertEquals(ConfigurationWrite.FileMoved, files.write(Fixtures.personas))

            files.reload().valid()
            // Un texte plus long, remis à la date que le mod a retenue.
            edit(permissionsPath, DeliveredTexts.PERMISSIONS + "\n")
            assertEquals(ConfigurationWrite.FileMoved, files.write(Fixtures.personas))
        }
    }

    @Test
    fun `des droits qui ne font pas une configuration valide ne s'écrivent pas`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            edit(permissionsPath, byHand)
            files.reload().valid()

            val refused = files.write(Fixtures.permissions(groups = mapOf("vip" to Group(parents = listOf("membre")))))
            assertEquals(setOf(IssueKind.MISSING_DEFAULT, IssueKind.UNKNOWN_PARENT), (refused as ConfigurationWrite.Refused).report.issues.map { it.kind }.toSet())
            assertArrayEquals(byHand.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
            assertEquals(stamp, Files.getLastModifiedTime(permissionsPath))

            files.write(Fixtures.personas).written()
        }
    }

    @Test
    fun `sans configuration valide lue, les droits sont en lecture seule`() {
        ConfigurationFiles(config).use { files ->
            // Avant toute lecture.
            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))
            assertFalse(Files.exists(config))

            // Après un rechargement refusé, pour les droits puis pour les réglages.
            files.reload().valid()
            edit(permissionsPath, "{ \"schema-version\": 1, \"groups\": {} }")
            assertTrue(files.reload() is ConfigurationRead.Refused)
            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))

            edit(permissionsPath, byHand)
            edit(settingsPath, "{ \"schema-version\": 1, \"mode\": \"regarde\" }")
            assertTrue(files.reload() is ConfigurationRead.Refused)
            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))
            assertArrayEquals(byHand.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))

            // La prochaine configuration valide rouvre l'écriture.
            edit(settingsPath, "{ \"schema-version\": 1, \"mode\": \"observe\" }")
            files.reload().valid()
            assertEquals(Mode.OBSERVE, files.write(Fixtures.personas).written().configuration.settings.mode)
        }
    }

    @Test
    fun `le fichier des réglages n'est jamais écrit`() {
        val settingsText = "{ \"schema-version\": 1, \"mode\": \"observe\" }"
        edit(settingsPath, settingsText)
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            files.write(Fixtures.personas).written()
            files.reload().valid()
        }
        assertArrayEquals(settingsText.toByteArray(Charsets.UTF_8), Files.readAllBytes(settingsPath))
        assertEquals(stamp, Files.getLastModifiedTime(settingsPath))
    }

    @Test
    fun `changedOutside nomme le fichier changé, et un rechargement, même refusé, remet le compte à zéro`() {
        ConfigurationFiles(config).use { files ->
            files.reload().valid()
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())

            edit(settingsPath, "{ \"schema-version\": 1, \"mode\": \"observe\" }")
            assertEquals(setOf(ConfigurationFile.SETTINGS), files.changedOutside())
            files.reload().valid()
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())

            edit(permissionsPath, "{ \"schema-version\": 1, \"groups\": {}, }")
            assertEquals(setOf(ConfigurationFile.PERMISSIONS), files.changedOutside())
            assertTrue(files.reload() is ConfigurationRead.Refused)
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())

            Files.delete(permissionsPath)
            assertEquals(setOf(ConfigurationFile.PERMISSIONS), files.changedOutside())
            assertTrue(files.reload() is ConfigurationRead.Refused)
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())
        }
    }

    /** Une édition faite par un autre : le texte, et une date qui n'est pas celle d'une écriture du mod. */
    private fun edit(path: Path, text: String, modified: FileTime = stamp) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        Files.setLastModifiedTime(path, modified)
    }

    private fun ConfigurationRead.valid(): ConfigurationRead.Valid = this as? ConfigurationRead.Valid ?: fail("La configuration est refusée : ${report.issues}")

    private fun ConfigurationWrite.written(): ConfigurationWrite.Written = this as? ConfigurationWrite.Written ?: fail("L'écriture n'a pas eu lieu : $this")
}
