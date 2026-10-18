// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

/** Ce que rend l'écriture d'un manuel des commandes (cahier des charges, 5.10). */
sealed interface ManualWrite {

    companion object {
        /** Le dossier des manuels, sous celui de la configuration. */
        const val FOLDER = "manuals"
    }

    /** Le manuel est écrit sous [fileName], dans [FOLDER]. */
    data class Done(val fileName: String) : ManualWrite

    /** Rien ne s'est écrit : [cause] dit pourquoi. */
    data class Failed(val cause: Exception) : ManualWrite
}
