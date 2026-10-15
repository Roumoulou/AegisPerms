// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.files.ConfigurationFiles
import fr.moulou.aegisperms.files.PublicationResult
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.hook.TreeResend
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.text.TextKeys.Command
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files

/**
 * Les fichiers et le cycle (cahier des charges, chapitre 4 et 8.5) : les écritures du mod, le rechargement, le refus, la surveillance, les sauvegardes, le
 * `/reload` du serveur, le mode observation, et qui lance `/aegis`.
 */
class FilesAndCycleGameTests : FileGameTests() {

    companion object {
        /** Ce que la surveillance peut prendre : sa période de deux secondes, son délai d'une seconde, et le rechargement. */
        private const val WATCH_TIMEOUT_MILLIS = 8_000L
    }

    /** Prouve qu'une modification par `/aegis` depuis la console écrit le fichier, publie une génération de source `command`, répond, et renvoie l'arbre au joueur concerné. */
    @GameTest
    fun consoleModificationWritesPublishesAndResends(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val before = checkNotNull(Publisher.lastPublication).generation
        val packets = Packets.commandsPackets(player).size
        val recorder = Recorder()
        helper.assertValueEqual(Console.perform(server, Console.source(server, recorder), "aegis group default allow minecraft:command.seed"), 1, "la commande répond")
        Console.settle(server)
        val last = checkNotNull(Publisher.lastPublication)
        helper.assertTrue(last.generation > before && last.source == PublicationSource.COMMAND, "une génération de source command est publiée, pas ${last.generation} ${last.source}")
        helper.assertTrue("\"minecraft:command.seed\"" in Files.readString(configDirectory.resolve("permissions.json")), "le fichier des droits porte la règle")
        val texts = Console.texts(recorder)
        helper.assertTrue(Console.text(Command.CHANGE_PUBLISHED, last.generation) in texts, "la console lit la génération publiée, pas $texts")
        TreeResend.flush(server)
        helper.assertTrue(Packets.commandsPackets(player).size > packets, "le joueur, que default concerne, reçoit un arbre")
        helper.assertTrue(Packets.lastTree(server, player).getChild("mc:seed") != null, "et cet arbre porte /mc:seed")
        helper.succeed()
    }

    /** Prouve qu'un fichier écrit à la main se recharge par `/aegis reload` : la génération suivante, de source `reload`, porte le groupe ajouté, et la réponse compte la règle qui change. */
    @GameTest
    fun handWrittenFileIsReloaded(helper: GameTestHelper) {
        val server = helper.server
        val before = checkNotNull(Publisher.lastPublication).generation
        Files.writeString(configDirectory.resolve("permissions.json"), ConfigurationOnDisk.VIP_PERMISSIONS)
        val recorder = Recorder()
        Console.perform(server, Console.source(server, recorder), "aegis reload")
        Console.settle(server)
        val last = checkNotNull(Publisher.lastPublication)
        helper.assertTrue(last.generation > before && last.source == PublicationSource.RELOAD, "une génération de source reload est publiée, pas ${last.generation} ${last.source}")
        helper.assertTrue(Publisher.current().state.permissions.groups.containsKey("vip"), "l'état en vigueur porte vip")
        val texts = Console.texts(recorder)
        helper.assertTrue(Console.text(Command.RELOADED, last.generation, 1, last.report.warnings.size) in texts, "la réponse compte la règle qui change, pas $texts")
        helper.succeed()
    }

