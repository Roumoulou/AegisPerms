// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** La ligne d'un chemin JSON dans le texte d'un fichier (cahier des charges, 4.5). */
class FileTextTest {

    private val written = listOf(
        """{""",
        """  "schema-version": 1,""",
        """  "groups": {""",
        """    "123": {""",
        """      "parents": [""",
        """        "membre",""",
        """        "vip"""",
        """      ],""",
        """      "rules": {""",
        """        "minecraft:command.teleport.targets.location": "allow",""",
        """        "minecraft:commands/entity_selectors": { "value": "deny", "visible": true }""",
        """      },""",
        """      "weight": 10""",
        """    },""",
        """    "default": { "parents": ["a", "b"] },""",
        """    "vip": {""",
        """      "note": "Une note que 123 n'a pas."""",
        """    }""",
        """  },""",
        """  "players": {}""",
        """}""",
    ).joinToString("\n")

    private fun textOf(content: String): FileText = FileText.of(content, JsonFileFormat.PERMISSIONS.lineLocator())

    private val text = textOf(written)

    @Test
    fun `une clé a sa ligne, à chaque profondeur`() {
        assertEquals(2, text.lineOf(listOf("schema-version")))
        assertEquals(3, text.lineOf(listOf("groups")))
        assertEquals(13, text.lineOf(listOf("groups", "123", "weight")))
        assertEquals(17, text.lineOf(listOf("groups", "vip", "note")))
        assertEquals(20, text.lineOf(listOf("players")))
    }

    @Test
    fun `un nom fait de chiffres reste une clé, et un rang se compte dans sa liste`() {
        assertEquals(4, text.lineOf(listOf("groups", "123")))
        assertEquals(6, text.lineOf(listOf("groups", "123", "parents", "0")))
        assertEquals(7, text.lineOf(listOf("groups", "123", "parents", "1")))
    }

    @Test
    fun `une clé de règle se retrouve, avec ses deux-points, ses points et sa barre oblique`() {
        assertEquals(10, text.lineOf(listOf("groups", "123", "rules", "minecraft:command.teleport.targets.location")))
        assertEquals(11, text.lineOf(listOf("groups", "123", "rules", "minecraft:commands/entity_selectors")))
    }

    @Test
    fun `ce qui s'écrit sur la ligne de sa clé prend cette ligne`() {
        assertEquals(11, text.lineOf(listOf("groups", "123", "rules", "minecraft:commands/entity_selectors", "visible")))
        assertEquals(15, text.lineOf(listOf("groups", "default", "parents")))
        assertEquals(15, text.lineOf(listOf("groups", "default", "parents", "1")))
    }

    @Test
    fun `un chemin que le fichier n'écrit pas prend la ligne de son plus proche ancêtre écrit`() {
        // `note` s'écrit plus bas, chez `vip` : la ligne est celle de `123`, pas celle d'un voisin.
        assertEquals(4, text.lineOf(listOf("groups", "123", "note")))
        assertEquals(5, text.lineOf(listOf("groups", "123", "parents", "7")))
        assertEquals(3, text.lineOf(listOf("groups", "absent", "weight")))
        assertNull(text.lineOf(listOf("aliases", "wla")))
        assertNull(text.lineOf(emptyList()))
    }

    @Test
    fun `un BOM ne décale rien`() {
        val withBom = textOf(FileText.BOM + written)
        assertNotNull(withBom.tree)
        assertEquals(2, withBom.lineOf(listOf("schema-version")))
        assertEquals(13, withBom.lineOf(listOf("groups", "123", "weight")))
    }

    @Test
    fun `un texte qui n'est pas du JSON strict n'a ni arbre ni ligne à donner`() {
        val malformed = textOf("{\n  \"schema-version\": 1,\n}")
        assertNull(malformed.tree)
        assertNull(malformed.lineOf(listOf("schema-version")))
    }

    @Test
    fun `un constat reçoit la ligne de son chemin, et garde celle qu'il porte déjà`() {
        val issue = Issue(IssueKind.UNKNOWN_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "123", "weight"))
        assertEquals(issue.copy(line = 13), text.withLine(issue))
        assertEquals(issue.copy(line = 2), text.withLine(issue.copy(line = 2)))
        assertNull(text.withLine(issue.copy(path = emptyList())).line)
    }
}
