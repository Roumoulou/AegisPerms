// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.resolve.NodeJudgment
import fr.moulou.aegisperms.resolve.NodeTrace
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.Subject
import fr.moulou.aegisperms.tree.NodeEntry
import fr.moulou.aegisperms.tree.NodeMark
import fr.moulou.aegisperms.tree.RootMark
import fr.moulou.aegisperms.tree.Unindexed

/**
 * Ce que `/aegis why` raconte d'une ligne (cahier des charges, 5.5), sans Minecraft : le chemin analysé, nœud par nœud, jugé par la fonction de décision,
 * l'exigence vanilla de chaque nœud évaluée par qui tient la sonde. L'étage 0 l'éprouve sur des arbres synthétiques ; `Explain` l'habille.
 */
internal object Explanations {

    /** Un nœud du chemin analysé : tapé, ou cible d'une redirection, que la forme exigée ne vise pas (3.5). */
    class Step(val node: CommandNode<*>, val typed: Boolean)

    /** Ce que donne l'exigence vanilla d'un nœud sur la sonde : elle passe, elle refuse, ou elle ne s'évalue pas, la sonde étant hors ligne (E6). */
    enum class Evaluation {
        PASSES,
        FAILS,
        NOT_EVALUATED,
    }

    /** Le statut d'un nœud (5.5). */
    enum class NodeStatus {
        SEEN,
        HIDDEN,
        VISIBLE_REFUSAL,
        SEEN_UNDER_VISIBLE_REFUSAL,
        UNSEEN_MOD,
        UNSEEN_VANILLA,
        OUT_OF_CATALOG,
        NOT_EVALUATED,
        INEFFECTIVE,
        BARE_FORM,
    }

    /** Le verdict d'une ligne (5.5). */
    enum class Verdict {
        PERMITTED,
        REFUSED_HIDDEN,
        REFUSED_VISIBLE,
        REFUSED_BY_VANILLA,
        INCOMPLETE,
    }

    /** Une ligne du récit : le nœud, son statut, sa décision, et sa trace quand il a une fiche. */
    class Row(val name: String, val status: NodeStatus, val decision: Decision?, val trace: NodeTrace?, val entry: NodeEntry?) {

        /** Vrai quand la règle qui l'emporte porte une clé ancêtre, et non la clé du nœud. */
        val byAncestorKey: Boolean get() = trace != null && entry != null && trace.own.winner?.key?.let { it != entry.key } == true
    }

    /** La première ligne, quand la ligne commence par une forme ou un alias : ce qu'il tape, la commande que cela désigne, et son mod. */
    class Headline(val typed: String, val target: String, val owner: String)

    class Report(val headline: Headline?, val rows: List<Row>, val deciding: Row?, val verdict: Verdict)

    /** Le chemin d'une analyse, comme la porte le parcourt : chaque contexte, sa racine quand elle n'est pas tapée, puis ses nœuds. */
    fun steps(parse: ParseResults<*>): List<Step> {
        val steps = ArrayList<Step>()
        var context: CommandContextBuilder<*>? = parse.context
        var first = true
        while (context != null) {
            if (!first && context.rootNode !is RootCommandNode<*>) steps += Step(context.rootNode, typed = false)
            for (parsed in context.nodes) steps += Step(parsed.node, typed = true)
            context = context.child
            first = false
        }
        return steps
    }

    /** Vrai quand la ligne s'analyse en entier jusqu'à un nœud qui s'exécute. */
    fun isComplete(parse: ParseResults<*>): Boolean = parse.exceptions.isEmpty() && !parse.reader.canRead() && parse.context.lastChild.command != null

