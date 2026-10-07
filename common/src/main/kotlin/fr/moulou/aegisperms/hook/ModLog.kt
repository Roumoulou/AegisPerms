// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.AegisPerms
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.Severity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Le journal du mod (cahier des charges, 5.9), sous le nom `aegisperms`, où Storify range aussi ses lignes. Une seule entrée : une clé de traduction
 * `aegisperms.log.<événement>` et ses arguments, sans fragment de phrase hors clé, parce que les deux langues s'écrivent à la session 5 (5.7). Jusque-là, la
 * ligne écrite est la clé suivie de ses arguments. Un constat s'écrit sous sa propre clé, `aegisperms.issue.<nature>`.
 */
object ModLog {

    /** L'événement d'une erreur interne dans une accroche (7.5). Arguments : l'accroche, la clé, la génération. */
    const val INTERNAL_ERROR = "internal_error"

    /** L'index d'un `Commands` n'a pas pu se construire, et son arbre est fermé aux sources jugées (D3). Aucun argument, la trace. */
    const val INDEX_FAILED = "index_failed"

    /** L'index du `Commands` en service manque à son adoption : l'arbre est fermé, et le catalogue ne s'écrit pas (D3). Aucun argument. */
    const val INDEX_MISSING = "index_missing"

    /** Le mod est chargé. Aucun argument. */
    const val LOADED = "loaded"

    /** Les fichiers que le premier démarrage vient d'écrire avec les valeurs livrées (4.2). Arguments : leurs noms. */
    const val FILES_CREATED = "files_created"

    /** Un fichier remplacé au démarrage par la copie `last-good` ou par les valeurs livrées (4.9). Arguments : le fichier, la source qui s'applique. */
    const val FALLBACK = "fallback"

    /** Une publication appliquée (5.9). Arguments : la génération, la source, le mode, la lecture seule, les groupes, les joueurs, les règles, les constats. */
    const val PUBLISHED = "published"

    /** Une publication refusée, l'état en vigueur reste (4.8). Arguments : la source, la génération qui reste, les constats. */
    const val REFUSED = "refused"

    /** Les clés de commande apparues au catalogue depuis le précédent (4.6). Arguments : leur nombre, puis les clés. */
    const val CATALOG_APPEARED = "catalog_appeared"

    /** Les clés de commande disparues du catalogue depuis le précédent (4.6). Arguments : leur nombre, puis les clés. */
    const val CATALOG_DISAPPEARED = "catalog_disappeared"

    /** Le catalogue précédent ne se lisait pas : il est régénéré, sans ses clés d'API ni leurs dates (4.6). Aucun argument. */
    const val CATALOG_UNREADABLE = "catalog_unreadable"

    /** Un fichier généré n'a pas pu s'écrire, et ce qui était en place reste. Argument : le fichier ; la trace. */
    const val WRITE_FAILED = "write_failed"

    /** L'autorité d'un joueur qui se connecte n'est pas l'enveloppe (6.6, D20). Arguments : le pseudo, l'UUID, la classe trouvée. */
    const val FOREIGN_AUTHORITY = "foreign_authority"

    /** Le fil des fichiers ne s'est pas arrêté dans le délai. Argument : le délai, en secondes. */
    const val STOP_TIMEOUT = "stop_timeout"

    private const val KEY_PREFIX = "aegisperms.log."
    private const val ISSUE_PREFIX = "aegisperms.issue."

    private val logger: Logger = LoggerFactory.getLogger(AegisPerms.MOD_ID)

    /** Ce qui écrit les lignes : le logger du mod, ou ce qu'un test de l'étage 1 pose à sa place pour les compter. */
    internal var writer: LogWriter = LogWriter { level, line, cause ->
        when (level) {
            LogLevel.INFO -> logger.info(line)
            LogLevel.WARN -> logger.warn(line)
            LogLevel.ERROR -> if (cause == null) logger.error(line) else logger.error(line, cause)
        }
    }

    /** Les erreurs internes déjà dites pour la génération [reportedGeneration], par accroche et par clé. */
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val reportedGeneration = AtomicLong(-1)

    fun info(event: String, vararg arguments: Any?) = write(LogLevel.INFO, event, null, arguments.asList())

    fun warn(event: String, vararg arguments: Any?) = write(LogLevel.WARN, event, null, arguments.asList())

    fun error(event: String, vararg arguments: Any?) = write(LogLevel.ERROR, event, null, arguments.asList())

    fun error(event: String, cause: Throwable, vararg arguments: Any?) = write(LogLevel.ERROR, event, cause, arguments.asList())

    /** Un constat (4.5), sous sa clé : `WARN` pour un avertissement, `ERROR` pour une erreur (5.9). Arguments : le fichier, le chemin et la ligne, puis les siens. */
    fun issue(issue: Issue) {
        val where = buildString {
            append(issue.file.fileName)
            if (issue.path.isNotEmpty()) append(':').append(issue.path.joinToString("."))
            issue.line?.let { append(':').append(it) }
        }
        val arguments = issue.arguments.map {
            when (it) {
                is IssueArgument.Literal -> it.text
                is IssueArgument.Translatable -> it.translationKey
            }
        }
        writeKey(if (issue.severity == Severity.ERROR) LogLevel.ERROR else LogLevel.WARN, ISSUE_PREFIX + issue.kind.name.lowercase(), null, listOf(where) + arguments)
    }

    /**
     * Une erreur interne de l'accroche [hook] sur la clé [key] (7.5), dite une fois par accroche, par clé et par génération, avec sa trace : un nœud
     * défaillant n'inonde pas le journal à chaque envoi d'arbre, et une publication nouvelle redonne la parole.
     */
    fun once(hook: String, key: String, cause: Throwable) {
        val generation = Publisher.generation()
        val last = reportedGeneration.get()
        if (generation != last && reportedGeneration.compareAndSet(last, generation)) reported.clear()
        if (reported.add("$hook|$key")) error(INTERNAL_ERROR, cause, hook, key, generation)
    }

    /** Oublie les erreurs internes déjà dites. Réservé aux tests. */
    internal fun forgetReported() {
        reported.clear()
        reportedGeneration.set(-1)
    }

    private fun write(level: LogLevel, event: String, cause: Throwable?, arguments: List<Any?>) = writeKey(level, KEY_PREFIX + event, cause, arguments)

    private fun writeKey(level: LogLevel, key: String, cause: Throwable?, arguments: List<Any?>) {
        val line = buildString {
            append(key)
            for (argument in arguments) append(' ').append(argument)
        }
        writer.write(level, line, cause)
    }
}

/** Le niveau d'une ligne du journal. */
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}

/** Ce qui reçoit une ligne du journal. */
fun interface LogWriter {

    fun write(level: LogLevel, line: String, cause: Throwable?)
}
