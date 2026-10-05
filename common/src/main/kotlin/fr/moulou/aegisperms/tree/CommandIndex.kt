// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys

/**
 * L'index d'un arbre de commandes (cahier des charges, 7.1 et 7.4) : la fiche de chaque nœud et le mod propriétaire de chaque commande racine. Il se calcule
 * une fois l'arbre construit, sans rien y écrire et sans rien publier : c'est qui l'adopte qui pose les fiches dans les nœuds.
 */
class CommandIndex<S> private constructor(
    /** La racine de l'arbre indexé. */
    val root: RootCommandNode<S>,
    /** Le mod propriétaire de chaque commande racine, sous son nom, dans l'ordre de l'arbre. */
    val owners: Map<String, Owner>,
    private val keys: KeyIndex<S>,
) {

    companion object {

        /**
         * Indexe l'arbre de [root] : le mod propriétaire de chaque commande racine, par [mods] et par les rattachements [assignedOwners] des réglages, puis
         * les clés, puis l'exigence vanilla de chaque nœud, lue par [requirements].
         */
        fun <S> build(root: RootCommandNode<S>, assignedOwners: Map<String, String>, mods: ModLocator, requirements: RequirementReader): CommandIndex<S> {
            val resolver = OwnerResolver(mods, assignedOwners)
            val owners = LinkedHashMap<String, Owner>()
            for (child in root.children) owners[child.name] = resolver.ownerOf(child)
            val keys = KeyIndexer.index(root, { owners.getValue(it.name).modId }, { requirements.read(it.requirement) })
            return CommandIndex(root, owners, keys)
        }

        /**
         * Les fiches de [child], ajouté après l'index sous un nœud de fiche [parent], et de son sous-arbre (7.5, les nœuds tardifs) : chacun prend la clé de
         * son parent prolongée de son nom. [isMarked] dit les nœuds qui portent déjà une marque : ils la gardent, eux et ce qui descend d'eux.
         */
        fun <S> lateEntries(parent: NodeEntry, child: CommandNode<S>, requirements: RequirementReader, isMarked: (CommandNode<S>) -> Boolean): List<Pair<CommandNode<S>, NodeEntry>> {
            val late = ArrayList<Pair<CommandNode<S>, NodeEntry>>()
            val seen = identitySetOf<CommandNode<S>>()

            fun enter(node: CommandNode<S>, parentKey: CommandKey, parentInheritsKey: Boolean) {
                if (isMarked(node) || !seen.add(node)) return
                val inheritsKey = parentInheritsKey || !Keys.isNodeName(node.name)
                val key = if (inheritsKey) parentKey else parentKey.descendant(node.name)
                late += node to NodeEntry(key, ownKey = null, requirements.read(node.requirement), isRoot = false, inheritsKey, shared = false)
                for (grandchild in node.children) enter(grandchild, key, inheritsKey)
            }

            // Brigadier quitte un alias de vanilla par sa redirection, sans lire ses enfants : celui qu'on lui ajoute prolonge la clé propre de l'alias.
            enter(child, parent.ownKey ?: parent.key, parent.inheritsKey)
            return late
        }
    }

    /** La marque de la racine, qui mène à cet index. */
    val rootMark = RootMark(this)

    /** Les clés sur lesquelles un nœud se juge, dans l'ordre de l'arbre. */
    val commandKeys: Set<CommandKey> get() = keys.nodesByKey.keys

    /** La clé propre de chaque alias de vanilla, vers celle de sa cible. */
    val aliasKeys: Map<CommandKey, CommandKey> get() = keys.aliasKeys

    /** La fiche de chaque nœud, dans une table par identité. */
    val entries: Map<CommandNode<S>, NodeEntry> get() = keys.entries

    /** La marque de [node] : celle de l'index pour la racine, sa fiche pour un nœud indexé, rien pour un nœud que l'index ne connaît pas. */
    fun markOf(node: CommandNode<S>): NodeMark? = if (node === root) rootMark else keys.entries[node]

    fun entryOf(node: CommandNode<S>): NodeEntry? = keys.entries[node]

    /** Les nœuds jugés sur [key] : celui qui porte la clé d'abord, puis ceux qui la lui empruntent. */
    fun nodesOf(key: CommandKey): List<CommandNode<S>> = keys.nodesByKey[key].orEmpty()

    /** Le mod propriétaire de la commande racine de nom [rootName], ou `null` quand l'arbre ne l'a pas. */
    fun ownerOf(rootName: String): Owner? = owners[rootName]
}
