// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

/**
 * Les clés de traduction que le code nomme (cahier des charges, 5.7), hors celles des constats, `aegisperms.issue.<nature>`, que `IssueKind` porte, et celles
 * du journal, `aegisperms.log.<événement>`, que `ModLog` porte. Les deux fichiers de langue portent chacune d'elles : l'étage 0 le vérifie sur les constantes
 * de cet objet, l'étage 1 sur les événements du journal.
 */
object TextKeys {

    /** Le préfixe de toute clé du mod. */
    const val PREFIX = "aegisperms."

    /** Le préfixe des événements du journal (5.9). */
    const val LOG_PREFIX = PREFIX + "log."

    /** Le préfixe des constats (4.5). */
    const val ISSUE_PREFIX = PREFIX + "issue."

    /** Le message du refus visible (5.6), que la porte d'exécution envoie avec son repli. */
    const val REFUSAL = PREFIX + "refusal"
}
