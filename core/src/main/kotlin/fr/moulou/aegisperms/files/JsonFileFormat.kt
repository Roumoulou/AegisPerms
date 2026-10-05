// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.storify.JsonFormat
import fr.moulou.storify.StoreFormat
import fr.moulou.storify.utils.DeepCopier
import fr.moulou.storify.validation.ErrorLineLocator
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Le format des fichiers JSON du mod (cahier des charges, 4.2), un format de Storify, pour qu'un store lise et écrive par lui.
 *
 * La lecture est celle du `JsonFormat` strict de Storify, à qui elle est déléguée entière : un commentaire, une virgule finale, une clé inconnue ou une
 * clé écrite deux fois font refuser le fichier, et un BOM posé par un éditeur est toléré (4.11, 4.12).
 *
 * L'écriture est celle du mod (4.4) : les données passent par l'arbre JSON, que le format trie quand le fichier le demande, puis [JsonLayout] le met en
 * page. Le texte est de l'UTF-8 sans BOM, en LF.
 */
class JsonFileFormat private constructor(private val json: Json, private val sorted: Boolean) : StoreFormat {

    companion object {
        /** La clé du numéro de structure, en tête de tout fichier que le mod lit (4.2). */
        internal const val SCHEMA_VERSION = "schema-version"

        /** Le format du fichier des réglages (4.3) : tous les réglages s'écrivent, dans l'ordre de leur déclaration, et les tables dans l'ordre de leurs entrées. */
        val SETTINGS = JsonFileFormat(strictJson(encodeDefaults = true), sorted = false)

        /**
         * Le format du fichier des droits (4.4) : `schema-version` en tête, les autres clés triées par point de code à tous les niveaux, et rien de ce qui
         * vaut sa valeur par défaut, un poids à 0, une liste, un objet ou un champ vides. Deux droits égaux donnent ainsi le même texte.
         */
        val PERMISSIONS = JsonFileFormat(strictJson(encodeDefaults = false), sorted = true)

        /** Un `Json` aux réglages de lecture de kotlinx, qui sont stricts : ni syntaxe relâchée, ni commentaire, ni virgule finale, ni clé inconnue. */
        private fun strictJson(encodeDefaults: Boolean): Json = Json { this.encodeDefaults = encodeDefaults }
    }

    private val reader = JsonFormat(json)

    override fun fileExtension(): String = "json"

    override fun <DATA> decodeFromPath(deserializer: DeserializationStrategy<DATA>, path: Path): DATA = reader.decodeFromPath(deserializer, path)

    override fun <DATA> encodeToPath(serializer: SerializationStrategy<DATA>, data: DATA, path: Path) {
        path.toAbsolutePath().parent?.createDirectories()
        path.writeText(encodeToText(serializer, data))
    }

    /** Le texte que ce format écrit pour [data]. */
    fun <DATA> encodeToText(serializer: SerializationStrategy<DATA>, data: DATA): String {
        val tree = json.encodeToJsonElement(serializer, data)
        return JsonLayout.print(if (sorted) sort(tree, root = true) else tree)
    }

    /** L'ordre d'une liste est une donnée : il reste. */
    private fun sort(element: JsonElement, root: Boolean = false): JsonElement = when (element) {
        is JsonObject -> {
            val order = if (root) compareBy<String> { it != SCHEMA_VERSION }.then(CodePointOrder) else CodePointOrder
            JsonObject(element.entries.sortedWith(compareBy(order) { it.key }).associate { it.key to sort(it.value) })
        }
        is JsonArray -> JsonArray(element.map { sort(it) })
        is JsonPrimitive -> element
    }

    override fun deepCopier(): DeepCopier = reader.deepCopier()

    override fun lineLocator(): ErrorLineLocator = reader.lineLocator()
}
