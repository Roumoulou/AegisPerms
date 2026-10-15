// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.command.Explanations.Evaluation
import fr.moulou.aegisperms.command.Explanations.NodeStatus
import fr.moulou.aegisperms.command.Explanations.Verdict
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.Hooks
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.resolve.DecisionTrace
import fr.moulou.aegisperms.resolve.NodeJudgment
import fr.moulou.aegisperms.resolve.RuleHit
import fr.moulou.aegisperms.resolve.Subject
import fr.moulou.aegisperms.text.TextKeys.Why
import fr.moulou.aegisperms.tree.NodeEntry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.players.NameAndId
import java.util.function.Predicate

/**
 * `/aegis why` et `/aegis check` (cahier des charges, 5.5) : la même fonction de décision que le chemin chaud, lue avec sa trace. `why` analyse la ligne
 * avec la source de la console, qui voit tout, puis juge chaque nœud du chemin avec la sonde du joueur ; `check` donne la décision d'une clé. Ni l'un ni
 * l'autre n'écrit ni ne se diffuse.
 */
internal object Explain {

    /** La sonde (5.1) : le sujet, la source avec laquelle on juge, et si le joueur est en ligne. */
    class Probe(val subject: Subject, val source: CommandSourceStack, val online: Boolean)

    /**
     * La sonde d'un joueur (E6) : sa propre source quand il est en ligne, qui porte l'enveloppe ; sinon la source de la console, sans entité, à qui l'on
     * donne une enveloppe bâtie sur son niveau d'`ops.json`.
     */
    fun probe(server: MinecraftServer, target: PlayerTarget): Probe {
        val online = server.playerList.getPlayer(target.uuid)
        if (online != null) {
            val level = (online.permissions() as? LevelBasedPermissionSet)?.level()?.id() ?: 0
            return Probe(Subject(online.uuid, level), online.createCommandSourceStack(), online = true)
        }
        val vanilla = server.getProfilePermissions(NameAndId(target.uuid, target.name ?: ""))
        return Probe(Subject(target.uuid, vanilla.level().id()), server.createCommandSourceStack().withPermission(Envelope(target.uuid, vanilla)), online = false)
    }

    fun why(reply: Reply, server: MinecraftServer, target: PlayerTarget, line: String): Int {
        val resolver = Publisher.currentOrNull() ?: return reply.refuse(fr.moulou.aegisperms.text.TextKeys.Command.NOT_PUBLISHED)
        val probe = probe(server, target)
        val command = line.trim().removePrefix("/")
        val parse = server.commands.dispatcher.parse(command, server.createCommandSourceStack())
        val report = Explanations.why(
            steps = Explanations.steps(parse),
            complete = Explanations.isComplete(parse),
            resolver = resolver,
            subject = probe.subject,
            requirePrefix = resolver.state.settings.namespaces.requirePrefix,
            markOf = Hooks::markOf,
            evaluate = { node -> evaluate(node, probe) { requirementOf(node).test(probe.source) } },
            evaluateWithAllRights = { node -> evaluate(node, probe) { Hooks.testWithAllRights(node, probe.source) } },
        )

        val lines = ArrayList<Component>()
        lines += reply.text(Why.TITLE, target.readableForm, command)
        if (Publisher.mode() == Mode.OBSERVE) lines += reply.text(Why.OBSERVE)
        report.headline?.let { lines += reply.text(Why.LEADS_TO, it.typed, it.target, it.owner) }
        for (row in report.rows) lines += reply.text(Why.ROW, row.name, reply.text(statusKey(row.status)), decisionOf(reply, row), originOf(reply, row))
        report.deciding?.trace?.own?.let { trace ->
            if (trace.setAside.isNotEmpty()) lines += reply.text(Why.SET_ASIDE, Reply.join(trace.setAside.map { setAsideEntry(reply, it) }, " ; "))
            for (tie in trace.weightTies) lines += reply.text(Why.TIE, tie.winner, tie.other, tie.weight)
        }
        lines += when (report.verdict) {
            Verdict.PERMITTED -> reply.text(Why.VERDICT_PERMITTED)
            Verdict.REFUSED_HIDDEN -> reply.text(Why.VERDICT_REFUSED_HIDDEN, target.readableForm)
            Verdict.REFUSED_VISIBLE -> reply.text(Why.VERDICT_REFUSED_VISIBLE, target.readableForm)
            Verdict.REFUSED_BY_VANILLA -> reply.text(Why.VERDICT_REFUSED_VANILLA)
            Verdict.INCOMPLETE -> reply.text(Why.VERDICT_INCOMPLETE)
        }
        if (!probe.online) lines += reply.text(Why.OFFLINE, target.readableForm)
        return reply.lines(lines)
    }

