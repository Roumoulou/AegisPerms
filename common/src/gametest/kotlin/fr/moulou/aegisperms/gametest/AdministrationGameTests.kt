// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.text.TextKeys.Command
import fr.moulou.aegisperms.text.TextKeys.Why
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.util.UUID

/** `/aegis` (cahier des charges, chapitre 5 et 8.5) : `why` et `check` depuis la console, une clé mal formée, `search`, `rename` et `clone`, la résolution d'un joueur. */
class AdministrationGameTests : FileGameTests() {

    /** Prouve que `why` et `check` depuis la console se lisent dans l'enregistreur, résolus dans sa langue : le titre, le verdict, la décision d'une clé. */
    @GameTest
    fun whyAndCheckFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val why = Recorder()
        Console.perform(server, Console.source(server, why), "aegis why ${player.uuid} mc:me coucou")
        val whyTexts = Console.texts(why)
        helper.assertTrue(whyTexts.size >= 3, "why répond ligne par ligne, pas $whyTexts")
        helper.assertValueEqual(whyTexts.first(), Console.text(Why.TITLE, player.readableForm(), "mc:me coucou"), "la première ligne dit le joueur et la ligne")
        helper.assertValueEqual(whyTexts.last(), Console.text(Why.VERDICT_PERMITTED), "la dernière le verdict : permise, default ouvre me")
        val check = Recorder()
        Console.perform(server, Console.source(server, check), "aegis check ${player.uuid} minecraft:command.kick")
        val checkTexts = Console.texts(check)
        helper.assertTrue(checkTexts.size >= 3, "check répond ligne par ligne, pas $checkTexts")
        helper.assertValueEqual(checkTexts[0], Console.text(Why.CHECK_TITLE, player.readableForm(), "minecraft:command.kick"), "le titre dit le joueur et la clé")
        helper.assertValueEqual(checkTexts[1], Console.text(Why.CHECK_DECISION, Console.text(Why.DECISION_DENY_HIDDEN)), "la décision : deny masqué, par command de default")
        helper.succeed()
    }

    /** Prouve qu'une clé mal formée est refusée en une ligne qui la cite, sans écriture ni génération. */
    @GameTest
    fun malformedKeyIsRefusedWithoutGeneration(helper: GameTestHelper) {
        val server = helper.server
        val before = Publisher.lastPublication
        val recorder = Recorder()
        helper.assertValueEqual(Console.perform(server, Console.source(server, recorder), "aegis group default allow Minecraft:command.me"), 0, "la commande est refusée")
        Console.settle(server)
        helper.assertTrue(Publisher.lastPublication === before, "rien n'est publié")
        helper.assertValueEqual(recorder.messages.size, 1, "en une ligne")
        helper.assertTrue("Minecraft:command.me" in recorder.messages.single().string, "qui cite la clé, pas ${recorder.messages.single().string}")
        helper.succeed()
    }

    /** Prouve `search`, `group rename` et `clone` : les règles qui couvrent une clé, un groupe renommé qui garde ses règles, un clone au même poids. */
    @GameTest
    fun searchRenameAndClone(helper: GameTestHelper) {
        val server = helper.server
        val search = Recorder()
        Console.perform(server, Console.source(server, search), "aegis search minecraft:command.help")
        val texts = Console.texts(search)
        helper.assertValueEqual(texts.firstOrNull().orEmpty(), Console.text(Command.SEARCH_TITLE, 3, "minecraft:command.help"), "trois règles couvrent help : command d'admin et de default, help de default ; pas $texts")
        val console = Console.source(server, Recorder())
        Console.perform(server, console, "aegis group vip create 80")
        Console.settle(server)
        Console.perform(server, console, "aegis group vip allow minecraft:command.seed")
        Console.settle(server)
        Console.perform(server, console, "aegis group vip rename premium")
        Console.settle(server)
        var groups = Publisher.current().state.permissions.groups
        helper.assertTrue("vip" !in groups && groups["premium"]?.rules?.containsKey("minecraft:command.seed") == true, "premium remplace vip et garde sa règle, pas ${groups.keys}")
        Console.perform(server, console, "aegis group premium clone gold")
        Console.settle(server)
        groups = Publisher.current().state.permissions.groups
        helper.assertValueEqual(groups["gold"]?.weight ?: -1, 80, "gold a le poids de premium")
        helper.assertTrue(groups["gold"]?.rules?.containsKey("minecraft:command.seed") == true, "et sa règle")
        helper.succeed()
    }

    /**
     * Prouve la résolution d'un joueur (5.4) : un UUID tel quel ; le pseudo d'un joueur en ligne, que le cache de profils du serveur de test, un simulacre sur un
     * ensemble, peut rapporter sous un autre joueur simulé du même pseudo : le joueur quand les deux sources s'accordent, le refus avec les deux UUID sinon ;
     * le pseudo d'une forme lisible du fichier, que ce simulacre compte hors ligne, sous le même UUID que le fichier. La casse se prouve à l'étage 0.
     */
    @GameTest
    fun playerResolvesByUuidOnlineNameAndReadableForm(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val fileName = "zoe-of-the-file-only"
        val fileUuid: UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$fileName".toByteArray(Charsets.UTF_8))
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), players = mapOf(ReadableForm.of(fileName, fileUuid) to Player(groups = listOf("admin")))))

        val byUuid = Recorder()
        Console.perform(server, Console.source(server, byUuid), "aegis player ${player.uuid} info")
        helper.assertValueEqual(Console.texts(byUuid).firstOrNull().orEmpty(), Console.text(Command.PLAYER_INFO_TITLE, player.nameAndId().name(), player.uuid), "un UUID tel quel désigne le joueur, avec son pseudo en ligne")

        val name = player.nameAndId().name()
        val online = server.playerList.getPlayerByName(name) ?: throw helper.assertionException("aucun joueur en ligne sous $name")
        val cached = server.services().nameToIdCache().get(name).orElse(null) ?: throw helper.assertionException("le cache de profils ne répond rien pour $name")
        val byName = Recorder()
        Console.perform(server, Console.source(server, byName), "aegis player $name info")
        val expected = if (cached.id() == online.uuid) {
            Console.text(Command.PLAYER_INFO_TITLE, online.nameAndId().name(), online.uuid)
        } else {
            Console.text(Command.PLAYER_AMBIGUOUS, name, "${online.uuid}, ${cached.id()}")
        }
        helper.assertValueEqual(Console.texts(byName).firstOrNull().orEmpty(), expected, "un pseudo désigne le joueur en ligne quand le cache de profils s'accorde avec la liste, et refuse avec les deux UUID sinon ; pas ${Console.texts(byName)}")

        val byForm = Recorder()
        Console.perform(server, Console.source(server, byForm), "aegis player $fileName info")
        val texts = Console.texts(byForm)
        helper.assertValueEqual(texts.firstOrNull().orEmpty(), Console.text(Command.PLAYER_INFO_TITLE, fileName, fileUuid), "le pseudo d'une forme lisible du fichier désigne son UUID, pas $texts")
        helper.assertTrue(Console.text(Command.PLAYER_INFO_GROUPS, "admin") in texts, "et la fiche dit ses groupes, pas $texts")
        helper.succeed()
    }
}
