// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Ce qui fait échouer un `/reload` à la demande d'un test (cahier des charges, 8.5) : un écouteur de rechargement du mod de test, qui lève dès qu'un datapack
 * porte `data/aegisperms-gametest/poison.json`. Un fichier de données fautif n'y suffit pas sur les trois versions servies : en 26.3 les tables de butin sont
 * un registre strict, et un fichier illisible fait échouer le rechargement ; en 26.1.2 et en 26.2 un `SimpleJsonResourceReloadListener` le saute en se
 * plaignant, et le rechargement réussit. L'écouteur passe par l'API `v0` du chargeur de ressources, dépréciée dans la Fabric API 0.163.0 mais la même dans
 * les trois Fabric API servies, là où la `v1` change de majeure entre 26.2 et 26.3.
 */
class ReloadPoison : ModInitializer {

    companion object {
        /** Le fichier qu'un datapack porte pour empoisonner le rechargement. */
        val POISON: Identifier = Identifier.fromNamespaceAndPath(TEST_MOD_ID, "poison.json")
    }

    @Suppress("DEPRECATION")
    override fun onInitialize() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId(): Identifier = Identifier.fromNamespaceAndPath(TEST_MOD_ID, "reload_poison")

            override fun onResourceManagerReload(manager: ResourceManager) {
                if (manager.getResource(POISON).isPresent) throw IllegalStateException("A datapack carries $POISON: this reload fails on purpose")
            }
        })
    }
}
