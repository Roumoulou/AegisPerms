// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Un niveau d'op de vanilla (cahier des charges, 2.5), sous le nom que les fichiers lui donnent : il sert au réglage `admin-level` et au champ `include-ops`
 * d'un groupe. Un joueur non-op est de niveau d'op 0, qui n'a pas de nom.
 */
@Serializable
enum class OpLevel(val level: Int) {
    @SerialName("moderators") MODERATORS(1),
    @SerialName("gamemasters") GAMEMASTERS(2),
    @SerialName("admins") ADMINS(3),
    @SerialName("owners") OWNERS(4);

    /** Vrai si un joueur dont le niveau d'op vaut [opLevel] atteint ce niveau d'op. */
    fun isReachedBy(opLevel: Int): Boolean = opLevel >= level
}
