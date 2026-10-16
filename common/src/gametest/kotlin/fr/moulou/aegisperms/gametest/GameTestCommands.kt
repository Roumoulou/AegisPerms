// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates
import net.minecraft.commands.Commands
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.PermissionLevel

/**
 * Les commandes d'un mod tiers, enregistrées par des lambdas (cahier des charges, 8.5) : `/aegistest_target` et `/aegistest_jump`, qui redirige vers elle,
 * pour le mod propriétaire trouvé par la classe hôte d'une lambda et la redirection jugée sur la clé de sa cible ; `/aegistest_fabric`, sous l'API de
 * permissions de Fabric, et `/aegistest_lucko`, sous l'API de lucko, deux consommateurs des fournisseurs du chapitre 6, chacun avec un repli au niveau 3 ;
 * `/aegistest_player`, réservée à une source qui porte un joueur, pour la sonde de `why` (5.5).
 */
class GameTestCommands : ModInitializer {

    companion object {
        /** La clé d'API que la commande sous l'API de Fabric demande. */
        val FABRIC_NODE: Identifier = Identifier.fromNamespaceAndPath(TEST_MOD_ID, "api/fabric")

        /** La chaîne à points que la commande sous l'API de lucko demande : la clé d'API `aegisperms-gametest:lucko`. */
        const val LUCKO_PERMISSION = "$TEST_MOD_ID.lucko"

        /** Le niveau d'op du repli des deux consommateurs quand le fournisseur ne répond pas. */
        val FALLBACK: PermissionLevel = PermissionLevel.ADMINS
    }

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val target = dispatcher.register(Commands.literal("aegistest_target").executes { 1 })
            dispatcher.register(Commands.literal("aegistest_jump").redirect(target))
            val fabric = PermissionPredicates.require<PermissionContextOwner>(FABRIC_NODE, FALLBACK)
            dispatcher.register(Commands.literal("aegistest_fabric").requires { source -> fabric.test(source as PermissionContextOwner) }.executes { 1 })
            dispatcher.register(Commands.literal("aegistest_lucko").requires(Permissions.require(LUCKO_PERMISSION, FALLBACK)).executes { 1 })
            // Un prédicat de mod qui demande un joueur : ce que `why` n'analyserait pas avec une source sans entité (5.5).
            dispatcher.register(Commands.literal("aegistest_player").requires { source -> source.entity is ServerPlayer }.executes { 1 })
        }
    }
}
