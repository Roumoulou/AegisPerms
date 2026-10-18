// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.BackupResult
import fr.moulou.aegisperms.files.CatalogFile
import fr.moulou.aegisperms.files.ConfigurationFiles
import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.files.ConfigurationSource
import fr.moulou.aegisperms.files.ConfigurationWrite
import fr.moulou.aegisperms.files.GeneratedWrite
import fr.moulou.aegisperms.files.ManualWrite
import fr.moulou.aegisperms.files.PublicationResult
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.files.RestoreResult
import fr.moulou.aegisperms.files.StateFile
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.IndexCatalog
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ConfigurationValidator
import fr.moulou.aegisperms.validate.ValidationReport
import net.minecraft.commands.CommandSourceStack
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.streams.toList

/**
 * Le fil des fichiers (cahier des charges, 7.4) : un exécuteur à un fil, démon, seul à appeler `ConfigurationFiles` et à compter les générations. Il
 * sérialise lectures, validations, écritures, sauvegardes, catalogue et état ; le fil serveur ne l'attend qu'au démarrage, en deux temps (D9), et une
 * publication lui revient par la référence du `Publisher`.
 *
 * `reload`, `write`, `check`, `backup` et `restore` sont les travaux que `/aegis` et la surveillance lui confient : chacun rend, dans une `CompletableFuture`,
 * son résultat et ce qu'il a publié (E8), et la commande répond sur le fil serveur quand il a fini. La surveillance des deux fichiers (4.8) tourne sur ce
 * même fil, par un exécuteur planifié.
 */
object FileThread {

    private const val THREAD_NAME = "AegisPerms files"
    private const val STOP_TIMEOUT_SECONDS = 5L
    private const val HOOK = "files"
    private const val LAST_GOOD = "last-good"

    /** Le dossier des sauvegardes sous celui de la configuration (4.10), le même nom que `files` lui donne. */
    private const val BACKUPS = "backups"

    private lateinit var files: ConfigurationFiles
    private lateinit var executor: ScheduledExecutorService

    /** La période de la surveillance (4.8), en millisecondes ; un test la raccourcit. */
    internal var watchPeriodMillis = 2_000L

    /** Le délai qui laisse un éditeur finir d'écrire avant le rechargement, en millisecondes ; un test le raccourcit. */
    internal var watchDelayMillis = 1_000L

    /** Ce qui reçoit le résultat d'un rechargement par la surveillance, sur le fil des fichiers : le cycle de vie le porte aux ops (E12). */
    internal var watchListener: (Outcome<ConfigurationRead>) -> Unit = {}

    private var watching: ScheduledFuture<*>? = null

    // ── Le fil des fichiers seulement ──────────────────────────────────────────

    private var generation = 0L

    /** Vrai entre la détection d'un changement extérieur et la fin du rechargement qu'il déclenche : un seul rechargement par changement. */
    private var reloadPending = false

    /** L'index adopté ; `null` tant qu'aucun index n'est adopté, ou quand il a manqué. */
    private var index: CommandIndex<CommandSourceStack>? = null

    /** Le catalogue que l'index adopté donne à la validation, `null` avec lui. */
    private var catalog: IndexCatalog<CommandSourceStack>? = null

    private var createdAtStart: Set<ConfigurationFile> = emptySet()

    /** Des clés d'API demandées depuis la dernière écriture du catalogue (D12). */
    private val apiKeysDirty = AtomicBoolean(false)
    private val apiKeyWritePending = AtomicBoolean(false)

    /**
     * À l'initialisation du mod, sur son fil : ouvre les stores, ce qui écrit les fichiers absents, lit la configuration sans catalogue et la publie comme
     * génération 0, provisoire, pour que le premier `Commands` trouve ses réglages (D9). Les clés d'API que le catalogue gardait sont reprises. Le fil
     * appelant attend.
     */
    fun start(directory: Path) {
        Files.createDirectories(directory)
        files = ConfigurationFiles(directory)
        // À l'arrêt, un rechargement différé par la surveillance s'annule au lieu de courir sur des stores fermés.
        executor = ScheduledThreadPoolExecutor(1) { runnable -> Thread(runnable, THREAD_NAME).apply { isDaemon = true } }.apply {
            executeExistingDelayedTasksAfterShutdownPolicy = false
            removeOnCancelPolicy = true
        }
        watching = null
        ApiKeyRequests.onNewKey = ::onNewApiKey
        await {
            generation = 0
            reloadPending = false
            index = null
            catalog = null
            apiKeysDirty.set(false)
            val start = files.start()
            createdAtStart = start.created
            Publisher.publish(EffectiveState(0, start.configuration))
            ApiKeyRequests.seed(files.readCatalog()?.apiKeys.orEmpty())
        }
    }