    /**
     * Le récit d'une ligne pour [subject] : chaque nœud jugé sur sa clé, son exigence vanilla évaluée sur la sonde par [evaluate], et, sous `allow`, le
     * prédicat de mod évalué à tous droits par [evaluateWithAllRights]. Le verdict vient du premier nœud qui refuse ; les nœuds suivants se jugent quand
     * même, parce qu'un `allow` sans effet s'y lit (5.5).
     */
    fun why(
        steps: List<Step>,
        complete: Boolean,
        resolver: Resolver,
        subject: Subject,
        requirePrefix: Boolean,
        markOf: (CommandNode<*>) -> NodeMark?,
        evaluate: (CommandNode<*>) -> Evaluation,
        evaluateWithAllRights: (CommandNode<*>) -> Evaluation,
    ): Report {
        val rows = ArrayList<Row>()
        var deciding: Row? = null
        var verdict: Verdict? = null
        fun refuse(row: Row, how: Verdict) {
            if (verdict == null) {
                verdict = how
                deciding = row
            }
        }
        for (step in steps) {
            val node = step.node
            val entry = when (val mark = markOf(node)) {
                null, is RootMark -> {
                    val row = Row(node.name, NodeStatus.OUT_OF_CATALOG, null, null, null)
                    rows += row
                    if (evaluate(node) == Evaluation.FAILS) refuse(row, Verdict.REFUSED_BY_VANILLA)
                    continue
                }
                Unindexed -> {
                    val row = Row(node.name, NodeStatus.HIDDEN, null, null, null)
                    rows += row
                    refuse(row, Verdict.REFUSED_HIDDEN)
                    continue
                }
                is NodeEntry -> mark
            }
            if (step.typed && entry.hiddenUnderRequirePrefix && requirePrefix) {
                val row = Row(node.name, NodeStatus.BARE_FORM, null, null, entry)
                rows += row
                refuse(row, Verdict.REFUSED_HIDDEN)
                continue
            }
            val trace = resolver.explainNode(subject, entry.key)
            val decision = trace.own.decision
            val row: Row
            when (trace.judgment) {
                NodeJudgment.HIDDEN -> {
                    row = Row(node.name, if (decision == Decision.ALLOW) NodeStatus.INEFFECTIVE else NodeStatus.HIDDEN, decision, trace, entry)
                    refuse(row, Verdict.REFUSED_HIDDEN)
                }
                NodeJudgment.REFUSED -> {
                    row = Row(node.name, NodeStatus.VISIBLE_REFUSAL, decision, trace, entry)
                    refuse(row, Verdict.REFUSED_VISIBLE)
                }
                NodeJudgment.ALLOWED_UNDER_REFUSAL -> {
                    row = Row(node.name, NodeStatus.SEEN_UNDER_VISIBLE_REFUSAL, decision, trace, entry)
                    refuse(row, Verdict.REFUSED_VISIBLE)
                }
                NodeJudgment.ALLOWED -> {
                    val status = if (entry.requirement != CommandRequirement.MOD_PREDICATE) {
                        NodeStatus.SEEN
                    } else {
                        when (evaluateWithAllRights(node)) {
                            Evaluation.PASSES -> NodeStatus.SEEN
                            Evaluation.FAILS -> NodeStatus.UNSEEN_MOD
                            Evaluation.NOT_EVALUATED -> NodeStatus.NOT_EVALUATED
                        }
                    }
                    row = Row(node.name, status, decision, trace, entry)
                    if (status == NodeStatus.UNSEEN_MOD) refuse(row, Verdict.REFUSED_BY_VANILLA)
                }
                NodeJudgment.UNSET -> {
                    val status = when (evaluate(node)) {
                        Evaluation.PASSES -> NodeStatus.SEEN
                        Evaluation.FAILS -> NodeStatus.UNSEEN_VANILLA
                        Evaluation.NOT_EVALUATED -> NodeStatus.NOT_EVALUATED
                    }
                    row = Row(node.name, status, decision, trace, entry)
                    if (status == NodeStatus.UNSEEN_VANILLA) refuse(row, Verdict.REFUSED_BY_VANILLA)
                }
            }
            rows += row
        }
        val headline = headlineOf(steps.firstOrNull(), markOf)
        val finalVerdict = verdict ?: if (complete) Verdict.PERMITTED else Verdict.INCOMPLETE
        return Report(headline, rows, deciding ?: rows.lastOrNull { it.trace != null }, finalVerdict)
    }

    /** La forme ou l'alias en tête de ligne, ramené à sa cible (3.4, 3.6) : la clé de sa fiche nomme la commande et son mod. */
    private fun headlineOf(first: Step?, markOf: (CommandNode<*>) -> NodeMark?): Headline? {
        val entry = first?.let { markOf(it.node) } as? NodeEntry ?: return null
        if (!entry.isForm && !entry.isVanillaAlias) return null
        val target = entry.key.segments.firstOrNull() ?: return null
        if (target == first.node.name) return null
        return Headline(first.node.name, target, entry.key.namespace ?: "?")
    }
}
