// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.storify.utils.AtomicFiles
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Les sauvegardes (cahier des charges, 4.10), sous `backups\` : une copie à l'octet des fichiers en place, dans un dossier horodaté qui n'apparaît
 * qu'entier, avec son manifeste. Rien ne s'y valide : on sauvegarde aussi des fichiers fautifs, et c'est souvent le moment où on le veut.
 *
 * Le mod n'y supprime jamais rien, pas même un dossier temporaire qu'une coupure y aurait laissé : aucune sauvegarde ne s'écrase, et aucune ne se retire.
 */
internal class Backups(private val directory: Path, private val reader: ConfigurationReader) {

    companion object {
        const val NAME = "backups"

        /** Le nom de la sauvegarde que la restauration fait des fichiers en place, avant de les remplacer. */
        const val BEFORE_RESTORE = "before-restore"

        /** Un nom de sauvegarde tient dans un nom de dossier, sous Windows compris, où la casse ne distingue rien. */
        private val NAME_PATTERN = Regex("[a-z0-9_-]{1,32}")

        /** L'horodatage d'un dossier, sans les deux-points que Windows refuse. */
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)
    }

    val root: Path = directory.resolve(NAME)

    /**
     * Copie les fichiers en place dans `backups\<horodatage>[-nom]\`, à l'heure locale de [clock], et rend le nom du dossier écrit ; `null` quand aucun
     * des deux fichiers n'existe. Un dossier déjà là n'est jamais écrasé : la sauvegarde prend alors le suffixe `-2`, puis `-3`. Une panne remonte.
     */
    fun write(name: String?, clock: Clock): String? {
        val present = LastGood.FILES.filter { Files.isRegularFile(directory.resolve(it)) }
        if (present.isEmpty()) return null

        val base = LocalDateTime.now(clock).format(STAMP) + name?.let { "-$it" }.orEmpty()
        val staged = StagedDirectory.stage(root.resolve(base)) { copy ->
            for (file in present) Files.copy(directory.resolve(file), copy.resolve(file))
            Manifest(Manifest.SCHEMA_VERSION, present, generation = null, clock.instant().truncatedTo(ChronoUnit.SECONDS).toString()).writeTo(copy)
        }
        try {
            var folder = base
            var next = 2
            while (true) {
                try {
                    StagedDirectory.move(staged, root.resolve(folder))
                    return folder
                } catch (_: FileAlreadyExistsException) {
                    folder = "$base-${next++}"
                }
            }
        } catch (failure: Exception) {
            runCatching { StagedDirectory.delete(staged) }
            throw failure
        }
    }

    /** Les sauvegardes, par le nom de leur dossier, triées : les dossiers de `backups\` qui portent un manifeste lisible. */
    fun list(): List<String> {
        if (!Files.isDirectory(root)) return emptyList()
        return try {
            Files.list(root).use { found -> found.filter { Files.isDirectory(it) && Manifest.read(it) != null }.map { it.fileName.toString() }.toList() }.sortedWith(CodePointOrder)
        } catch (_: IOException) {
            emptyList()
        }
    }

    /** Le dossier de la sauvegarde nommée [folder], ou `null`. Seul un nom que [list] rend est admis : aucun chemin ne sort de `backups\`. */
    fun resolve(folder: String): Path? = if (folder in list()) root.resolve(folder) else null

    /** La sauvegarde de [folder], lue et validée comme une configuration entière ; `null` quand elle ne porte pas les deux fichiers. */
    fun read(folder: Path, catalog: Catalog?): ConfigurationRead? {
        val manifest = Manifest.read(folder) ?: return null
        return if (manifest.lists(LastGood.FILES, folder)) reader.read(folder, catalog) else null
    }

    /**
     * Remplace les fichiers en place par ceux de [folder], à l'octet, l'un après l'autre : chacun est toujours une version entière, mais les deux ne se
     * remplacent pas d'un seul geste. Une panne remonte.
     */
    fun restore(folder: Path) {
        for (file in LastGood.FILES) AtomicFiles.write(directory.resolve(file)) { temporary -> Files.copy(folder.resolve(file), temporary) }
    }
}
