// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/** Qui porte un ensemble de règles : un groupe, par son nom, ou un joueur, par sa forme lisible. */
sealed interface RuleSetOwner {

    data class OfGroup(val name: String) : RuleSetOwner

    data class OfPlayer(val readableForm: String) : RuleSetOwner
}
