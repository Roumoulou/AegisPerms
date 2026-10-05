// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.KnownPlayer
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.RuleSet
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.RuleValue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * La fonction de décision d'un état en vigueur (cahier des charges, 2.6), la seule (2.2) : l'arbre du client, l'analyse, l'exécution, `/aegis why`,
 * `/aegis check` et les API de permissions passent par elle. Elle porte le cache des décisions de son état, vide à sa naissance : un état nouveau arrive
 * avec un résolveur nouveau, et un calcul fait sur l'ancien ne peut rien y écrire (7.4).
 *
 * Le cache s'indexe par UUID et par niveau d'op, parce que le niveau d'op change les groupes de départ du sujet : après un `/op`, la commande suivante se
 * juge dans une autre case. Les joueurs que le fichier des droits ne connaît pas partagent une case par niveau.
 */
class Resolver(val state: EffectiveState) {

    companion object {
        /** Les niveaux d'op, de 0 à 4. */
        private val OP_LEVEL_COUNT = OpLevel.OWNERS.level + 1
    }

    private val knownPlayerCaches = ConcurrentHashMap<UUID, AtomicReferenceArray<SubjectCache?>>()
    private val unknownPlayerCaches = AtomicReferenceArray<SubjectCache?>(OP_LEVEL_COUNT)

    /** La décision d'un sujet pour une clé, étape 0 comprise. */
    fun decision(subject: Subject, key: Key): Decision = decision(subject.uuid, subject.opLevel, key)

    /** La même, sans objet sujet : la forme du chemin chaud, sans allocation une fois la décision en cache. */
    fun decision(uuid: UUID, opLevel: Int, key: Key): Decision {
        if (isStep0(opLevel, key)) return Decision.ALLOW
        return ruleDecision(cacheFor(uuid, opLevel), key)
    }

    /** Le jugement d'un nœud de clé [key] (2.7). */
    fun judgment(subject: Subject, key: CommandKey): NodeJudgment = judgment(subject.uuid, subject.opLevel, key)

    /** Le même, sans objet sujet : la forme du chemin chaud, sans allocation une fois le jugement en cache. */
    fun judgment(uuid: UUID, opLevel: Int, key: CommandKey): NodeJudgment {
        require(key.isNode) { "Only a node key has a judgment: $key" }
        if (isStep0(opLevel, key)) return NodeJudgment.ALLOWED
        val cache = cacheFor(uuid, opLevel)
        cache.judgments[key]?.let { return it }
        val computed = computeJudgment(cache, key)
        return cache.judgments.putIfAbsent(key, computed) ?: computed
    }

    /** L'ordre dans lequel les groupes d'un sujet s'examinent. */
    fun order(subject: Subject): ExaminationOrder = cacheFor(subject.uuid, subject.opLevel).order

    /** La trace d'une décision. */
    fun explain(subject: Subject, key: Key): DecisionTrace {
        val cache = cacheFor(subject.uuid, subject.opLevel)
        val hits = hits(cache.player, cache.order, key).toList()
        if (isStep0(subject.opLevel, key)) return DecisionTrace(key, Decision.ALLOW, step0 = true, winner = null, setAside = hits, order = cache.order, weightTies = emptyList())
        val winner = hits.firstOrNull()
        val decision = decisionOf(winner)
        return DecisionTrace(key, decision, step0 = false, winner = winner, setAside = hits.drop(1), order = cache.order, weightTies = weightTies(cache, key, winner, decision))
    }

    /** La trace d'un nœud : son jugement, et la clé de son chemin qui le ferme. */
    fun explainNode(subject: Subject, key: CommandKey): NodeTrace {
        val judgment = judgment(subject, key)
        val own = explain(subject, key)
        val closingKey = when (judgment) {
            NodeJudgment.HIDDEN -> key.pathKeys.first { decision(subject, it) == Decision.DENY_HIDDEN }
            NodeJudgment.REFUSED, NodeJudgment.ALLOWED_UNDER_REFUSAL -> key.pathKeys.first { decision(subject, it).isRefusal }
            NodeJudgment.ALLOWED, NodeJudgment.UNSET -> null
        }
        val closing = when {
            closingKey == null -> null
            closingKey === key -> own
            else -> explain(subject, closingKey)
        }
        return NodeTrace(key, judgment, own, closing)
    }

    /**
     * Les règles sans effet pour un sujet : chaque `allow` de nœud dont une clé du chemin, plus haut, porte un refus. Ses règles propres d'abord, puis celles
     * de ses groupes dans l'ordre d'examen, par clé croissante dans chaque ensemble.
     */
    fun ineffectiveRules(subject: Subject): List<IneffectiveRule> {
        val cache = cacheFor(subject.uuid, subject.opLevel)
        val ineffective = mutableListOf<IneffectiveRule>()
        fun examine(owner: RuleSetOwner, ruleSet: RuleSet) {
            for ((key, rule) in ruleSet.rules.entries.sortedWith(compareBy(CodePointOrder) { it.key.text })) {
                if (rule.value != RuleValue.ALLOW || key !is CommandKey) continue
                val closingKey = key.pathKeys.firstOrNull { it !== key && decision(subject, it).isRefusal } ?: continue
                ineffective += IneffectiveRule(RuleHit(owner, key, rule), closingKey)
            }
        }
        cache.player?.let { examine(RuleSetOwner.OfPlayer(it.readableForm), it.rules) }
        for (group in cache.order.groups) examine(RuleSetOwner.OfGroup(group.name), state.groupRules.getValue(group.name))
        return ineffective
    }

