// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Ce qu'une règle dit de sa clé (cahier des charges, 2.5), tel que le fichier des droits l'écrit (4.4) : la clé est celle de l'entrée qui porte la règle.
 * [visible] ne vaut que pour un `deny` ; absent, le réglage `denied-commands` décide.
 */
data class Rule(val value: RuleValue, val visible: Boolean? = null, val note: String? = null) {

    companion object {
        /** La forme courte `"allow"`. */
        val ALLOW = Rule(RuleValue.ALLOW)

        /** La forme courte `"deny"`. */
        val DENY = Rule(RuleValue.DENY)
    }
}

/** La valeur d'une règle. */
enum class RuleValue {
    ALLOW,
    DENY,
}
