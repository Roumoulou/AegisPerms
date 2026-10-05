// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

/** Ce qu'une sauvegarde rend (cahier des charges, 4.10). */
sealed interface BackupResult {

    /** Les fichiers en place sont copiés dans le dossier [folder] de `backups\`, sous le nom qu'il a fini par prendre. */
    data class Done(val folder: String) : BackupResult

    /** Le nom demandé ne fait pas un nom de dossier sûr : des minuscules, des chiffres, `_` et `-`, trente-deux caractères au plus. */
    data object InvalidName : BackupResult

    /** Aucun des deux fichiers n'existe : il n'y a rien à copier. */
    data object NothingToBackUp : BackupResult

    /** La copie a échoué, et [cause] dit la panne. Aucun dossier de sauvegarde n'est apparu. */
    data class Failed(val cause: Exception) : BackupResult
}
