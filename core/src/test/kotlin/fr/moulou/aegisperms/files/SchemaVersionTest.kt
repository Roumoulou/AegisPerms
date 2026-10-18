// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le numéro de structure d'un fichier et la chaîne de ses migrations (cahier des charges, 4.2). */
class SchemaVersionTest {

    /** Une chaîne fictive, faute de structure antérieure à la première : le numéro 1 nommait `ranks` ce que le 2 nomme `groups`, et le 3 ajoute `players`. */
    private val migrations = SchemaMigrations(
        current = 3,
        steps = mapOf(
            1 to { root -> JsonObject(root.entries.associate { (key, value) -> (if (key == "ranks") "groups" else key) to value }) },
            2 to { root -> JsonObject(root + ("players" to JsonObject(emptyMap()))) },
        ),
    )

    @Test
    fun `le numéro se lit sur l'arbre, où qu'il soit écrit`() {
        assertEquals(SchemaVersion.Known(1), SchemaVersion.of(root("""{ "schema-version": 1 }""")))
        assertEquals(SchemaVersion.Known(7), SchemaVersion.of(root("""{ "groups": {}, "schema-version": 7 }""")))
        assertEquals(SchemaVersion.Missing, SchemaVersion.of(root("""{ "groups": {} }""")))
    }

    @Test
    fun `un numéro qui n'est pas un entier d'au moins 1, écrit sans guillemets, est mal écrit`() {
        for (written in listOf("\"1\"", "0", "-1", "1.5", "1e0", "true", "null", "[1]", "{}")) {
            assertTrue(SchemaVersion.of(root("""{ "schema-version": $written }""")) is SchemaVersion.Invalid, written)
        }
    }

    @Test
    fun `une chaîne de migrations porte un arbre au numéro courant, pas à pas`() {
        val current = root("""{ "schema-version": 3, "groups": { "default": {} }, "players": {} }""")
        assertEquals(current, migrations.migrate(root("""{ "schema-version": 1, "ranks": { "default": {} } }"""), from = 1))
        assertEquals(current, migrations.migrate(root("""{ "schema-version": 2, "groups": { "default": {} } }"""), from = 2))
        assertEquals(current, migrations.migrate(current, from = 3))
    }

    @Test
    fun `sans structure antérieure, la chaîne est vide et rend l'arbre tel quel`() {
        val tree = root("""{ "schema-version": 1, "groups": {} }""")
        assertEquals(tree, SchemaMigrations(current = 1).migrate(tree, from = 1))
    }

    @Test
    fun `une chaîne qui saute un numéro, ou qui en porte un de trop, est refusée à sa construction`() {
        val step: (JsonObject) -> JsonObject = { it }
        assertThrows(IllegalArgumentException::class.java) { SchemaMigrations(current = 3, steps = mapOf(1 to step)) }
        assertThrows(IllegalArgumentException::class.java) { SchemaMigrations(current = 1, steps = mapOf(1 to step)) }
        assertThrows(IllegalArgumentException::class.java) { SchemaMigrations(current = 0) }
    }

    @Test
    fun `un numéro hors de la chaîne ne se migre pas`() {
        val tree = root("""{ "schema-version": 4 }""")
        assertThrows(IllegalArgumentException::class.java) { migrations.migrate(tree, from = 4) }
        assertThrows(IllegalArgumentException::class.java) { migrations.migrate(tree, from = 0) }
    }

    private fun root(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
