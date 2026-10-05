// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Settings
import java.util.IdentityHashMap

/** Une forme à ajouter à la racine de l'arbre, forme préfixée ou alias (cahier des charges, 3.4 et 3.6). */
class PlannedForm<S>(
    /** Le nom du littéral racine, tel qu'on le tape. */
    val name: String,
    /**
     * Le nœud dont la forme porte la commande et partage les enfants : sa cible, ramenée au bout de ses redirections simples. C'est sur sa clé que la forme
     * se juge.
     */
    val end: CommandNode<S>,
    /** Les nœuds dont la forme conjugue les exigences : ceux du chemin qui mène à [end], redirections traversées comprises. */
    val path: List<CommandNode<S>>,
)

/** Ce qu'il advient d'une entrée de la table des alias (3.6). Seul [Added] ajoute un littéral à l'arbre. */
sealed interface AliasOutcome {

    /** L'alias s'ajoute à la racine de l'arbre. */
    data object Added : AliasOutcome

    /**
     * Son nom est celui d'une commande racine qui mène au même nœud que sa cible : il ne crée rien, et rend sa forme nue à cette commande sous la forme
     * exigée.
     */
    data object RestoresBareForm : AliasOutcome

    /** Son nom est déjà pris à la racine par une autre commande ou par une forme préfixée : l'ajouter fusionnerait les deux nœuds (3.8). */
    data object NameTaken : AliasOutcome

    /** La commande racine de sa cible n'est pas dans l'arbre : son mod n'est pas là. */
    data object TargetMissing : AliasOutcome

    /** Sa cible n'est pas une suite de littéraux : [word] n'est pas un littéral sous ce qui le précède, une valeur d'argument par exemple. */
    data class TargetNotLiterals(val word: String) : AliasOutcome

    /** Sa cible s'écrit avec le préfixe qu'on tape au lieu du modid : [expected] est l'écriture attendue. */
    data class TypedPrefix(val expected: String) : AliasOutcome
}

/**
 * Ce que les réglages font ajouter à un arbre, décidé sans y toucher : [CommandForms.apply] l'exécute, et la validation le lit pour une configuration
 * candidate.
 */
class FormPlan<S>(
    /** La forme préfixée de chaque commande racine qui en reçoit une, sous le nom nu de la commande. */
    val prefixedForms: Map<String, PlannedForm<S>>,
    /** Les alias à ajouter, sous leur nom. */
    val aliases: Map<String, PlannedForm<S>>,
    /** Le sort de chaque entrée de la table des alias, sous son nom. */
    val aliasOutcomes: Map<String, AliasOutcome>,
    /** Les commandes racines dont la forme préfixée est refusée parce que son nom est déjà pris à la racine (3.8) : leur nom nu, vers ce nom refusé. */
    val takenFormNames: Map<String, String>,
    /** Les formes nues qu'une source jugée ne voit pas sous la forme exigée (3.5), dans un ensemble par identité. */
    val hiddenBareForms: Set<CommandNode<S>>,
)

/** Ce que [CommandForms.apply] a ajouté à un arbre. Ses tables sont par identité. */
class Forms<S>(
    /** Chaque forme ajoutée, vers le nœud sur la clé duquel elle se juge. */
    val targets: Map<CommandNode<S>, CommandNode<S>>,
    /** Chaque nœud dont des formes partagent les enfants, vers ces formes. */
    val sharing: Map<CommandNode<S>, List<CommandNode<S>>>,
    /** Les formes nues qu'une source jugée ne voit pas sous la forme exigée. */
    val hiddenBareForms: Set<CommandNode<S>>,
) {

    companion object {
        /** Aucune forme : l'arbre tel que ses mods l'ont enregistré. */
        fun <S> none(): Forms<S> = Forms(emptyMap(), emptyMap(), emptySet())
    }
}

/**
 * Les formes préfixées et les alias (cahier des charges, chapitre 3), ajoutés à l'arbre à chaque construction de `Commands`, avant l'index des clés.
 *
 * Une forme est un littéral racine qui porte la commande de sa cible, l'exigence de tout le chemin qui y mène, et partage ses enfants : les mêmes nœuds, pas
 * des copies. Une redirection ne conviendrait pas : l'arbre qu'un joueur reçoit ne garde une redirection que si sa cible y figure, et sous la forme exigée la
 * forme nue n'y figure pas. Seule garde une redirection la forme d'un nœud qui en a une que Brigadier ne laisse pas remplacer par des enfants : vers la racine
 * de l'arbre, ou avec un modificateur.
 */
