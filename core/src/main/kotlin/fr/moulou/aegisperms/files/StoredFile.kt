// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.storify.core.Store
import fr.moulou.storify.encodeToPathAtomically
import kotlinx.serialization.KSerializer
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Un fichier de configuration et son store (cahier des charges, 4.11). Il ouvre le store, ce qui crée le fichier quand il manque, le relit, et construit à
 * la relecture un store qui n'a pas pu l'être plus tôt (4.8). Un fichier qui existe n'est jamais complété ici, et un fichier absent se crée à l'ouverture
 * d'un store, jamais à sa relecture.
 *
 * Il retient l'empreinte du fichier à chaque lecture par le store, réussie ou non, et à chaque écriture : c'est ce qui fait reconnaître un changement
 * fait par un autre (4.8).
 *
 * La lecture elle-même, et ce qu'elle rend, est celle de [FileReader] : aucune exception de fichier ne sort d'une lecture.
 */
internal class StoredFile<DATA : Any>(
    val file: ConfigurationFile,
    val path: Path,
    private val format: JsonFileFormat,
    private val serializer: KSerializer<DATA>,
    /** Construit le store sur [path] ; Storify y écrit le fichier quand il manque. */
    private val openStore: (Path) -> Store<DATA>,
) : AutoCloseable {

    /** La lecture de ce fichier, que la lecture sans store partage. */
    val reader = FileReader(file, format, serializer)

    /** Vrai une fois le store construit. */
    val isOpen: Boolean get() = store != null

    /** Vrai pour un store ouvert en lecture seule, qui refuserait toute écriture. */
    val isReadOnly: Boolean get() = store?.isReadOnly == true

    /** Ce que le store tient, les données de sa dernière lecture réussie, ou `null` tant qu'il n'a pas pu se construire. */
    val data: DATA? get() = store?.data

    /** Vrai quand le fichier n'est plus celui de la dernière lecture ou écriture : un autre l'a écrit, remplacé ou supprimé. Vrai aussi avant toute lecture d'un fichier présent. */
    val hasChanged: Boolean get() = Fingerprint.of(path) != fingerprint

    private var store: Store<DATA>? = null

    private var fingerprint: Fingerprint? = null

    /** Vrai dès la première lecture : un fichier absent ne se crée qu'à l'ouverture, pas à une relecture, le store eût-il manqué son ouverture (4.8). */
    private var everRead = false

    /** Lit le fichier par son store : l'ouvre s'il ne l'est pas, ce qui crée un fichier absent, et le relit sinon. En échec, le store garde ce qu'il tenait. */
    fun read(): FileRead<DATA> {
        val opened = store
        val first = !everRead
        everRead = true
        if (opened == null && !path.exists()) {
            if (!first) {
                fingerprint = Fingerprint.of(path)
                return FileRead.Refused(listOf(reader.issueOf(path, NoSuchFileException(path.toString()))))
            }
            return create().also { fingerprint = Fingerprint.of(path) }
        }

        // L'empreinte se prend avant la lecture : un fichier qui change pendant qu'on le lit sera vu comme changé.
        fingerprint = Fingerprint.of(path)
        return reader.read(path) {
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
        FileRead.Refused(listOf(reader.issueOf(path, failure)))
    }

    private fun open(): Store<DATA> = openStore(path).also { store = it }

    /**
     * Écrit [data] dans le fichier, entier et atomiquement (4.2), puis le relit par le store, qui tient ainsi ce qui vient d'être écrit. Les données d'un
     * store sont immuables : c'est le fichier qui change, et le store qui le suit.
     *
     * Écrire sans store ouvert, ou sur un store en lecture seule, est une faute du code. Une panne d'écriture remonte telle quelle, le fichier précédent
     * resté entier : c'est à l'appelant de la rendre.
     */
    fun write(data: DATA): FileRead<DATA> {
        val opened = checkNotNull(store) { "No store is open on '$path': nothing can be written" }
        check(!opened.isReadOnly) { "The store of '$path' is read-only" }
        format.encodeToPathAtomically(serializer, data, path)
        return read()
    }

    override fun close() {
        store?.close()
        store = null
    }
}
