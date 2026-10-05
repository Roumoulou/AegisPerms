// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Un joueur (cahier des charges, 2.5), tel que le fichier des droits l'écrit (4.4) : sa forme lisible ([ReadableForm]) est la clé de l'entrée qui le porte,
 * et l'UUID qu'elle donne est son identité.
 */
data class Player(
    /** Les groupes directs, dans l'ordre écrit. */
    val groups: List<String> = emptyList(),
    /** Les règles propres du joueur, sous le texte de leur clé. */
    val rules: Map<String, Rule> = emptyMap(),
    val note: String? = null,
)
