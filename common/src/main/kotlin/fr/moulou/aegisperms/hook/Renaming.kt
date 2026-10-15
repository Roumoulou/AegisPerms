// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.ReadableForm
import java.util.UUID

/**
 * Le renommage d'un joueur du fichier des droits (cahier des charges, 4.4) : quand il se connecte sous un nouveau pseudo, sa forme lisible se réécrit, à la
 * même place et avec le même contenu. Sans Minecraft, pour que l'étage 0 l'éprouve.
 */
object Renaming {

    /** Les droits où la forme lisible de [uuid] devient `<name>#<uuid>` ; les mêmes droits quand le fichier ne le connaît pas ou l'écrit déjà sous ce pseudo. */
    fun renamed(permissions: Permissions, uuid: UUID, name: String): Permissions {
        val current = permissions.players.keys.firstOrNull { ReadableForm.split(it)?.second == uuid.toString() } ?: return permissions
        val renamed = ReadableForm.of(name, uuid)
        if (current == renamed) return permissions
        return permissions.copy(players = permissions.players.entries.associate { (form, player) -> (if (form == current) renamed else form) to player })
    }
}
