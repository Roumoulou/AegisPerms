// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

/**
 * L'empreinte d'un fichier (cahier des charges, 4.1) : sa taille et sa date de modification, que le mod retient à chaque lecture et à chaque écriture pour
 * reconnaître un changement fait par un autre (4.8).
 *
 * Elle ne lit pas le contenu : une édition qui garde la taille, dans la même unité de date du système de fichiers, lui échappe.
 */
internal data class Fingerprint(val size: Long, val modified: FileTime) {

    companion object {
        /** L'empreinte de [path], ou `null` quand il manque ou ne se laisse pas lire. */
        fun of(path: Path): Fingerprint? = try {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            Fingerprint(attributes.size(), attributes.lastModifiedTime())
        } catch (_: IOException) {
            null
        }
    }
}
