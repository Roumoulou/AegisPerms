// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Une clé nomme ce qu'une règle autorise ou refuse (cahier des charges, 2.4). Deux familles, qui ne se couvrent jamais l'une l'autre : les clés de commande
 * ([CommandKey]), découpées aux points, et les clés d'API ([ApiKey]), découpées aux barres obliques.
 *
 * Les clés sont internées par [Keys] : deux clés égales sont le même objet, ce qui garde les caches de décision sans allocation.
 */
sealed class Key {

    /** La forme canonique, celle qui s'écrit dans une règle. */
    abstract val text: String

    /** Les clés dont une règle couvre celle-ci, de la plus précise à la plus générale, elle-même en tête. */
    abstract val coveringChain: List<Key>

    final override fun toString(): String = text

    final override fun equals(other: Any?): Boolean = this === other || (other is Key && other.text == text)

    final override fun hashCode(): Int = text.hashCode()
}

/**
 * Une clé de commande : `command` pour toutes les commandes, `<modid>:command` pour celles d'un mod, `<modid>:command.<nœud>.<nœud>` pour un nœud de l'arbre
 * Brigadier.
 */
class CommandKey internal constructor(
    /** Le modid du mod propriétaire, ou `null` pour la clé `command`. */
    val namespace: String?,
    /** Les noms des nœuds, de la commande racine au nœud visé, tels que Brigadier les porte, sans échappement ; vide pour une clé générale. */
    val segments: List<String>,
    override val text: String,
) : Key() {

    /** Vrai pour `aegisperms:command.aegis` et ses descendantes, que les clés générales ne couvrent pas (2.6). */
    val isAdministration: Boolean = namespace == Keys.AEGISPERMS && segments.isNotEmpty() && segments[0] == Keys.ADMINISTRATION_ROOT

    /** Vrai pour `command` et `<modid>:command`, qui nomment une famille entière. */
    val isGeneral: Boolean get() = segments.isEmpty()

    /** Vrai pour une clé qui nomme un nœud. */
    val isNode: Boolean get() = segments.isNotEmpty()

    /** La clé ancêtre la plus proche : `<modid>:command` pour une commande racine, `command` pour `<modid>:command`, rien pour `command`. */
    val directAncestor: CommandKey? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        when {
            namespace == null -> null
            segments.isEmpty() -> Keys.ALL_COMMANDS
            else -> Keys.command(namespace, segments.subList(0, segments.size - 1))
        }
    }

    /** Pour une clé d'administration, la chaîne s'arrête à `aegisperms:command.aegis`. */
    override val coveringChain: List<CommandKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        generateSequence(this) { it.directAncestor }.takeWhile { !(isAdministration && it.isGeneral) }.toList()
    }

    /** Les clés du chemin (2.7) : celles des nœuds, de la commande racine jusqu'à celui-ci ; vide pour une clé générale. */
    val pathKeys: List<CommandKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (namespace == null) emptyList() else (1..segments.size).map { Keys.command(namespace, segments.subList(0, it)) }
    }

    /** La clé du nœud de nom [name] placé sous celui-ci, ou la clé d'une commande racine pour `<modid>:command`. */
    fun descendant(name: String): CommandKey {
        val owner = checkNotNull(namespace) { "The key 'command' names no mod: no descendant can be built from it" }
        return Keys.command(owner, segments + name)
    }

    /** Vrai si une règle portant cette clé vaut pour [other] : même clé, ou clé ancêtre, l'exception de `/aegis` comprise. */
    fun covers(other: CommandKey): Boolean {
        if (other.isAdministration && isGeneral) return false
        if (namespace == null) return true
        if (namespace != other.namespace || segments.size > other.segments.size) return false
        for (i in segments.indices) if (segments[i] != other.segments[i]) return false
        return true
    }
}

/** Une clé d'API : un identifiant écrit en entier, `minecraft:commands/entity_selectors` ou `homes:home/limit`. */
class ApiKey internal constructor(
    val namespace: String,
    /** Les segments du chemin, découpés aux barres obliques ; jamais vide. */
    val segments: List<String>,
    override val text: String,
) : Key() {

    /**
     * La clé ancêtre la plus proche, du même espace de noms, ou rien : aucune règle ne couvre un espace de noms entier, et le chemin `command` est celui
     * d'une clé de commande.
     */
    val directAncestor: ApiKey? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val shorter = segments.subList(0, segments.size - 1)
        if (shorter.isEmpty() || Keys.isCommandPath(shorter)) null else Keys.api(namespace, shorter)
    }

    override val coveringChain: List<ApiKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        generateSequence(this) { it.directAncestor }.toList()
    }

    /** Vrai si une règle portant cette clé vaut pour [other] : même clé, ou clé ancêtre du même espace de noms. */
    fun covers(other: ApiKey): Boolean {
        if (namespace != other.namespace || segments.size > other.segments.size) return false
        for (i in segments.indices) if (segments[i] != other.segments[i]) return false
        return true
    }
}
