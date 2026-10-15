// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker
import net.minecraft.gametest.framework.GameTestHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Ce que partagent les classes de tests de l'étage 2 (cahier des charges, 8.5) : avant chaque test, l'état en vigueur revient à celui du premier démarrage
 * et plus aucun joueur en ligne n'est op, pour que les tests d'un même lot ne se lisent pas les uns les autres. Les assertions remontent telles quelles, sans
 * l'enveloppe de la réflexion.
 */
abstract class AegisGameTests : CustomTestMethodInvoker {

    override fun invokeTestMethod(helper: GameTestHelper, method: Method) {
        TestStates.reset()
        Ops.deopAll(helper.server)
        try {
            method.invoke(this, helper)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException as? RuntimeException ?: failure
        }
    }
}
