// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.HookFixtures
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

/** Qui lance `/aegis` (cahier des charges, 5.2), sur des sources de compilation, sans serveur. */
class AdministratorsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    @Test
    fun `une source jugée passe, canUse ayant déjà décidé`() {
        val envelope = Envelope(UUID.randomUUID(), LevelBasedPermissionSet.forLevel(PermissionLevel.ALL))
        assertTrue(Administrators.mayAdminister(Commands.createCompilationContext(envelope)))
    }

    @Test
    fun `parmi les sources non jugées, seule une source OWNER sans entité et non silencieuse passe`() {
        assertTrue(Administrators.mayAdminister(Commands.createCompilationContext(LevelBasedPermissionSet.OWNER)))
        assertFalse(Administrators.mayAdminister(Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)), "un bloc de commande porte GAMEMASTER")
        assertFalse(Administrators.mayAdminister(Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS)), "la source sans droits n'est pas un niveau")
        assertFalse(Administrators.mayAdminister(Commands.createCompilationContext(LevelBasedPermissionSet.OWNER).withSuppressedOutput()), "une fonction est silencieuse")
    }
}
