// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import fr.moulou.aegisperms.files.CommandEntry
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.NodeEntry
import java.util.IdentityHashMap

/**
 * Le manuel d'un arbre (cahier des charges, 5.10), bâti depuis son index et son catalogue : les commandes racines telles que leurs mods les ont enregistrées,
 * groupées par mod propriétaire ; les alias de vanilla et ceux de la table dans la colonne de leur cible, sans section à eux ; les formes préfixées de même ;
 * chaque nœud avec la clé sur laquelle il se juge. Un nœud rattaché à plusieurs nœuds ne se déroule que sous le chemin de sa clé, et se cite ailleurs.
 */
object ManualBuilder {

    fun <S> build(
        index: CommandIndex<S>,
        catalog: Map<String, CommandEntry>,
        descriptions: Descriptions,
        lang: Lang,
        modVersion: String,
        gameVersion: String,
        date: String,
        mode: Mode,
        requirePrefix: Boolean,
        descriptionsIssue: String? = null,
    ): Manual {
        val vanillaAliases = HashMap<CommandKey, MutableList<String>>()
        for (node in index.originals) {
            val entry = index.entryOf(node) ?: continue
            if (entry.isVanillaAlias) vanillaAliases.getOrPut(entry.key) { ArrayList() } += node.name
        }
        val tableAliases = IdentityHashMap<CommandNode<S>, MutableList<String>>()
        for ((name, alias) in index.plan.aliases) tableAliases.getOrPut(alias.end) { ArrayList() } += name

        val context = Context(index, catalog, descriptions, lang, tableAliases)
        val byMod = LinkedHashMap<String, MutableList<ManualCommand>>()
        for (node in index.originals) {
            val entry = index.entryOf(node) ?: continue
            if (entry.isVanillaAlias) continue
            val modId = index.owners[node.name]?.modId ?: continue
            val aliases = (vanillaAliases[entry.key].orEmpty() + tableAliases[node].orEmpty()).sortedWith(CodePointOrder)
            val command = ManualCommand(node.name, index.plan.prefixedForms[node.name]?.name, aliases, descriptions.of(entry.key, lang), context.nodeOf(node, entry, expand = true))
            byMod.getOrPut(modId) { ArrayList() } += command
        }
        val mods = byMod.entries
            .sortedWith(compareBy<Map.Entry<String, List<ManualCommand>>> { it.key != Keys.MINECRAFT }.thenBy(CodePointOrder) { it.key })
            .map { (id, commands) -> ManualMod(id, index.mods.nameOf(id), index.mods.versionOf(id), commands.sortedWith(compareBy(CodePointOrder) { it.name })) }
        return Manual(lang, modVersion, gameVersion, date, mode, requirePrefix, mods, descriptionsIssue)
    }

    private class Context<S>(
        private val index: CommandIndex<S>,
        private val catalog: Map<String, CommandEntry>,
        private val descriptions: Descriptions,
        private val lang: Lang,
        private val tableAliases: Map<CommandNode<S>, List<String>>,
    ) {

        /** Le nœud [node], de fiche [entry] ; déroulé avec ses enfants quand [expand], cité seul sinon. */
        fun nodeOf(node: CommandNode<S>, entry: NodeEntry, expand: Boolean): ManualNode {
            val key = entry.key
            // Les marques du catalogue sont celles de la clé : un nœud jugé sur la clé de son ancêtre n'en a pas à lui.
            val catalogEntry = if (entry.inheritsKey) null else catalog[key.text]
            val redirect = node.redirect?.let { target ->
                if (target === index.root) ManualRedirect.AnyCommand else index.entryOf(target)?.let { ManualRedirect.ToKey(it.key) } ?: ManualRedirect.ToName(target.name)
            }
            val children = if (!expand) {
                emptyList()
            } else {
                node.children
                    .sortedWith(compareBy<CommandNode<S>> { it !is LiteralCommandNode<S> }.thenBy(CodePointOrder) { it.name })
                    .mapNotNull { child ->
                        val childEntry = index.entryOf(child) ?: return@mapNotNull null
                        // Un nœud rattaché à plusieurs nœuds porte la clé de son premier chemin (2.4) : il ne se déroule que là.
                        val expected = if (entry.inheritsKey || !Keys.isNodeName(child.name)) key else key.descendant(child.name)
                        nodeOf(child, childEntry, expand = childEntry.key == expected)
                    }
            }
            return ManualNode(
                name = node.name,
                literal = node is LiteralCommandNode<S>,
                key = key,
                inheritsKey = entry.inheritsKey,
                executable = node.command != null,
                requirement = entry.requirement,
                moreDemanding = catalogEntry?.moreDemanding == true,
                sensitive = catalogEntry?.sensitive,
                reference = !expand,
                redirect = redirect,
                aliases = tableAliases[node].orEmpty().sortedWith(CodePointOrder),
                description = if (expand && !entry.inheritsKey) descriptions.of(key, lang) else null,
                children = children,
            )
        }
    }
}