    fun check(reply: Reply, server: MinecraftServer, target: PlayerTarget, keyText: String): Int {
        val resolver = Publisher.currentOrNull() ?: return reply.refuse(fr.moulou.aegisperms.text.TextKeys.Command.NOT_PUBLISHED)
        val key = Queries.parseKey(reply, keyText) ?: return 0
        val probe = probe(server, target)
        val index = Suggestions.indexOf(server)
        val lines = ArrayList<Component>()
        lines += reply.text(Why.CHECK_TITLE, target.readableForm, key.text)
        var effective = key
        if (key is CommandKey) {
            index?.aliasKeys?.get(key)?.let { aliasTarget ->
                lines += reply.text(Why.CHECK_ALIAS, key.text, aliasTarget.text)
                effective = aliasTarget
            }
        }
        val trace = resolver.explain(probe.subject, effective)
        lines += reply.text(Why.CHECK_DECISION, decisionText(reply, trace.decision))
        lines += if (trace.step0) reply.text(Why.CHECK_STEP0) else reply.text(Why.CHECK_RULE, originOf(reply, trace, effective == trace.winner?.key))
        for (hit in trace.setAside) lines += reply.text(Why.CHECK_SET_ASIDE, ownerOf(reply, hit, trace), hit.key.text, Queries.ruleValue(reply, hit.rule))
        val checked = effective
        if (checked is CommandKey && checked.isNode) {
            val node = resolver.explainNode(probe.subject, checked)
            lines += when (node.judgment) {
                NodeJudgment.ALLOWED -> reply.text(Why.CHECK_NODE_PERMITTED)
                NodeJudgment.UNSET -> reply.text(Why.CHECK_NODE_PERMITTED_VANILLA)
                NodeJudgment.REFUSED -> reply.text(Why.CHECK_NODE_REFUSED)
                NodeJudgment.HIDDEN, NodeJudgment.ALLOWED_UNDER_REFUSAL -> {
                    val closing = node.closing?.key
                    if (closing != null && closing != checked) reply.text(Why.CHECK_NODE_CLOSED, closing.text) else reply.text(Why.CHECK_NODE_HIDDEN)
                }
            }
            if (index != null && index.nodesOf(checked).isEmpty()) lines += reply.text(Why.CHECK_ABSENT)
        } else if (checked is ApiKey && checked != Keys.ENTITY_SELECTORS && checked !in ApiKeyRequests.keys()) {
            lines += reply.text(Why.CHECK_API_ABSENT)
        }
        if (!probe.online) lines += reply.text(Why.OFFLINE, target.readableForm)
        return reply.lines(lines)
    }

    // ── Les briques ──────────────────────────────────────────────────────────────

