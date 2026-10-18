// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.text.Lang

/**
 * Un manuel des commandes (cahier des charges, 5.10) : tout ce que le rendu écrit, bâti depuis l'index d'un arbre par [ManualBuilder], sans rien du jeu, pour
 * que l'étage 0 l'éprouve.
 */
class Manual(
    val lang: Lang,
    val modVersion: String,
    val gameVersion: String,
    /** La date du jour, en ISO 8601. */
    val date: String,
    val mode: Mode,
    val requirePrefix: Boolean,
    /** Les mods, `minecraft` d'abord puis les autres par ordre alphabétique, chacun avec ses commandes racines. */
    val mods: List<ManualMod>,
    /** Ce qui a empêché de lire le fichier des descriptions de l'administrateur, ou `null`. */
    val descriptionsIssue: String? = null,
) {
    val commandCount: Int get() = mods.sumOf { it.commands.size }

    val nodeCount: Int get() = mods.sumOf { it.nodeCount }
}

/** Un mod et ses commandes racines, par ordre alphabétique de leur nom. */
class ManualMod(
    val id: String,
    /** Le nom que le loader affiche, ou `null` hors du loader et pour l'espace de noms `unknown`. */
    val name: String?,
    val version: String?,
    val commands: List<ManualCommand>,
) {
    val nodeCount: Int get() = commands.sumOf { it.root.count() }
}

/** Une commande racine : sa forme nue, sa forme préfixée, ses alias, et son arbre. */
class ManualCommand(
    /** La forme nue, telle que le mod l'a enregistrée. */
    val name: String,
    /** La forme préfixée, telle qu'on la tape et sans la barre oblique, ou `null`. */
    val prefixedForm: String?,
    /** Les alias qui mènent à cette racine, de vanilla et de la table, tels qu'on les tape. */
    val aliases: List<String>,
    val description: String?,
    val root: ManualNode,
)

/** Un nœud de l'arbre d'une commande, avec sa clé et ce qui compte pour une règle. */
class ManualNode(
    val name: String,
    /** Vrai pour un littéral, faux pour un argument. */
    val literal: Boolean,
    /** La clé sur laquelle le nœud se juge. */
    val key: CommandKey,
    /** Vrai pour un nœud jugé sur la clé de son ancêtre, faute d'un nom qui fasse une clé (2.4). */
    val inheritsKey: Boolean,
    val executable: Boolean,
    val requirement: CommandRequirement,
    /** Vrai pour un nœud plus exigeant que son ancêtre (2.8). */
    val moreDemanding: Boolean,
    /** Ce qui rend la clé sensible (2.9), par le nom de la raison, ou `null`. */
    val sensitive: String?,
    /** Vrai pour un nœud rattaché à plusieurs nœuds, atteint ici par un autre chemin que celui de sa clé : il se cite sans se dérouler (2.4). */
    val reference: Boolean,
    val redirect: ManualRedirect?,
    /** Les alias de la table qui mènent à ce nœud, tels qu'on les tape. */
    val aliases: List<String>,
    val description: String?,
    val children: List<ManualNode>,
) {
    /** Le nombre de lignes de l'arbre : ce nœud et ce qui se déroule sous lui. */
    fun count(): Int = 1 + children.sumOf { it.count() }
}

/** Ce vers quoi un nœud redirige. */
sealed interface ManualRedirect {

    /** Vers la racine de l'arbre : `run`, sous `execute`, mène à toute commande. */
    data object AnyCommand : ManualRedirect

    /** Vers un nœud indexé, par sa clé. */
    data class ToKey(val key: CommandKey) : ManualRedirect

    /** Vers un nœud sans fiche, par son nom. */
    data class ToName(val name: String) : ManualRedirect
}