    /** Oublie les décisions en cache d'un joueur, à sa déconnexion. */
    fun forget(uuid: UUID) {
        knownPlayerCaches.remove(uuid)
    }

    /** L'étape 0 se lit à chaque appel sur le niveau d'op du sujet, hors du cache (7.6). */
    private fun isStep0(opLevel: Int, key: Key): Boolean = key is CommandKey && key.isAdministration && state.settings.adminLevel.isReachedBy(opLevel)

    private fun cacheFor(uuid: UUID, opLevel: Int): SubjectCache {
        val player = state.players[uuid]
        val slots = if (player == null) unknownPlayerCaches else knownPlayerCaches[uuid] ?: knownPlayerCaches.computeIfAbsent(uuid) { AtomicReferenceArray(OP_LEVEL_COUNT) }
        slots.get(opLevel)?.let { return it }
        val created = SubjectCache(player, ExaminationOrder.of(state, player?.groups.orEmpty(), opLevel))
        return slots.compareAndExchange(opLevel, null, created) ?: created
    }

    /** La décision des seules règles, les étapes 1 à 3. */
    private fun ruleDecision(cache: SubjectCache, key: Key): Decision {
        cache.decisions[key]?.let { return it }
        val computed = decisionOf(hits(cache.player, cache.order, key).firstOrNull())
        return cache.decisions.putIfAbsent(key, computed) ?: computed
    }

    private fun computeJudgment(cache: SubjectCache, key: CommandKey): NodeJudgment {
        var underVisibleRefusal = false
        for (pathKey in key.pathKeys) {
            val decision = ruleDecision(cache, pathKey)
            if (pathKey === key) {
                return when (decision) {
                    Decision.DENY_HIDDEN -> NodeJudgment.HIDDEN
                    Decision.DENY_VISIBLE -> NodeJudgment.REFUSED
                    Decision.ALLOW -> if (underVisibleRefusal) NodeJudgment.ALLOWED_UNDER_REFUSAL else NodeJudgment.ALLOWED
                    // Jamais sous un refus : la règle qui refuse une clé ancêtre couvre aussi celle-ci.
                    Decision.UNSET -> NodeJudgment.UNSET
                }
            }
            when (decision) {
                Decision.DENY_HIDDEN -> return NodeJudgment.HIDDEN
                Decision.DENY_VISIBLE -> underVisibleRefusal = true
                Decision.ALLOW, Decision.UNSET -> {}
            }
        }
        error("A node key is the last of its own path keys: $key")
    }

    /** Les règles qui répondent de [key], dans l'ordre : celle des règles propres du joueur, puis celle de chaque groupe de [order]. */
    private fun hits(player: KnownPlayer?, order: ExaminationOrder, key: Key): Sequence<RuleHit> = sequence {
        if (player != null) hit(RuleSetOwner.OfPlayer(player.readableForm), player.rules, key)?.let { yield(it) }
        for (group in order.groups) hit(RuleSetOwner.OfGroup(group.name), state.groupRules.getValue(group.name), key)?.let { yield(it) }
    }

    private fun hit(owner: RuleSetOwner, ruleSet: RuleSet, key: Key): RuleHit? {
        val answering = ruleSet.mostPrecise(key) ?: return null
        return RuleHit(owner, answering, ruleSet.rules.getValue(answering))
    }

    private fun decisionOf(winner: RuleHit?): Decision = if (winner == null) Decision.UNSET else state.decisionOf(winner.rule)

    /**
     * Les groupes de départ de même poids que celui de la règle qui l'emporte, rangés après lui par leur nom, qui auraient changé la décision en passant
     * devant lui. L'ordre se recalcule pour chacun : c'est ce qui écarte le groupe que l'héritage, et non le nom, a fait passer après. `default` n'en est
     * jamais : à poids égal, c'est une règle qui le range après les autres, pas son nom.
     */
    private fun weightTies(cache: SubjectCache, key: Key, winner: RuleHit?, decision: Decision): List<WeightTie> {
        val winnerGroup = (winner?.owner as? RuleSetOwner.OfGroup)?.name ?: return emptyList()
        val startingGroups = cache.order.startingGroups
        val winnerStart = cache.order.groups.first { it.name == winnerGroup }.chain.first()
        val winnerIndex = startingGroups.indexOfFirst { it.name == winnerStart }
        val weight = startingGroups[winnerIndex].weight
        val ties = mutableListOf<WeightTie>()
        for (otherIndex in winnerIndex + 1 until startingGroups.size) {
            val other = startingGroups[otherIndex]
            if (other.weight != weight || other.name == Group.DEFAULT) break
            val otherFirst = startingGroups.toMutableList().apply { add(winnerIndex, removeAt(otherIndex)) }
            val otherwise = decisionOf(hits(cache.player, ExaminationOrder.from(state.permissions.groups, otherFirst), key).firstOrNull())
            if (otherwise != decision) ties += WeightTie(winnerStart, other.name, weight)
        }
        return ties
    }

    /** La case d'un sujet : son ordre d'examen, calculé une fois, et ses décisions. */
    private class SubjectCache(val player: KnownPlayer?, val order: ExaminationOrder) {
        val decisions = ConcurrentHashMap<Key, Decision>()
        val judgments = ConcurrentHashMap<CommandKey, NodeJudgment>()
    }
}