    /**
     * Prouve qu'un fichier invalide est refusé : l'état en vigueur reste, `state.json` dit le refus et ses constats, la réponse les porte, et les
     * modifications par commande attendent une configuration valide (4.8).
     */
    @GameTest
    fun invalidFileIsRefusedAndTheStateSaysSo(helper: GameTestHelper) {
        val server = helper.server
        val permissions = configDirectory.resolve("permissions.json")
        val valid = Files.readString(permissions)
        val before = checkNotNull(Publisher.lastPublication)
        try {
            Files.writeString(permissions, "{ pas du json")
            val recorder = Recorder()
            Console.perform(server, Console.source(server, recorder), "aegis reload")
            Console.settle(server)
            helper.assertTrue(Publisher.lastPublication === before, "rien n'est publié")
            val state = ConfigurationFiles(configDirectory).use { it.readState() } ?: throw helper.assertionException("state.json manque ou ne se lit pas")
            helper.assertValueEqual(state.result, PublicationResult.REFUSED, "state.json dit le refus")
            helper.assertValueEqual(state.generation, before.generation, "et la génération qui reste")
            helper.assertTrue(state.issues.any { it.severity == "error" && it.file == "permissions.json" }, "avec l'erreur du fichier, pas ${state.issues}")
            val texts = Console.texts(recorder)
            helper.assertValueEqual(texts.firstOrNull().orEmpty(), Console.text(Command.RELOAD_REFUSED, state.issues.count { it.severity == "error" }), "la réponse commence par le refus")
            helper.assertTrue(texts.drop(1).any { it.startsWith("permissions.json") }, "et porte les constats, pas $texts")
            val refused = Recorder()
            Console.perform(server, Console.source(server, refused), "aegis group vip create 80")
            Console.settle(server)
            helper.assertTrue(Console.text(Command.READ_ONLY) in Console.texts(refused), "une modification attend une configuration valide, pas ${Console.texts(refused)}")
        } finally {
            Files.writeString(permissions, valid)
            FileThread.reload(PublicationSource.RELOAD).get()
        }
        helper.succeed()
    }

    /**
     * Prouve que la surveillance recharge un fichier modifié hors du mod, de source `watch`, dans les secondes qui suivent. L'attente se fait sur l'horloge,
     * le fil serveur arrêté : la surveillance compte en secondes, et le serveur de test ne tient pas vingt ticks par seconde.
     */
    @GameTest
    fun watchReloadsAFileChangedOutside(helper: GameTestHelper) {
        val before = checkNotNull(Publisher.lastPublication).generation
        Files.writeString(configDirectory.resolve("permissions.json"), ConfigurationOnDisk.VIP_PERMISSIONS)
        val deadline = System.currentTimeMillis() + WATCH_TIMEOUT_MILLIS
        while (Publisher.lastPublication.let { it == null || it.generation <= before }) {
            if (System.currentTimeMillis() > deadline) throw helper.assertionException("la surveillance n'a rien publié en $WATCH_TIMEOUT_MILLIS ms")
            Thread.sleep(50)
        }
        val last = checkNotNull(Publisher.lastPublication)
        helper.assertValueEqual(last.source, PublicationSource.WATCH, "la surveillance a publié")
        helper.assertTrue(Publisher.current().state.permissions.groups.containsKey("vip"), "et l'état en vigueur porte vip")
        Console.settle(helper.server)
        helper.succeed()
    }

    /** Prouve que `/aegis backup` copie les fichiers en place, et que `/aegis restore` les remet, publie de source `restore` et met de côté ceux d'avant. */
    @GameTest
    fun backupThenRestore(helper: GameTestHelper) {
        val server = helper.server
        val console = Console.source(server, Recorder())
        Console.perform(server, console, "aegis backup gametest")
        Console.settle(server)
        val folder = FileThread.backupNames().lastOrNull { "-gametest" in it && "before-restore" !in it } ?: throw helper.assertionException("aucune sauvegarde gametest dans ${FileThread.backupNames()}")
        Console.perform(server, console, "aegis group vip create 80")
        Console.settle(server)
        helper.assertTrue(Publisher.current().state.permissions.groups.containsKey("vip"), "vip est créé")
        val recorder = Recorder()
        Console.perform(server, Console.source(server, recorder), "aegis restore $folder")
        Console.settle(server)
        val last = checkNotNull(Publisher.lastPublication)
        helper.assertValueEqual(last.source, PublicationSource.RESTORE, "la restauration publie")
        helper.assertFalse(Publisher.current().state.permissions.groups.containsKey("vip"), "et vip n'y est plus")
        helper.assertTrue(FileThread.backupNames().any { "before-restore" in it }, "les fichiers d'avant sont mis de côté, pas ${FileThread.backupNames()}")
        helper.assertTrue(Console.text(Command.RESTORED, folder, last.generation) in Console.texts(recorder), "la console lit la restauration, pas ${Console.texts(recorder)}")
        helper.succeed()
    }

