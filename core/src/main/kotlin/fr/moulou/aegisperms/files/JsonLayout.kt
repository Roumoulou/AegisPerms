// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * La mise en page des fichiers JSON que le mod écrit (cahier des charges, 4.2 et 4.4) : un objet s'écrit une clé par ligne, une liste de valeurs simples
 * sur une ligne, un objet et une liste vides sans rien entre leurs crochets. L'indentation est de deux espaces, les fins de ligne sont des LF, et le texte
 * finit par une fin de ligne. Une liste qui porte un objet ou une autre liste s'écrit un élément par ligne.
 *
 * Une clé par ligne est aussi ce que le localisateur de lignes de Storify sait lire : un constat y retrouve la ligne exacte de son chemin JSON.
 */
internal object JsonLayout {

    private const val INDENT = "  "

    /** Le texte de [element], fin de ligne finale comprise. */
    fun print(element: JsonElement): String = buildString {
        appendElement(element, 0)
        append('\n')
    }

    private fun StringBuilder.appendElement(element: JsonElement, depth: Int) {
        when (element) {
            is JsonObject -> appendObject(element, depth)
            is JsonArray -> appendArray(element, depth)
            // Le texte d'une valeur simple est celui de kotlinx, guillemets et échappements compris.
            is JsonPrimitive -> append(element.toString())
        }
    }

    private fun StringBuilder.appendObject(members: JsonObject, depth: Int) {
        if (members.isEmpty()) {
            append("{}")
            return
        }
        append('{')
        var first = true
        for ((key, value) in members) {
            if (!first) append(',')
            first = false
            appendLineStart(depth + 1)
            append(JsonPrimitive(key).toString()).append(": ")
            appendElement(value, depth + 1)
        }
        appendLineStart(depth)
        append('}')
    }

    private fun StringBuilder.appendArray(elements: JsonArray, depth: Int) {
        if (elements.all { it is JsonPrimitive }) {
            elements.joinTo(this, separator = ", ", prefix = "[", postfix = "]") { it.toString() }
            return
        }
        append('[')
        var first = true
        for (element in elements) {
            if (!first) append(',')
            first = false
            appendLineStart(depth + 1)
            appendElement(element, depth + 1)
        }
        appendLineStart(depth)
        append(']')
    }

    private fun StringBuilder.appendLineStart(depth: Int) {
        append('\n')
        repeat(depth) { append(INDENT) }
    }
}
