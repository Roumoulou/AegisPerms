// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import java.util.concurrent.ConcurrentHashMap

/**
 * La grammaire des clés (cahier des charges, 2.4) et leur table d'internement.
 *
 * Dans un nom de nœud, un point s'écrit `%2E` et un `%` s'écrit `%25`, en majuscules : ce sont les deux seuls échappements, si bien qu'une clé n'a qu'une
 * écriture. Les noms sont exacts et sensibles à la casse.
 */
object Keys {

    /** Le modid d'AegisPerms, espace de noms des clés de `/aegis`. */
    const val AEGISPERMS = "aegisperms"

    /** Le nom de la commande racine `/aegis`. */
    const val ADMINISTRATION_ROOT = "aegis"

    /** Le modid de vanilla. */
    const val MINECRAFT = "minecraft"

    private const val COMMAND = "command"
    private const val COMMAND_PREFIX = "command."

    /** Un espace de noms et un segment de clé d'API suivent la même règle, celle des identifiants du jeu : minuscules, chiffres, `_`, `-` et `.`. */
    private val IDENTIFIER_PART = Regex("[a-z0-9_.-]+")

    private val interned = ConcurrentHashMap<String, Key>()

    /** La clé `command`, qui couvre toutes les commandes, `/aegis` excepté (2.6). */
    val ALL_COMMANDS: CommandKey = intern(CommandKey(null, emptyList(), COMMAND))

    /** `aegisperms:command.aegis`, la clé de la commande `/aegis`. */
    val ADMINISTRATION: CommandKey = command(AEGISPERMS, listOf(ADMINISTRATION_ROOT))

    /** `minecraft:commands/entity_selectors`, l'atome des sélecteurs d'entités (2.8). */
    val ENTITY_SELECTORS: ApiKey = api(MINECRAFT, listOf("commands", "entity_selectors"))

    /** Lit une clé ; lève [MalformedKeyException] si elle est mal formée. */
    fun parse(input: String): Key {
        interned[input]?.let { return it }
        val colon = input.indexOf(':')
        if (colon <= 0) throw MalformedKeyException(input, KeyDefect.MISSING_NAMESPACE)
        val namespace = input.substring(0, colon)
        val rest = input.substring(colon + 1)
        return when {
            rest == COMMAND -> command(namespace, emptyList())
            rest.startsWith(COMMAND_PREFIX) -> command(namespace, rest.substring(COMMAND_PREFIX.length).split('.').map { unescape(input, it) })
            rest.isEmpty() -> throw MalformedKeyException(input, KeyDefect.EMPTY_PATH)
            else -> api(namespace, rest.split('/'))
        }
    }

    /** Lit une clé, ou rend `null` si elle est mal formée. */
    fun parseOrNull(input: String): Key? = try {
        parse(input)
    } catch (_: MalformedKeyException) {
        null
    }

    /** La clé de commande `<namespace>:command.<segments>`, les noms de nœuds donnés sans échappement ; sans segment, `<namespace>:command`. */
    fun command(namespace: String, segments: List<String>): CommandKey {
        val text = buildString {
            append(namespace).append(':').append(COMMAND)
            for (segment in segments) append('.').append(escape(segment))
        }
        if (!IDENTIFIER_PART.matches(namespace)) throw MalformedKeyException(text, KeyDefect.INVALID_NAMESPACE)
        interned[text]?.let { return it as CommandKey }
        for (segment in segments) {
            if (segment.isEmpty()) throw MalformedKeyException(text, KeyDefect.EMPTY_NODE_NAME)
            if (segment.any { it.isWhitespace() || it.isISOControl() }) throw MalformedKeyException(text, KeyDefect.INVALID_NODE_NAME)
        }
        return intern(CommandKey(namespace, segments.toList(), text))
    }

    /** La clé d'API `<namespace>:<segments>`, les segments joints par des barres obliques. */
    fun api(namespace: String, segments: List<String>): ApiKey {
        apiDefect(namespace, segments)?.let { throw MalformedKeyException(apiText(namespace, segments), it) }
        return internedApi(namespace, segments)
    }

    /**
     * La clé d'API d'une chaîne à points, telle que l'API de lucko la donne (6.4) : le premier segment devient l'espace de noms, les suivants le chemin, si
     * bien que `essentialcommands.home.set` se lit `essentialcommands:home/set`. Rend `null` quand la chaîne ne donne pas un identifiant valide.
     */
    fun fromDottedString(dotted: String): ApiKey? {
        val parts = dotted.split('.')
        if (parts.size < 2) return null
        val namespace = parts[0]
        val segments = parts.subList(1, parts.size).flatMap { it.split('/') }
        return if (apiDefect(namespace, segments) == null) internedApi(namespace, segments) else null
    }

    /** Échappe un nom de nœud pour l'écrire dans une clé. */
    fun escape(name: String): String {
        if (name.indexOf('%') < 0 && name.indexOf('.') < 0) return name
        return buildString(name.length + 4) {
            for (c in name) when (c) {
                '%' -> append("%25")
                '.' -> append("%2E")
                else -> append(c)
            }
        }
    }

    /** Vrai pour un chemin qui vaut `command` ou commence par `command.` : c'est celui d'une clé de commande, jamais d'une clé d'API. */
    internal fun isCommandPath(segments: List<String>): Boolean = (segments.size == 1 && segments[0] == COMMAND) || segments[0].startsWith(COMMAND_PREFIX)

    private fun unescape(input: String, part: String): String {
        if (part.indexOf('%') < 0) return part
        val out = StringBuilder(part.length)
        var i = 0
        while (i < part.length) {
            if (part[i] != '%') {
                out.append(part[i])
                i++
                continue
            }
            when {
                part.startsWith("%25", i) -> out.append('%')
                part.startsWith("%2E", i) -> out.append('.')
                else -> throw MalformedKeyException(input, KeyDefect.INVALID_ESCAPE)
            }
            i += 3
        }
        return out.toString()
    }

    private fun apiDefect(namespace: String, segments: List<String>): KeyDefect? = when {
        !IDENTIFIER_PART.matches(namespace) -> KeyDefect.INVALID_NAMESPACE
        segments.isEmpty() -> KeyDefect.EMPTY_PATH
        segments.any { it.isEmpty() } -> KeyDefect.EMPTY_SEGMENT
        segments.any { !IDENTIFIER_PART.matches(it) } -> KeyDefect.INVALID_SEGMENT
        isCommandPath(segments) -> KeyDefect.COMMAND_PATH
        else -> null
    }

    private fun apiText(namespace: String, segments: List<String>): String = namespace + ":" + segments.joinToString("/")

    /** Suppose [namespace] et [segments] sans défaut. */
    private fun internedApi(namespace: String, segments: List<String>): ApiKey {
        val text = apiText(namespace, segments)
        interned[text]?.let { return it as ApiKey }
        return intern(ApiKey(namespace, segments.toList(), text))
    }

    private fun <K : Key> intern(key: K): K {
        @Suppress("UNCHECKED_CAST")
        return (interned.putIfAbsent(key.text, key) ?: key) as K
    }
}
