// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.Texts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Les descriptions du jar (cahier des charges, 5.10) : les deux fichiers aux mêmes clés, chacune une clé de commande, et le fichier de l'administrateur. Les
 * fichiers se lisent dans `src\main\resources`, que l'étage 0 de `common` n'a pas sur son classpath.
 */
class DescriptionsTest {

    companion object {
        private val DIRECTORY = Paths.get("src", "main", "resources", "assets", "aegisperms", "descriptions")

        /** Les deux fichiers du jar, lus à la source. */
        val jar: Map<Lang, Map<String, String>> = Lang.entries.associateWith { Texts.parse(Files.readString(DIRECTORY.resolve(it.fileName + ".json"))) }
    }

    private val aegis = Keys.parse("aegisperms:command.aegis") as CommandKey

    @Test
    fun `les deux fichiers de descriptions portent les mêmes clés, toutes des clés de commande, et aucune phrase vide`() {
        val french = jar.getValue(Lang.FR)
        val english = jar.getValue(Lang.EN)

        assertTrue(french.isNotEmpty())
        assertEquals(french.keys, english.keys)
        for ((key, text) in french + english) {
            assertTrue(Keys.parseOrNull(key) is CommandKey, key)
            assertTrue(text.isNotBlank() && text.trim() == text, key)
        }
        assertTrue(aegis.text in french, "/aegis a sa description")
        for (sub in listOf("status", "why", "check", "search", "catalog", "manual", "group", "player", "reload", "validate", "backup", "restore", "mode")) {
            assertTrue(aegis.descendant(sub).text in french, "/aegis $sub a sa description")
        }
    }

    @Test
    fun `le fichier de l'administrateur est lu quand il est là, ignoré quand il manque, dit quand il ne se lit pas`(@TempDir directory: Path) {
        assertNull(Descriptions.load(directory, jar).issue)
        assertEquals(jar.getValue(Lang.FR).getValue(aegis.text), Descriptions.load(directory, jar).descriptions.of(aegis, Lang.FR))

        Files.writeString(directory.resolve(Descriptions.FILE_NAME), "{\n  \"aegisperms:command.aegis\": {\"fr\": \"Le pare-feu.\", \"en\": \"The firewall.\"},\n  \"homes:command.home\": {\"fr\": \"Chez soi.\"}\n}\n")
        val loaded = Descriptions.load(directory, jar)
        assertNull(loaded.issue)
        assertEquals("Le pare-feu.", loaded.descriptions.of(aegis, Lang.FR))
        assertEquals("The firewall.", loaded.descriptions.of(aegis, Lang.EN))
        val home = Keys.parse("homes:command.home") as CommandKey
        assertEquals("Chez soi.", loaded.descriptions.of(home, Lang.FR))
        assertNull(loaded.descriptions.of(home, Lang.EN), "une langue absente chez l'administrateur et dans le jar n'a rien")

        Files.writeString(directory.resolve(Descriptions.FILE_NAME), "{ pas du json")
        val broken = Descriptions.load(directory, jar)
        assertNotNull(broken.issue)
        assertEquals(jar.getValue(Lang.EN).getValue(aegis.text), broken.descriptions.of(aegis, Lang.EN), "le jar sert seul")
    }
}
