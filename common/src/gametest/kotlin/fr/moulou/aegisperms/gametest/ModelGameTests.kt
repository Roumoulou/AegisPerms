// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Rule
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.permissions.PermissionLevel

/** Le modèle sur serveur (cahier des charges, 2.6, 2.8 et 8.5) : l'étape 0, l'atome des sélecteurs, les pouvoirs hors commandes. */
class ModelGameTests : AegisGameTests() {

    /** Prouve que `command` ne couvre pas `/aegis`, qu'`aegisperms:command.aegis` l'ouvre, et que l'étape 0 l'ouvre à un op d'`admin-level` seulement. */
    @GameTest
    fun commandDoesNotCoverAegis(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val aegis = Trees.node(server, Keys.ADMINISTRATION_ROOT)
        TestStates.publish(permissionsWith(mapOf("command" to Rule.ALLOW)))
        helper.assertFalse(aegis.canUse(player.source), "command ne couvre pas /aegis")
        TestStates.publish(permissionsWith(mapOf("command" to Rule.DENY, Keys.ADMINISTRATION.text to Rule.ALLOW)))
        helper.assertTrue(aegis.canUse(player.source), "une règle sur aegisperms:command.aegis l'ouvre")
        TestStates.reset()
        Ops.opAs(server, player, PermissionLevel.OWNERS)
        helper.assertTrue(aegis.canUse(player.source), "l'étape 0 l'ouvre à un op d'admin-level")
        Ops.opAs(server, player, PermissionLevel.ADMINS)
        helper.assertFalse(aegis.canUse(player.source), "pas à un op de niveau 3, que default refuse")
        helper.succeed()
    }

    /** Prouve que `/kick @a` échoue à l'analyse sans l'atome des sélecteurs, par l'erreur du parseur, et passe avec lui : Bob de 2.11. */
    @GameTest
    fun selectorsNeedTheirAtom(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.kick" to Rule.ALLOW)))
        val refused = Trees.parseException(server, "mc:kick @a", source)
        helper.assertTrue(refused != null && refused.type === EntitySelectorParser.ERROR_SELECTORS_NOT_ALLOWED, "sans l'atome, le sélecteur est refusé à l'analyse : $refused")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.kick" to Rule.ALLOW, Keys.ENTITY_SELECTORS.text to Rule.ALLOW)))
        helper.assertTrue(Trees.parseException(server, "mc:kick @a", source) == null, "avec l'atome, la ligne s'analyse")
        helper.succeed()
    }

    /** Prouve qu'un op de niveau 2 privé de `/gamemode` garde `canUseGameMasterBlocks` : les pouvoirs hors commandes restent à vanilla. */
    @GameTest
    fun gamemasterKeepsBlocksWithoutGamemode(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        Ops.opAs(server, player, PermissionLevel.GAMEMASTERS)
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.gamemode" to Rule.DENY)))
        helper.assertFalse(Trees.node(server, "mc:gamemode").canUse(player.source), "l'op de niveau 2 perd /mc:gamemode")
        helper.assertTrue(player.isCreative, "le joueur simulé est en créatif")
        helper.assertTrue(player.canUseGameMasterBlocks(), "il garde les blocs de commande : un niveau de commande suit vanilla")
        helper.succeed()
    }
}