object CommandForms {

    /**
     * Le préfixe qu'on tape pour le mod [modId] (3.3) : celui de la table [prefixes], son modid à défaut. Un préfixe qui est le modid d'un autre mod chargé
     * cède la place au modid, sans quoi deux mods se partageraient un même `x:`.
     */
    fun prefixOf(modId: String, prefixes: Map<String, String>, mods: ModLocator): String {
        val prefix = prefixes[modId] ?: return modId
        return if (prefix != modId && mods.isLoaded(prefix)) modId else prefix
    }

    /** Vrai pour `/aegis`, qui garde sa forme nue pour tous : l'administration se tape partout pareil (3.5). */
    fun isAdministration(rootName: String, owner: Owner): Boolean = owner.modId == Keys.AEGISPERMS && rootName == Keys.ADMINISTRATION_ROOT

    /**
     * Décide ce que [settings] fait ajouter à l'arbre de [root], dont [originals] sont les commandes racines telles que leurs mods les ont enregistrées et
     * [owners] leurs mods propriétaires. Rien n'est ajouté ici.
     */
    fun <S> plan(root: RootCommandNode<S>, originals: List<CommandNode<S>>, owners: Map<String, Owner>, settings: Settings, mods: ModLocator): FormPlan<S> {
        val originalsByName = originals.associateBy { it.name }
        val plannedNames = HashSet<String>()

        val prefixedForms = LinkedHashMap<String, PlannedForm<S>>()
        val takenFormNames = LinkedHashMap<String, String>()
        for (node in originals) {
            val form = prefixedFormOf(root, node, owners.getValue(node.name), settings, mods) ?: continue
            if (form.name in originalsByName || !plannedNames.add(form.name)) takenFormNames[node.name] = form.name else prefixedForms[node.name] = form
        }

        val aliases = LinkedHashMap<String, PlannedForm<S>>()
        val aliasOutcomes = LinkedHashMap<String, AliasOutcome>()
        val restored = identitySetOf<CommandNode<S>>()
        for ((name, targetText) in settings.aliases.entries.sortedWith(compareBy(CodePointOrder) { it.key })) {
            val target = when (val resolution = resolve(root, originalsByName, owners, settings, mods, targetText)) {
                is Resolution.Failed -> {
                    aliasOutcomes[name] = resolution.outcome
                    continue
                }
                is Resolution.Found -> resolution
            }
            val homonym = originalsByName[name]
            aliasOutcomes[name] = when {
                homonym != null && chainEnd(root, homonym, ArrayList()) === target.end -> {
                    restored += homonym
                    AliasOutcome.RestoresBareForm
                }
                homonym != null || !plannedNames.add(name) -> AliasOutcome.NameTaken
                else -> {
                    aliases[name] = PlannedForm(name, target.end, target.path)
                    AliasOutcome.Added
                }
            }
        }

        val hidden = identitySetOf<CommandNode<S>>()
        for (name in prefixedForms.keys) {
            val node = originalsByName.getValue(name)
            if (node !in restored && !isAdministration(name, owners.getValue(name))) hidden += node
        }
        return FormPlan(prefixedForms, aliases, aliasOutcomes, takenFormNames, hidden)
    }

    /**
     * La forme préfixée que reçoit [node], commande racine de mod propriétaire [owner] (3.4), ou `null` : les formes désactivées, une commande sans mod
     * propriétaire connu, ou un nom qui porte déjà un deux-points, n'en donnent pas. Que son nom soit libre à la racine reste à voir par qui l'ajoute.
     */
    fun <S> prefixedFormOf(root: RootCommandNode<S>, node: CommandNode<S>, owner: Owner, settings: Settings, mods: ModLocator): PlannedForm<S>? {
        if (!settings.namespaces.enabled || owner.source == OwnerSource.UNKNOWN || ':' in node.name || !Keys.isNodeName(node.name)) return null
        val path = arrayListOf(node)
        val end = chainEnd(root, node, path)
        return PlannedForm(prefixOf(owner.modId, settings.namespaces.prefixes, mods) + ":" + node.name, end, path)
    }

