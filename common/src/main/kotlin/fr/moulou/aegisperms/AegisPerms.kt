// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Lifecycle
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Keys
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionLevel

/**
 * Le point d'entrée du mod, sur un serveur dédié seulement. Il lit la configuration avant la création du serveur, enregistre la racine `/aegis` et branche
 * le cycle de vie ; les fournisseurs d'API viennent ensuite.
 */
object AegisPerms : DedicatedServerModInitializer {
    const val MOD_ID = "aegisperms"

    override fun onInitializeServer() {
        FileThread.start(FabricLoader.getInstance().configDir.resolve(MOD_ID))
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> dispatcher.register(administrationRoot()) }
        Lifecycle.register()
        ModLog.info(ModLog.LOADED)
    }

    /**
     * La racine `/aegis` seule (D17), un littéral sans sous-commande qui ne fait rien : sans elle, ni l'étape 0 ni « `command` ne couvre pas `/aegis` » ne se
     * prouvent sur un serveur. Son exigence vanilla est l'`admin-level` des réglages publiés, et sa commande une lambda du mod, pour que son mod propriétaire
     * soit `aegisperms`. La grammaire de 5.3 vient à la session 5.
     */
    private fun administrationRoot(): LiteralArgumentBuilder<CommandSourceStack> {
        val level = PermissionLevel.byId(Publisher.settings().adminLevel.level)
        return Commands.literal(Keys.ADMINISTRATION_ROOT).requires(Commands.hasPermission<CommandSourceStack>(PermissionCheck.Require(Permission.HasCommandLevel(level)))).executes { 0 }
    }
}
