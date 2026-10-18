// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Mode
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.name

/** Les sauvegardes et la restauration (cahier des charges, 4.10). */
class BackupsTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val settingsPath: Path get() = config.resolve("settings.json")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    private val backups: Path get() = config.resolve("backups")

    /** Une horloge arrêtée à 12:05:09 UTC, sur un serveur à l'heure de Paris, où il est 14:05:09. */
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:05:09.750Z"), ZoneId.of("Europe/Paris"))

    private val stampName = "2026-10-05_14-05-09"

    /** Une date ancienne, qu'une réécriture ferait sauter. */
    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    /** Les fichiers d'un administrateur, à sa main : un BOM, des tabulations, des clés dans son ordre. */
    private val settingsByHand = FileText.BOM + "{\n\t\"mode\": \"observe\",\n\t\"schema-version\": 1\n}\n"

    private val permissionsByHand = "{ \"schema-version\": 1,\n  \"groups\": { \"vip\": { \"weight\": 80, \"note\": \"À la main.\" }, \"default\": {} } }"

    private val byHand = Configuration(
        Settings.DELIVERED.copy(mode = Mode.OBSERVE),
        Fixtures.permissions(groups = mapOf("vip" to Group(weight = 80, note = "À la main."), "default" to Group())),
    )

    private fun put(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        Files.setLastModifiedTime(path, stamp)
    }

    private fun putByHand() {
        put(settingsPath, settingsByHand)
        put(permissionsPath, permissionsByHand)
    }

    private fun files(): ConfigurationFiles = ConfigurationFiles(config, clock)

    private fun BackupResult.folder(): String = (this as? BackupResult.Done)?.folder ?: fail("La sauvegarde n'a pas eu lieu : $this")

    private fun RestoreResult.restored(): RestoreResult.Restored = this as? RestoreResult.Restored ?: fail("La restauration n'a pas eu lieu : $this")

    private fun names(directory: Path): Set<String> = Files.list(directory).use { found -> found.map { it.name }.toList().toSet() }

    private fun assertBytes(text: String, path: Path) = assertArrayEquals(text.toByteArray(Charsets.UTF_8), Files.readAllBytes(path), path.toString())

    @Test
    fun `le dossier d'une sauvegarde porte l'heure locale, sans deux-points, et le nom demandé`() {
        putByHand()
        files().use { files ->
            assertEquals(stampName, files.backup().folder())
            assertEquals("$stampName-avant-la-1_2", files.backup("avant-la-1_2").folder())
        }
        assertEquals(setOf(stampName, "$stampName-avant-la-1_2"), names(backups))
        assertTrue(names(backups).none { ':' in it })
    }

    @Test
    fun `une sauvegarde copie les deux fichiers à l'octet, avec un manifeste, sans toucher aux fichiers en place`() {
        putByHand()
        val folder = files().use { it.backup().folder() }

        val saved = backups.resolve(folder)
        assertEquals(setOf("settings.json", "permissions.json", "manifest.json"), names(saved))
        assertBytes(settingsByHand, saved.resolve("settings.json"))
        assertBytes(permissionsByHand, saved.resolve("permissions.json"))
        val manifest = """
            {
              "schema-version": 1,
              "files": ["settings.json", "permissions.json"],
              "written-at": "2026-10-05T12:05:09Z"
            }
        """.trimIndent() + "\n"
        assertBytes(manifest, saved.resolve("manifest.json"))

        for ((path, text) in listOf(settingsPath to settingsByHand, permissionsPath to permissionsByHand)) {
            assertBytes(text, path)
            assertEquals(stamp, Files.getLastModifiedTime(path))
        }
        assertEquals(setOf("settings.json", "permissions.json", "backups"), names(config))
    }

    @Test
    fun `une sauvegarde n'écrase jamais un dossier, la suivante prend le suffixe 2, puis 3`() {
        putByHand()
        files().use { files ->
            assertEquals(stampName, files.backup().folder())
            put(permissionsPath, DeliveredTexts.PERMISSIONS)
            assertEquals("$stampName-2", files.backup().folder())
            assertEquals("$stampName-3", files.backup().folder())
            assertEquals("$stampName-nuit", files.backup("nuit").folder())
            assertEquals("$stampName-nuit-2", files.backup("nuit").folder())
        }
        assertBytes(permissionsByHand, backups.resolve(stampName).resolve("permissions.json"))
        assertBytes(DeliveredTexts.PERMISSIONS, backups.resolve("$stampName-2").resolve("permissions.json"))
        assertEquals(5, names(backups).size)
    }

    @Test
    fun `un nom mal formé est refusé, et rien ne s'écrit`() {
        putByHand()
        files().use { files ->
            for (name in listOf("avec espace", "Majuscule", "a/b", "a\\b", "..", "", "x".repeat(33), "été", "a:b", "nom.")) {
                assertEquals(BackupResult.InvalidName, files.backup(name), name)
            }
            assertEquals("$stampName-${"x".repeat(32)}", files.backup("x".repeat(32)).folder())
        }
        assertEquals(1, names(backups).size)
    }

    @Test
    fun `des fichiers fautifs se sauvegardent quand même, et un fichier absent ne bloque pas la sauvegarde, qui ne se restaure pas`() {
        files().use { files ->
            assertEquals(BackupResult.NothingToBackUp, files.backup())
            assertFalse(Files.exists(backups))

            val broken = "{ \"schema-version\": 1, "
            put(settingsPath, broken)
            val folder = files.backup().folder()
            assertEquals(setOf("settings.json", "manifest.json"), names(backups.resolve(folder)))
            assertBytes(broken, backups.resolve(folder).resolve("settings.json"))
            assertTrue(Files.readString(backups.resolve(folder).resolve("manifest.json")).contains("\"files\": [\"settings.json\"]"))

            assertEquals(RestoreResult.IncompleteBackup, files.restore(folder))
            assertBytes(broken, settingsPath)
        }
    }

    @Test
    fun `backups liste les dossiers à manifeste lisible, triés`() {
        putByHand()
        files().use { files ->
            assertEquals(emptyList<String>(), files.backups())
            files.backup("zoulou")
            files.backup()
            files.backup("alpha")

            // Ce qui n'est pas une sauvegarde : un fichier, un dossier sans manifeste, un dossier au manifeste illisible.
            Files.writeString(backups.resolve("notes.txt"), "à moi")
            Files.createDirectories(backups.resolve("sans-manifeste"))
            Files.createDirectories(backups.resolve("manifeste-coupe"))
            Files.writeString(backups.resolve("manifeste-coupe").resolve("manifest.json"), "{ \"schema-version\": 1,")

            assertEquals(listOf(stampName, "$stampName-alpha", "$stampName-zoulou"), files.backups())
        }
    }

    @Test
    fun `backup puis restore rendent le même texte`() {
        putByHand()
        files().use { files ->
            assertEquals(byHand, files.start().configuration)
            val folder = files.backup("avant").folder()

            // Le serveur vit : une commande réécrit les droits, l'administrateur retouche les réglages.
            assertTrue(files.write(Fixtures.personas) is ConfigurationWrite.Written)
            val edited = "{ \"schema-version\": 1, \"mode\": \"enforce\" }"
            put(settingsPath, edited)
            val personasText = Files.readString(permissionsPath)

            val restored = files.restore(folder).restored()
            assertEquals(byHand, restored.configuration)
            assertTrue(restored.report.isValid)
            assertBytes(settingsByHand, settingsPath)
            assertBytes(permissionsByHand, permissionsPath)

            // Ce qui était en place a été mis de côté avant d'être remplacé.
            assertEquals("$stampName-before-restore", restored.savedAs)
            assertBytes(edited, backups.resolve("$stampName-before-restore").resolve("settings.json"))
            assertBytes(personasText, backups.resolve("$stampName-before-restore").resolve("permissions.json"))
            assertBytes(permissionsByHand, backups.resolve(folder).resolve("permissions.json"))
        }
    }

    @Test
    fun `restore valide la sauvegarde comme une configuration entière avant de rien toucher`() {
        putByHand()
        files().use { files ->
            files.start()
            val folder = files.backup().folder()
            put(permissionsPath, DeliveredTexts.PERMISSIONS)
            put(settingsPath, DeliveredTexts.SETTINGS)

            Files.writeString(backups.resolve(folder).resolve("permissions.json"), "{ \"schema-version\": 1, \"groups\": { \"vip\": {} } }")
            val invalid = files.restore(folder) as RestoreResult.Refused
            assertEquals(listOf(IssueKind.MISSING_DEFAULT), invalid.report.issues.map { it.kind })

            Files.writeString(backups.resolve(folder).resolve("permissions.json"), "{ \"schema-version\": 1, \"groups\": {}, }")
            val malformed = files.restore(folder) as RestoreResult.Refused
            assertEquals(listOf(IssueKind.MALFORMED_FILE), malformed.report.issues.map { it.kind })

            Files.delete(backups.resolve(folder).resolve("manifest.json"))
            assertEquals(RestoreResult.UnknownBackup, files.restore(folder))
        }
        for ((path, text) in listOf(settingsPath to DeliveredTexts.SETTINGS, permissionsPath to DeliveredTexts.PERMISSIONS)) {
            assertBytes(text, path)
            assertEquals(stamp, Files.getLastModifiedTime(path))
        }
        // Aucune mise de côté n'a eu lieu : rien n'allait être remplacé.
        assertEquals(setOf(stampName), names(backups))
    }

    @Test
    fun `restore refuse un dossier inconnu et tout chemin qui sort de backups`() {
        putByHand()
        files().use { files ->
            val start = files.start()
            // Une copie entière, hors de backups, qu'un chemin relatif atteindrait.
            assertEquals(GeneratedWrite.Done, files.writeLastGood(start.configuration, generation = 1))
            val folder = files.backup().folder()

            val outside = listOf("absent", "", ".", "..", "../last-good", "..\\last-good", "$folder/../../last-good", "$folder/..", config.resolve("last-good").toString(), folder.uppercase() + "x")
            for (name in outside) assertEquals(RestoreResult.UnknownBackup, files.restore(name), name)
        }
        assertBytes(settingsByHand, settingsPath)
        assertBytes(permissionsByHand, permissionsPath)
        assertEquals(setOf(stampName), names(backups))
    }

    @Test
    fun `restore rouvre l'écriture après un démarrage en lecture seule, et n'est pas un changement extérieur`() {
        putByHand()
        val folder = files().use { files ->
            files.start()
            files.backup().folder()
        }
        put(permissionsPath, "{ \"schema-version\": 1, \"groups\": { \"vip\": {} } }")

        files().use { files ->
            assertTrue(files.start().readOnly)
            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))

            assertEquals(byHand, files.restore(folder).restored().configuration)
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())
            val written = files.write(Fixtures.personas) as ConfigurationWrite.Written
            assertEquals(Mode.OBSERVE, written.configuration.settings.mode)
        }
        assertBytes(settingsByHand, settingsPath)
    }
}
