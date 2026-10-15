// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.AegisPerms
import fr.moulou.aegisperms.files.ConfigurationFiles
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.tree.AliasOutcome
import fr.moulou.aegisperms.tree.NodeEntry
import fr.moulou.aegisperms.tree.OwnerSource
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.gametest.framework.GameTestHelper

/** Les clés et le mod propriétaire (cahier des charges, 2.4, 3.2 et 8.5) : les alias de vanilla, les commandes des mods, le catalogue. */
class KeysAndOwnersGameTests : AegisGameTests() {

    /** Prouve que `/tp`, alias de vanilla, suit la clé de `/teleport`. */
    @GameTest
    fun tpFollowsTeleport(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.teleport" to Rule.ALLOW)))
        helper.assertTrue(Trees.node(server, "mc:tp").canUse(source), "/mc:tp prend la clé de /teleport, ouverte")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.teleport" to Rule.DENY)))
        helper.assertFalse(Trees.node(server, "mc:tp").canUse(source), "un refus sur /teleport ferme /tp")
        helper.succeed()
    }

    /** Prouve que la commande d'une lambda a le mod de test pour mod propriétaire, et que sa redirection se juge sur la clé de sa cible. */
    @GameTest
    fun lambdaCommandBelongsToTheTestMod(helper: GameTestHelper) {
        val server = helper.server
        val index = Trees.indexOf(server.commands) ?: throw helper.assertionException("le dispatcher en service n'a pas d'index")
        val owner = index.ownerOf("aegistest_target")
        helper.assertValueEqual(owner?.modId ?: "aucun", TEST_MOD_ID, "le mod propriétaire d'une commande enregistrée par une lambda")
        helper.assertValueEqual<Any>(owner?.source ?: "aucune", OwnerSource.DETECTED, "trouvé par sa classe, sans rattachement")
        val source = helper.judgedPlayer().source
        val target = Trees.node(server, "$TEST_MOD_ID:aegistest_target")
        val jump = Trees.node(server, "$TEST_MOD_ID:aegistest_jump")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.aegistest_target" to Rule.ALLOW)))
        helper.assertTrue(target.canUse(source), "la commande du mod, ouverte, se voit")
        helper.assertTrue(jump.canUse(source), "sa redirection, jugée sur la clé de sa cible, aussi")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.aegistest_target" to Rule.DENY)))
        helper.assertFalse(target.canUse(source), "la commande du mod, refusée, disparaît")
        helper.assertFalse(jump.canUse(source), "et sa redirection avec elle")
        helper.succeed()
    }

    /** Prouve que `/teleport` a `minecraft` pour mod propriétaire et `/aegis` `aegisperms`, par la classe de leur commande, en développement (7.7). */
    @GameTest
    fun teleportBelongsToMinecraft(helper: GameTestHelper) {
        val index = Trees.indexOf(helper.server.commands) ?: throw helper.assertionException("le dispatcher en service n'a pas d'index")
        helper.assertValueEqual(index.ownerOf("teleport")?.modId ?: "aucun", Keys.MINECRAFT, "le mod propriétaire de /teleport")
        helper.assertValueEqual(index.ownerOf("aegis")?.modId ?: "aucun", Keys.AEGISPERMS, "celui de /aegis")
        helper.succeed()
    }

    /** Prouve que le catalogue écrit au démarrage porte les commandes du mod de test et celles de vanilla, avec leur mod propriétaire et leurs alias. */
    @GameTest
    fun catalogListsTheTestModCommands(helper: GameTestHelper) {
        val catalog = ConfigurationFiles(FabricLoader.getInstance().configDir.resolve(AegisPerms.MOD_ID)).use { it.readCatalog() }
            ?: throw helper.assertionException("catalog.json manque ou ne se lit pas")
        helper.assertValueEqual(catalog.commands["$TEST_MOD_ID:command.aegistest_target"]?.owner ?: "absent", TEST_MOD_ID, "la commande du mod de test est au catalogue, avec son mod")
        helper.assertValueEqual(catalog.commands["minecraft:command.teleport"]?.owner ?: "absent", Keys.MINECRAFT, "et /teleport avec le sien")
        helper.assertValueEqual(catalog.commands["minecraft:command.tp"]?.aliasOf ?: "absent", "minecraft:command.teleport", "l'alias de vanilla y nomme la clé de sa cible")
        helper.succeed()
    }

    /** Prouve qu'un alias dont le nom est déjà pris à la racine est refusé, que la commande en place garde son nom, et qu'un alias au nom libre s'ajoute. */
    @GameTest
    fun takenAliasNameIsRefused(helper: GameTestHelper) {
        val server = helper.server
        TestStates.publish(permissionsWith(deliveredDefaultPlus()), Settings.DELIVERED.copy(aliases = mapOf("seed" to "minecraft:teleport", "aegistest_fly" to "minecraft:teleport")))
        val commands = Trees.buildCommands(server)
        val index = Trees.indexOf(commands) ?: throw helper.assertionException("le Commands construit à part n'a pas d'index")
        helper.assertValueEqual<Any>(index.plan.aliasOutcomes["seed"] ?: "absent", AliasOutcome.NameTaken, "un alias au nom d'une commande racine est refusé")
        helper.assertValueEqual<Any>(index.plan.aliasOutcomes["aegistest_fly"] ?: "absent", AliasOutcome.Added, "un alias au nom libre est ajouté")
        val seed = Trees.markOf(commands.dispatcher.root.getChild("seed")) as? NodeEntry
        helper.assertValueEqual(seed?.key?.text ?: "sans fiche", "minecraft:command.seed", "/seed reste la commande de vanilla")
        helper.assertTrue(commands.dispatcher.root.getChild("aegistest_fly") != null, "l'alias ajouté est à la racine")
        helper.succeed()
    }

    /** Prouve que `/help` d'un joueur sous la forme exigée ne cite aucune forme nue. */
    @GameTest
    fun helpUnderRequirePrefixNamesNoBareForm(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        val recorder = Recorder()
        server.commands.performPrefixedCommand(source.withSource(recorder), "mc:help")
        val lines = recorder.messages.map { it.string }
        helper.assertTrue(lines.isNotEmpty(), "/mc:help répond")
        val bare = lines.filter { it.startsWith("/") && !it.substringBefore(' ').contains(':') }
        helper.assertTrue(bare.isEmpty(), "aucune forme nue dans l'aide : $bare")
        helper.assertTrue(lines.any { it.startsWith("/mc:") }, "les formes préfixées y sont : $lines")
        helper.succeed()
    }
}