    /** L'exigence d'un nœud sur la sonde : un prédicat de mod ne s'évalue pas hors ligne, et ce qui lève ne s'évalue pas non plus. */
    private fun evaluate(node: CommandNode<*>, probe: Probe, test: () -> Boolean): Evaluation {
        if (!probe.online && (Hooks.markOf(node) as? NodeEntry)?.requirement == CommandRequirement.MOD_PREDICATE) return Evaluation.NOT_EVALUATED
        return try {
            if (test()) Evaluation.PASSES else Evaluation.FAILS
        } catch (_: RuntimeException) {
            if (probe.online) Evaluation.FAILS else Evaluation.NOT_EVALUATED
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun requirementOf(node: CommandNode<*>): Predicate<CommandSourceStack> = node.requirement as Predicate<CommandSourceStack>

    private fun statusKey(status: NodeStatus): String = when (status) {
        NodeStatus.SEEN -> Why.STATUS_SEEN
        NodeStatus.HIDDEN -> Why.STATUS_HIDDEN
        NodeStatus.VISIBLE_REFUSAL -> Why.STATUS_VISIBLE_REFUSAL
        NodeStatus.SEEN_UNDER_VISIBLE_REFUSAL -> Why.STATUS_SEEN_UNDER_VISIBLE_REFUSAL
        NodeStatus.UNSEEN_MOD -> Why.STATUS_UNSEEN_MOD
        NodeStatus.UNSEEN_VANILLA -> Why.STATUS_UNSEEN_VANILLA
        NodeStatus.OUT_OF_CATALOG -> Why.STATUS_OUT_OF_CATALOG
        NodeStatus.NOT_EVALUATED -> Why.STATUS_NOT_EVALUATED
        NodeStatus.INEFFECTIVE -> Why.STATUS_INEFFECTIVE
        NodeStatus.BARE_FORM -> Why.STATUS_BARE_FORM
    }

    private fun decisionOf(reply: Reply, row: Explanations.Row): MutableComponent = when (val decision = row.decision) {
        null -> reply.text(Why.DECISION_VANILLA)
        else -> decisionText(reply, decision)
    }

    private fun decisionText(reply: Reply, decision: Decision): MutableComponent = when (decision) {
        Decision.ALLOW -> reply.text(Why.DECISION_ALLOW)
        Decision.DENY_VISIBLE -> reply.text(Why.DECISION_DENY_VISIBLE)
        Decision.DENY_HIDDEN -> reply.text(Why.DECISION_DENY_HIDDEN)
        Decision.UNSET -> reply.text(Why.DECISION_UNSET)
    }

    /** La règle qui l'emporte pour un nœud : l'étape 0, l'ensemble qui répond avec son héritage et sa clé, ou l'exigence vanilla sous `unset`. */
    private fun originOf(reply: Reply, row: Explanations.Row): MutableComponent {
        val trace = row.trace?.own ?: return reply.text(Why.ORIGIN_VANILLA)
        if (trace.winner == null && !trace.step0) {
            val entry = row.entry ?: return reply.text(Why.ORIGIN_NONE)
            return when (entry.requirement) {
                CommandRequirement.NONE -> reply.text(Why.ORIGIN_REQUIREMENT_NONE)
                CommandRequirement.MOD_PREDICATE -> reply.text(Why.ORIGIN_REQUIREMENT_MOD)
                else -> reply.text(Why.ORIGIN_REQUIREMENT_LEVEL, entry.requirement.name.lowercase())
            }
        }
        return originOf(reply, trace, !row.byAncestorKey)
    }

    private fun originOf(reply: Reply, trace: DecisionTrace, ownKey: Boolean): MutableComponent {
        if (trace.step0) return reply.text(Why.ORIGIN_STEP0)
        val winner = trace.winner ?: return reply.text(Why.ORIGIN_NONE)
        val origin = when (val owner = winner.owner) {
            is RuleSetOwner.OfPlayer -> reply.text(Why.ORIGIN_PLAYER, winner.key.text)
            is RuleSetOwner.OfGroup -> reply.text(Why.ORIGIN_GROUP, chainOf(trace, owner.name), winner.key.text)
        }
        if (!ownKey) origin.append(reply.text(Why.ORIGIN_ANCESTOR))
        return origin
    }

    /** Le groupe qui répond, précédé des groupes par lesquels le sujet y arrive : `admin > moderateur`. */
    private fun chainOf(trace: DecisionTrace, group: String): String = trace.order.groups.firstOrNull { it.name == group }?.chain?.joinToString(" > ") ?: group

    private fun ownerOf(reply: Reply, hit: RuleHit, trace: DecisionTrace): MutableComponent = when (val owner = hit.owner) {
        is RuleSetOwner.OfPlayer -> reply.text(fr.moulou.aegisperms.text.TextKeys.Command.OWNER_PLAYER, owner.readableForm)
        is RuleSetOwner.OfGroup -> reply.text(fr.moulou.aegisperms.text.TextKeys.Command.OWNER_GROUP, chainOf(trace, owner.name))
    }

    private fun setAsideEntry(reply: Reply, hit: RuleHit): MutableComponent = reply.text(Why.SET_ASIDE_ENTRY, ownerName(hit), hit.key.text, Queries.ruleValue(reply, hit.rule))

    private fun ownerName(hit: RuleHit): String = when (val owner = hit.owner) {
        is RuleSetOwner.OfPlayer -> owner.readableForm
        is RuleSetOwner.OfGroup -> owner.name
    }
}
