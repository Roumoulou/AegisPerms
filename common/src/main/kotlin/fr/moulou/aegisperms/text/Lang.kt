// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

/** Une des deux langues du mod (cahier des charges, 5.7) : le code que le réglage `language` emploie, et le nom de son fichier de langue dans le jar. */
enum class Lang(val code: String, val fileName: String) {
    FR("fr", "fr_fr"),
    EN("en", "en_us");

    companion object {
        /** La langue d'un code de client ou de locale, `fr_fr`, `fr-CA`, `fr`, `en_us` : le français pour `fr`, l'anglais pour tout le reste, `null` compris. */
        fun ofCode(code: String?): Lang {
            val lower = code?.lowercase() ?: return EN
            return if (lower == "fr" || lower.startsWith("fr_") || lower.startsWith("fr-")) FR else EN
        }
    }
}
