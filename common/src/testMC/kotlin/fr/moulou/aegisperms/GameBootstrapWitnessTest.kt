// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le témoin de l'étage 1 : il éprouve le harnais lui-même, pas le mod.
 *
 * `fabric-loader-junit` installe le chargeur Fabric par un écouteur de session de la plateforme JUnit, ce qui rend les registres de Minecraft
 * accessibles sans serveur. Le build l'amorce côté serveur, où un mod `"environment": "server"` se charge. Le jour où une montée de version
 * casse cet amorçage, c'est ce fichier qui le dit. Aucun mixin ne s'applique à cet étage : ce qui passe par un mixin s'éprouve à l'étage 2.
 */
class GameBootstrapWitnessTest {

    @Test
    fun `le jeu amorcé donne accès au registre des blocs`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()

        assertEquals("minecraft:stone", BuiltInRegistries.BLOCK.getKey(Blocks.STONE).toString())
    }

    @Test
    fun `le chargeur amorcé côté serveur connaît le mod`() {
        assertTrue(FabricLoader.getInstance().isModLoaded(AegisPerms.MOD_ID))
    }
}
