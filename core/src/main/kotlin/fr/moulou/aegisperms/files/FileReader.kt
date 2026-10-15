// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.storify.StoreDecodeException
import fr.moulou.storify.validation.DuplicateKeyException
import fr.moulou.storify.validation.PathSegment
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.nio.file.Path

/**
 * La lecture d'un fichier de configuration (cahier des charges, 4.5), la même avec ou sans store : par son store au démarrage et au rechargement, sans
 * store pour `/aegis validate`, une sauvegarde ou la copie `last-good`.
 *
 * Rien de ce qui vient du fichier n'en sort en exception (4.9) : une lecture rend ses données, ou des constats. Le fichier s'inspecte avant d'être décodé
 * ([FileInspection]), et le décodage strict de Storify reste le filet : ce qu'il refuse encore devient un constat, avec la ligne qu'il donne.
 */
internal class FileReader<DATA : Any>(val file: ConfigurationFile, private val format: JsonFileFormat, private val serializer: KSerializer<DATA>) {

    private val inspection = FileInspection(file, format.migrations)

    /** Lit [source] sans store, sans rien créer ni retenir. */
    fun read(source: Path): FileRead<DATA> = read(source) { format.decodeFromPath(serializer, source) }

    /** Lit [source], que [decode] décode une fois l'inspection passée : un store qui s'ouvre ou se relit. */
    fun read(source: Path, decode: () -> DATA): FileRead<DATA> {
        val text = try {
            FileText.read(source, format)
        } catch (failure: Exception) {
            return FileRead.Refused(listOf(unreadable(failure)))
        }
        // Un texte qui n'est pas du JSON strict n'a pas d'arbre à inspecter : le décodage le refusera, avec sa ligne.
        val found = text.tree?.let { inspection.inspect(it, serializer.descriptor) }.orEmpty()
        if (found.isNotEmpty()) return FileRead.Refused(found.map(text::withLine))
        return try {
            FileRead.Loaded(decode(), created = false, text)
        } catch (failure: Exception) {
            FileRead.Refused(listOf(issueOf(source, failure)))
        }
    }

    /** Le constat d'une lecture de [source] que Storify ou le format refusent. Storify enveloppe ce qu'elle lève en lisant ; un décodage sans store le lève nu. */
    fun issueOf(source: Path, failure: Exception): Issue {
        val cause = (failure as? StoreDecodeException)?.cause ?: failure
        return when (cause) {
            is DuplicateKeyException -> Issue(IssueKind.DUPLICATE_KEY, file, pathOf(cause.path), literals(cause.key), cause.line)
            // Le filet du format : l'inspection a déjà refusé ce numéro, sauf si le fichier a changé entre les deux lectures.
            is SchemaVersionException -> inspection.versionIssue(cause.version) ?: malformed(source, failure, cause)
            is SerializationException -> malformed(source, failure, cause)
            else -> unreadable(cause)
        }
    }

    private fun malformed(source: Path, failure: Exception, cause: Throwable): Issue {
        // La ligne est celle que Storify tire du message du parseur, du chemin qu'il nomme ou de la fin du fichier atteinte (C-56) ; sans store, la même
        // exception se construit pour la lui demander.
        val line = (failure as? StoreDecodeException ?: StoreDecodeException(source, format, cause)).line
        val said = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifEmpty { cause.javaClass.simpleName }
        return Issue(IssueKind.MALFORMED_FILE, file, emptyList(), literals(said), line)
    }

    /** Le chemin d'un doublon, que Storify donne depuis sa 0.6.0 (C-55), dans la forme des constats : la clé en dernier segment, un rang de liste en chiffres. */
    private fun pathOf(segments: List<PathSegment>): List<String> = segments.map {
        when (it) {
            is PathSegment.Key -> it.name
            is PathSegment.Index -> it.index.toString()
        }
    }

    private fun unreadable(cause: Throwable): Issue = Issue(IssueKind.UNREADABLE_FILE, file, emptyList(), literals(listOfNotNull(cause.javaClass.simpleName, cause.message).joinToString(": ")))

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)
}
