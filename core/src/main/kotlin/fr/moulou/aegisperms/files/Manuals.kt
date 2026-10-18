// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Les manuels des commandes (cahier des charges, 5.10), sous `manuals\` : un fichier Markdown par écriture, `commands-<aaaa-mm-jj>-<langue>.md`, en UTF-8
 * sans BOM et en LF, écrit à côté puis déplacé, pour qu'un lecteur ne voie jamais un manuel à moitié écrit. Aucun manuel ne s'écrase : un fichier déjà là
 * fait prendre au suivant le suffixe `-2`, puis `-3`, comme une sauvegarde (4.10). Le mod n'y supprime jamais rien.
 */
internal class Manuals(directory: Path) {

    companion object {
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    val root: Path = directory.resolve(ManualWrite.FOLDER)

    /** Écrit [text] pour la langue de code [language], à la date locale de [clock], et rend le nom du fichier écrit. Une panne remonte. */
    fun write(language: String, text: String, clock: Clock): String {
        Files.createDirectories(root)
        val base = "commands-" + LocalDate.now(clock).format(STAMP) + "-" + language
        val temporary = Files.createTempFile(root, "$base-", ".tmp")
        try {
            Files.writeString(temporary, text, Charsets.UTF_8)
            var name = "$base.md"
            var next = 2
            while (true) {
                try {
                    Files.move(temporary, root.resolve(name))
                    return name
                } catch (_: FileAlreadyExistsException) {
                    name = "$base-${next++}.md"
                }
            }
        } catch (failure: Exception) {
            runCatching { Files.deleteIfExists(temporary) }
            throw failure
        }
    }
}