    /** Ajoute à l'arbre de [root] les formes et les alias de [plan]. */
    fun <S> apply(root: RootCommandNode<S>, plan: FormPlan<S>): Forms<S> {
        val targets = IdentityHashMap<CommandNode<S>, CommandNode<S>>()
        val sharing = IdentityHashMap<CommandNode<S>, MutableList<CommandNode<S>>>()
        for (form in plan.prefixedForms.values + plan.aliases.values) {
            val node = nodeOf(form)
            root.addChild(node)
            targets[node] = form.end
            if (node.redirect == null) sharing.getOrPut(form.end) { ArrayList() } += node
        }
        return Forms(targets, sharing, plan.hiddenBareForms)
    }

    /**
     * Le littéral d'une forme : la commande de [PlannedForm.end], l'exigence de tout son chemin, et ses enfants, partagés. Un nœud qui redirige ne peut pas
     * avoir d'enfants : quand [PlannedForm.end] redirige encore, vers la racine de l'arbre ou avec un modificateur, la forme reproduit sa redirection.
     */
    fun <S> nodeOf(form: PlannedForm<S>): LiteralCommandNode<S> {
        val end = form.end
        val requirement = Requirements.ofPath(form.path)
        if (end.redirect != null) return LiteralCommandNode(form.name, end.command, requirement, end.redirect, end.redirectModifier, end.isFork)
        val node = LiteralCommandNode(form.name, end.command, requirement, null, null, false)
        for (child in end.children) node.addChild(child)
        return node
    }

    /**
     * Le chemin de la cible d'un alias (3.6) : sa commande racine, écrite `<modid>:<forme nue>` ou par sa forme nue seule, puis des littéraux, chacun
     * cherché sous le bout des redirections simples de celui qui le précède.
     */
    private fun <S> resolve(root: RootCommandNode<S>, originalsByName: Map<String, CommandNode<S>>, owners: Map<String, Owner>, settings: Settings, mods: ModLocator, targetText: String): Resolution<S> {
        val words = targetText.split(' ').filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return Resolution.Failed(AliasOutcome.TargetMissing)
        val start = originalsByName[first] ?: run {
            val colon = first.indexOf(':')
            val bareName = first.substring(colon + 1)
            val candidate = originalsByName[bareName]?.takeIf { colon > 0 } ?: return Resolution.Failed(AliasOutcome.TargetMissing)
            val owner = owners.getValue(bareName)
            val head = first.substring(0, colon)
            when {
                head == owner.modId -> candidate
                owner.source != OwnerSource.UNKNOWN && head == prefixOf(owner.modId, settings.namespaces.prefixes, mods) -> {
                    return Resolution.Failed(AliasOutcome.TypedPrefix((listOf(owner.modId + ":" + bareName) + words.drop(1)).joinToString(" ")))
                }
                else -> return Resolution.Failed(AliasOutcome.TargetMissing)
            }
        }
        val path = arrayListOf(start)
        var end = chainEnd(root, start, path)
        for (word in words.drop(1)) {
            val literal = end.getChild(word) as? LiteralCommandNode<S> ?: return Resolution.Failed(AliasOutcome.TargetNotLiterals(word))
            path += literal
            end = chainEnd(root, literal, path)
        }
        return Resolution.Found(end, path)
    }

    /**
     * Le nœud au bout des redirections simples de [node], celles d'un nœud sans enfant ni modificateur : `tp` mène à `teleport`. La racine de l'arbre n'est
     * jamais ce bout, et une redirection à modificateur arrête la marche : ces redirections-là se gardent. Chaque nœud atteint rejoint [path].
     */
    private fun <S> chainEnd(root: RootCommandNode<S>, node: CommandNode<S>, path: MutableList<CommandNode<S>>): CommandNode<S> {
        val seen = identitySetOf<CommandNode<S>>()
        var current = node
        while (seen.add(current)) {
            val next = current.redirect ?: break
            if (next === root || current.children.isNotEmpty() || current.redirectModifier != null || current.isFork) break
            path += next
            current = next
        }
        return current
    }

    private sealed interface Resolution<S> {

        class Found<S>(val end: CommandNode<S>, val path: List<CommandNode<S>>) : Resolution<S>

        class Failed<S>(val outcome: AliasOutcome) : Resolution<S>
    }
}
