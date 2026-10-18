// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** La mise en page des fichiers JSON que le mod écrit (cahier des charges, 4.2 et 4.4). */
class JsonLayoutTest {

    @Test
    fun `un objet s'écrit un champ par ligne, à deux espaces, et le texte finit par une fin de ligne`() {
        val tree = buildJsonObject {
            put("schema-version", 1)
            putJsonObject("namespaces") {
                put("enabled", true)
                putJsonObject("prefixes") { put("minecraft", "mc") }
            }
            put("refusal-message", JsonNull)
        }
        val expected = """
            {
              "schema-version": 1,
              "namespaces": {
                "enabled": true,
                "prefixes": {
                  "minecraft": "mc"
                }
              },
              "refusal-message": null
            }
        """.trimIndent() + "\n"
        assertEquals(expected, JsonLayout.print(tree))
    }

    @Test
    fun `une liste de valeurs simples tient sur une ligne`() {
        val tree = buildJsonObject {
            putJsonArray("groups") {
                add("membre")
                add("vip")
            }
            putJsonArray("parents") { add("moderateur") }
        }
        val expected = """
            {
              "groups": ["membre", "vip"],
              "parents": ["moderateur"]
            }
        """.trimIndent() + "\n"
        assertEquals(expected, JsonLayout.print(tree))
    }

    @Test
    fun `une liste qui porte un objet s'écrit un élément par ligne`() {
        val tree = buildJsonObject {
            putJsonArray("issues") {
                add(buildJsonObject { put("kind", "unknown_parent") })
                add(buildJsonObject { putJsonArray("path") { add("groups") } })
            }
        }
        val expected = """
            {
              "issues": [
                {
                  "kind": "unknown_parent"
                },
                {
                  "path": ["groups"]
                }
              ]
            }
        """.trimIndent() + "\n"
        assertEquals(expected, JsonLayout.print(tree))
    }

    @Test
    fun `un objet vide et une liste vide s'écrivent sans rien entre leurs crochets`() {
        val tree = buildJsonObject {
            putJsonObject("players") {}
            putJsonArray("parents") {}
        }
        val expected = """
            {
              "players": {},
              "parents": []
            }
        """.trimIndent() + "\n"
        assertEquals(expected, JsonLayout.print(tree))
        assertEquals("{}\n", JsonLayout.print(buildJsonObject {}))
        assertEquals("[]\n", JsonLayout.print(buildJsonArray {}))
    }

    @Test
    fun `les guillemets, les barres obliques inverses et les fins de ligne s'échappent, les accents s'écrivent tels quels`() {
        val tree = buildJsonObject {
            put("note", "Il a dit \"non\",\nchemin C:\\jeu, « à l'été »")
            put("clé \"citée\"", "é")
        }
        val expected = "{\n" +
            "  \"note\": \"Il a dit \\\"non\\\",\\nchemin C:\\\\jeu, « à l'été »\",\n" +
            "  \"clé \\\"citée\\\"\": \"é\"\n" +
            "}\n"
        assertEquals(expected, JsonLayout.print(tree))
    }

    @Test
    fun `le texte n'a ni retour chariot ni tabulation`() {
        val text = JsonLayout.print(buildJsonObject { putJsonObject("groups") { putJsonObject("default") { put("weight", 0) } } })
        assertFalse(text.contains('\r'))
        assertFalse(text.contains('\t'))
    }
}
