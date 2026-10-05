// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.SensitiveReason
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Instant

/**
 * Le catalogue (cahier des charges, 4.6), le contenu de `catalog.json` : ce que le serveur connaît de clés. Le mod l'écrit seul, à chaque démarrage et à
 * chaque `/reload` réussi, et ne le lit jamais comme une configuration.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CatalogFile(
    @SerialName("schema-version") val schemaVersion: Int,
    /** Les clés de commande du dispatcher en service, sous leur texte. Elles se recalculent à chaque écriture. */
    @EncodeDefault val commands: Map<String, CommandEntry> = emptyMap(),
    /** Les clés d'API, sous leur texte. Elles se gardent d'un démarrage à l'autre : un mod ne demande une clé qu'au moment où il en a besoin. */
    @SerialName("api-keys") @EncodeDefault val apiKeys: Map<String, ApiKeyEntry> = emptyMap(),
) {

    companion object {
        const val FILE_NAME = "catalog.json"

        /** Le numéro de structure que cette version lit et écrit. */
        const val SCHEMA_VERSION = 1

        /** Le catalogue de [path], ou `null` quand il ne se lit pas : il se régénère alors, sans les clés d'API qu'il gardait ni leurs dates. */
        internal fun read(path: Path): CatalogFile? = try {
            JsonFileFormat.GENERATED.decodeFromPath(serializer(), path)
        } catch (_: Exception) {
            null
        }

        /**
         * Le catalogue qui succède à [previous] : les clés de commande sont celles de [commands], qui remplacent les anciennes ; les clés d'API sont
         * celles de [previous], avec leur date, plus celles de [requestedApiKeys], une clé vers son type, datées de [now] quand elles sont nouvelles.
         */
        fun succeeding(previous: CatalogFile?, commands: Map<String, CommandEntry>, requestedApiKeys: Map<String, String>, now: Instant): CatalogFile {
            val known = previous?.apiKeys.orEmpty()
            val requested = requestedApiKeys.mapValues { (key, type) -> ApiKeyEntry(type, known[key]?.firstRequested ?: now.toString()) }
            return CatalogFile(SCHEMA_VERSION, commands, known + requested)
        }
    }
}

/** Ce que le catalogue dit d'une clé de commande (4.6). Ce qui vaut sa valeur par défaut ne s'écrit pas. */
@Serializable
data class CommandEntry(
    val kind: NodeKind,
    /** Vrai pour un nœud qui s'exécute, pas seulement un passage vers ses enfants. */
    val executable: Boolean = false,
    /** Ce que vanilla exige pour ce nœud. */
    val requirement: CommandRequirement = CommandRequirement.NONE,
    /** La clé de la cible, quand cette clé est celle d'un alias : une règle écrite sur elle ne couvre rien (2.4). */
    @SerialName("alias-of") val aliasOf: String? = null,
    /** Vrai pour un nœud rattaché à plusieurs nœuds de l'arbre, qui porte la clé de son premier chemin (2.4). */
    val shared: Boolean = false,
    /** Vrai pour un nœud plus exigeant que son ancêtre (2.8). */
    @SerialName("more-demanding") val moreDemanding: Boolean = false,
    /** Ce qui rend la clé sensible (2.9), par le nom de la raison ([sensitiveName]) ; `null` pour une clé qui ne l'est pas. */
    val sensitive: String? = null,
    /** Le modid du mod propriétaire de la commande racine (3.2). */
    val owner: String? = null,
    /** Les modids des autres mods qui enregistrent la même racine. */
    @SerialName("co-owners") val coOwners: List<String> = emptyList(),
    /** La forme préfixée de la commande, telle qu'on la tape, sans la barre oblique (3.4). */
    @SerialName("prefixed-form") val prefixedForm: String? = null,
    /** Les noms des alias qui mènent à ce nœud (3.6). */
    val aliases: List<String> = emptyList(),
) {

    companion object {
        /** Le nom sous lequel une raison s'écrit au catalogue, celui de la fin de sa clé de traduction. */
        fun sensitiveName(reason: SensitiveReason): String = reason.name.lowercase()
    }
}

/** La nature d'un nœud de commande. */
@Serializable
enum class NodeKind {
    @SerialName("literal") LITERAL,
    @SerialName("argument") ARGUMENT,
}

/** Ce que vanilla exige pour un nœud de commande (2.8) : rien, un niveau d'op, ou un prédicat de mod, que le mod ne sait pas lire. */
@Serializable
enum class CommandRequirement(val opLevel: OpLevel? = null) {
    @SerialName("none") NONE,
    @SerialName("moderators") MODERATORS(OpLevel.MODERATORS),
    @SerialName("gamemasters") GAMEMASTERS(OpLevel.GAMEMASTERS),
    @SerialName("admins") ADMINS(OpLevel.ADMINS),
    @SerialName("owners") OWNERS(OpLevel.OWNERS),
    @SerialName("mod-predicate") MOD_PREDICATE;

    companion object {
        /** L'exigence d'un niveau d'op. */
        fun of(opLevel: OpLevel): CommandRequirement = entries.first { it.opLevel == opLevel }
    }
}

/** Ce que le catalogue dit d'une clé d'API (4.6) : son type, et l'instant de sa première demande, en ISO 8601, à la seconde et en UTC. */
@Serializable
data class ApiKeyEntry(val type: String, @SerialName("first-requested") val firstRequested: String)
