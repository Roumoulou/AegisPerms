// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.AegisPerms
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Le journal du mod (cahier des charges, 5.9), sous le nom `aegisperms`, où Storify range aussi ses lignes. Une seule entrée : une clé de traduction
 * `aegisperms.log.<événement>` et ses arguments, sans fragment de phrase hors clé, parce que les deux langues s'écrivent à la session 5 (5.7). Jusque-là, la
 * ligne écrite est la clé suivie de ses arguments.
 */
object ModLog {

    /** L'événement d'une erreur interne dans une accroche (7.5). Arguments : l'accroche, la clé, la génération. */
    const val INTERNAL_ERROR = "internal_error"

    /** L'index d'un `Commands` n'a pas pu se construire, et son arbre est fermé aux sources jugées (D3). Aucun argument, la trace. */
    const val INDEX_FAILED = "index_failed"

    /** Le mod est chargé. Aucun argument. */
    const val LOADED = "loaded"

    private const val KEY_PREFIX = "aegisperms.log."

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

    fun info(event: String, vararg arguments: Any?) = write(LogLevel.INFO, event, null, arguments)

    fun warn(event: String, vararg arguments: Any?) = write(LogLevel.WARN, event, null, arguments)

    fun error(event: String, vararg arguments: Any?) = write(LogLevel.ERROR, event, null, arguments)

    fun error(event: String, cause: Throwable, vararg arguments: Any?) = write(LogLevel.ERROR, event, cause, arguments)

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

    private fun write(level: LogLevel, event: String, cause: Throwable?, arguments: Array<out Any?>) {
        val line = buildString {
            append(KEY_PREFIX).append(event)
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
