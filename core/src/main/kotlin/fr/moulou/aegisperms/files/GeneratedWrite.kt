// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

/** Ce que l'écriture d'un fichier généré rend (cahier des charges, 4.1) : la copie `last-good`, le catalogue, l'état. Une panne s'y rend, elle ne se lève pas. */
sealed interface GeneratedWrite {

    data object Done : GeneratedWrite

    /** L'écriture a échoué, et [cause] dit la panne. Ce qui était en place avant y reste. */
    data class Failed(val cause: Exception) : GeneratedWrite
}
