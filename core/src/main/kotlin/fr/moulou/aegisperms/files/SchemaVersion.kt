// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Le numéro de structure d'un fichier (cahier des charges, 4.2), tel que son arbre JSON l'écrit sous la clé `schema-version`. Le mod le lit lui-même, sur
 * l'arbre, avant tout décodage.
 */
internal sealed interface SchemaVersion {

    companion object {
        /** La clé du numéro, en tête de tout fichier que le mod lit. */
        const val KEY = "schema-version"

        /** Le numéro que porte la racine [root] d'un fichier. */
        fun of(root: JsonObject): SchemaVersion {
            val written = root[KEY] ?: return Missing
            val number = (written as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()
            return if (number == null || number < 1) Invalid(written) else Known(number)
        }
    }

    /** La clé manque. */
    data object Missing : SchemaVersion

    /** La valeur n'est pas un entier d'au moins 1, écrit sans guillemets. */
    data class Invalid(val written: JsonElement) : SchemaVersion

    /** Un numéro qui se lit ; qu'il soit celui du mod, plus ancien ou plus récent, c'est à qui le lit d'en juger. */
    data class Known(val number: Int) : SchemaVersion
}

/**
 * Les migrations d'un fichier (4.2) : pour chaque numéro de structure antérieur à [current], le passage de son arbre à celui du numéro suivant. Un fichier
 * d'un numéro plus ancien se migre ainsi en mémoire, à sa lecture, sans être réécrit.
 */
internal class SchemaMigrations(val current: Int, private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()) {

    init {
        require(current >= 1) { "The current schema version starts at 1: $current" }
        require(steps.keys == (1 until current).toSet()) { "One migration is expected from each schema version below $current, and from no other: ${steps.keys.sorted()}" }
    }

    /** L'arbre de numéro [from] porté au numéro courant, pas à pas, son `schema-version` compris. */
    fun migrate(root: JsonObject, from: Int): JsonObject {
        require(from in 1..current) { "No migration starts from schema version $from: the current one is $current" }
        var migrated = root
        for (version in from until current) migrated = steps.getValue(version)(migrated)
        return JsonObject(migrated + (SchemaVersion.KEY to JsonPrimitive(current)))
    }
}

/** Un fichier que le format refuse de décoder pour son numéro de structure : absent, mal écrit, ou plus récent que le mod. */
internal class SchemaVersionException(val version: SchemaVersion, val current: Int) : SerializationException(
    when (version) {
        SchemaVersion.Missing -> "Missing '${SchemaVersion.KEY}'"
        is SchemaVersion.Invalid -> "Invalid '${SchemaVersion.KEY}': ${version.written}"
        is SchemaVersion.Known -> "'${SchemaVersion.KEY}' ${version.number} is newer than the supported $current"
    },
)