    /**
     * À `SERVER_STARTING`, l'index du `Commands` courant adopté, ou `null` quand il a manqué : écrit le catalogue, relit la configuration avec lui, ce qui
     * rend le vrai verdict du démarrage, repli compris, publie la génération 1 de source `startup`, écrit `state.json`, et `last-good` quand les deux
     * fichiers viennent de leur place (4.9). Le fil serveur attend.
     */
    fun startup(adopted: CommandIndex<CommandSourceStack>?) = await {
        try {
            index = adopted
            catalog = adopted?.let { writeCatalog(it, logChanges = true) }
            val start = files.start(catalog)
            if (createdAtStart.isNotEmpty()) ModLog.info(ModLog.FILES_CREATED, createdAtStart.joinToString(", ") { it.fileName })
            if (start.settingsSource != ConfigurationSource.FILE) ModLog.error(ModLog.FALLBACK, ConfigurationFile.SETTINGS.fileName, start.settingsSource.name.lowercase())
            if (start.permissionsSource != ConfigurationSource.FILE) ModLog.error(ModLog.FALLBACK, ConfigurationFile.PERMISSIONS.fileName, start.permissionsSource.name.lowercase())
            val fallback = setOfNotNull(
                ConfigurationFile.SETTINGS.takeIf { start.settingsSource != ConfigurationSource.FILE },
                ConfigurationFile.PERMISSIONS.takeIf { start.permissionsSource != ConfigurationSource.FILE },
            )
            publish(start.configuration, start.report, PublicationSource.STARTUP, start.readOnly, lastGood = start.settingsSource == ConfigurationSource.FILE && !start.readOnly, fallback)
            watching = executor.scheduleWithFixedDelay(::watch, watchPeriodMillis, watchPeriodMillis, TimeUnit.MILLISECONDS)
        } catch (error: Throwable) {
            ModLog.once(HOOK, "startup", error)
        }
    }

    /**
     * La surveillance (4.8), toutes les deux secondes sur le fil : quand `watch-files` le dit, un fichier qu'un autre que le mod a écrit déclenche un
     * rechargement de source `watch`, après le délai qui laisse un éditeur finir d'écrire. Une lecture refusée ne se recharge pas deux fois :
     * `ConfigurationFiles` retient son empreinte.
     */
    private fun watch() {
        try {
            if (reloadPending || !Publisher.settings().watchFiles || files.changedOutside().isEmpty()) return
            reloadPending = true
            executor.schedule(Runnable {
                reload(PublicationSource.WATCH).whenComplete { outcome, error ->
                    reloadPending = false
                    if (error != null) ModLog.once(HOOK, "watch", error) else watchListener(outcome)
                }
            }, watchDelayMillis, TimeUnit.MILLISECONDS)
        } catch (error: Throwable) {
            reloadPending = false
            ModLog.once(HOOK, "watch", error)
        }
    }

    /**
     * Après un `/reload` réussi, l'index du nouveau `Commands` adopté, ou `null` quand il a manqué : écrit le catalogue, puis rejuge en mémoire la
     * configuration en vigueur contre lui, constats au journal. Ni relecture des fichiers, ni génération nouvelle, ni `state.json` (D10).
     */
    fun adopt(adopted: CommandIndex<CommandSourceStack>?) {
        executor.execute {
            try {
                index = adopted
                catalog = adopted?.let { writeCatalog(it, logChanges = true) }
                ConfigurationValidator.validate(Publisher.current().state.configuration, catalog).issues.forEach(ModLog::issue)
            } catch (error: Throwable) {
                ModLog.once(HOOK, "adopt", error)
            }
        }
    }

    /** Un rechargement de source [source] (4.8) : relit, valide, et publie une génération nouvelle, ou laisse l'état en vigueur et dit le refus. */
    fun reload(source: PublicationSource): CompletableFuture<Outcome<ConfigurationRead>> = CompletableFuture.supplyAsync({
        val before = Publisher.lastPublication
        val read = files.reload(catalog)
        val published = when (read) {
            is ConfigurationRead.Valid -> publish(read.configuration, read.report, source, readOnly = false, lastGood = true)
            is ConfigurationRead.Refused -> refuse(source, read.report)
        }
        Outcome(read, published, before)
    }, executor)

