// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.storify.core.Store
import kotlinx.serialization.KSerializer
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Un fichier de configuration et son store (cahier des charges, 4.11). Il ouvre le store, ce qui crée le fichier quand il manque, le relit, et construit à
 * la relecture un store qui n'a pas pu l'être plus tôt (4.8). Un fichier qui existe n'est jamais complété ni réécrit ici, et un fichier absent se crée à
 * l'ouverture d'un store, jamais à sa relecture.
 *
 * La lecture elle-même, et ce qu'elle rend, est celle de [FileReader] : aucune exception de fichier ne sort d'ici.
 */
internal class StoredFile<DATA : Any>(
    val file: ConfigurationFile,
    val path: Path,
    private val format: JsonFileFormat,
    serializer: KSerializer<DATA>,
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

    private var store: Store<DATA>? = null

    /** Lit le fichier par son store : l'ouvre s'il ne l'est pas, ce qui crée un fichier absent, et le relit sinon. En échec, le store garde ce qu'il tenait. */
    fun read(): FileRead<DATA> {
        val opened = store
        if (opened == null && !path.exists()) return create()
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

    override fun close() {
        store?.close()
        store = null
    }
}
