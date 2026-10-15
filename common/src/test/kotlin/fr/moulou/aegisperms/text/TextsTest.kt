// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Paths

/** Les textes (cahier des charges, 5.7) : les deux fichiers de langue, lus depuis les sources, et la grammaire des gabarits. */
class TextsTest {

    companion object {
        /** Les fichiers de langue des sources : l'étage 0 n'a pas les ressources traitées, que les modules de version produisent. */
        private val LANG_DIRECTORY = Paths.get("src", "main", "resources", "assets", "aegisperms", "lang")

        private val tables: Map<Lang, Map<String, String>> = Lang.entries.associateWith { Texts.parse(Files.readString(LANG_DIRECTORY.resolve(it.fileName + ".json"))) }

        /** Les clés que `TextKeys` nomme : ses constantes qui ne sont pas des préfixes. */
        private val namedKeys: List<String> = TextKeys::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
            .filter { !it.endsWith(".") }
    }

    @Test
    fun `les deux fichiers de langue portent les mêmes clés`() {
        assertEquals(tables.getValue(Lang.FR).keys, tables.getValue(Lang.EN).keys)
        assertTrue(tables.getValue(Lang.FR).isNotEmpty())
    }

    @Test
    fun `chaque clé des fichiers est une clé du mod`() {
        for ((lang, table) in tables) for (key in table.keys) assertTrue(key.startsWith(TextKeys.PREFIX), "$lang : $key")
    }

    @Test
    fun `chaque nature de constat a son texte dans les deux langues`() {
        for (lang in Lang.entries) for (kind in IssueKind.entries) assertTrue(kind.translationKey in tables.getValue(lang), "$lang : ${kind.translationKey}")
    }

    @Test
    fun `chaque clé que le code nomme a son texte dans les deux langues`() {
        assertTrue(namedKeys.isNotEmpty())
        for (lang in Lang.entries) for (key in namedKeys) assertTrue(key in tables.getValue(lang), "$lang : $key")
    }

    @Test
    fun `un gabarit attend le même nombre de valeurs dans les deux langues`() {
        val french = tables.getValue(Lang.FR)
        val english = tables.getValue(Lang.EN)
        for ((key, template) in french) assertEquals(Texts.arity(template), Texts.arity(english.getValue(key)), key)
    }

    @Test
    fun `un gabarit se remplit dans l'ordre, par rang, et un pour cent doublé en écrit un`() {
        assertEquals("a puis b", Texts.format("%s puis %s", listOf("a", "b")))
        assertEquals("b puis a", Texts.format("%2\$s puis %1\$s", listOf("a", "b")))
        assertEquals("50 %", Texts.format("%s %%", listOf(50)))
        assertEquals("sans valeur : %s", Texts.format("sans valeur : %s", emptyList()))
        assertEquals(2, Texts.arity("%s et %s"))
        assertEquals(3, Texts.arity("%3\$s avant %1\$s"))
        assertEquals(0, Texts.arity("100 %%"))
    }

    @Test
    fun `une clé sans gabarit rend la clé suivie de ses arguments`() {
        assertEquals("aegisperms.nothing a 1", Texts.text("aegisperms.nothing", Lang.FR, "a", 1))
    }

    @Test
    fun `la langue d'un code est le français pour fr et l'anglais pour le reste`() {
        assertEquals(Lang.FR, Lang.ofCode("fr_fr"))
        assertEquals(Lang.FR, Lang.ofCode("fr-CA"))
        assertEquals(Lang.FR, Lang.ofCode("FR"))
        assertEquals(Lang.EN, Lang.ofCode("en_us"))
        assertEquals(Lang.EN, Lang.ofCode("de_de"))
        assertEquals(Lang.EN, Lang.ofCode(null))
    }
}
