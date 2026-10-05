// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.storify.utils.AtomicFiles
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Un dossier qui n'apparaît qu'entier (cahier des charges, 4.9 et 4.10) : ses fichiers s'écrivent dans un dossier voisin, sous un nom temporaire, que son
 * appelant met ensuite en place d'un seul déplacement. Une coupure laisse au pire un dossier temporaire, que le passage suivant balaie.
 *
 * Le nom temporaire est celui des écritures atomiques de Storify, `<nom>.<8 hexadécimaux>.tmp`, et le balayage ne reconnaît que lui : un dossier qu'un
 * autre a posé là n'est jamais touché.
 */
internal object StagedDirectory {

    /** Écrit par [write] un dossier voisin de [target], et le rend, prêt à être mis en place. En échec, il est retiré et l'exception remonte. */
    fun stage(target: Path, write: (Path) -> Unit): Path {
        val staged = target.resolveSibling(AtomicFiles.tempFileName(target.fileName.toString()))
        try {
            Files.createDirectories(staged)
            write(staged)
            return staged
        } catch (failure: Exception) {
            runCatching { delete(staged) }
            throw failure
        }
    }

    /** Met [source] à la place de [target], qui ne doit pas exister : atomiquement quand le système de fichiers le permet, et jamais par-dessus un autre. */
    fun move(source: Path, target: Path) {
        // Le déplacement atomique d'un dossier sur un autre remplace ou échoue selon le système : le refus se fait donc ici.
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FileAlreadyExistsException(target.toString())
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    /** Retire les temporaires qu'une coupure a laissés à côté de [target]. Silencieux sur un dossier absent ou illisible. */
    fun sweep(target: Path) {
        val parent = target.toAbsolutePath().parent ?: return
        val own = AtomicFiles.ownTempPattern(target.fileName.toString())
        runCatching {
            Files.newDirectoryStream(parent) { candidate -> own.matches(candidate.fileName.toString()) }.use { orphans -> orphans.forEach { runCatching { delete(it) } } }
        }
    }

    /** Retire [path], fichier ou dossier, avec tout ce qu'il porte. Sans effet s'il manque. */
    fun delete(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(path).use { tree -> tree.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
