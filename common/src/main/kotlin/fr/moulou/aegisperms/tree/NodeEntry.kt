// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey

/**
 * Ce que le mod attache à un nœud de commande (cahier des charges, 7.4), retrouvé par identité dans le chemin chaud. Un nœud sans marque est celui d'un arbre
 * que le mod n'a pas indexé, l'arbre privé d'un autre mod par exemple : il n'a pas de clé, et vanilla décide (2.4).
 */
sealed interface NodeMark

/** La fiche d'un nœud indexé : la clé sur laquelle il se juge, et de quoi appliquer 2.7 et 2.8 sans rien recalculer. */
class NodeEntry(
    /**
     * La clé sur laquelle le nœud se juge : la sienne ; celle de sa cible pour un alias de vanilla ; celle de son plus proche ancêtre qui en a une quand son
     * nom, ou celui d'un nœud entre eux, ne fait pas une clé.
     */
    val key: CommandKey,
    /** La clé propre d'un alias de vanilla, `minecraft:command.tp`, sur laquelle une règle ne couvre rien (2.4) ; `null` pour tout autre nœud. */
    val ownKey: CommandKey?,
    /** L'exigence vanilla du nœud (2.8). */
    val requirement: CommandRequirement,
    /** Vrai pour une commande racine, un enfant de la racine de l'arbre. */
    val isRoot: Boolean,
    /** Vrai pour un nœud jugé sur la clé d'un ancêtre, faute d'un nom qui fasse une clé : aucune règle ne le vise seul. */
    val inheritsKey: Boolean,
    /** Vrai pour un nœud rattaché à plusieurs nœuds de l'arbre, qui porte la clé de son premier chemin (2.4). */
    val shared: Boolean,
    /**
     * Vrai pour une forme préfixée ou un alias de la table (3.4, 3.6) : un littéral racine que le mod a ajouté, jugé sur la clé de sa cible, dont il porte la
     * commande et partage les enfants.
     */
    val isForm: Boolean = false,
    /**
     * Vrai pour la forme nue d'une commande qui a une forme préfixée : sous la forme exigée, une source jugée ne la voit pas (3.5). `/aegis` et la commande
     * qu'un alias de même nom rend aux joueurs ne le sont jamais.
     */
    val hiddenUnderRequirePrefix: Boolean = false,
    /**
     * Les formes et les alias qui partagent les enfants de ce nœud : un enfant qu'on lui ajoute tard leur revient aussi (3.4), et l'arbre d'un joueur qui ne
     * reçoit pas ce nœud dirige vers l'une d'elles les redirections qui le visaient (3.7).
     */
    val forms: List<CommandNode<*>> = emptyList(),
) : NodeMark {

    /** Vrai pour un alias de vanilla, un littéral racine qui redirige vers une autre commande racine. */
    val isVanillaAlias: Boolean get() = ownKey != null
}

/** La marque de la racine d'un arbre indexé : elle mène à son index, pour qu'une commande racine enregistrée après lui reçoive sa fiche (3.8). */
class RootMark(val index: CommandIndex<*>) : NodeMark

/**
 * La marque d'un nœud dont l'arbre n'a pas pu être indexé : pour une source jugée, elle vaut un refus masqué, parce qu'un serveur fermé vaut mieux qu'un
 * serveur ouvert à vanilla par accident (7.5).
 */
data object Unindexed : NodeMark
