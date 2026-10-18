// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name

/** Les deux stores de la configuration et les constats de leur lecture (cahier des charges, 4.2, 4.5 et 4.11). */
class StoredFileTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val settingsPath: Path get() = config.resolve("settings.json")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    /** Une date ancienne, qu'une réécriture ferait sauter. */
    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    @Test
    fun `le premier démarrage crée les deux fichiers à l'octet près`() {
        Stores.settings(config).use { settings ->
            val read = settings.read().loaded()
            assertTrue(read.created)
            assertEquals(Settings.DELIVERED, read.data)
        }
        Stores.permissions(config).use { permissions ->
            val read = permissions.read().loaded()
            assertTrue(read.created)
            assertEquals(Permissions.DELIVERED, read.data)
        }
        assertArrayEquals(DeliveredTexts.SETTINGS.toByteArray(Charsets.UTF_8), Files.readAllBytes(settingsPath))
        assertArrayEquals(DeliveredTexts.PERMISSIONS.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
        // Ni temporaire ni sidecar ne restent à côté.
        assertEquals(setOf("permissions.json", "settings.json"), Files.list(config).use { files -> files.map { it.name }.toList().toSet() })
    }

    @Test
    fun `un fichier présent n'est ni réécrit ni complété, à l'ouverture, à la relecture et à la fermeture`() {
        val settingsText = "{ \"schema-version\": 1, \"mode\": \"observe\" }"
        val permissionsText = "{\n\t\"schema-version\": 1,\n\t\"groups\": { \"default\": { \"rules\": { \"command\": { \"value\": \"deny\" } } } }\n}\n"
        put(settingsPath, settingsText)
        put(permissionsPath, permissionsText)

        Stores.settings(config).use { settings ->
            val first = settings.read().loaded()
            assertFalse(first.created)
            assertEquals(Settings.DELIVERED.copy(mode = Mode.OBSERVE), first.data)
            assertEquals(first.data, settings.read().loaded().data)
        }
        Stores.permissions(config).use { permissions ->
            val first = permissions.read().loaded()
            assertFalse(first.created)
            assertEquals(Permissions(schemaVersion = 1, groups = mapOf("default" to Group(rules = mapOf("command" to Rule.DENY)))), first.data)
            assertEquals(first.data, permissions.read().loaded().data)
        }
        assertUntouched(settingsPath, settingsText)
        assertUntouched(permissionsPath, permissionsText)
    }

    @Test
    fun `le store des réglages est en lecture seule, celui des droits ne l'est pas`() {
        Stores.settings(config).use { settings ->
            assertFalse(settings.isOpen)
            settings.read().loaded()
            assertTrue(settings.isOpen)
            assertTrue(settings.isReadOnly)
        }
        Stores.permissions(config).use { permissions ->
            permissions.read().loaded()
            assertFalse(permissions.isReadOnly)
        }
    }

    @Test
    fun `un fichier qui n'est pas du JSON strict est refusé sans faire tomber l'ouverture, et reste tel quel`() {
        val cases = mapOf(
            "un commentaire" to "{\n  \"schema-version\": 1, // une note\n  \"mode\": \"observe\"\n}\n",
            "une virgule finale" to "{\n  \"schema-version\": 1,\n  \"mode\": \"observe\",\n}\n",
            "une clé sans guillemets" to "{\n  schema-version: 1\n}\n",
            "un fichier coupé au milieu" to DeliveredTexts.SETTINGS.take(DeliveredTexts.SETTINGS.length / 2),
            "un fichier vide" to "",
            "autre chose que du JSON" to "mode = observe\n",
        )
        for ((name, text) in cases) {
            put(settingsPath, text)
            Stores.settings(config).use { settings ->
                val issue = settings.read().issues().single()
                assertEquals(IssueKind.MALFORMED_FILE, issue.kind, name)
                assertEquals(ConfigurationFile.SETTINGS, issue.file, name)
                assertEquals(emptyList<String>(), issue.path, name)
                assertTrue((issue.arguments.single() as IssueArgument.Literal).text.isNotBlank(), name)
                // Un fichier vide a pour ligne fautive celle où il s'arrête, la première (Storify, C-56).
                assertNotNull(issue.line, name)
                if (text.isEmpty()) assertEquals(1, issue.line, name)
                assertFalse(settings.isOpen, name)
                assertNull(settings.data, name)
            }
            assertUntouched(settingsPath, text)
        }
    }

    @Test
    fun `un fichier mal formé dit la ligne de sa faute`() {
        put(settingsPath, "{\n  \"schema-version\": 1,\n  \"mode\": \"observe\" // une note\n}\n")
        Stores.settings(config).use { settings -> assertEquals(3, settings.read().issues().single().line) }
    }

    @Test
    fun `un champ écrit deux fois est refusé, avec son chemin et la ligne du second`() {
        val text = "{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"default\": {},\n    \"vip\": { \"weight\": 1 },\n    \"vip\": { \"weight\": 2 }\n  }\n}\n"
        put(permissionsPath, text)
        Stores.permissions(config).use { permissions ->
            assertEquals(listOf(Issue(IssueKind.DUPLICATE_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "vip"), literals("vip"), line = 6)), permissions.read().issues())
            assertFalse(permissions.isOpen)
        }
        assertUntouched(permissionsPath, text)
    }

    @Test
    fun `un champ inconnu et une valeur hors de son domaine sortent ensemble, avec leur chemin et leur ligne`() {
        put(settingsPath, "{\n  \"schema-version\": 1,\n  \"admin_level\": \"admins\",\n  \"mode\": \"regarde\"\n}\n")
        Stores.settings(config).use { settings ->
            val expected = listOf(
                Issue(IssueKind.UNKNOWN_KEY, ConfigurationFile.SETTINGS, listOf("admin_level"), literals("admin_level"), line = 3),
                Issue(IssueKind.VALUE_OUT_OF_DOMAIN, ConfigurationFile.SETTINGS, listOf("mode"), literals("\"regarde\"", "enforce, observe"), line = 4),
            )
            assertEquals(expected, settings.read().issues())
            assertFalse(settings.isOpen)
        }
    }

    @Test
    fun `un numéro de structure absent ou plus récent que le mod est refusé`() {
        val versionPath = listOf("schema-version")
        put(permissionsPath, "{\n  \"groups\": {}\n}\n")
        Stores.permissions(config).use { permissions ->
            assertEquals(listOf(Issue(IssueKind.MISSING_SCHEMA_VERSION, ConfigurationFile.PERMISSIONS, versionPath)), permissions.read().issues())
        }
        put(permissionsPath, "{\n  \"schema-version\": 2,\n  \"ranks\": {}\n}\n")
        Stores.permissions(config).use { permissions ->
            assertEquals(listOf(Issue(IssueKind.NEWER_SCHEMA_VERSION, ConfigurationFile.PERMISSIONS, versionPath, literals("2", "1"), line = 2)), permissions.read().issues())
        }
    }

    @Test
    fun `un BOM posé par un éditeur est toléré, et reste dans le fichier`() {
        val text = FileText.BOM + "{\n  \"schema-version\": 1,\n  \"mode\": \"observe\"\n}\n"
        put(settingsPath, text)
        Stores.settings(config).use { settings ->
            assertEquals(Mode.OBSERVE, settings.read().loaded().data.mode)
            assertEquals(Mode.OBSERVE, settings.read().loaded().data.mode)
        }
        assertUntouched(settingsPath, text)
    }

    @Test
    fun `un fichier supprimé pendant que le serveur tourne est illisible à la relecture, et n'est pas recréé`() {
        Stores.permissions(config).use { permissions ->
            permissions.read().loaded()
            Files.delete(permissionsPath)

            val issue = permissions.read().issues().single()
            assertEquals(IssueKind.UNREADABLE_FILE, issue.kind)
            assertEquals(ConfigurationFile.PERMISSIONS, issue.file)
            assertTrue((issue.arguments.single() as IssueArgument.Literal).text.startsWith("NoSuchFileException"))
            assertFalse(Files.exists(permissionsPath))
            assertEquals(Permissions.DELIVERED, permissions.data)
        }
    }

    @Test
    fun `un dossier à la place du fichier est illisible, sans exception`() {
        Files.createDirectories(settingsPath)
        Stores.settings(config).use { settings ->
            assertEquals(IssueKind.UNREADABLE_FILE, settings.read().issues().single().kind)
            assertFalse(settings.isOpen)
        }
        assertTrue(Files.isDirectory(settingsPath))
    }

    @Test
    fun `une relecture ratée garde ce que le store tenait, et un fichier corrigé se relit`() {
        put(settingsPath, "{ \"schema-version\": 1, \"mode\": \"observe\" }")
        Stores.settings(config).use { settings ->
            settings.read().loaded()

            put(settingsPath, "{ \"schema-version\": 1, \"mode\": \"regarde\" }")
            assertEquals(IssueKind.VALUE_OUT_OF_DOMAIN, settings.read().issues().single().kind)
            assertEquals(Mode.OBSERVE, settings.data?.mode)

            put(settingsPath, "{ \"schema-version\": 1, \"mode\": \"observe\", }")
            assertEquals(IssueKind.MALFORMED_FILE, settings.read().issues().single().kind)
            assertEquals(Mode.OBSERVE, settings.data?.mode)

            put(settingsPath, "{ \"schema-version\": 1, \"mode\": \"enforce\" }")
            assertEquals(Mode.ENFORCE, settings.read().loaded().data.mode)
            assertEquals(Mode.ENFORCE, settings.data?.mode)
        }
    }

    @Test
    fun `un store qui n'a pas pu se construire ne recrée pas un fichier supprimé à la relecture`() {
        put(permissionsPath, "{ \"schema-version\": 1, \"groups\": { \"default\": {} }, }")
        Stores.permissions(config).use { permissions ->
            assertEquals(IssueKind.MALFORMED_FILE, permissions.read().issues().single().kind)
            Files.delete(permissionsPath)
            assertEquals(IssueKind.UNREADABLE_FILE, permissions.read().issues().single().kind)
            assertFalse(Files.exists(permissionsPath))
            assertFalse(permissions.isOpen)
        }
    }

    @Test
    fun `un store qui n'a pas pu se construire se construit à la relecture suivante`() {
        put(permissionsPath, "{ \"schema-version\": 1, \"groups\": { \"default\": {} }, }")
        Stores.permissions(config).use { permissions ->
            assertEquals(IssueKind.MALFORMED_FILE, permissions.read().issues().single().kind)
            assertFalse(permissions.isOpen)

            put(permissionsPath, "{ \"schema-version\": 1, \"groups\": { \"default\": {} } }")
            assertEquals(mapOf("default" to Group()), permissions.read().loaded().data.groups)
            assertTrue(permissions.isOpen)
        }
    }

    @Test
    fun `un fichier d'un numéro plus ancien se migre en mémoire, sans être réécrit`() {
        // Une structure 2 fictive, dont la 1 nommait `ranks` ce qu'elle nomme `groups`.
        val format = JsonFileFormat.permissions(
            SchemaMigrations(current = 2, steps = mapOf(1 to { root -> JsonObject(root.entries.associate { (key, value) -> (if (key == "ranks") "groups" else key) to value }) })),
        )
        val text = "{\n  \"schema-version\": 1,\n  \"ranks\": {\n    \"default\": {}\n  }\n}\n"
        put(permissionsPath, text)

        Stores.permissions(config, format).use { permissions ->
            val expected = Permissions(schemaVersion = 2, groups = mapOf("default" to Group()))
            assertEquals(expected, permissions.read().loaded().data)
            assertEquals(expected, permissions.read().loaded().data)
        }
        assertUntouched(permissionsPath, text)
    }

    private fun put(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        Files.setLastModifiedTime(path, stamp)
    }

    private fun <DATA> FileRead<DATA>.loaded(): FileRead.Loaded<DATA> = when (this) {
        is FileRead.Loaded -> this
        is FileRead.Refused -> fail("Le fichier est refusé : $issues")
    }

    private fun FileRead<*>.issues(): List<Issue> = when (this) {
        is FileRead.Loaded -> fail("Le fichier est lu : $data")
        is FileRead.Refused -> issues
    }

    private fun assertUntouched(path: Path, text: String) {
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), Files.readAllBytes(path))
        assertEquals(stamp, Files.getLastModifiedTime(path))
    }
}