    /** Prouve qu'un `/reload` réussi met en service un nouveau `Commands`, indexé, que le fil des fichiers adopte en réécrivant le catalogue, et que les arbres repartent. */
    @GameTest
    fun successfulReloadAdoptsTheNewIndex(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val packets = Packets.commandsPackets(player).size
        val commandsBefore = server.commands
        val catalog = configDirectory.resolve("catalog.json")
        val writtenBefore = Files.getLastModifiedTime(catalog)
        val reload = server.reloadResources(server.packRepository.selectedIds)
        helper.assertTrue(reload.isDone && !reload.isCompletedExceptionally, "le /reload réussit")
        helper.assertTrue(server.commands !== commandsBefore, "un nouveau Commands est en service")
        helper.assertTrue(Trees.indexOf(server.commands) != null, "et il porte son index")
        Console.settle(server)
        helper.assertTrue(Files.getLastModifiedTime(catalog) > writtenBefore, "le fil des fichiers a réécrit le catalogue à l'adoption")
        TreeResend.flush(server)
        helper.assertTrue(Packets.commandsPackets(player).size > packets, "les arbres repartent")
        helper.succeed()
    }

    /**
     * Prouve qu'un `/reload` raté, par un datapack écrit dans le monde que le rechargement refuse, garde l'ancien `Commands` et son index. Le datapack porte
     * le fichier que [ReloadPoison] refuse : un fichier de données fautif ne fait échouer le rechargement qu'en 26.3.
     */
    @GameTest
    fun failedReloadKeepsTheOldIndex(helper: GameTestHelper) {
        val server = helper.server
        val commandsBefore = server.commands
        val indexBefore = Trees.indexOf(commandsBefore)
        val pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("aegis-bad")
        try {
            Files.createDirectories(pack.resolve("data/$TEST_MOD_ID"))
            Files.writeString(pack.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"description\": \"un datapack que le mod de test refuse au rechargement\",\n    \"min_format\": 82,\n    \"max_format\": 9999\n  }\n}\n")
            Files.writeString(pack.resolve("data/$TEST_MOD_ID/poison.json"), "{}\n")
            server.packRepository.reload()
            helper.assertTrue("file/aegis-bad" in server.packRepository.availableIds, "le datapack est découvert, parmi ${server.packRepository.availableIds}")
            val reload = server.reloadResources(server.packRepository.selectedIds + "file/aegis-bad")
            helper.assertTrue(reload.isCompletedExceptionally, "le /reload échoue")
            helper.assertTrue(server.commands === commandsBefore, "l'ancien Commands reste en service")
            helper.assertTrue(Trees.indexOf(server.commands) === indexBefore, "avec son index")
        } finally {
            pack.toFile().deleteRecursively()
            server.packRepository.reload()
        }
        helper.succeed()
    }

    /** Prouve que le mode observation calcule sans appliquer : le nœud suit vanilla, la porte laisse passer, `status` dit le mode. Les écarts au journal se prouvent à l'étage 1. */
    @GameTest
    fun observationComputesWithoutApplying(helper: GameTestHelper) {
        val server = helper.server
        val source = helper.judgedPlayer().source
        val me = Trees.node(server, "mc:me")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("minecraft:command.me" to Rule.DENY)))
        helper.assertFalse(me.canUse(source), "sous enforce, le deny masque /mc:me")
        val recorder = Recorder()
        Console.perform(server, Console.source(server, recorder), "aegis mode observe")
        helper.assertValueEqual(Publisher.mode(), Mode.OBSERVE, "le mode est posé en mémoire")
        helper.assertTrue(me.canUse(source), "sous observe, le nœud suit vanilla et se voit")
        helper.assertTrue(Trees.runs(server.commands.dispatcher, "mc:me coucou", source), "et la porte laisse passer la ligne que la règle refuse")
        Console.perform(server, Console.source(server, recorder), "aegis status")
        helper.assertTrue(Console.text(Command.STATUS_MODE, Console.text(Command.MODE_OBSERVE)) in Console.texts(recorder), "status dit le mode observation, pas ${Console.texts(recorder)}")
        helper.succeed()
    }

    /** Prouve que `/aegis` refuse une source silencieuse, celle d'une fonction, et une source qui porte une entité, là où la console passe (5.2). */
    @GameTest
    fun aegisRefusesFunctionAndEntitySources(helper: GameTestHelper) {
        val server = helper.server
        val console = server.createCommandSourceStack()
        helper.assertValueEqual(Console.perform(server, console.withSource(Recorder()), "aegis status"), 1, "la console lance /aegis")
        helper.assertValueEqual(Console.perform(server, console.withSuppressedOutput(), "aegis status"), 0, "une source silencieuse, celle d'une fonction, est refusée")
        helper.assertValueEqual(Console.perform(server, console.withEntity(helper.judgedPlayer()).withSource(Recorder()), "aegis status"), 0, "une source qui porte une entité, execute as depuis la console, est refusée")
        helper.succeed()
    }
}
