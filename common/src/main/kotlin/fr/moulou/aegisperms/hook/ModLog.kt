// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.AegisPerms
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.Messages
import fr.moulou.aegisperms.text.TextKeys
import fr.moulou.aegisperms.text.Texts
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.Severity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Le journal du mod (cahier des charges, 5.9), sous le nom `aegisperms`, où Storify range aussi ses lignes. Une seule entrée : une clé de traduction
 * `aegisperms.log.<événement>` et ses arguments, résolus en texte dans la langue de la console (5.7). Un constat s'écrit sous sa propre clé,
 * `aegisperms.issue.<nature>`, précédé de son fichier, de son chemin et de sa ligne. Ce qui écrit la ligne reçoit aussi la clé et les arguments : c'est
 * ce que les tests comparent.
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

    /** Le serveur n'authentifie pas les joueurs (`online-mode=false`) : l'UUID d'un joueur se calcule depuis son pseudo (2.3, 5.4). Aucun argument. */
    const val OFFLINE_MODE = "offline_mode"

    /** Les fichiers que le premier démarrage vient d'écrire avec les valeurs livrées (4.2). Argument : leurs noms, séparés par des virgules. */
    const val FILES_CREATED = "files_created"

    /** Un fichier remplacé au démarrage par la copie `last-good` ou par les valeurs livrées (4.9). Arguments : le fichier, la source qui s'applique. */
    const val FALLBACK = "fallback"

    /** Une publication appliquée (5.9). Arguments : la génération, la source, le mode, la lecture seule, les groupes, les joueurs, les règles, les constats. */
    const val PUBLISHED = "published"

    /** Une publication refusée, l'état en vigueur reste (4.8). Arguments : la source, la génération qui reste, les constats. */
    const val REFUSED = "refused"

    /** Les clés de commande apparues au catalogue depuis le précédent (4.6). Arguments : leur nombre, les clés séparées par des virgules. */
    const val CATALOG_APPEARED = "catalog_appeared"

    /** Les clés de commande disparues du catalogue depuis le précédent (4.6). Arguments : leur nombre, les clés séparées par des virgules. */
    const val CATALOG_DISAPPEARED = "catalog_disappeared"

    /** Le catalogue précédent ne se lisait pas : il est régénéré, sans ses clés d'API ni leurs dates (4.6). Aucun argument. */
    const val CATALOG_UNREADABLE = "catalog_unreadable"

    /** Un fichier généré n'a pas pu s'écrire, et ce qui était en place reste. Argument : le fichier ; la trace. */
    const val WRITE_FAILED = "write_failed"

    /** L'autorité d'un joueur qui se connecte n'est pas l'enveloppe (6.6). Arguments : le pseudo, l'UUID, la classe trouvée. */
    const val FOREIGN_AUTHORITY = "foreign_authority"

    /** Le fil des fichiers ne s'est pas arrêté dans le délai. Argument : le délai, en secondes. */
    const val STOP_TIMEOUT = "stop_timeout"

    /** Un écart du mode observation (5.8). Arguments : le sujet, la clé, la décision du mod, la réponse de vanilla. */
    const val OBSERVE_DEVIATION = "observe_deviation"

    /** Les écarts de la minute écoulée que le plafond a tus (5.8). Argument : leur nombre. */
    const val OBSERVE_SUPPRESSED = "observe_suppressed"

    /** Un joueur du fichier revenu sous un nouveau pseudo, dont la forme lisible se réécrit (4.4). Arguments : l'ancienne forme, la nouvelle. */
    const val PLAYER_RENAMED = "player_renamed"

    /** La décision de la porte d'exécution pour une ligne lancée par une source jugée, au niveau `DEBUG` seulement (5.9). Arguments : le sujet, la commande racine, l'issue. */
    const val DECISION = "decision"

    private val logger: Logger = LoggerFactory.getLogger(AegisPerms.MOD_ID)

    /** Ce qui écrit les lignes : le logger du mod, ou ce qu'un test de l'étage 1 pose à sa place pour les lire. */
    internal var writer: LogWriter = LogWriter { line ->
        when (line.level) {
            LogLevel.DEBUG -> logger.debug(line.text)
            LogLevel.INFO -> logger.info(line.text)
            LogLevel.WARN -> logger.warn(line.text)
            LogLevel.ERROR -> if (line.cause == null) logger.error(line.text) else logger.error(line.text, line.cause)
        }
    }

    /** Vrai quand les lignes `DEBUG` s'écrivent : la décision ne se résout pas en texte pour rien sur le chemin chaud. Un test de l'étage 1 le pose. */
    internal var debugEnabled: () -> Boolean = { logger.isDebugEnabled }

    /** Les erreurs internes déjà dites pour la génération [reportedGeneration], par accroche et par clé. */
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val reportedGeneration = AtomicLong(-1)

    /** Une ligne `DEBUG`, écrite seulement quand ce niveau l'est (5.9). */
    fun debug(event: String, vararg arguments: Any?) {
        if (debugEnabled()) write(LogLevel.DEBUG, event, null, arguments.asList())
    }

    fun info(event: String, vararg arguments: Any?) = write(LogLevel.INFO, event, null, arguments.asList())

    fun warn(event: String, vararg arguments: Any?) = write(LogLevel.WARN, event, null, arguments.asList())

    fun error(event: String, vararg arguments: Any?) = write(LogLevel.ERROR, event, null, arguments.asList())

    fun error(event: String, cause: Throwable, vararg arguments: Any?) = write(LogLevel.ERROR, event, cause, arguments.asList())

    /**
     * Un constat (4.5), sous sa clé : `WARN` pour un avertissement, `ERROR` pour une erreur (5.9). Le texte commence par le fichier, le chemin et la ligne ;
     * la clé est suivie de ce même repère, puis des arguments du constat.
     */
    fun issue(issue: Issue) {
        val lang = language()
        val where = Messages.whereOf(issue)
        val arguments = issue.arguments.map {
            when (it) {
                is IssueArgument.Literal -> it.text
                is IssueArgument.Translatable -> Texts.text(it.translationKey, lang)
            }
        }
        val level = if (issue.severity == Severity.ERROR) LogLevel.ERROR else LogLevel.WARN
        val key = issue.kind.translationKey
        writer.write(LogLine(level, key, listOf(where) + arguments, "$where : ${Texts.text(key, lang, *arguments.toTypedArray())}", null))
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

    private fun write(level: LogLevel, event: String, cause: Throwable?, arguments: List<Any?>) {
        val key = TextKeys.LOG_PREFIX + event
        writer.write(LogLine(level, key, arguments, Texts.text(key, language(), *arguments.toTypedArray()), cause))
    }

    /** La langue du journal : celle de la console (5.7), sous le réglage `language` des réglages publiés. */
    private fun language(): Lang = Messages.consoleLanguage(Publisher.settings().language)
}

/** Le niveau d'une ligne du journal. */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/** Une ligne du journal : son niveau, sa clé et ses arguments, son texte résolu, et la cause quand il y en a une. */
class LogLine(val level: LogLevel, val key: String, val arguments: List<Any?>, val text: String, val cause: Throwable?) {

    /** La clé suivie de ses arguments, séparés par des espaces : la forme que les tests comparent. */
    fun keyAndArguments(): String = (listOf(key) + arguments).joinToString(" ")
}

/** Ce qui reçoit une ligne du journal. */
fun interface LogWriter {

    fun write(line: LogLine)
}
