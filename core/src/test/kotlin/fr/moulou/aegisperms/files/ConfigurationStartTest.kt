// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** Le démarrage et son repli : `last-good`, puis les valeurs livrées, et la lecture seule (cahier des charges, 4.9). */
class ConfigurationStartTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val settingsPath: Path get() = config.resolve("settings.json")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    /** Une date ancienne, qu'une réécriture ferait sauter. */
    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    /** La configuration publiée avant la panne, celle que `last-good` garde. */
    private val published = Configuration(Settings.DELIVERED.copy(mode = Mode.OBSERVE), Fixtures.personas)

    private val observing = "{ \"schema-version\": 1, \"mode\": \"observe\" }"

    private val malformedSettings = "{ \"schema-version\": 1, \"mode\": \"observe\", }"

    /** Des droits qui se lisent et ne se valident pas : `default` manque. */
    private val invalidPermissions = "{ \"schema-version\": 1, \"groups\": { \"vip\": {} } }"

    private val personasText = JsonFileFormat.PERMISSIONS.encodeToText(Permissions.serializer(), Fixtures.personas)

    private fun put(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        Files.setLastModifiedTime(path, stamp)
    }

    /** Un serveur qui a tourné : ses deux fichiers valides, et la copie de sa dernière publication. */
    private fun publishOnce() {
        put(settingsPath, observing)
        put(permissionsPath, personasText)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(published, start.configuration)
            assertEquals(GeneratedWrite.Done, files.writeLastGood(start.configuration, generation = 1))
        }
    }

    private fun assertUntouched(path: Path, text: String) {
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), Files.readAllBytes(path))
        assertEquals(stamp, Files.getLastModifiedTime(path))
    }

    private fun kinds(start: ConfigurationStart): List<IssueKind> = start.report.issues.map { it.kind }

    @Test
    fun `le premier démarrage crée les deux fichiers et applique la configuration livrée`() {
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(Configuration(Settings.DELIVERED, Permissions.DELIVERED), start.configuration)
            assertEquals(ConfigurationSource.FILE, start.settingsSource)
            assertEquals(ConfigurationSource.FILE, start.permissionsSource)
            assertEquals(setOf(ConfigurationFile.SETTINGS, ConfigurationFile.PERMISSIONS), start.created)
            assertEquals(emptyList<Issue>(), start.report.issues)
            assertFalse(start.readOnly)
            assertTrue(files.write(Fixtures.personas) is ConfigurationWrite.Written)
        }
        assertArrayEquals(DeliveredTexts.SETTINGS.toByteArray(Charsets.UTF_8), Files.readAllBytes(settingsPath))
    }

    @Test
    fun `des réglages illisibles laissent ceux de last-good s'appliquer, sans réécrire le fichier, et les droits restent inscriptibles`() {
        publishOnce()
        put(settingsPath, malformedSettings)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(published, start.configuration)
            assertEquals(ConfigurationSource.LAST_GOOD, start.settingsSource)
            assertEquals(ConfigurationSource.FILE, start.permissionsSource)
            assertEquals(listOf(IssueKind.MALFORMED_FILE), kinds(start))
            assertEquals(ConfigurationFile.SETTINGS, start.report.issues.single().file)
            assertFalse(start.readOnly)
            assertUntouched(settingsPath, malformedSettings)

            // Les droits viennent de leur fichier : une écriture passe, validée avec les réglages qui s'appliquent.
            val written = files.write(Permissions.DELIVERED) as ConfigurationWrite.Written
            assertEquals(Mode.OBSERVE, written.configuration.settings.mode)
        }
        assertUntouched(settingsPath, malformedSettings)
    }

    @Test
    fun `sans copie valide, des réglages illisibles laissent les réglages livrés s'appliquer`() {
        put(settingsPath, malformedSettings)
        put(permissionsPath, personasText)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(Configuration(Settings.DELIVERED, Fixtures.personas), start.configuration)
            assertEquals(ConfigurationSource.DELIVERED, start.settingsSource)
            assertEquals(ConfigurationSource.FILE, start.permissionsSource)
            assertFalse(start.readOnly)
        }
        assertUntouched(settingsPath, malformedSettings)
        assertUntouched(permissionsPath, personasText)
    }

    @Test
    fun `des droits invalides laissent ceux de last-good s'appliquer, en lecture seule`() {
        publishOnce()
        put(permissionsPath, invalidPermissions)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(published, start.configuration)
            assertEquals(ConfigurationSource.FILE, start.settingsSource)
            assertEquals(ConfigurationSource.LAST_GOOD, start.permissionsSource)
            assertEquals(listOf(IssueKind.MISSING_DEFAULT), kinds(start))
            assertTrue(start.readOnly)

            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))
        }
        assertUntouched(permissionsPath, invalidPermissions)
    }

    @Test
    fun `sans copie valide, des droits invalides laissent les deux groupes livrés s'appliquer, en lecture seule`() {
        put(settingsPath, observing)
        put(permissionsPath, invalidPermissions)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(Configuration(Settings.DELIVERED.copy(mode = Mode.OBSERVE), Permissions.DELIVERED), start.configuration)
            assertEquals(ConfigurationSource.FILE, start.settingsSource)
            assertEquals(ConfigurationSource.DELIVERED, start.permissionsSource)
            assertTrue(start.readOnly)
            assertEquals(ConfigurationWrite.ReadOnly, files.write(Fixtures.personas))
        }
        assertUntouched(permissionsPath, invalidPermissions)
    }

    @Test
    fun `une copie sans manifeste complet ne se reprend pas`() {
        publishOnce()
        Files.delete(config.resolve("last-good").resolve("manifest.json"))
        put(permissionsPath, invalidPermissions)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(ConfigurationSource.DELIVERED, start.permissionsSource)
            assertEquals(Permissions.DELIVERED, start.configuration.permissions)
            assertEquals(Mode.OBSERVE, start.configuration.settings.mode)
        }
    }

    @Test
    fun `des réglages illisibles et des droits invalides se replient tous les deux`() {
        publishOnce()
        put(settingsPath, malformedSettings)
        put(permissionsPath, invalidPermissions)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(published, start.configuration)
            assertEquals(ConfigurationSource.LAST_GOOD, start.settingsSource)
            assertEquals(ConfigurationSource.LAST_GOOD, start.permissionsSource)
            // La validation attendait les réglages : seul leur constat de lecture est dit, et le repli a trouvé seul que les droits ne se valident pas.
            assertEquals(listOf(IssueKind.MALFORMED_FILE), kinds(start))
            assertTrue(start.readOnly)
        }

        StagedDirectory.delete(config.resolve("last-good"))
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(Configuration(Settings.DELIVERED, Permissions.DELIVERED), start.configuration)
            assertEquals(ConfigurationSource.DELIVERED, start.settingsSource)
            assertEquals(ConfigurationSource.DELIVERED, start.permissionsSource)
            assertTrue(start.readOnly)
        }
        assertUntouched(settingsPath, malformedSettings)
        assertUntouched(permissionsPath, invalidPermissions)
    }

    @Test
    fun `un fichier absent se crée au démarrage, même quand l'autre est fautif`() {
        put(settingsPath, malformedSettings)
        ConfigurationFiles(config).use { files ->
            val start = files.start()
            assertEquals(setOf(ConfigurationFile.PERMISSIONS), start.created)
            assertEquals(ConfigurationSource.DELIVERED, start.settingsSource)
            assertEquals(ConfigurationSource.FILE, start.permissionsSource)
            assertEquals(Configuration(Settings.DELIVERED, Permissions.DELIVERED), start.configuration)
        }
        assertArrayEquals(DeliveredTexts.PERMISSIONS.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
        assertUntouched(settingsPath, malformedSettings)
    }

    @Test
    fun `un fichier corrigé puis rechargé reprend la main et rouvre l'écriture`() {
        publishOnce()
        put(permissionsPath, invalidPermissions)
        ConfigurationFiles(config).use { files ->
            assertTrue(files.start().readOnly)
            // Le fichier fautif n'a pas bougé depuis sa lecture : la surveillance n'a rien à recharger.
            assertEquals(emptySet<ConfigurationFile>(), files.changedOutside())

            put(permissionsPath, DeliveredTexts.PERMISSIONS)
            Files.setLastModifiedTime(permissionsPath, FileTime.fromMillis(stamp.toMillis() + 60_000))
            assertEquals(setOf(ConfigurationFile.PERMISSIONS), files.changedOutside())

            val reloaded = files.reload() as ConfigurationRead.Valid
            assertEquals(Permissions.DELIVERED, reloaded.configuration.permissions)
            assertTrue(files.write(Fixtures.personas) is ConfigurationWrite.Written)
        }
    }
}
