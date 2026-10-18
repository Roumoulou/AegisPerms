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

    /** Prouve que `/tp`, alias de vanilla, n'a pas de forme préfixée, se masque avec `/teleport` sous la forme exigée, et suit sa clé quand la table des alias le rend. */
    @GameTest
    fun tpFollowsTeleport(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val source = player.source
        helper.assertTrue(server.commands.dispatcher.root.getChild("mc:tp") == null, "un alias de vanilla n'a pas de forme préfixée")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.teleport" to Rule.ALLOW)))
        helper.assertTrue(Trees.node(server, "mc:teleport").canUse(source), "/mc:teleport, ouverte, se voit")
        helper.assertFalse(Trees.node(server, "tp").canUse(source), "/tp reste masqué sous la forme exigée, avec la forme nue de sa cible")
        val settings = Settings.DELIVERED.copy(aliases = mapOf("tp" to "minecraft:teleport"))
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.teleport" to Rule.ALLOW)), settings)
        val dispatcher = Trees.buildCommands(server).dispatcher
        helper.assertTrue(Trees.runs(dispatcher, "tp ${player.nameAndId().name()}", source), "la table des alias rend /tp, qui suit la clé de /teleport, ouverte")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.teleport" to Rule.DENY)), settings)
        helper.assertFalse(dispatcher.root.getChild("tp").canUse(source), "un refus sur /teleport ferme /tp")
        helper.succeed()
    }

    /**
     * Prouve que la commande d'une lambda a le mod de test pour mod propriétaire, que sa redirection, alias, se juge sur la clé de sa cible sans recevoir de
     * forme, se masque avec elle sous la forme exigée, et la suit quand la table des alias la rend.
     */
    @GameTest
    fun lambdaCommandBelongsToTheTestMod(helper: GameTestHelper) {
        val server = helper.server
        val index = Trees.indexOf(server.commands) ?: throw helper.assertionException("le dispatcher en service n'a pas d'index")
        val owner = index.ownerOf("aegistest_target")
        helper.assertValueEqual(owner?.modId ?: "aucun", TEST_MOD_ID, "le mod propriétaire d'une commande enregistrée par une lambda")
        helper.assertValueEqual<Any>(owner?.source ?: "aucune", OwnerSource.DETECTED, "trouvé par sa classe, sans rattachement")
        val source = helper.judgedPlayer().source
        val target = Trees.node(server, "$TEST_MOD_ID:aegistest_target")
        val jump = Trees.node(server, "aegistest_jump")
        val mark = Trees.markOf(jump) as? NodeEntry ?: throw helper.assertionException("la redirection n'a pas de fiche")
        helper.assertValueEqual(mark.key.text, "$TEST_MOD_ID:command.aegistest_target", "la redirection se juge sur la clé de sa cible")
        helper.assertValueEqual(mark.ownKey?.text ?: "aucune", "$TEST_MOD_ID:command.aegistest_jump", "et garde la sienne pour le catalogue")
        helper.assertTrue(server.commands.dispatcher.root.getChild("$TEST_MOD_ID:aegistest_jump") == null, "un alias n'a pas de forme préfixée")
        val allowed = permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.aegistest_target" to Rule.ALLOW))
        TestStates.publish(allowed)
        helper.assertTrue(target.canUse(source), "la commande du mod, ouverte, se voit")
        helper.assertFalse(jump.canUse(source), "sa redirection reste masquée sous la forme exigée, avec la forme nue de sa cible")
        val settings = Settings.DELIVERED.copy(aliases = mapOf("aegistest_jump" to "$TEST_MOD_ID:aegistest_target"))
        TestStates.publish(allowed, settings)
        val dispatcher = Trees.buildCommands(server).dispatcher
        helper.assertTrue(dispatcher.root.getChild("aegistest_jump").canUse(source), "rendue par la table des alias, la redirection suit la clé de sa cible, ouverte")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.aegistest_target" to Rule.DENY)), settings)
        helper.assertFalse(dispatcher.root.getChild("$TEST_MOD_ID:aegistest_target").canUse(source), "la commande du mod, refusée, disparaît")
        helper.assertFalse(dispatcher.root.getChild("aegistest_jump").canUse(source), "et sa redirection avec elle")
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
