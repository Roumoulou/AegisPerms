// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * L'inspection de l'arbre JSON d'un fichier, avant son décodage (cahier des charges, 4.5) : le numéro de structure, puis l'arbre confronté aux descripteurs
 * du modèle. Elle dit en une passe tous les champs inconnus et toutes les valeurs hors de leur domaine, chacune avec son chemin JSON, là où le décodage
 * s'arrêterait à la première, sans chemin ni nature.
 *
 * Les clés et les domaines fermés se lisent dans les descripteurs, donc dans les annotations du modèle : rien ne s'en recopie ici. Seule la règle a sa
 * connaissance propre, parce qu'elle s'écrit de deux façons (4.4).
 */
@OptIn(ExperimentalSerializationApi::class)
internal class FileInspection(private val file: ConfigurationFile, private val migrations: SchemaMigrations) {

    companion object {
        private const val RULE_VALUE = "value"

        /** Au-delà, une valeur citée dans un constat est coupée : un objet entier n'a rien à faire dans un message. */
        private const val WRITTEN_LIMIT = 60

        private val VERSION_PATH = listOf(SchemaVersion.KEY)

        private val RULE: String = Rule.serializer().descriptor.serialName
    }

    /**
     * Les constats de [root], la racine d'un fichier dont [descriptor] décrit la structure courante. Un numéro de structure que le mod ne lit pas arrête
     * l'inspection, et un numéro plus ancien s'inspecte une fois son arbre migré. Les constats sortent sans ligne.
     */
    fun inspect(root: JsonElement, descriptor: SerialDescriptor): List<Issue> {
        if (root !is JsonObject) return listOf(outOfDomain(emptyList(), root, ValueDomain.OBJECT))
        val version = SchemaVersion.of(root)
        versionIssue(version)?.let { return listOf(it) }
        val current = migrations.migrate(root, (version as SchemaVersion.Known).number)
        return buildList { check(current, descriptor, emptyList()) }
    }

    /** Le constat d'un numéro de structure que le mod ne lit pas, ou `null` quand il le lit, tel quel ou par migration. */
    fun versionIssue(version: SchemaVersion): Issue? = when (version) {
        SchemaVersion.Missing -> Issue(IssueKind.MISSING_SCHEMA_VERSION, file, VERSION_PATH)
        is SchemaVersion.Invalid -> outOfDomain(VERSION_PATH, version.written, ValueDomain.SCHEMA_VERSION)
        is SchemaVersion.Known -> if (version.number <= migrations.current) null else Issue(IssueKind.NEWER_SCHEMA_VERSION, file, VERSION_PATH, literals(version.number.toString(), migrations.current.toString()))
    }

    private fun MutableList<Issue>.check(element: JsonElement, descriptor: SerialDescriptor, path: List<String>) {
        if (element is JsonNull && descriptor.isNullable) return
        when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> checkClass(element, descriptor, path)
            StructureKind.MAP -> if (element is JsonObject) for ((key, value) in element) check(value, descriptor.getElementDescriptor(1), path + key) else this += outOfDomain(path, element, ValueDomain.OBJECT)
            StructureKind.LIST -> if (element is JsonArray) element.forEachIndexed { index, value -> check(value, descriptor.getElementDescriptor(0), path + index.toString()) } else this += outOfDomain(path, element, ValueDomain.LIST)
            SerialKind.ENUM -> if (!(element is JsonPrimitive && element.isString && element.content in descriptor.elementNames)) this += outOfDomain(path, element, IssueArgument.Literal(descriptor.elementNames.joinToString(", ")))
            PrimitiveKind.INT -> if (unquoted(element)?.toIntOrNull() == null) this += outOfDomain(path, element, ValueDomain.INTEGER)
            PrimitiveKind.BOOLEAN -> if (unquoted(element) !in setOf("true", "false")) this += outOfDomain(path, element, ValueDomain.BOOLEAN)
            PrimitiveKind.STRING -> if (!(element is JsonPrimitive && element.isString)) this += outOfDomain(path, element, ValueDomain.TEXT)
            // Le modèle n'a pas d'autre genre de valeur : s'il en vient un, le décodage reste juge.
            else -> {}
        }
    }

    private fun MutableList<Issue>.checkClass(element: JsonElement, descriptor: SerialDescriptor, path: List<String>) {
        val isRule = descriptor.serialName == RULE
        if (isRule && element is JsonPrimitive && element.isString) {
            // La forme courte : la valeur de la règle, seule.
            check(element, descriptor.getElementDescriptor(descriptor.getElementIndex(RULE_VALUE)), path)
            return
        }
        if (element !is JsonObject) {
            this += outOfDomain(path, element, if (isRule) ValueDomain.RULE else ValueDomain.OBJECT)
            return
        }
        for ((key, value) in element) {
            val index = descriptor.getElementIndex(key)
            if (index == CompositeDecoder.UNKNOWN_NAME) this += Issue(IssueKind.UNKNOWN_KEY, file, path + key, literals(key)) else check(value, descriptor.getElementDescriptor(index), path + key)
        }
        if (isRule && RULE_VALUE !in element) this += outOfDomain(path, element, ValueDomain.RULE)
    }

    private fun outOfDomain(path: List<String>, element: JsonElement, domain: ValueDomain): Issue = outOfDomain(path, element, IssueArgument.Translatable(domain.translationKey))

    private fun outOfDomain(path: List<String>, element: JsonElement, domain: IssueArgument): Issue = Issue(IssueKind.VALUE_OUT_OF_DOMAIN, file, path, listOf(IssueArgument.Literal(written(element)), domain))

    /** La valeur telle qu'écrite, guillemets compris : `"1"` n'est pas `1`. */
    private fun written(element: JsonElement): String = element.toString().let { if (it.length > WRITTEN_LIMIT) it.take(WRITTEN_LIMIT) + "..." else it }

    /** Le texte d'une valeur simple écrite sans guillemets, un nombre ou un booléen ; `null` pour toute autre. */
    private fun unquoted(element: JsonElement): String? = (element as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }?.content

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)
}
