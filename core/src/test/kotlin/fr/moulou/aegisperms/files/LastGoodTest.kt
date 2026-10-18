// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.name

/** La copie `last-good`, la dernière configuration publiée (cahier des charges, 4.9). */
class LastGoodTest {

    @TempDir
    lateinit var dir: Path

    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val current: Path get() = config.resolve("last-good")

    private val previous: Path get() = config.resolve("last-good.old")

    /** Une horloge arrêtée, avec des millièmes que le manifeste ne garde pas. */
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:05:09.750Z"), ZoneOffset.UTC)

    private val published = Configuration(Settings.DELIVERED.copy(mode = Mode.OBSERVE), Fixtures.personas)

    /** La lecture de la copie, telle que le démarrage la fait. */
    private val lastGood: LastGood
        get() {
            val reader = ConfigurationReader(
                FileReader(ConfigurationFile.SETTINGS, JsonFileFormat.SETTINGS, Settings.serializer()),
                FileReader(ConfigurationFile.PERMISSIONS, JsonFileFormat.PERMISSIONS, Permissions.serializer()),
            )
            return LastGood(config, reader, JsonFileFormat.SETTINGS, JsonFileFormat.PERMISSIONS)
        }

    @Test
    fun `la copie porte les deux fichiers tels que le mod les écrit, et son manifeste à l'octet près`() {
        write()
        assertEquals(setOf("settings.json", "permissions.json", "manifest.json"), names(current))
        assertEquals(JsonFileFormat.SETTINGS.encodeToText(Settings.serializer(), published.settings), Files.readString(current.resolve("settings.json")))
        assertEquals(JsonFileFormat.PERMISSIONS.encodeToText(Permissions.serializer(), published.permissions), Files.readString(current.resolve("permissions.json")))

        val manifest = """
            {
              "schema-version": 1,
              "files": ["settings.json", "permissions.json"],
              "generation": 7,
              "written-at": "2026-10-05T12:05:09Z"
            }
        """.trimIndent() + "\n"
        assertArrayEquals(manifest.toByteArray(Charsets.UTF_8), Files.readAllBytes(current.resolve("manifest.json")))
        // Écrire la copie ne crée ni n'ouvre les fichiers de configuration.
        assertEquals(setOf("last-good"), names(config))
    }

    @Test
    fun `la copie se relit avec sa génération`() {
        write()
        assertEquals(LastGood.Copy(published, generation = 7), lastGood.read(catalog = null))
    }

    @Test
    fun `une seconde copie remplace la première, sans rien laisser à côté`() {
        write(generation = 7)
        val second = published.copy(permissions = Permissions.DELIVERED)
        write(second, generation = 8)

        assertEquals(LastGood.Copy(second, generation = 8), lastGood.read(catalog = null))
        assertEquals(setOf("last-good"), names(config))
    }

    @Test
    fun `un remplacement interrompu retombe sur last-good-old`() {
        write()
        // La coupure tombe entre les deux déplacements : la précédente est rangée, la nouvelle pas encore en place.
        Files.move(current, previous)
        assertEquals(LastGood.Copy(published, generation = 7), lastGood.read(catalog = null))

        // Le passage suivant écrit sa copie, et retire celle qui ne sert plus.
        write(generation = 9)
        assertEquals(9, lastGood.read(catalog = null)?.generation)
        assertEquals(setOf("last-good"), names(config))
    }

    @Test
    fun `un temporaire laissé par une coupure est balayé à la copie suivante`() {
        Files.createDirectories(config.resolve("last-good.0a1b2c3d.tmp"))
        Files.writeString(config.resolve("last-good.0a1b2c3d.tmp").resolve("settings.json"), "{")
        write()
        assertEquals(setOf("last-good"), names(config))
    }

    @Test
    fun `une copie sans manifeste complet est refusée`() {
        val manifest = current.resolve("manifest.json")
        fun rewrite(change: (String) -> String) {
            Files.writeString(manifest, change(Files.readString(manifest)))
        }
        val cases = mapOf<String, () -> Unit>(
            "sans manifeste" to { Files.delete(manifest) },
            "au manifeste coupé" to { rewrite { it.take(40) } },
            "sans génération" to { rewrite { it.replace("  \"generation\": 7,\n", "") } },
            "qui ne nomme pas les droits" to { rewrite { it.replace(", \"permissions.json\"", "") } },
            "qui nomme un fichier de trop" to { rewrite { it.replace("\"permissions.json\"", "\"permissions.json\", \"catalog.json\"") } },
            "dont un fichier manque" to { Files.delete(current.resolve("settings.json")) },
        )
        for ((name, damage) in cases) {
            StagedDirectory.delete(config)
            write()
            assertTrue(lastGood.read(catalog = null) != null, name)
            damage()
            assertNull(lastGood.read(catalog = null), name)
        }
    }

    @Test
    fun `une copie dont un fichier ne se lit plus ou ne se valide plus est refusée, et la précédente la remplace`() {
        write(generation = 7)
        Files.writeString(current.resolve("permissions.json"), "{ \"schema-version\": 1, \"groups\": {} }")
        assertNull(lastGood.read(catalog = null))

        Files.writeString(current.resolve("permissions.json"), "{ \"schema-version\": 1, \"groups\": { \"default\": {} }, }")
        assertNull(lastGood.read(catalog = null))

        // Une copie précédente, entière, se reprend à sa place.
        Files.move(current, config.resolve("abîmée"))
        write(generation = 6)
        Files.move(current, previous)
        Files.move(config.resolve("abîmée"), current)
        assertEquals(6, lastGood.read(catalog = null)?.generation)
    }

    private fun write(configuration: Configuration = published, generation: Long = 7) {
        ConfigurationFiles(config, clock).use { files -> assertEquals(GeneratedWrite.Done, files.writeLastGood(configuration, generation)) }
    }

    private fun names(directory: Path): Set<String> = Files.list(directory).use { found -> found.map { it.name }.toList().toSet() }
}
