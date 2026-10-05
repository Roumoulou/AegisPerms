// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * Les deux écritures d'une règle dans le fichier des droits (cahier des charges, 4.4) : la forme courte, `"allow"` ou `"deny"`, et la forme longue, un objet
 * qui porte `value` et, en option, `visible` et `note`. Une règle sans `visible` ni `note` s'écrit courte ; les autres s'écrivent longues, sans leurs champs
 * absents, quels que soient les réglages du `Json` qui écrit.
 *
 * Il ne vaut que pour le JSON, dont il lit et écrit l'arbre.
 */
@OptIn(ExperimentalSerializationApi::class)
object RuleSerializer : JsonTransformingSerializer<Rule>(Rule.generatedSerializer()) {

    private const val VALUE = "value"

    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is JsonPrimitive && element.isString) JsonObject(mapOf(VALUE to element)) else element

    override fun transformSerialize(element: JsonElement): JsonElement {
        val written = element.jsonObject.filterValues { it !is JsonNull }
        return if (written.keys == setOf(VALUE)) written.getValue(VALUE) else JsonObject(written)
    }
}
