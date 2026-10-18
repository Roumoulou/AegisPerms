// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.tree.RequirementReader
import fr.moulou.aegisperms.tree.Requirements
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionProviderCheck
import java.util.function.Predicate

/**
 * La lecture de l'exigence vanilla d'un nœud (cahier des charges, 2.8). Une commande de vanilla déclare son niveau d'op par `Commands.hasPermission`, un
 * `PermissionProviderCheck` qui se laisse lire ; un nœud sans exigence porte le prédicat par défaut de Brigadier. Tout autre prédicat est celui d'un mod, que
 * le mod ne sait pas lire : il l'évalue.
 */
object VanillaRequirements : RequirementReader {

    override fun read(requirement: Predicate<*>): CommandRequirement {
        if (Requirements.isDefault(requirement)) return CommandRequirement.NONE
        if (requirement !is PermissionProviderCheck<*>) return CommandRequirement.MOD_PREDICATE
        return when (val check = requirement.test()) {
            is PermissionCheck.AlwaysPass -> CommandRequirement.NONE
            is PermissionCheck.Require -> (check.permission() as? Permission.HasCommandLevel)?.let { ofLevel(it.level().id()) } ?: CommandRequirement.MOD_PREDICATE
            else -> CommandRequirement.MOD_PREDICATE
        }
    }

    /** L'exigence d'un niveau d'op de vanilla : le niveau d'op 0, celui de tous, n'en est pas une. */
    private fun ofLevel(level: Int): CommandRequirement = when (level) {
        0 -> CommandRequirement.NONE
        else -> OpLevel.entries.firstOrNull { it.level == level }?.let(CommandRequirement::of) ?: CommandRequirement.MOD_PREDICATE
    }
}
