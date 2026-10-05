// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import java.util.UUID

/**
 * La forme lisible d'un joueur (cahier des charges, 4.4) : `Pseudo#uuid`. Elle se découpe à son dernier `#`, parce qu'un UUID n'en contient pas et qu'un
 * pseudo hors ligne le peut.
 */
object ReadableForm {

    /** Ce qui tient la place d'un pseudo inconnu. */
    const val UNKNOWN_NAME = "?"

    /** La forme lisible d'un joueur de pseudo [name], ou sans pseudo connu. */
    fun of(name: String?, uuid: UUID): String = "${name ?: UNKNOWN_NAME}#$uuid"

    /** Le pseudo, `null` pour `?`, et le texte de l'UUID, que rien ne vérifie ici ; `null` quand la forme n'a pas ses deux parts. */
    fun split(readable: String): Pair<String?, String>? {
        val hash = readable.lastIndexOf('#')
        if (hash <= 0 || hash == readable.length - 1) return null
        val name = readable.substring(0, hash)
        return (if (name == UNKNOWN_NAME) null else name) to readable.substring(hash + 1)
    }

    /** Vrai pour un UUID écrit en forme canonique : en minuscules, avec ses tirets. */
    fun isCanonicalUuid(text: String): Boolean = parseUuid(text)?.toString() == text

    /** L'UUID que [text] écrit, en forme canonique ou non, ou `null`. */
    fun parseUuid(text: String): UUID? = try {
        if (text.length != 36) null else UUID.fromString(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
