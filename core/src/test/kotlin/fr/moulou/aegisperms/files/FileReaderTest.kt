// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** La lecture d'un fichier sans store, pour `/aegis validate`, une sauvegarde ou la copie `last-good` (cahier des charges, 4.5, 4.9 et 4.10). */
class FileReaderTest {

    @TempDir
    lateinit var dir: Path

    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    private val reader = FileReader(ConfigurationFile.PERMISSIONS, JsonFileFormat.PERMISSIONS, Permissions.serializer())

    @Test
    fun `un fichier lu sans store rend les mêmes données et les mêmes constats que par son store, lignes comprises`() {
        val cases = mapOf(
            "un fichier valide" to DeliveredTexts.PERMISSIONS,
            "un fichier écrit à la main, sous un BOM" to FileText.BOM + "{ \"schema-version\": 1, \"groups\": { \"default\": { \"rules\": { \"command\": { \"value\": \"deny\" } } } } }",
            "une clé inconnue et une valeur hors de son domaine" to "{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"vip\": {\n      \"poids\": 3,\n      \"include-ops\": \"ops\"\n    }\n  }\n}\n",
            "un commentaire" to "{\n  \"schema-version\": 1,\n  \"groups\": {} // une note\n}\n",
            "une clé écrite deux fois" to "{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"vip\": {},\n    \"vip\": {}\n  }\n}\n",
            "un numéro de structure absent" to "{\n  \"groups\": {}\n}\n",
            "un numéro de structure plus récent" to "{\n  \"schema-version\": 2\n}\n",
        )
        for ((name, text) in cases) {
            put(text)
            Stores.permissions(config).use { stored ->
                val withoutStore = outcome(reader.read(permissionsPath))
                assertEquals(outcome(stored.read()), withoutStore, name)
                assertEquals(withoutStore, outcome(stored.reader.read(permissionsPath)), name)
            }
        }
    }

    @Test
    fun `les constats d'une lecture sans store portent leur ligne`() {
        put("{\n  \"schema-version\": 1,\n  \"groups\": {} // une note\n}\n")
        assertEquals(3, outcome(reader.read(permissionsPath)).second.single().line)

        put("{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"vip\": {},\n    \"vip\": {}\n  }\n}\n")
        assertEquals(5, outcome(reader.read(permissionsPath)).second.single().line)

        put("{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"vip\": {\n      \"poids\": 3\n    }\n  }\n}\n")
        assertEquals(5, outcome(reader.read(permissionsPath)).second.single().line)
    }

    @Test
    fun `une lecture sans store ne crée pas un fichier absent`() {
        val issue = outcome(reader.read(permissionsPath)).second.single()
        assertEquals(IssueKind.UNREADABLE_FILE, issue.kind)
        assertEquals(ConfigurationFile.PERMISSIONS, issue.file)
        assertFalse(Files.exists(permissionsPath))
        assertFalse(Files.exists(config))
    }

    @Test
    fun `une lecture sans store lit un fichier rangé ailleurs, sous un autre nom de dossier`() {
        val backup = dir.resolve("backups").resolve("2026-10-05_14-05-09-avant l'été").resolve("permissions.json")
        Files.createDirectories(backup.parent)
        Files.writeString(backup, DeliveredTexts.PERMISSIONS)
        val read = reader.read(backup)
        assertTrue(read is FileRead.Loaded && read.data == Permissions.DELIVERED && !read.created)
    }

    private fun put(text: String) {
        Files.createDirectories(config)
        Files.writeString(permissionsPath, text)
    }

    /** Ce qu'une lecture rend, mis à plat pour se comparer : les données, ou les constats. */
    private fun outcome(read: FileRead<Permissions>): Pair<Permissions?, List<Issue>> = when (read) {
        is FileRead.Loaded -> read.data to emptyList()
        is FileRead.Refused -> null to read.issues
    }
}
