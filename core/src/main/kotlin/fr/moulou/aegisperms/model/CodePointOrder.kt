// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * L'ordre des chaînes, point de code par point de code (cahier des charges, 2.6) : celui des noms de groupes dans l'ordre d'examen, et de tout ce que le mod
 * trie. Il ne dépend d'aucune langue, et s'écarte de l'ordre naturel de `String`, par unité UTF-16, au-delà du plan de base d'Unicode.
 */
object CodePointOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i)
            val cb = b.codePointAt(j)
            if (ca != cb) return ca.compareTo(cb)
            i += Character.charCount(ca)
            j += Character.charCount(cb)
        }
        return (a.length - i).compareTo(b.length - j)
    }
}
