// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import fr.moulou.aegisperms.api.FabricPermissionProvider
import fr.moulou.aegisperms.api.LuckoPermissionProvider
import fr.moulou.aegisperms.command.AegisCommand
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Lifecycle
import fr.moulou.aegisperms.hook.ModLog
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader

/**
 * Le point d'entrée du mod, sur un serveur dédié seulement. Il lit la configuration avant la création du serveur, enregistre `/aegis`, branche le cycle de
 * vie et s'inscrit comme fournisseur des deux API de permissions.
 */
object AegisPerms : DedicatedServerModInitializer {
    const val MOD_ID = "aegisperms"

    override fun onInitializeServer() {
        FileThread.start(FabricLoader.getInstance().configDir.resolve(MOD_ID))
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> dispatcher.register(AegisCommand.tree()) }
        Lifecycle.register()
        FabricPermissionProvider.register()
        LuckoPermissionProvider.register()
        ModLog.info(ModLog.LOADED)
    }

    /** La version du mod, telle que le loader la lit dans `fabric.mod.json` ; `?` hors du loader. */
    fun version(): String = FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("?")
}
