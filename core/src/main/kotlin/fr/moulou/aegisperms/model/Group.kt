// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Un groupe (cahier des charges, 2.5), tel que le fichier des droits l'écrit (4.4) : son nom est la clé de l'entrée qui le porte, et tous ses champs sont
 * facultatifs.
 */
data class Group(
    val weight: Int = 0,
    /** Les parents, dans l'ordre déclaré. */
    val parents: List<String> = emptyList(),
    /** Le niveau d'op à partir duquel un op est membre d'office, ou `null` quand le groupe n'en compte pas. */
    val includeOps: OpLevel? = null,
    /** Les règles propres du groupe, sous le texte de leur clé. */
    val rules: Map<String, Rule> = emptyMap(),
    val note: String? = null,
) {

    companion object {
        /** Le groupe qui s'applique à tout joueur sans être nommé, et qui ne se supprime pas. */
        const val DEFAULT = "default"

        private val NAME = Regex("[a-z0-9_-]+")

        /** Vrai pour un nom fait de minuscules, de chiffres, de `_` et de `-`. */
        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
