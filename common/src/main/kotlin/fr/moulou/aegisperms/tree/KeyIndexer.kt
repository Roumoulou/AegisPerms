// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import java.util.IdentityHashMap

/** Les fiches des nœuds d'un arbre. */
class KeyIndex<S>(
    /** La fiche de chaque nœud, dans une table par identité. */
    val entries: Map<CommandNode<S>, NodeEntry>,
    /** Les nœuds jugés sur chaque clé, dans l'ordre de l'arbre : celui qui porte la clé d'abord, puis ceux qui la lui empruntent. */
    val nodesByKey: Map<CommandKey, List<CommandNode<S>>>,
    /** La clé propre de chaque alias de vanilla, vers celle de sa cible : `minecraft:command.tp` mène à `minecraft:command.teleport`. */
    val aliasKeys: Map<CommandKey, CommandKey>,
)

/**
 * Le calcul des clés d'un arbre de commandes (cahier des charges, 2.4), depuis sa racine, avec Brigadier et le cœur seuls.
 *
 * Une commande racine prend la clé `<modid>:command.<nom>`, et un nœud la clé de son parent prolongée de son nom. Un nœud rattaché à plusieurs nœuds garde la
 * clé de son premier chemin en profondeur, dans l'ordre d'enregistrement. Un alias de vanilla, littéral racine sans enfant qui redirige vers une autre
 * commande racine, se juge sur la clé de sa cible. Les autres redirections gardent leur clé, et leur cible la sienne : le parcours ne suit que les enfants.
 *
 * Un nom vide ou qui porte un blanc ne fait pas une clé : le nœud et tout ce qui descend de lui se jugent sur la clé du plus proche ancêtre qui en a une, et
 * une commande racine dans ce cas reste sans fiche.
 *
 * Une forme préfixée ou un alias de la table se juge sur la clé de sa cible, à toute profondeur, et les enfants qu'il partage avec elle gardent les clés du
 * chemin de la cible (3.4, 3.6) : le parcours n'entre pas dans les formes.
 */
object KeyIndexer {

    /**
     * Indexe l'arbre de [root]. [namespaceOf] rend le modid du mod propriétaire d'une commande racine, [requirementOf] l'exigence vanilla d'un nœud, et
     * [forms] dit les formes et les alias que le mod a ajoutés à l'arbre.
     */
    fun <S> index(root: RootCommandNode<S>, namespaceOf: (CommandNode<S>) -> String, requirementOf: (CommandNode<S>) -> CommandRequirement, forms: Forms<S> = Forms.none()): KeyIndex<S> {
        val visits = IdentityHashMap<CommandNode<S>, Visit>()
        val order = ArrayList<CommandNode<S>>()

        fun visit(node: CommandNode<S>, key: CommandKey, isRoot: Boolean, inheritsKey: Boolean) {
            val known = visits[node]
            if (known != null) {
                known.shared = true
                return
            }
            visits[node] = Visit(key, ownKey = null, isRoot, inheritsKey, isForm = false)
            order += node
            for (child in node.children) {
                if (inheritsKey || !Keys.isNodeName(child.name)) visit(child, key, isRoot = false, inheritsKey = true) else visit(child, key.descendant(child.name), isRoot = false, inheritsKey = false)
            }
        }

        val roots = root.children.filter { it !in forms.targets && Keys.isNodeName(it.name) }
        val aliasTargets = IdentityHashMap<CommandNode<S>, CommandNode<S>>()
        for (child in roots) vanillaAliasTarget(root, child)?.let { aliasTargets[child] = it }
        for (child in roots) {
            if (child !in aliasTargets) visit(child, Keys.command(namespaceOf(child), listOf(child.name)), isRoot = true, inheritsKey = false)
        }

        val aliasKeys = LinkedHashMap<CommandKey, CommandKey>()
        for (alias in roots) {
            if (alias !in aliasTargets) continue
            // Une cible restée sans fiche, faute d'un nom qui fasse une clé, laisse son alias sans fiche aussi.
            val target = visits[finalTarget(alias, aliasTargets)] ?: continue
            val ownKey = Keys.command(namespaceOf(alias), listOf(alias.name))
            visits[alias] = Visit(target.key, ownKey, isRoot = true, inheritsKey = false, isForm = false)
            order += alias
            aliasKeys[ownKey] = target.key
        }

        for (form in root.children) {
            val target = visits[forms.targets[form] ?: continue] ?: continue
            visits[form] = Visit(target.key, ownKey = null, isRoot = true, inheritsKey = false, isForm = true)
            order += form
        }

        val entries = IdentityHashMap<CommandNode<S>, NodeEntry>()
        val nodesByKey = LinkedHashMap<CommandKey, MutableList<CommandNode<S>>>()
        for (node in order) {
            val visit = visits.getValue(node)
            entries[node] = NodeEntry(visit.key, visit.ownKey, requirementOf(node), visit.isRoot, visit.inheritsKey, visit.shared, visit.isForm, node in forms.hiddenBareForms, forms.sharing[node].orEmpty())
            nodesByKey.getOrPut(visit.key) { ArrayList() } += node
        }
        return KeyIndex(entries, nodesByKey, aliasKeys)
    }

    /**
     * La cible d'un alias de vanilla, ou `null` : [child] est un littéral racine sans enfant qui redirige vers une autre commande racine. Un nœud qui redirige
     * et porte des enfants, ce qu'une fusion de Brigadier peut produire, garde sa propre clé.
     */
    fun <S> vanillaAliasTarget(root: RootCommandNode<S>, child: CommandNode<S>): CommandNode<S>? {
        if (child !is LiteralCommandNode<S> || child.children.isNotEmpty()) return null
        val target = child.redirect ?: return null
        return target.takeIf { it !== child && root.getChild(it.name) === it }
    }

    /** La commande au bout d'une chaîne d'alias. Une chaîne qui boucle rend l'un de ses alias, qui n'a pas de fiche. */
    private fun <S> finalTarget(alias: CommandNode<S>, aliasTargets: Map<CommandNode<S>, CommandNode<S>>): CommandNode<S> {
        val seen = identitySetOf<CommandNode<S>>()
        var current = alias
        while (seen.add(current)) current = aliasTargets[current] ?: return current
        return current
    }

    /** Ce que le parcours retient d'un nœud avant d'en faire une fiche : `shared` ne se sait qu'à la fin. */
    private class Visit(val key: CommandKey, val ownKey: CommandKey?, val isRoot: Boolean, val inheritsKey: Boolean, val isForm: Boolean) {
        var shared = false
    }
}
