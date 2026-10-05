// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.Issue

/** Ce que la lecture d'un fichier rend (cahier des charges, 4.5) : ses données, ou les constats qui le font refuser. */
internal sealed interface FileRead<out DATA> {

    /**
     * Le fichier se lit. [created] est vrai quand il manquait et que le mod vient de l'écrire avec les valeurs livrées. [text] est le texte lu, pour donner
     * leur ligne aux constats que la validation fera de [data].
     */
    class Loaded<DATA>(val data: DATA, val created: Boolean, val text: FileText) : FileRead<DATA>

    /** Le fichier est refusé : [issues] dit pourquoi, chaque constat avec son chemin JSON et sa ligne quand ils se trouvent. */
    class Refused(val issues: List<Issue>) : FileRead<Nothing>
}
