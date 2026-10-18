// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.hook.Hooks
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.text.TextKeys.Why
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.Identifier

/**
 * `/advancement` nœud par nœud (cahier des charges, 2.6 à 2.8 et 8.5) : ce qu'un `allow` sur la racine ouvre, ce qu'un `deny` sur `revoke` ferme, un refus
 * visible au fond de l'arbre, un `allow` sans effet sous un refus, et l'exécution vraie, qui donne le progrès. Les joueurs désignés s'écrivent `@s`, l'atome des
 * sélecteurs ouvert : tous les joueurs simulés portent le même pseudo, et un UUID ne désigne pas un joueur pour `players()`.
 */
class AdvancementGameTests : AegisGameTests() {

    companion object {
        private const val ADVANCEMENT = "minecraft:command.advancement"

        /** L'atome des sélecteurs (2.8) : les joueurs désignés s'écrivent `@s`, parce que tous les joueurs simulés portent le même pseudo et qu'un UUID ne désigne pas un joueur. */
        private const val SELECTORS = "minecraft:commands/entity_selectors"

        /** Le progrès « L'âge de pierre », présent dans les trois versions. */
        private const val STONE_AGE = "minecraft:story/mine_stone"
    }

    /** Prouve qu'un `allow` sur `advancement` et un `deny` sur `advancement.revoke` ouvrent `grant` et ferment `revoke`, à l'arbre, à l'analyse et pour `why`. */
    @GameTest
    fun grantOpenRevokeClosed(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val source = player.source
        TestStates.publish(permissionsWith(deliveredDefaultPlus(SELECTORS to Rule.ALLOW, ADVANCEMENT to Rule.ALLOW, "$ADVANCEMENT.revoke" to Rule.DENY)))
        helper.assertTrue(Trees.node(server, "mc:advancement").canUse(source), "/mc:advancement, ouverte, se voit")
        helper.assertTrue(Trees.node(server, "mc:advancement", "grant").canUse(source), "grant hérite de l'allow de la racine")
        helper.assertFalse(Trees.node(server, "mc:advancement", "revoke").canUse(source), "revoke, refusé, ne se voit pas")
        helper.assertFalse(Trees.node(server, "mc:advancement", "revoke", "targets", "everything").canUse(source), "ni rien sous lui")
        val dispatcher = server.commands.dispatcher
        helper.assertTrue(Trees.runs(dispatcher, "mc:advancement grant @s everything", source), "grant s'analyse et passe la porte")
        helper.assertTrue(Trees.runs(dispatcher, "mc:advancement grant @s only $STONE_AGE", source), "avec un seul progrès aussi")
        helper.assertFalse(Trees.runs(dispatcher, "mc:advancement revoke @s everything", source), "revoke ne s'analyse pas pour ce joueur")
        val why = Recorder()
        Console.perform(server, Console.source(server, why), "aegis why ${player.uuid} mc:advancement revoke @s everything")
        val texts = Console.texts(why)
        helper.assertValueEqual(texts.lastOrNull().orEmpty(), Console.text(Why.VERDICT_REFUSED_HIDDEN, player.readableForm()), "why conclut au refus masqué, pas $texts")
        helper.succeed()
    }