    /** La lecture des deux fichiers tels qu'ils sont, sans rien créer, retenir ni appliquer : `/aegis validate` (5.3). */
    fun check(): CompletableFuture<ConfigurationRead> = CompletableFuture.supplyAsync({ files.check(catalog) }, executor)

    /**
     * Une écriture des droits (4.8), la seule que le mod fasse : écrit, relit, et publie une génération nouvelle, ou dit pourquoi rien ne s'est écrit. Le
     * repli du démarrage se reconduit : il ne cesse qu'à une relecture valide des deux fichiers (4.9), et `last-good` ne s'écrit pas tant que les réglages
     * viennent de lui.
     */
    fun write(permissions: Permissions, source: PublicationSource = PublicationSource.COMMAND): CompletableFuture<Outcome<ConfigurationWrite>> = CompletableFuture.supplyAsync({
        val before = Publisher.lastPublication
        val fallback = before?.fallback.orEmpty()
        val write = files.write(permissions, catalog)
        val published = when (write) {
            is ConfigurationWrite.Written -> publish(write.configuration, write.report, source, readOnly = false, lastGood = ConfigurationFile.SETTINGS !in fallback, fallback)
            is ConfigurationWrite.Refused -> refuse(source, write.report)
            ConfigurationWrite.FileMoved, ConfigurationWrite.ReadOnly, is ConfigurationWrite.Failed -> null
        }
        Outcome(write, published, before)
    }, executor)

    /** Une sauvegarde des deux fichiers en place (4.10), sous [name] quand il est donné. */
    fun backup(name: String?): CompletableFuture<BackupResult> = CompletableFuture.supplyAsync({ files.backup(name) }, executor)

    /** Une restauration (4.10) : les fichiers en place remplacés par ceux de la sauvegarde [folder], relus et publiés de source `restore`. */
    fun restore(folder: String): CompletableFuture<Outcome<RestoreResult>> = CompletableFuture.supplyAsync({
        val before = Publisher.lastPublication
        val result = files.restore(folder, catalog)
        val published = when (result) {
            is RestoreResult.Restored -> publish(result.configuration, result.report, PublicationSource.RESTORE, readOnly = false, lastGood = true)
            is RestoreResult.Refused -> refuse(PublicationSource.RESTORE, result.report)
            RestoreResult.UnknownBackup, RestoreResult.IncompleteBackup, is RestoreResult.Failed -> null
        }
        Outcome(result, published, before)
    }, executor)

    /** L'écriture d'un manuel des commandes (5.10) dans `manuals\`, pour la langue de code [language] : le nom du fichier écrit, ou la panne. */
    fun writeManual(language: String, text: String): CompletableFuture<ManualWrite> = CompletableFuture.supplyAsync({ files.writeManual(language, text) }, executor)

    /** Le dossier de configuration que le fil sert, ou `null` avant le démarrage. */
    fun directory(): Path? = if (::files.isInitialized) files.directory else null

