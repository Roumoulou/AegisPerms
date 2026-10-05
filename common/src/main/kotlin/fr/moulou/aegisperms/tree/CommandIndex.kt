// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Settings

/**
 * L'index d'un arbre de commandes (cahier des charges, 7.1 et 7.4) : la fiche de chaque nœud, le mod propriétaire de chaque commande racine, les formes
 * préfixées et les alias que les réglages y ont ajoutés. Il se calcule une fois l'arbre construit, sans poser aucune fiche et sans rien publier : c'est qui
 * l'adopte qui pose les fiches dans les nœuds.
 */
class CommandIndex<S> private constructor(
    /** La racine de l'arbre indexé. */
    val root: RootCommandNode<S>,
    /** Les commandes racines telles que leurs mods les ont enregistrées, avant les formes et les alias, dans l'ordre de l'arbre. */
    val originals: List<CommandNode<S>>,
    /** Le mod propriétaire de chacune de ces commandes racines, sous son nom. */
    val owners: Map<String, Owner>,
    /** Ce que les réglages ont fait ajouter à l'arbre, avec le sort de chaque alias. */
    val plan: FormPlan<S>,
    private val keys: KeyIndex<S>,
    private val settings: Settings,
    private val mods: ModLocator,
    private val requirements: RequirementReader,
) {

    companion object {

        /**
         * Indexe l'arbre de [root] : le mod propriétaire de chaque commande racine, par [mods] et par les rattachements de [settings] ; puis les formes
         * préfixées et les alias de [settings], ajoutés à l'arbre avant les clés (3.8) ; puis les clés, et l'exigence vanilla de chaque nœud, lue par
         * [requirements].
         */
        fun <S> build(root: RootCommandNode<S>, settings: Settings, mods: ModLocator, requirements: RequirementReader): CommandIndex<S> {
            val resolver = OwnerResolver(mods, settings.namespaces.owners)
            val originals = root.children.toList()
            val owners = LinkedHashMap<String, Owner>()
            for (node in originals) owners[node.name] = resolver.ownerOf(node)
            val plan = CommandForms.plan(root, originals, owners, settings, mods)
            val forms = CommandForms.apply(root, plan)
            val keys = KeyIndexer.index(root, { owners.getValue(it.name).modId }, { requirements.read(it.requirement) }, forms)
            return CommandIndex(root, originals, owners, plan, keys, settings, mods, requirements)
        }

        /**
         * Les fiches de [child], ajouté après l'index sous un nœud de fiche [parent], et de son sous-arbre (7.5, les nœuds tardifs) : chacun prend la clé de
         * son parent prolongée de son nom. [isMarked] dit les nœuds qui portent déjà une marque : ils la gardent, eux et ce qui descend d'eux. Les formes qui
         * partagent les enfants de [parent] sont dans [NodeEntry.forms] : c'est à qui pose ces fiches de leur ajouter [child] (3.4).
         */
        fun <S> lateEntries(parent: NodeEntry, child: CommandNode<S>, requirements: RequirementReader, isMarked: (CommandNode<S>) -> Boolean): List<Pair<CommandNode<S>, NodeEntry>> {
            val late = ArrayList<Pair<CommandNode<S>, NodeEntry>>()
            // Brigadier quitte un alias de vanilla par sa redirection, sans lire ses enfants : celui qu'on lui ajoute prolonge la clé propre de l'alias.
            enter(child, parent.ownKey ?: parent.key, parent.inheritsKey, requirements, isMarked, identitySetOf(), late)
            return late
        }

        private fun <S> enter(
            node: CommandNode<S>,
            parentKey: CommandKey,
            parentInheritsKey: Boolean,
            requirements: RequirementReader,
            isMarked: (CommandNode<S>) -> Boolean,
            seen: MutableSet<CommandNode<S>>,
            into: MutableList<Pair<CommandNode<S>, NodeEntry>>,
        ) {
            if (isMarked(node) || !seen.add(node)) return
            val inheritsKey = parentInheritsKey || !Keys.isNodeName(node.name)
            val key = if (inheritsKey) parentKey else parentKey.descendant(node.name)
            into += node to NodeEntry(key, ownKey = null, requirements.read(node.requirement), isRoot = false, inheritsKey, shared = false)
            for (child in node.children) enter(child, key, inheritsKey, requirements, isMarked, seen, into)
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

    /** Le mod propriétaire de la commande racine de nom [rootName], ou `null` quand l'arbre ne l'avait pas à sa construction. */
    fun ownerOf(rootName: String): Owner? = owners[rootName]

    /**
     * Ce que reçoit [node], commande racine enregistrée après l'index (3.8) : son mod propriétaire, sa fiche et celles de son sous-arbre, et sa forme
     * préfixée. `null` pour une racine dont le nom ne fait pas une clé. [markOf] rend la marque qu'un nœud de l'arbre porte déjà.
     *
     * La racine tardive n'entre ni dans [owners] ni dans le catalogue : ils sont ceux de la construction de l'arbre, et la suivante la comptera.
     */
    fun lateRoot(node: CommandNode<S>, markOf: (CommandNode<S>) -> NodeMark?): LateRoot<S>? {
        if (!Keys.isNodeName(node.name)) return null
        val owner = OwnerResolver(mods, settings.namespaces.owners).ownerOf(node)
        val ownKey = Keys.command(owner.modId, listOf(node.name))
        val aliasTarget = KeyIndexer.vanillaAliasTarget(root, node)?.let { markOf(it) as? NodeEntry }
        val key = aliasTarget?.key ?: ownKey

        // Un nom déjà pris à la racine refuse la forme : l'ajouter fusionnerait les deux nœuds (3.8).
        val planned = CommandForms.prefixedFormOf(root, node, owner, settings, mods)?.takeIf { root.getChild(it.name) == null }
        val formKey = when {
            planned == null -> null
            planned.end === node -> key
            else -> (markOf(planned.end) as? NodeEntry)?.key
        }
        val form = if (planned != null && formKey != null) CommandForms.nodeOf(planned) else null

        val entries = ArrayList<Pair<CommandNode<S>, NodeEntry>>()
        entries += node to NodeEntry(
            key,
            ownKey.takeIf { aliasTarget != null },
            requirements.read(node.requirement),
            isRoot = true,
            inheritsKey = false,
            shared = false,
            isForm = false,
            hiddenUnderRequirePrefix = form != null && !CommandForms.isAdministration(node.name, owner),
            forms = if (form != null && form.redirect == null && planned?.end === node) listOf(form) else emptyList(),
        )
        val seen = identitySetOf<CommandNode<S>>().apply { add(node) }
        for (child in node.children) enter(child, key, parentInheritsKey = false, requirements, { markOf(it) != null }, seen, entries)
        if (form != null && formKey != null) {
            entries += form to NodeEntry(formKey, ownKey = null, requirements.read(form.requirement), isRoot = true, inheritsKey = false, shared = false, isForm = true)
        }
        return LateRoot(owner, entries, form)
    }
}

/** Ce que reçoit une commande racine enregistrée après l'index. */
class LateRoot<S>(
    val owner: Owner,
    /** Les fiches à poser : celle de la racine, celles de son sous-arbre, et celle de sa forme préfixée quand elle en reçoit une. */
    val entries: List<Pair<CommandNode<S>, NodeEntry>>,
    /** La forme préfixée à ajouter à la racine de l'arbre, une fois les fiches posées ; `null` quand la commande n'en reçoit pas. */
    val form: LiteralCommandNode<S>?,
)
