// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ce qu'une règle dit de sa clé (cahier des charges, 2.5), tel que le fichier des droits l'écrit (4.4) : la clé est celle de l'entrée qui porte la règle.
 * [visible] ne vaut que pour un `deny` ; absent, le réglage `denied-commands` décide.
 *
 * Elle s'écrit en forme courte ou longue par [RuleSerializer], qui s'appuie sur le sérialiseur généré, gardé pour lui.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = RuleSerializer::class)
@KeepGeneratedSerializer
data class Rule(val value: RuleValue, val visible: Boolean? = null, val note: String? = null) {

    companion object {
        /** La forme courte `"allow"`. */
        val ALLOW = Rule(RuleValue.ALLOW)

        /** La forme courte `"deny"`. */
        val DENY = Rule(RuleValue.DENY)
    }
}

/** La valeur d'une règle, `allow` ou `deny` : `unset` n'en est pas une, c'est l'absence de règle. */
@Serializable
enum class RuleValue {
    @SerialName("allow") ALLOW,
    @SerialName("deny") DENY,
}
