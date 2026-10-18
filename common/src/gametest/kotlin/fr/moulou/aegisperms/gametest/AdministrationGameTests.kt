// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.files.ManualWrite
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.text.TextKeys.Command
import fr.moulou.aegisperms.text.TextKeys.Why
import fr.moulou.aegisperms.tree.IndexCatalog
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * `/aegis` (cahier des charges, chapitre 5 et 8.5) : `why` et `check` depuis la console, une clé mal formée, `search`, `rename` et `clone`, la résolution
 * d'un joueur, l'ordre de `group list`, le cycle de vie d'un groupe et les règles d'un joueur, `/aegis` nu, `catalog` et `validate`.
 */
class AdministrationGameTests : FileGameTests() {

    /** Prouve que `why` sonde un joueur en ligne avec sa propre source : un prédicat de mod qui demande un joueur passe, là où la console n'en porte aucun. */
    @GameTest
    fun whyProbesWithThePlayersOwnSource(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.aegistest_player" to Rule.ALLOW)))
        val recorder = Recorder()
        Console.perform(server, Console.source(server, recorder), "aegis why ${player.uuid} $TEST_MOD_ID:aegistest_player")
        val texts = Console.texts(recorder)
        helper.assertValueEqual(texts.lastOrNull().orEmpty(), Console.text(Why.VERDICT_PERMITTED), "le prédicat du mod, évalué sur la source du joueur, passe ; pas $texts")
        helper.assertFalse(texts.any { Console.text(Why.STATUS_NOT_EVALUATED) in it }, "en ligne, rien ne reste non évalué, pas $texts")
        helper.succeed()
    }

