// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/** La décision d'un sujet pour une clé (cahier des charges, 2.1) : `allow`, `deny` ou `unset`. Un `deny` est un refus, visible ou masqué (2.7). */
enum class Decision {
    ALLOW,
    DENY_VISIBLE,
    DENY_HIDDEN,
    UNSET;

    /** Vrai pour un refus, visible ou masqué. */
    val isRefusal: Boolean get() = this == DENY_VISIBLE || this == DENY_HIDDEN
}
