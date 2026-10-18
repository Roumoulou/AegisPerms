// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.storify.JsonFormat
import fr.moulou.storify.StoreFormat
import fr.moulou.storify.utils.DeepCopier
import fr.moulou.storify.validation.DuplicateKeyException
import fr.moulou.storify.validation.ErrorLineLocator
import fr.moulou.storify.validation.JsonDuplicateKeys
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Le format des fichiers JSON du mod (cahier des charges, 4.2), un format de Storify, pour qu'un store lise et écrive par lui.
 *
 * La lecture est celle du `JsonFormat` strict de Storify, à qui elle est déléguée : un commentaire, une virgule finale, un champ inconnu ou un champ écrit
 * deux fois font refuser le fichier, et un BOM posé par un éditeur est toléré (4.11, 4.12). Le format y ajoute le numéro de structure : il refuse lui-même
 * un fichier qui ne le porte pas ou dont le numéro est plus récent que le mod, et porte au numéro courant, en mémoire, l'arbre d'un numéro plus ancien.
 *
 * L'écriture est celle du mod (4.4) : les données passent par l'arbre JSON, que le format trie quand le fichier le demande, puis [JsonLayout] le met en
 * page. Le texte est de l'UTF-8 sans BOM, en LF.
 */
class JsonFileFormat private constructor(private val json: Json, private val sorted: Boolean, internal val migrations: SchemaMigrations) : StoreFormat {

    companion object {
        /** Le format du fichier des réglages (4.3) : tous les réglages s'écrivent, dans l'ordre de leur déclaration, et les tables dans l'ordre de leurs entrées. */
        val SETTINGS = settings(SchemaMigrations(Settings.SCHEMA_VERSION))

        /**
         * Le format du fichier des droits (4.4) : `schema-version` en tête, les autres clés triées par point de code à tous les niveaux, et rien de ce qui
         * vaut sa valeur par défaut, un poids à 0, une liste, un objet ou un champ vides. Deux droits égaux donnent ainsi le même texte.
         */
        val PERMISSIONS = permissions(SchemaMigrations(Permissions.SCHEMA_VERSION))

        /**
         * Le format des fichiers que le mod écrit seul (4.1) : le manifeste d'une copie, le catalogue, l'état. Trié comme le fichier des droits, sans ce qui
         * vaut sa valeur par défaut, et de structure 1.
         */
        internal val GENERATED = JsonFileFormat(strictJson(encodeDefaults = false), sorted = true, SchemaMigrations(current = 1))

        internal fun settings(migrations: SchemaMigrations): JsonFileFormat = JsonFileFormat(strictJson(encodeDefaults = true), sorted = false, migrations)

        internal fun permissions(migrations: SchemaMigrations): JsonFileFormat = JsonFileFormat(strictJson(encodeDefaults = false), sorted = true, migrations)

        /** Un `Json` aux réglages de lecture de kotlinx, qui sont stricts : ni syntaxe relâchée, ni commentaire, ni virgule finale, ni champ inconnu. */
        private fun strictJson(encodeDefaults: Boolean): Json = Json { this.encodeDefaults = encodeDefaults }
    }

    private val reader = JsonFormat(json)

    override fun fileExtension(): String = "json"

    override fun <DATA> decodeFromPath(deserializer: DeserializationStrategy<DATA>, path: Path): DATA {
        val text = path.readText().removePrefix(FileText.BOM)
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        }
        // Un texte mal formé, ou qui n'est pas un objet, va au lecteur strict : c'est lui qui le diagnostique, avec sa ligne.
        if (root == null) return reader.decodeFromPath(deserializer, path)

        val version = SchemaVersion.of(root)
        if (version !is SchemaVersion.Known || version.number > migrations.current) throw SchemaVersionException(version, migrations.current)
        if (version.number == migrations.current) return reader.decodeFromPath(deserializer, path)

        val decoded = json.decodeFromJsonElement(deserializer, migrations.migrate(root, version.number))
        // L'arbre ne garde que le dernier d'un champ écrit deux fois : le doublon se cherche sur le texte, comme le lecteur strict le fait.
        JsonDuplicateKeys.firstDuplicate(text)?.let { throw DuplicateKeyException(it.key, it.line, it.path) }
        return decoded
    }

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
            val order = if (root) compareBy<String> { it != SchemaVersion.KEY }.then(CodePointOrder) else CodePointOrder
            JsonObject(element.entries.sortedWith(compareBy(order) { it.key }).associate { it.key to sort(it.value) })
        }
        is JsonArray -> JsonArray(element.map { sort(it) })
        is JsonPrimitive -> element
    }

    override fun deepCopier(): DeepCopier = reader.deepCopier()

    override fun lineLocator(): ErrorLineLocator = reader.lineLocator()
}
