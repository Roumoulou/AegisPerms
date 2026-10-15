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
        reset(helper)
        try {
            method.invoke(this, helper)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException as? RuntimeException ?: failure
        }
    }

    /** Ce qui se remet avant chaque test : l'état publié en mémoire et les ops. Une classe qui passe par les fichiers y ajoute le disque. */
    protected open fun reset(helper: GameTestHelper) {
        TestStates.reset()
        Ops.deopAll(helper.server)
    }
}

/**
 * Les tests qui passent par les fichiers du serveur de test : avant chacun, le fichier des droits revient aux deux groupes livrés, écrit et publié par le fil
 * des fichiers, si bien que l'état en vigueur est celui du disque et que la publication suivante se compare à lui. Les tests d'un lot courent l'un après
 * l'autre dans le même tick : seul un test qui attend des ticks va dans l'environnement `serial`, et il n'y touche aux fichiers que par la surveillance.
 */
abstract class FileGameTests : AegisGameTests() {

    override fun reset(helper: GameTestHelper) {
        super.reset(helper)
        ConfigurationOnDisk.restoreDelivered()
    }
}
