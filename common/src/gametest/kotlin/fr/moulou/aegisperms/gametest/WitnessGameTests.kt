// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.AegisPerms
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.gametest.framework.GameTestHelper

/** Le témoin de l'étage 2 : le serveur de test démarre, charge le mod à côté du mod de test, et joue un test. */
class WitnessGameTests {

    /** Prouve que le serveur de test a chargé le mod : le chargeur le connaît par son identifiant. */
    @GameTest
    fun modIsLoaded(helper: GameTestHelper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(AegisPerms.MOD_ID), "le serveur de test a chargé ${AegisPerms.MOD_ID}")
        helper.succeed()
    }
}
