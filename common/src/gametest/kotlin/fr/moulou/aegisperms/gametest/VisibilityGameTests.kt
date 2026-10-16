// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.moulou.aegisperms.hook.Hooks
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.tree.NodeEntry
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.MinecraftServer

/** Voir et utiliser (cahier des charges, 2.7, 3.8 et 8.5) : ce qu'une source jugée voit, analyse, complète et exécute, et ce qu'un nœud tardif reçoit. */
class VisibilityGameTests : AegisGameTests() {

    /** Prouve qu'une commande ouverte à `default` se voit sans être op, sous sa forme préfixée seule, puis disparaît sans règle. */
    @GameTest
    fun openCommandIsSeenThenGoesWithoutRule(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule.ALLOW)))
        helper.assertTrue(Trees.node(server, "mc:seed").canUse(source), "/mc:seed, ouverte à default, se voit sans être op")
        helper.assertFalse(Trees.node(server, "seed").canUse(source), "sa forme nue reste cachée sous la forme exigée")
        TestStates.reset()
        helper.assertFalse(Trees.node(server, "mc:seed").canUse(source), "sans règle, command à deny la ferme")
        helper.succeed()
    }

    /** Prouve qu'un refus masqué est une commande inconnue : l'analyse échoue comme pour une commande qui n'existe pas, et rien n'est reconnu. */
    @GameTest
    fun hiddenRefusalIsAnUnknownCommand(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        val unknown = CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
        val refused = Trees.parseException(server, "mc:seed", source)
        helper.assertTrue(refused != null && refused.type === unknown, "une commande refusée et masquée est une commande inconnue à l'analyse : $refused")
        val nonexistent = Trees.parseException(server, "aegistest_nothing", source)
        helper.assertTrue(nonexistent != null && nonexistent.type === unknown, "une commande inexistante donne la même exception : $nonexistent")
        helper.assertTrue(server.commands.dispatcher.parse("mc:seed", source).context.nodes.isEmpty(), "rien n'est reconnu")
        helper.succeed()
    }

    /** Prouve qu'un refus visible s'analyse, que la porte l'arrête avec la clé du message de refus, et que rien ne s'exécute. */
    @GameTest
    fun visibleRefusalStopsAtTheGate(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule(RuleValue.DENY, visible = true))))
        helper.assertTrue(Trees.node(server, "mc:seed").canUse(source), "un refus visible garde le nœud vu")
        helper.assertTrue(Trees.parseException(server, "mc:seed", source) == null, "la ligne s'analyse en entier")
        val recorder = Recorder()
        server.commands.performPrefixedCommand(source.withSource(recorder), "mc:seed")
        helper.assertValueEqual(recorder.messages.size, 1, "la source reçoit un seul message, celui du refus")
        val keys = recorder.messages.single().translationKeys
        helper.assertTrue(Hooks.REFUSAL_KEY in keys, "le message porte la clé du refus visible, pas $keys")
        helper.succeed()
    }

    /** Prouve que les suggestions d'un nœud masqué n'apparaissent pas, à la racine comme sous `execute as ... run`. */
    @GameTest
    fun hiddenNodeHasNoSuggestions(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        val open = deliveredDefaultPlus("minecraft:command.execute" to Rule.ALLOW, "minecraft:commands/entity_selectors" to Rule.ALLOW)
        TestStates.publish(permissionsWith(open + ("minecraft:command.seed" to Rule.ALLOW)))
        helper.assertTrue("mc:seed" in suggestions(server, "mc:se", source), "une commande ouverte se suggère")
        helper.assertTrue("mc:seed" in suggestions(server, "mc:execute as @s run mc:se", source), "et sous execute as ... run")
        TestStates.publish(permissionsWith(open))
        helper.assertFalse("mc:seed" in suggestions(server, "mc:se", source), "une commande masquée ne se suggère pas")
        helper.assertFalse("mc:seed" in suggestions(server, "mc:execute as @s run mc:se", source), "ni sous execute as ... run")
        helper.succeed()
    }

    /** Prouve que la porte tient aussi `CommandDispatcher#execute(ParseResults)`, par où un mod exécuterait une analyse : sous refus visible, 0 et le message du refus. */
    @GameTest
    fun visibleRefusalStopsTheExecutionOfAParse(helper: GameTestHelper) {
        val server = helper.server
        val recorder = Recorder()
        val source = helper.judgedPlayer().source.withSource(recorder)
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule(RuleValue.DENY, visible = true))))
        val dispatcher = server.commands.dispatcher
        val parse = dispatcher.parse("mc:seed", source)
        helper.assertTrue(parse.exceptions.isEmpty() && !parse.reader.canRead(), "la ligne s'analyse en entier")
        helper.assertValueEqual(dispatcher.execute(parse), 0, "l'exécution de l'analyse rend 0")
        helper.assertValueEqual(recorder.messages.size, 1, "la source reçoit un seul message")
        val keys = recorder.messages.single().translationKeys
        helper.assertTrue(Hooks.REFUSAL_KEY in keys, "celui du refus visible, pas $keys")
        helper.succeed()
    }

    /** Prouve que la forme exigée vaut aussi après `run` : `mc:execute as @s run seed` ne passe pas, `... run mc:seed` passe. */
    @GameTest
    fun bareFormAfterRunIsRefused(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.execute" to Rule.ALLOW, "minecraft:commands/entity_selectors" to Rule.ALLOW, "minecraft:command.seed" to Rule.ALLOW)))
        helper.assertTrue(Trees.runs(server.commands.dispatcher, "mc:execute as @s run mc:seed", source), "la forme préfixée passe après run")
        helper.assertFalse(Trees.runs(server.commands.dispatcher, "mc:execute as @s run seed", source), "la forme nue ne passe pas après run")
        helper.succeed()
    }

    /** Prouve qu'une commande racine enregistrée après l'index reçoit sa fiche et sa forme préfixée, et que la forme exigée vaut pour elle (3.8). */
    @GameTest
    fun lateRootReceivesItsForm(helper: GameTestHelper) {
        val server = helper.server
        val dispatcher = server.commands.dispatcher
        val late = dispatcher.root.getChild("aegistest_late") ?: dispatcher.register(Commands.literal("aegistest_late").executes { 1 })
        val key = "$TEST_MOD_ID:command.aegistest_late"
        helper.assertValueEqual((Trees.markOf(late) as? NodeEntry)?.key?.text ?: "aucune", key, "la racine tardive porte une fiche sur sa clé")
        val form = dispatcher.root.getChild("$TEST_MOD_ID:aegistest_late") ?: throw helper.assertionException("la racine tardive n'a pas reçu sa forme préfixée")
        helper.assertValueEqual((Trees.markOf(form) as? NodeEntry)?.key?.text ?: "aucune", key, "la forme porte la clé de sa cible")
        val source = helper.judgedPlayer().source
        TestStates.publish(permissionsWith(deliveredDefaultPlus(key to Rule.ALLOW)))
        helper.assertTrue(form.canUse(source), "ouverte, la forme préfixée se voit")
        helper.assertFalse(late.canUse(source), "sa forme nue reste cachée sous la forme exigée")
        helper.succeed()
    }

    /** Prouve qu'un enfant ajouté à une commande après l'index reçoit sa fiche, sur la clé de son parent prolongée de son nom. */
    @GameTest
    fun lateChildReceivesItsEntry(helper: GameTestHelper) {
        val target = Trees.node(helper.server, "aegistest_target")
        val late = target.getChild("late") ?: Commands.literal("late").executes { 1 }.build().also { target.addChild(it) }
        val mark = Trees.markOf(late)
        helper.assertTrue(mark is NodeEntry, "l'enfant tardif porte une fiche, pas ${mark?.javaClass?.simpleName}")
        helper.assertValueEqual((mark as NodeEntry).key.text, "$TEST_MOD_ID:command.aegistest_target.late", "sa clé prolonge celle de son parent")
        helper.succeed()
    }

    /** Prouve que deux joueurs, l'un avec une règle propre, voient deux arbres, nœud par nœud et dans le paquet reçu. */
    @GameTest
    fun twoPlayersSeeTwoTrees(helper: GameTestHelper) {
        val server = helper.server
        val favored = helper.judgedPlayer()
        val other = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), players = mapOf(favored.readableForm() to Player(rules = mapOf("minecraft:command.seed" to Rule.ALLOW)))))
        helper.assertTrue(Trees.node(server, "mc:seed").canUse(favored.source), "le joueur à la règle propre voit /mc:seed")
        helper.assertFalse(Trees.node(server, "mc:seed").canUse(other.source), "l'autre ne la voit pas")
        server.commands.sendCommands(favored)
        server.commands.sendCommands(other)
        helper.assertTrue(Packets.lastTree(server, favored).getChild("mc:seed") != null, "l'arbre reçu par le premier a /mc:seed")
        helper.assertTrue(Packets.lastTree(server, other).getChild("mc:seed") == null, "celui du second ne l'a pas")
        helper.succeed()
    }

    private fun suggestions(server: MinecraftServer, line: String, source: CommandSourceStack): List<String> {
        val dispatcher = server.commands.dispatcher
        return dispatcher.getCompletionSuggestions(dispatcher.parse(line, source)).get().list.map { it.text }
    }
}
