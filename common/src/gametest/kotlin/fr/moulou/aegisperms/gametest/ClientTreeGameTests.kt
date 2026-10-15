// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.hook.TreeResend
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/** L'arbre du client (cahier des charges, 3.7 et 8.5) : ce que le paquet envoyé à un joueur porte, et ce que la forme exigée en fait. */
class ClientTreeGameTests : AegisGameTests() {

    /** Prouve que le paquet reçu après des règles porte la commande ouverte, pas la fermée, et aucune forme nue sous la forme exigée. */
    @GameTest
    fun packetHoldsOpenCommandsOnly(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule.ALLOW)))
        server.commands.sendCommands(player)
        val tree = Packets.lastTree(server, player)
        helper.assertTrue(tree.getChild("mc:seed") != null, "/mc:seed, ouverte, est dans l'arbre reçu")
        helper.assertTrue(tree.getChild("mc:help") != null, "/mc:help, que default ouvre, aussi")
        helper.assertTrue(tree.getChild("mc:kick") == null, "/mc:kick, que command à deny ferme, n'y est pas")
        helper.assertTrue(tree.getChild("seed") == null && tree.getChild("help") == null, "aucune forme nue sous la forme exigée")
        helper.assertTrue(tree.getChild("aegis") == null, "/aegis reste aux ops d'admin-level")
        helper.succeed()
    }

    /**
     * Prouve, sur un `Commands` construit à part, ce que la forme exigée fait de l'arbre : la forme nue absente, la forme préfixée et l'alias avec leurs
     * enfants, `mc:execute as` qui mène à `mc:execute`, et un alias profond qui garde l'exigence de son chemin.
     */
    @GameTest
    fun requirePrefixShapesTheTree(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        TestStates.publish(
            permissionsWith(deliveredDefaultPlus("minecraft:command.gamemode" to Rule.ALLOW, "minecraft:command.execute" to Rule.ALLOW, "minecraft:command.seed" to Rule.ALLOW)),
            Settings.DELIVERED.copy(aliases = mapOf("gm" to "minecraft:gamemode", "wla" to "minecraft:whitelist add")),
        )
        val dispatcher = Trees.buildCommands(server).dispatcher
        val source = player.source
        helper.assertFalse(dispatcher.root.getChild("gamemode").canUse(source), "sous la forme exigée, la forme nue ne se voit pas")
        helper.assertTrue(Trees.runs(dispatcher, "mc:gamemode creative", source), "la forme préfixée s'analyse avec ses arguments et passe la porte")
        helper.assertTrue(Trees.runs(dispatcher, "mc:execute as ${player.nameAndId().name()} run mc:seed", source), "execute préfixé mène à ses sous-commandes et à sa cible")
        helper.assertTrue(Trees.runs(dispatcher, "gm creative", source), "un alias porte les enfants de sa cible")
        helper.assertFalse(dispatcher.root.getChild("wla").canUse(source), "un alias vers whitelist add garde l'exigence de /whitelist, que rien n'ouvre")
        val tree = Trees.usableTree(dispatcher, source)
        helper.assertTrue(tree.getChild("gamemode") == null, "l'arbre du joueur n'a pas la forme nue")
        helper.assertTrue(tree.getChild("mc:gamemode")?.children?.isNotEmpty() == true, "il a les arguments de la forme préfixée")
        helper.assertTrue(tree.getChild("gm")?.children?.isNotEmpty() == true, "et ceux de l'alias")
        val execute = tree.getChild("mc:execute")
        val redirect = execute?.getChild("as")?.children?.firstOrNull()?.redirect
        helper.assertTrue(redirect != null && redirect === execute, "dans l'arbre du joueur, execute as redirige vers la forme préfixée d'execute")
        helper.succeed()
    }

    /** Prouve que le drapeau `restricted` du paquet ne suit pas AegisPerms : un nœud ouvert par `allow` le garde quand vanilla l'exige, et le client le dira. */
    @GameTest
    fun restrictedFlagDoesNotFollowAegis(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule.ALLOW)))
        server.commands.sendCommands(player)
        val tree = Packets.lastTree(server, player)
        val seed = tree.getChild("mc:seed") ?: throw helper.assertionException("/mc:seed manque à l'arbre reçu")
        helper.assertTrue(seed.restrictedFlag, "/mc:seed, ouverte par allow, garde le drapeau restricted de son exigence vanilla")
        helper.assertTrue(seed.executableFlag, "et reste exécutable")
        val help = tree.getChild("mc:help") ?: throw helper.assertionException("/mc:help manque à l'arbre reçu")
        helper.assertFalse(help.restrictedFlag, "/mc:help, sans exigence vanilla, n'est pas restreinte")
        helper.succeed()
    }

    /** Prouve que les arbres repartent à la fin du tick après une demande de renvoi : un paquet de plus arrive au joueur. */
    @GameTest(environment = "aegisperms-gametest:serial", maxTicks = 100)
    fun treesAreResentAtEndOfTick(helper: GameTestHelper) {
        val player = helper.judgedPlayer()
        val before = Packets.commandsPackets(player).size
        TreeResend.requestAll()
        helper.succeedWhen { helper.assertTrue(Packets.commandsPackets(player).size > before, "un arbre de plus est envoyé après la demande") }
    }
}