    /** Prouve un refus visible au fond de l'arbre : `grant <targets> everything` reste vu, s'analyse, et la porte le refuse avec son message, là où `only` passe. */
    @GameTest
    fun visibleDenyOnEverythingLeavesOnlyOpen(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val source = player.source
        TestStates.publish(permissionsWith(deliveredDefaultPlus(SELECTORS to Rule.ALLOW, ADVANCEMENT to Rule.ALLOW, "$ADVANCEMENT.grant.targets.everything" to Rule(RuleValue.DENY, visible = true))))
        helper.assertTrue(Trees.node(server, "mc:advancement", "grant", "targets", "everything").canUse(source), "un refus visible garde le nœud vu")
        helper.assertTrue(Trees.parseException(server, "mc:advancement grant @s everything", source) == null, "la ligne s'analyse en entier")
        helper.assertFalse(Trees.runs(server.commands.dispatcher, "mc:advancement grant @s everything", source), "et la porte la refuse")
        val recorder = Recorder()
        server.commands.performPrefixedCommand(source.withSource(recorder), "mc:advancement grant @s everything")
        helper.assertValueEqual(recorder.messages.size, 1, "la source reçoit un seul message")
        helper.assertTrue(Hooks.REFUSAL_KEY in recorder.messages.single().translationKeys, "celui du refus visible, pas ${recorder.messages.single().translationKeys}")
        helper.assertTrue(Trees.runs(server.commands.dispatcher, "mc:advancement grant @s only $STONE_AGE", source), "only, que rien ne ferme, passe")
        helper.assertTrue(Trees.runs(server.commands.dispatcher, "mc:advancement revoke @s everything", source), "et revoke everything aussi : le refus ne vise que grant")
        helper.succeed()
    }

    /** Prouve qu'un `allow` sous un refus n'ouvre rien (2.7) : `revoke` à `deny` ferme `revoke <targets> only` malgré son `allow`, et `why` le dit sans effet. */
    @GameTest
    fun allowUnderDeniedRevokeIsIneffective(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val source = player.source
        TestStates.publish(permissionsWith(deliveredDefaultPlus(SELECTORS to Rule.ALLOW, ADVANCEMENT to Rule.ALLOW, "$ADVANCEMENT.revoke" to Rule.DENY, "$ADVANCEMENT.revoke.targets.only" to Rule.ALLOW)))
        helper.assertFalse(Trees.node(server, "mc:advancement", "revoke", "targets", "only").canUse(source), "l'allow sous le refus n'ouvre pas only")
        helper.assertFalse(Trees.runs(server.commands.dispatcher, "mc:advancement revoke @s only $STONE_AGE", source), "la ligne ne passe pas")
        val why = Recorder()
        Console.perform(server, Console.source(server, why), "aegis why ${player.uuid} mc:advancement revoke @s only $STONE_AGE")
        val texts = Console.texts(why)
        helper.assertTrue(texts.any { Console.text(Why.STATUS_INEFFECTIVE) in it }, "why dit l'allow sans effet, pas $texts")
        helper.assertValueEqual(texts.lastOrNull().orEmpty(), Console.text(Why.VERDICT_REFUSED_HIDDEN, player.readableForm()), "et conclut au refus masqué")
        helper.succeed()
    }

    /** Prouve l'exécution vraie : le joueur, à qui `grant` est ouvert et `revoke` fermé, se donne un progrès et ne peut pas se le retirer ; la console le retire. */
    @GameTest
    fun grantExecutesAndRevokeDoesNot(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus(SELECTORS to Rule.ALLOW, ADVANCEMENT to Rule.ALLOW, "$ADVANCEMENT.revoke" to Rule.DENY)))
        val stoneAge = server.advancements.get(Identifier.parse(STONE_AGE)) ?: throw helper.assertionException("le progrès $STONE_AGE manque au serveur de test")
        helper.assertFalse(player.advancements.getOrStartProgress(stoneAge).isDone, "le joueur simulé n'a pas le progrès au départ")
        val recorder = Recorder()
        server.commands.performPrefixedCommand(player.source.withSource(recorder), "mc:advancement grant @s only $STONE_AGE")
        helper.assertTrue(player.advancements.getOrStartProgress(stoneAge).isDone, "grant, ouvert, donne le progrès ; messages : ${recorder.messages.map { it.string }}")
        server.commands.performPrefixedCommand(player.source.withSource(Recorder()), "mc:advancement revoke @s only $STONE_AGE")
        helper.assertTrue(player.advancements.getOrStartProgress(stoneAge).isDone, "revoke, fermé, ne retire rien")
        Console.perform(server, Console.source(server, Recorder()), "execute as ${player.uuid} run advancement revoke @s only $STONE_AGE")
        helper.assertFalse(player.advancements.getOrStartProgress(stoneAge).isDone, "la console, jamais jugée, retire le progrès")
        helper.succeed()
    }
}
