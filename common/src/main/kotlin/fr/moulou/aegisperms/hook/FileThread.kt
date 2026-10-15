// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.CatalogFile
import fr.moulou.aegisperms.files.ConfigurationFiles
import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.files.ConfigurationSource
import fr.moulou.aegisperms.files.ConfigurationWrite
import fr.moulou.aegisperms.files.GeneratedWrite
import fr.moulou.aegisperms.files.PublicationResult
import fr.moulou.aegisperms.files.PublicationSource
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le fil des fichiers (cahier des charges, 7.4) : un exécuteur à un fil, démon, seul à appeler `ConfigurationFiles` et à compter les générations. Il
 * sérialise lectures, validations, écritures, catalogue et état ; le fil serveur ne l'attend qu'au démarrage, en deux temps (D9), et une publication lui
 * revient par la référence du `Publisher`.
 *
 * `reload` et `write` sont les points d'appel de `/aegis reload`, de la surveillance et des modifications par commande : prêts, sans appelant à cette
 * session.
 */
object FileThread {

    private const val THREAD_NAME = "AegisPerms files"
    private const val STOP_TIMEOUT_SECONDS = 5L
    private const val HOOK = "files"
    private const val LAST_GOOD = "last-good"

    private lateinit var files: ConfigurationFiles
    private lateinit var executor: ExecutorService

    // ── Le fil des fichiers seulement ──────────────────────────────────────────

    private var generation = 0L

    /** L'index adopté, et le catalogue qu'il donne à la validation ; `null` tant qu'aucun index n'est adopté, ou quand il a manqué. */
    private var index: CommandIndex<CommandSourceStack>? = null
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
        executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME).apply { isDaemon = true } }
        ApiKeyRequests.onNewKey = ::onNewApiKey
        await {
            generation = 0
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
     * rend le vrai verdict du démarrage, repli compris, publie la génération 1 de source `startup`, puis écrit `state.json` et `last-good` quand les deux
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
        } catch (error: Throwable) {
            ModLog.once(HOOK, "startup", error)
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
    fun reload(source: PublicationSource): CompletableFuture<ConfigurationRead> = CompletableFuture.supplyAsync({
        val read = files.reload(catalog)
        when (read) {
            is ConfigurationRead.Valid -> publish(read.configuration, read.report, source, readOnly = false, lastGood = true)
            is ConfigurationRead.Refused -> refuse(source, read.report)
        }
        read
    }, executor)

    /** La lecture des deux fichiers tels qu'ils sont, sans rien créer, retenir ni appliquer : `/aegis validate` (5.3). */
    fun check(): CompletableFuture<ConfigurationRead> = CompletableFuture.supplyAsync({ files.check(catalog) }, executor)

    /** Une écriture des droits (4.8), la seule que le mod fasse : écrit, relit, et publie une génération nouvelle, ou dit pourquoi rien ne s'est écrit. */
    fun write(permissions: Permissions, source: PublicationSource = PublicationSource.COMMAND): CompletableFuture<ConfigurationWrite> = CompletableFuture.supplyAsync({
        val write = files.write(permissions, catalog)
        when (write) {
            is ConfigurationWrite.Written -> publish(write.configuration, write.report, source, readOnly = false, lastGood = true)
            is ConfigurationWrite.Refused -> refuse(source, write.report)
            ConfigurationWrite.FileMoved, ConfigurationWrite.ReadOnly, is ConfigurationWrite.Failed -> {}
        }
        write
    }, executor)

    /** À `SERVER_STOPPING` : les clés d'API en attente s'écrivent, les stores se ferment, le fil s'arrête sous un délai borné. */
    fun stop() {
        if (!::executor.isInitialized || executor.isShutdown) return
        executor.execute {
            writeApiKeys()
            files.close()
        }
        executor.shutdown()
        if (!executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) ModLog.warn(ModLog.STOP_TIMEOUT, STOP_TIMEOUT_SECONDS)
    }

    /** Attend que le fil ait fini ce qu'il a en cours. Réservé aux tests. */
    internal fun drain() = await {}

    private fun publish(configuration: Configuration, report: ValidationReport, source: PublicationSource, readOnly: Boolean, lastGood: Boolean, fallback: Set<ConfigurationFile> = emptySet()): EffectiveState {
        generation++
        val state = EffectiveState(generation, configuration)
        val previous = Publisher.publish(state)
        Publisher.lastPublication = LastPublication(generation, source, Instant.now(), readOnly, fallback, report)
        val permissions = configuration.permissions
        val rules = permissions.groups.values.sumOf { it.rules.size } + permissions.players.values.sumOf { it.rules.size }
        ModLog.info(ModLog.PUBLISHED, generation, source.name.lowercase(), configuration.settings.mode.name.lowercase(), readOnly, permissions.groups.size, permissions.players.size, rules, report.issues.size)
        report.issues.forEach(ModLog::issue)
        writeOrWarn(StateFile.FILE_NAME, files.writeState(generation, source, PublicationResult.APPLIED, report))
        if (lastGood) writeOrWarn(LAST_GOOD, files.writeLastGood(configuration, generation))
        TreeResend.afterPublication(previous, state)
        return state
    }

    private fun refuse(source: PublicationSource, report: ValidationReport) {
        ModLog.error(ModLog.REFUSED, source.name.lowercase(), generation, report.issues.size)
        report.issues.forEach(ModLog::issue)
        writeOrWarn(StateFile.FILE_NAME, files.writeState(generation, source, PublicationResult.REFUSED, report))
    }

    /** Écrit le catalogue de [adopted] (4.6) et rend celui que la validation lira, avec les clés apparues depuis le catalogue précédent. */
    private fun writeCatalog(adopted: CommandIndex<CommandSourceStack>, logChanges: Boolean): IndexCatalog<CommandSourceStack> {
        val update = files.updateCatalog(IndexCatalog(adopted, ApiKeyRequests.keys()).commands(), ApiKeyRequests.types(), ApiKeyRequests.refusedTypes())
        apiKeysDirty.set(false)
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
            executor.execute {
                apiKeyWritePending.set(false)
                writeApiKeys()
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