    /** Les sauvegardes présentes, par le nom de leur dossier, triées : ce que `restore` complète. Lu sans passer par les stores, depuis n'importe quel fil. */
    fun backupNames(): List<String> {
        if (!::files.isInitialized) return emptyList()
        val root = files.directory.resolve(BACKUPS)
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream -> stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList() }
    }

    /** À `SERVER_STOPPING` : les clés d'API en attente s'écrivent, les stores se ferment, le fil s'arrête sous un délai borné. */
    fun stop() {
        if (!::executor.isInitialized || executor.isShutdown) return
        watching?.cancel(false)
        watching = null
        executor.execute {
            writeApiKeys()
            files.close()
        }
        executor.shutdown()
        if (!executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) ModLog.warn(ModLog.STOP_TIMEOUT, STOP_TIMEOUT_SECONDS)
    }

    /** Attend que le fil ait fini ce qu'il a en cours. Réservé aux tests. */
    internal fun drain() = await {}

    private fun publish(configuration: Configuration, report: ValidationReport, source: PublicationSource, readOnly: Boolean, lastGood: Boolean, fallback: Set<ConfigurationFile> = emptySet()): Published {
        generation++
        val state = EffectiveState(generation, configuration)
        // Une écriture du mod ne relit pas le fichier des réglages : le mode posé par `/aegis mode` lui survit (5.8).
        val previous = Publisher.publish(state, resetMode = source != PublicationSource.COMMAND)
        Publisher.lastPublication = LastPublication(generation, source, Instant.now(), readOnly, fallback, report)
        val permissions = configuration.permissions
        val rules = permissions.groups.values.sumOf { it.rules.size } + permissions.players.values.sumOf { it.rules.size }
        ModLog.info(ModLog.PUBLISHED, generation, source.name.lowercase(), configuration.settings.mode.name.lowercase(), readOnly, permissions.groups.size, permissions.players.size, rules, report.issues.size)
        report.issues.forEach(ModLog::issue)
        writeOrWarn(StateFile.FILE_NAME, files.writeState(generation, source, PublicationResult.APPLIED, report))
        if (lastGood) writeOrWarn(LAST_GOOD, files.writeLastGood(configuration, generation))
        TreeResend.afterPublication(previous, state)
        return Published(previous, state)
    }

    /** Une publication refusée : l'état en vigueur reste, le journal et `state.json` le disent. Rend `null`, rien n'étant publié. */
    private fun refuse(source: PublicationSource, report: ValidationReport): Published? {
        ModLog.error(ModLog.REFUSED, source.name.lowercase(), generation, report.issues.size)
        report.issues.forEach(ModLog::issue)
        writeOrWarn(StateFile.FILE_NAME, files.writeState(generation, source, PublicationResult.REFUSED, report))
        return null
    }

    /** Écrit le catalogue de [adopted] (4.6) et rend celui que la validation lira, avec les clés apparues depuis le catalogue précédent. */
    private fun writeCatalog(adopted: CommandIndex<CommandSourceStack>, logChanges: Boolean): IndexCatalog<CommandSourceStack> {
        // Le drapeau tombe avant la lecture des tables : une clé inscrite pendant l'écriture le relève, et la prochaine écriture la prendra.
        apiKeysDirty.set(false)
        val update = files.updateCatalog(IndexCatalog(adopted, ApiKeyRequests.keys()).commands(), ApiKeyRequests.types(), ApiKeyRequests.refusedTypes())
        if (update.previousUnreadable) ModLog.warn(ModLog.CATALOG_UNREADABLE)
        writeOrWarn(CatalogFile.FILE_NAME, update.write)
        val changes = update.changes
        if (logChanges && changes != null) {
            if (changes.appeared.isNotEmpty()) ModLog.info(ModLog.CATALOG_APPEARED, changes.appeared.size, changes.appeared.joinToString(", "))
            if (changes.disappeared.isNotEmpty()) ModLog.info(ModLog.CATALOG_DISAPPEARED, changes.disappeared.size, changes.disappeared.joinToString(", "))
        }
        return IndexCatalog(adopted, ApiKeyRequests.keys(), changes?.appeared.orEmpty())
    }

    /** Une clé d'API nouvelle : le catalogue se réécrit, regroupé sur le fil (D12). Avant l'adoption d'un index, c'est le démarrage qui l'écrira. */
    private fun onNewApiKey() {
        apiKeysDirty.set(true)
        if (!::executor.isInitialized || executor.isShutdown) return
        if (apiKeyWritePending.compareAndSet(false, true)) {
            try {
                executor.execute {
                    apiKeyWritePending.set(false)
                    writeApiKeys()
                }
            } catch (_: RejectedExecutionException) {
                // Le fil s'arrête entre le contrôle et le dépôt : l'arrêt écrit lui-même les clés en attente.
                apiKeyWritePending.set(false)
            }
        }
    }

    private fun writeApiKeys() {
        val adopted = index ?: return
        if (!apiKeysDirty.getAndSet(false)) return
        writeOrWarn(CatalogFile.FILE_NAME, files.updateCatalog(IndexCatalog(adopted, ApiKeyRequests.keys()).commands(), ApiKeyRequests.types(), ApiKeyRequests.refusedTypes()).write)
    }

    private fun writeOrWarn(file: String, write: GeneratedWrite) {
        if (write is GeneratedWrite.Failed) ModLog.error(ModLog.WRITE_FAILED, write.cause, file)
    }

    private fun <T> await(block: () -> T): T = executor.submit(Callable(block)).get()
}

/** Une publication faite : l'état publié et celui qu'il remplace, `null` à la première. */
class Published(val previous: EffectiveState?, val state: EffectiveState)

/** Ce qu'un travail du fil des fichiers rend à qui l'a demandé : son [result], ce qu'il a publié, et la dernière publication d'avant lui. */
class Outcome<T>(val result: T, val published: Published?, val before: LastPublication?)
