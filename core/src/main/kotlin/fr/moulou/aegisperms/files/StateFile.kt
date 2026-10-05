// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.ValidationReport
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.time.Instant

/**
 * L'état (cahier des charges, 4.7), le contenu de `state.json` : la dernière tentative de publication, appliquée ou refusée, et ses constats. Il dit à qui
 * édite les fichiers sans voir la console si sa dernière édition a pris. Le mod l'écrit à chaque tentative et ne le relit jamais.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StateFile(
    @SerialName("schema-version") val schemaVersion: Int,
    /** La génération de l'état en vigueur après la tentative : la nouvelle quand elle est appliquée, celle qui reste quand elle est refusée. */
    val generation: Long,
    /** L'instant de la tentative, en ISO 8601, à la seconde et en UTC. */
    val date: String,
    val source: PublicationSource,
    val result: PublicationResult,
    /** Les constats de la lecture et de la validation, dans leur ordre. Ils s'écrivent toujours, même quand il n'y en a aucun. */
    @EncodeDefault val issues: List<StateIssue> = emptyList(),
) {

    companion object {
        const val FILE_NAME = "state.json"

        /** Le numéro de structure que cette version lit et écrit. */
        const val SCHEMA_VERSION = 1

        fun of(generation: Long, date: Instant, source: PublicationSource, result: PublicationResult, report: ValidationReport): StateFile =
            StateFile(SCHEMA_VERSION, generation, date.toString(), source, result, report.issues.map(StateIssue::of))

        /** L'état de [path], pour qui le consulte ; `null` quand il manque ou ne se lit pas. */
        internal fun read(path: Path): StateFile? = try {
            JsonFileFormat.GENERATED.decodeFromPath(serializer(), path)
        } catch (_: Exception) {
            null
        }
    }
}

/** Ce qui a demandé une publication (4.7). */
@Serializable
enum class PublicationSource {
    @SerialName("startup") STARTUP,
    @SerialName("reload") RELOAD,
    @SerialName("command") COMMAND,
    @SerialName("watch") WATCH,
    @SerialName("restore") RESTORE,
}

/** Le sort d'une tentative de publication (4.7). */
@Serializable
enum class PublicationResult {
    @SerialName("applied") APPLIED,
    @SerialName("refused") REFUSED,
}

/**
 * Un constat tel que l'état l'écrit : sans texte, parce que celui qui le lit le traduit, par la clé `aegisperms.issue.<kind>` (5.7). La nature, le niveau
 * et le fichier s'écrivent par leur nom, en texte libre, pour qu'un lecteur d'une autre version ne bute pas sur un nom qu'il ne connaît pas.
 */
@Serializable
data class StateIssue(
    val severity: String,
    val kind: String,
    val file: String,
    val path: List<String> = emptyList(),
    val line: Int? = null,
    val arguments: List<@Serializable(with = IssueArgumentSerializer::class) IssueArgument> = emptyList(),
) {

    companion object {
        fun of(issue: Issue): StateIssue = StateIssue(issue.severity.name.lowercase(), issue.kind.name.lowercase(), issue.file.fileName, issue.path, issue.line, issue.arguments)
    }
}

/**
 * Les deux écritures d'un argument de constat dans l'état : un texte nu pour un argument à reprendre tel quel, et `{ "translate": "<clé>" }` pour un
 * argument qui a sa propre clé de traduction. Il ne vaut que pour le JSON.
 */
internal object IssueArgumentSerializer : KSerializer<IssueArgument> {

    private const val TRANSLATE = "translate"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IssueArgument) {
        val element = when (value) {
            is IssueArgument.Literal -> JsonPrimitive(value.text)
            is IssueArgument.Translatable -> JsonObject(mapOf(TRANSLATE to JsonPrimitive(value.translationKey)))
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), element)
    }

    override fun deserialize(decoder: Decoder): IssueArgument {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        val key = ((element as? JsonObject)?.takeIf { it.keys == setOf(TRANSLATE) }?.get(TRANSLATE) as? JsonPrimitive)?.takeIf { it.isString }
        return when {
            key != null -> IssueArgument.Translatable(key.content)
            element is JsonPrimitive && element.isString -> IssueArgument.Literal(element.content)
            else -> throw SerializationException("An issue argument is a text or { \"$TRANSLATE\": \"<key>\" }: $element")
        }
    }
}