    /** Prouve l'ordre de `group list` (2.6) : par poids décroissant, `default` après les autres à poids égal, puis par nom. */
    @GameTest
    fun groupListOrdersByWeightThenDefaultLast(helper: GameTestHelper) {
        val server = helper.server
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), groups = mapOf("vip" to Group(weight = 80), "aaa" to Group())))
        val recorder = Recorder()
        Console.perform(server, Console.source(server, recorder), "aegis group list")
        val texts = Console.texts(recorder)
        helper.assertValueEqual(texts.firstOrNull().orEmpty(), Console.text(Command.GROUP_LIST_TITLE, 4), "le titre compte les groupes, pas $texts")
        val expected = listOf(
            Console.text(Command.GROUP_LIST_ENTRY, "admin", 100, 1) + Console.text(Command.GROUP_INCLUDE_OPS, "owners"),
            Console.text(Command.GROUP_LIST_ENTRY, "vip", 80, 0),
            Console.text(Command.GROUP_LIST_ENTRY, "aaa", 0, 0),
            Console.text(Command.GROUP_LIST_ENTRY, "default", 0, 3),
        )
        helper.assertValueEqual(texts.drop(1), expected, "admin, vip, aaa, puis default, qui passe après aaa à poids égal")
        helper.succeed()
    }

    /** Prouve le cycle de vie d'un groupe depuis la console : créé, pesé, membres d'office, parent, règle et note, lu par `info`, puis défait et supprimé. */
    @GameTest
    fun groupLifecycleFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val console = Console.source(server, Recorder())
        perform(helper, server, console, "aegis group staff create 50")
        perform(helper, server, console, "aegis group staff weight 60")
        perform(helper, server, console, "aegis group staff include-ops gamemasters")
        perform(helper, server, console, "aegis group staff parent add admin")
        perform(helper, server, console, "aegis group staff deny minecraft:command.kick")
        perform(helper, server, console, "aegis group staff note \"Les modérateurs.\"")
        val built = Group(weight = 60, parents = listOf("admin"), includeOps = OpLevel.GAMEMASTERS, rules = mapOf("minecraft:command.kick" to Rule.DENY), note = "Les modérateurs.")
        helper.assertValueEqual(publishedGroup(helper, "staff"), built, "le groupe publié porte chaque modification")
        val info = Recorder()
        Console.perform(server, Console.source(server, info), "aegis group staff info")
        val expected = listOf(
            Console.text(Command.GROUP_INFO_TITLE, "staff", 60),
            Console.text(Command.GROUP_INFO_PARENTS, "admin"),
            Console.text(Command.GROUP_INFO_INCLUDE_OPS, "gamemasters"),
            Console.text(Command.NOTE, "Les modérateurs."),
            Console.text(Command.GROUP_INFO_RULES, 1),
            Console.text(Command.RULE_ENTRY, "minecraft:command.kick", Console.text(Command.RULE_DENY)),
            Console.text(Command.GROUP_INFO_MEMBERS_NONE),
        )
        helper.assertValueEqual(Console.texts(info), expected, "info dit le poids, le parent, les membres d'office, la note, la règle et l'absence de membre")
        perform(helper, server, console, "aegis group staff parent remove admin")
        perform(helper, server, console, "aegis group staff unset minecraft:command.kick")
        perform(helper, server, console, "aegis group staff include-ops none")
        helper.assertValueEqual(publishedGroup(helper, "staff"), Group(weight = 60, note = "Les modérateurs."), "défait, le groupe garde son poids et sa note")
        perform(helper, server, console, "aegis group staff delete")
        helper.assertTrue("staff" !in Publisher.current().state.permissions.groups, "supprimé, le groupe quitte les droits publiés")
        helper.succeed()
    }

    /** Prouve les règles d'un joueur depuis la console : un groupe, `allow`, `deny-visible` et une note, lus par `info`, puis défaits. */
    @GameTest
    fun playerRulesFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val uuid = player.uuid
        val console = Console.source(server, Recorder())
        perform(helper, server, console, "aegis player $uuid group add admin")
        perform(helper, server, console, "aegis player $uuid allow minecraft:command.seed")
        perform(helper, server, console, "aegis player $uuid deny-visible minecraft:command.kick")
        perform(helper, server, console, "aegis player $uuid note \"Un habitué.\"")
        val visibleDeny = Rule(RuleValue.DENY, visible = true)
        val written = Player(groups = listOf("admin"), rules = mapOf("minecraft:command.seed" to Rule.ALLOW, "minecraft:command.kick" to visibleDeny), note = "Un habitué.")
        helper.assertValueEqual(publishedPlayer(helper, player.readableForm()), written, "le joueur publié, sous sa forme lisible, porte chaque modification")
        val info = Recorder()
        Console.perform(server, Console.source(server, info), "aegis player $uuid info")
        val texts = Console.texts(info)
        helper.assertTrue(Console.text(Command.PLAYER_INFO_GROUPS, "admin") in texts, "info dit le groupe direct, pas $texts")
        helper.assertTrue(Console.text(Command.NOTE, "Un habitué.") in texts, "et la note, pas $texts")
        helper.assertTrue(Console.text(Command.PLAYER_INFO_RULES, 2) in texts, "et compte les règles propres, pas $texts")
        helper.assertTrue(Console.text(Command.RULE_ENTRY, "minecraft:command.kick", Console.text(Command.RULE_DENY_VISIBLE)) in texts, "dont le deny visible, pas $texts")
        perform(helper, server, console, "aegis player $uuid unset minecraft:command.seed")
        perform(helper, server, console, "aegis player $uuid group remove admin")
        val undone = Player(rules = mapOf("minecraft:command.kick" to visibleDeny), note = "Un habitué.")
        helper.assertValueEqual(publishedPlayer(helper, player.readableForm()), undone, "défait, le joueur garde le deny et sa note")
        helper.succeed()
    }

    /** Prouve `/aegis` nu, `catalog` sur un début de clé et `validate` : l'usage, les fiches et leurs marques, la relecture sans rien appliquer et ce qu'elle changerait. */
    @GameTest
    fun usageCatalogAndValidateFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val usage = Recorder()
        Console.perform(server, Console.source(server, usage), "aegis")
        helper.assertValueEqual(Console.texts(usage), listOf(Console.text(Command.USAGE)), "/aegis nu répond l'usage")

        val index = Trees.indexOf(server.commands) ?: throw helper.assertionException("le dispatcher en service n'a pas d'index")
        val kickKeys = IndexCatalog(index).commands().keys.filter { it.startsWith("minecraft:command.kick") }.sortedWith(CodePointOrder)
        helper.assertTrue(kickKeys.firstOrNull() == "minecraft:command.kick", "le catalogue connaît /kick et ses nœuds, pas $kickKeys")
        val catalog = Recorder()
        Console.perform(server, Console.source(server, catalog), "aegis catalog minecraft:command.kick")
        val entries = Console.texts(catalog)
        helper.assertValueEqual(entries.firstOrNull().orEmpty(), Console.text(Command.CATALOG_TITLE, kickKeys.size, 0), "le titre compte les clés qui commencent par le début donné, pas $entries")
        helper.assertValueEqual(entries.size, kickKeys.size + 1, "puis une ligne par clé")
        val kick = entries[1]
        helper.assertTrue(kick.startsWith(Console.text(Command.CATALOG_ENTRY, "minecraft:command.kick", "")), "la première fiche est celle de la racine, pas $kick")
        helper.assertTrue(Console.text(Command.CATALOG_REQUIREMENT_LEVEL, "admins") in kick, "elle dit l'exigence de vanilla, pas $kick")
        helper.assertTrue(Console.text(Command.CATALOG_PREFIXED_FORM, "mc:kick") in kick, "et la forme préfixée, pas $kick")

        val valid = Recorder()
        Console.perform(server, Console.source(server, valid), "aegis validate")
        Console.settle(server)
        helper.assertValueEqual(Console.texts(valid), listOf(Console.text(Command.VALIDATE_VALID, 0), Console.text(Command.VALIDATE_NO_CHANGE)), "les fichiers livrés sont valides et ne changeraient rien")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.seed" to Rule.ALLOW)))
        val changed = Recorder()
        Console.perform(server, Console.source(server, changed), "aegis validate")
        Console.settle(server)
        val texts = Console.texts(changed)
        helper.assertValueEqual(texts.getOrNull(1).orEmpty(), Console.text(Command.VALIDATE_CHANGES, 1), "l'état publié a une règle de plus que le disque : une règle changerait, pas $texts")
        helper.succeed()
    }

    /**
     * Prouve que `/aegis manual` écrit le manuel des commandes dans `manuals\` (5.10) : mod par mod, chaque nœud avec sa clé, sans section pour un alias de
     * vanilla ; qu'un second manuel n'écrase pas le premier ; et qu'une langue inconnue se refuse.
     */
    @GameTest
    fun manualIsWrittenFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val manuals = configDirectory.resolve(ManualWrite.FOLDER)
        val before = manualNames(manuals)
        val recorder = Recorder()
        helper.assertValueEqual(Console.perform(server, Console.source(server, recorder), "aegis manual fr"), 1, "la commande est acceptée")
        Console.settle(server)
        val written = (manualNames(manuals) - before).toList()
        helper.assertValueEqual(written.size, 1, "un manuel de plus est écrit, pas $written")
        val name = written.single()
        helper.assertTrue(name.matches(Regex("commands-\\d{4}-\\d{2}-\\d{2}-fr(-\\d+)?\\.md")), "nommé par la date et la langue, pas $name")
        val reply = Console.texts(recorder).singleOrNull().orEmpty()
        helper.assertTrue(reply.startsWith(Console.text(Command.MANUAL_DONE, "${ManualWrite.FOLDER}/$name", "").substringBefore(" (")), "la réponse dit le fichier, pas $reply")
        val text = Files.readString(manuals.resolve(name))
        for (expected in listOf(
            "# Les commandes du serveur", "## minecraft (Minecraft ", "### /msg", "- `msg` : `minecraft:command.msg`", "`minecraft:command.msg.targets.message`",
            "### /aegis", "- `manual` : `aegisperms:command.aegis.manual`", "## aegisperms-gametest", "### /aegistest_target",
        )) {
            helper.assertTrue(expected in text, "le manuel porte « $expected »")
        }
        helper.assertTrue("| `/msg` | `/mc:msg` | `/tell`, `/w` | `minecraft:command.msg` |" in text, "la table de vanilla donne la forme et les alias de /msg")
        helper.assertFalse("### /tell\n" in text || "### /w\n" in text || "### /tp\n" in text, "un alias de vanilla n'a pas de section")
        helper.assertValueEqual(Console.perform(server, Console.source(server, Recorder()), "aegis manual en"), 1, "un manuel anglais s'écrit aussi")
        Console.settle(server)
        helper.assertValueEqual((manualNames(manuals) - before).size, 2, "sans écraser le premier")
        val refused = Recorder()
        helper.assertValueEqual(Console.perform(server, Console.source(server, refused), "aegis manual de"), 0, "une langue inconnue se refuse")
        helper.assertValueEqual(Console.texts(refused).singleOrNull().orEmpty(), Console.text(Command.MANUAL_UNKNOWN_LANGUAGE, "de"), "en une ligne qui la cite")
        helper.succeed()
    }

    private fun manualNames(manuals: Path): Set<String> = if (Files.isDirectory(manuals)) Files.list(manuals).use { found -> found.map { it.fileName.toString() }.toList().toSet() } else emptySet()

    /** Lance une modification depuis [console], qui doit l'accepter, et attend que le fil des fichiers l'ait écrite et publiée. */
    private fun perform(helper: GameTestHelper, server: MinecraftServer, console: CommandSourceStack, line: String) {
        helper.assertValueEqual(Console.perform(server, console, line), 1, "« $line » est acceptée")
        Console.settle(server)
    }

    private fun publishedGroup(helper: GameTestHelper, name: String): Group =
        Publisher.current().state.permissions.groups[name] ?: throw helper.assertionException("le groupe $name manque aux droits publiés")

    private fun publishedPlayer(helper: GameTestHelper, readableForm: String): Player =
        Publisher.current().state.permissions.players[readableForm] ?: throw helper.assertionException("le joueur $readableForm manque aux droits publiés")

    /** Prouve que `why` et `check` depuis la console se lisent dans l'enregistreur, résolus dans sa langue : le titre, le verdict, la décision d'une clé. */
    @GameTest
    fun whyAndCheckFromTheConsole(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val why = Recorder()
        Console.perform(server, Console.source(server, why), "aegis why ${player.uuid} mc:help")
        val whyTexts = Console.texts(why)
        helper.assertTrue(whyTexts.size >= 3, "why répond ligne par ligne, pas $whyTexts")
        helper.assertValueEqual(whyTexts.first(), Console.text(Why.TITLE, player.readableForm(), "mc:help"), "la première ligne dit le joueur et la ligne")
        helper.assertValueEqual(whyTexts.last(), Console.text(Why.VERDICT_PERMITTED), "la dernière le verdict : permise, default ouvre help")
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
