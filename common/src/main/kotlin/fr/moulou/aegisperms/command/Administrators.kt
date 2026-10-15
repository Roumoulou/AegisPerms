// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.hook.Envelope
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel

/**
 * Qui lance `/aegis` (cahier des charges, 5.2). Une source jugée y est déjà décidée par `canUse`, l'étape 0 et les règles sur `aegisperms:command.aegis`.
 * Parmi les sources non jugées, la console et le RCON seules : `OWNER`, sans entité, et non silencieuse. Un bloc de commande ou un panneau portent
 * `GAMEMASTER` ; une fonction est silencieuse ; un `execute as` lancé par la console porte une entité : tous refusés, quel que soit `admin-level`.
 */
object Administrators {

    fun mayAdminister(source: CommandSourceStack): Boolean {
        val authority = source.permissions()
        if (authority is Envelope) return true
        val level = (authority as? LevelBasedPermissionSet)?.level() ?: return false
        return level == PermissionLevel.OWNERS && source.entity == null && !source.isSilent
    }
}
