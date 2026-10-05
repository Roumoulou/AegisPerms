// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import fr.moulou.aegisperms.files.CommandEntry
import fr.moulou.aegisperms.files.NodeKind
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.SensitiveKeys
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.aegisperms.validate.DemandingDescendant
import fr.moulou.aegisperms.validate.Issue

/**
 * Le catalogue d'un index (cahier des charges, 4.6) : les entrées de `catalog.json` pour ses clés de commande, et ce que la validation demande au catalogue.
 *
 * [apiKeys] sont les clés d'API déjà demandées, celles que le fichier gardait et celles de ce démarrage. [appeared] sont les clés de commande apparues depuis
 * le catalogue précédent, telles que son écriture les a dites : sans catalogue précédent, il n'y en a pas.
 */
class IndexCatalog<S>(private val index: CommandIndex<S>, private val apiKeys: Set<ApiKey> = emptySet(), appeared: Collection<String> = emptyList()) : Catalog {

    companion object {
        /** Au-delà de cette part du nom en fautes de frappe, deux noms ne sont plus voisins : la clé proposée induirait en erreur. */
        private const val NEAREST_NAME_SHARE = 3
    }

    private val namespaces: Set<String> = index.commandKeys.mapNotNullTo(HashSet()) { it.namespace }

    /** Les clés de l'index sous leur clé ancêtre la plus proche : les commandes racines d'un mod sous sa clé générale. */
    private val descendants: Map<CommandKey, List<CommandKey>> = index.commandKeys.groupBy { checkNotNull(it.directAncestor) }

    private val appearedKeys: List<CommandKey> = appeared.mapNotNull { Keys.parseOrNull(it) as? CommandKey }

    /** Les entrées de `catalog.json` : une par clé sur laquelle un nœud se juge, et une par clé propre d'alias de vanilla, qui nomme celle de sa cible. */
    fun commands(): Map<String, CommandEntry> {
        val aliasesByKey = HashMap<CommandKey, MutableList<String>>()
        for ((name, alias) in index.plan.aliases) index.entryOf(alias.end)?.let { aliasesByKey.getOrPut(it.key) { ArrayList() } += name }

        val commands = LinkedHashMap<String, CommandEntry>()
        for (key in index.commandKeys) {
            val node = bearerOf(key)
            val entry = checkNotNull(index.entryOf(node))
            commands[key.text] = entryOf(node, entry).copy(
                moreDemanding = isMoreDemanding(key),
                sensitive = SensitiveKeys.reasonFor(key)?.let { CommandEntry.sensitiveName(it) },
                aliases = aliasesByKey[key].orEmpty().sortedWith(CodePointOrder),
            )
        }
        for ((node, entry) in index.entries) {
            val ownKey = entry.ownKey ?: continue
            commands[ownKey.text] = entryOf(node, entry).copy(aliasOf = entry.key.text)
        }
        return commands
    }

    override fun hasCommandKey(key: CommandKey): Boolean = key in index.commandKeys

    override fun hasNamespace(namespace: String): Boolean = namespace in namespaces

    /**
     * La clé voisine de [key] : là où son chemin quitte le catalogue, le nœud de même parent dont le nom lui ressemble le plus, à quelques fautes de frappe
     * près. Rien quand aucun nom n'est assez proche, ou quand son espace de noms n'a aucune commande.
     */
    override fun nearestCommandKey(key: CommandKey): CommandKey? {
        val namespace = key.namespace ?: return null
        var known = Keys.command(namespace, emptyList())
        for (name in key.segments) {
            val next = known.descendant(name)
            if (next in index.commandKeys) {
                known = next
                continue
            }
            return descendants[known].orEmpty()
                .map { it to distance(name, it.segments.last()) }
                .filter { (_, edits) -> edits <= maxOf(1, name.length / NEAREST_NAME_SHARE) }
                .minByOrNull { (_, edits) -> edits }
                ?.first
        }
        return null
    }

    /** Les clés apparues depuis le catalogue précédent sous la même clé ancêtre que [key] : ce qu'un argument renommé laisse à sa place. */
    override fun appearedNear(key: CommandKey): List<CommandKey> = appearedKeys.filter { it !== key && it.directAncestor === key.directAncestor }

    override fun aliasTarget(key: CommandKey): CommandKey? = index.aliasKeys[key]

    override fun demandingDescendants(key: CommandKey): List<DemandingDescendant> = index.commandKeys.mapNotNull { candidate ->
        val level = levelOf(candidate)
        if (level != null && candidate !== key && key.covers(candidate) && isMoreDemanding(candidate)) DemandingDescendant(candidate, level) else null
    }

    /** Vanilla ne demande au serveur que l'atome des sélecteurs (6.2) : c'est la seule clé d'API connue sans avoir été demandée. */
    override fun hasApiKey(key: ApiKey): Boolean = key === Keys.ENTITY_SELECTORS || key in apiKeys

    override fun treeIssues(configuration: Configuration): List<Issue> = TreeValidator.issues(index, configuration)

    /** Ce qu'un nœud et sa fiche disent seuls d'une entrée. Le mod propriétaire et la forme préfixée sont ceux d'une commande racine. */
    private fun entryOf(node: CommandNode<S>, entry: NodeEntry): CommandEntry {
        val owner = index.owners[node.name]?.takeIf { entry.isRoot && it.source != OwnerSource.UNKNOWN }
        return CommandEntry(
            kind = if (node is LiteralCommandNode<S>) NodeKind.LITERAL else NodeKind.ARGUMENT,
            executable = node.command != null,
            requirement = entry.requirement,
            shared = entry.shared,
            owner = owner?.modId,
            coOwners = owner?.coOwners.orEmpty(),
            prefixedForm = index.plan.prefixedForms[node.name]?.name?.takeIf { entry.isRoot },
        )
    }

    /** Le nœud qui porte [key] : le premier de ceux qui se jugent sur elle, devant ceux qui la lui empruntent. */
    private fun bearerOf(key: CommandKey): CommandNode<S> = index.nodesOf(key).first()

    /** Le niveau d'op que vanilla exige pour le nœud de [key], ou `null` quand son exigence n'est pas un niveau. */
    private fun levelOf(key: CommandKey): Int? = index.entryOf(bearerOf(key))?.requirement?.opLevel?.level

    /**
     * Vrai si le nœud de [key] exige un niveau d'op plus haut que son plus proche ancêtre qui en exige un (2.8) ; sans ancêtre de ce genre, il suffit qu'il en
     * exige un et ne soit pas une commande racine.
     */
    private fun isMoreDemanding(key: CommandKey): Boolean {
        val level = levelOf(key) ?: return false
        var ancestor = key.directAncestor
        while (ancestor != null && ancestor.isNode) {
            if (ancestor in index.commandKeys) levelOf(ancestor)?.let { return level > it }
            ancestor = ancestor.directAncestor
        }
        return key.segments.size > 1
    }

    /** La distance d'édition entre deux noms : le nombre de lettres à ajouter, retirer ou changer pour passer de l'un à l'autre. */
    private fun distance(from: String, to: String): Int {
        var previous = IntArray(to.length + 1) { it }
        for (i in 1..from.length) {
            val current = IntArray(to.length + 1)
            current[0] = i
            for (j in 1..to.length) current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (from[i - 1] == to[j - 1]) 0 else 1)
            previous = current
        }
        return previous[to.length]
    }
}
