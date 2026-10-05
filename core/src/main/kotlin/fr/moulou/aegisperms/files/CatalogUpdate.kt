// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.CodePointOrder

/** Ce que l'écriture du catalogue rend (cahier des charges, 4.6). */
data class CatalogUpdate(
    /** Le catalogue tel qu'il vient d'être calculé, écrit ou non. */
    val catalog: CatalogFile,
    /** Les clés de commande apparues et disparues depuis le catalogue précédent, à dire au journal ; `null` quand il n'y en avait pas à comparer. */
    val changes: CatalogChanges?,
    /** Vrai quand un catalogue précédent existait et ne se lisait pas : il vaut un avertissement, et les clés d'API qu'il gardait sont perdues, dates comprises. */
    val previousUnreadable: Boolean,
    /** Le sort de l'écriture elle-même. */
    val write: GeneratedWrite,
)

/** Les clés de commande apparues et disparues d'un catalogue au suivant, triées par point de code. */
data class CatalogChanges(val appeared: List<String>, val disappeared: List<String>) {

    companion object {
        fun between(previous: CatalogFile, next: CatalogFile): CatalogChanges = CatalogChanges(
            appeared = (next.commands.keys - previous.commands.keys).sortedWith(CodePointOrder),
            disappeared = (previous.commands.keys - next.commands.keys).sortedWith(CodePointOrder),
        )
    }

    val isEmpty: Boolean get() = appeared.isEmpty() && disappeared.isEmpty()
}
