// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import com.mojang.brigadier.arguments.IntegerArgumentType
import fr.moulou.aegisperms.files.CommandRequirement
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.Bootstrap
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.Permissions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** La lecture de l'exigence vanilla d'un nœud (cahier des charges, 2.8 et 8.4), face aux classes du jeu. */
class VanillaRequirementsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `une exigence de niveau se lit par son niveau`() {
        assertEquals(CommandRequirement.MODERATORS, VanillaRequirements.read(Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_MODERATORS)))
        assertEquals(CommandRequirement.GAMEMASTERS, VanillaRequirements.read(Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_GAMEMASTERS)))
        assertEquals(CommandRequirement.ADMINS, VanillaRequirements.read(Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_ADMINS)))
        assertEquals(CommandRequirement.OWNERS, VanillaRequirements.read(Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_OWNERS)))
        assertEquals(CommandRequirement.GAMEMASTERS, VanillaRequirements.read(Commands.literal("gamemode").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).build().requirement))
    }

    @Test
    fun `le niveau de tous ne vaut aucune exigence`() {
        assertEquals(CommandRequirement.NONE, VanillaRequirements.read(Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_ALL)))
    }

    @Test
    fun `le prédicat par défaut de Brigadier ne vaut aucune exigence`() {
        assertEquals(CommandRequirement.NONE, VanillaRequirements.read(Commands.literal("help").build().requirement))
        assertEquals(CommandRequirement.NONE, VanillaRequirements.read(Commands.argument("page", IntegerArgumentType.integer()).build().requirement))
    }

    @Test
    fun `tout autre prédicat est un prédicat de mod`() {
        val playersOnly = Commands.literal("home").requires { it.isPlayer }.build().requirement
        val atom = Commands.hasPermission<CommandSourceStack>(PermissionCheck.Require(Permissions.COMMANDS_ENTITY_SELECTORS))

        assertEquals(CommandRequirement.MOD_PREDICATE, VanillaRequirements.read(playersOnly))
        assertEquals(CommandRequirement.MOD_PREDICATE, VanillaRequirements.read(atom))
    }
}
