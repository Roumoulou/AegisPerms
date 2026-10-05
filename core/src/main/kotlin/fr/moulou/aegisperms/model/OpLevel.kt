// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Un niveau d'op de vanilla (cahier des charges, 2.5), sous le nom que les fichiers lui donnent : il sert au réglage `admin-level` et au champ `include-ops`
 * d'un groupe. Un joueur non-op est de niveau 0, qui n'a pas de nom.
 */
enum class OpLevel(val level: Int) {
    MODERATORS(1),
    GAMEMASTERS(2),
    ADMINS(3),
    OWNERS(4);

    /** Vrai si un joueur dont le niveau d'op vaut [opLevel] atteint ce niveau. */
    fun isReachedBy(opLevel: Int): Boolean = opLevel >= level
}
