// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Les deux écritures d'une règle dans le fichier des droits (cahier des charges, 4.4). */
class RuleSerializerTest {

    private val json = Json

    @Test
    fun `une règle sans visible ni note s'écrit en forme courte`() {
        assertEquals("\"allow\"", write(Rule.ALLOW))
        assertEquals("\"deny\"", write(Rule.DENY))
    }

    @Test
    fun `une règle qui porte visible ou note s'écrit en forme longue, sans ses champs absents`() {
        assertEquals("""{"value":"deny","visible":true}""", write(Rule(RuleValue.DENY, visible = true)))
        assertEquals("""{"value":"deny","visible":false}""", write(Rule(RuleValue.DENY, visible = false)))
        assertEquals("""{"value":"allow","note":"Les VIP se téléportent aux coordonnées."}""", write(Rule(RuleValue.ALLOW, note = "Les VIP se téléportent aux coordonnées.")))
        assertEquals("""{"value":"deny","visible":true,"note":"Fermé."}""", write(Rule(RuleValue.DENY, visible = true, note = "Fermé.")))
    }

    @Test
    fun `la forme écrite ne dépend pas des réglages du Json`() {
        val writingDefaults = Json { encodeDefaults = true }
        assertEquals("\"allow\"", write(Rule.ALLOW, writingDefaults))
        assertEquals("""{"value":"deny","visible":true}""", write(Rule(RuleValue.DENY, visible = true), writingDefaults))
    }

    @Test
    fun `la forme courte et la forme longue se lisent`() {
        assertEquals(Rule.ALLOW, read("\"allow\""))
        assertEquals(Rule.DENY, read("\"deny\""))
        assertEquals(Rule.ALLOW, read("""{"value":"allow"}"""))
        assertEquals(Rule(RuleValue.DENY, visible = false, note = "Fermé."), read("""{"note":"Fermé.","value":"deny","visible":false}"""))
    }

    @Test
    fun `une valeur inconnue, une forme longue sans value et une valeur qui n'est ni un texte ni un objet sont refusées`() {
        for (text in listOf("\"peut-être\"", "\"Allow\"", """{"visible":true}""", """{"value":"allow","poids":3}""", "12", "true", "null", "[\"allow\"]")) {
            assertThrows(SerializationException::class.java, { read(text) }, text)
        }
    }

    private fun write(rule: Rule, json: Json = this.json): String = json.encodeToString(Rule.serializer(), rule)

    private fun read(text: String): Rule = json.decodeFromString(Rule.serializer(), text)
}
