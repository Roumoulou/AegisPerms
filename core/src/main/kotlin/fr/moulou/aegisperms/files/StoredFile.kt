// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.storify.StoreDecodeException
import fr.moulou.storify.core.Store
import fr.moulou.storify.validation.DuplicateKeyException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Un fichier de configuration et son store (cahier des charges, 4.11). Il ouvre le store, ce qui crée le fichier quand il manque, le relit, et construit à
 * la relecture un store qui n'a pas pu l'être plus tôt (4.8). Un fichier qui existe n'est jamais complété ni réécrit ici, et un fichier absent se crée à
 * l'ouverture d'un store, jamais à sa relecture.
 *
 * Rien de ce qui vient du fichier n'en sort en exception (4.9) : une lecture rend ses données, ou des constats. Le fichier s'inspecte avant d'être décodé
 * ([FileInspection]), et Storify reste le filet : ce qu'elle refuse encore devient un constat, avec la ligne qu'elle donne.
 */
internal class StoredFile<DATA : Any>(
    val file: ConfigurationFile,
    val path: Path,
    private val format: JsonFileFormat,
    private val serializer: KSerializer<DATA>,
    /** Construit le store sur [path] ; Storify y écrit le fichier quand il manque. */
    private val openStore: (Path) -> Store<DATA>,
) : AutoCloseable {

    /** Vrai une fois le store construit. */
    val isOpen: Boolean get() = store != null

    /** Vrai pour un store ouvert en lecture seule, qui refuserait toute écriture. */
    val isReadOnly: Boolean get() = store?.isReadOnly == true

    /** Ce que le store tient, les données de sa dernière lecture réussie, ou `null` tant qu'il n'a pas pu se construire. */
    val data: DATA? get() = store?.data

    private val inspection = FileInspection(file, format.migrations)

    private var store: Store<DATA>? = null

    /** Lit le fichier par son store : l'ouvre s'il ne l'est pas, ce qui crée un fichier absent, et le relit sinon. En échec, le store garde ce qu'il tenait. */
    fun read(): FileRead<DATA> {
        val opened = store
        if (opened == null && !path.exists()) return create()
        return read(path) {
            if (opened == null) {
                open().data
            } else {
                opened.reloadFromFile()
                opened.data
            }
        }
    }

    private fun create(): FileRead<DATA> = try {
        val data = open().data
        FileRead.Loaded(data, created = true, FileText.read(path, format))
    } catch (failure: Exception) {
        FileRead.Refused(listOf(issueOf(path, failure)))
    }

    private fun open(): Store<DATA> = openStore(path).also { store = it }

    private fun read(source: Path, decode: () -> DATA): FileRead<DATA> {
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

    /** Le constat d'une lecture que Storify ou le format refusent. Storify enveloppe ce qu'elle lève en lisant ; un décodage hors store le lève nu. */
    private fun issueOf(source: Path, failure: Exception): Issue {
        val cause = (failure as? StoreDecodeException)?.cause ?: failure
        return when (cause) {
            is DuplicateKeyException -> Issue(IssueKind.DUPLICATE_KEY, file, emptyList(), literals(cause.key), cause.line)
            // Le filet du format : l'inspection a déjà refusé ce numéro, sauf si le fichier a changé entre les deux lectures.
            is SchemaVersionException -> inspection.versionIssue(cause.version) ?: malformed(source, failure, cause)
            is SerializationException -> malformed(source, failure, cause)
            else -> unreadable(cause)
        }
    }

    private fun malformed(source: Path, failure: Exception, cause: Throwable): Issue {
        // La ligne est celle que Storify tire du message du parseur ; hors store, la même exception se construit pour la lui demander.
        val line = (failure as? StoreDecodeException ?: StoreDecodeException(source, format, cause)).line
        val said = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifEmpty { cause.javaClass.simpleName }
        return Issue(IssueKind.MALFORMED_FILE, file, emptyList(), literals(said), line)
    }

    private fun unreadable(cause: Throwable): Issue = Issue(IssueKind.UNREADABLE_FILE, file, emptyList(), literals(listOfNotNull(cause.javaClass.simpleName, cause.message).joinToString(": ")))

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)

    override fun close() {
        store?.close()
        store = null
    }
}
