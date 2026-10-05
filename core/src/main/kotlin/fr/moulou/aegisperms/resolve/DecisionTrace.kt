// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner

/**
 * La trace d'une décision, pour `/aegis check` et `/aegis why` (cahier des charges, 5.5) : l'étape 0 quand elle répond, la règle qui l'emporte, celles des
 * ensembles écartés, l'ordre d'examen et les égalités de poids qui ont joué.
 */
class DecisionTrace(
    val key: Key,
    val decision: Decision,
    /** Vrai quand l'étape 0 répond. */
    val step0: Boolean,
    /** La règle qui l'emporte, ou `null` sous l'étape 0 et quand la décision vaut `unset`. */
    val winner: RuleHit?,
    /** Les règles qui répondraient dans les ensembles examinés après celui qui l'emporte, dans l'ordre d'examen ; sous l'étape 0, toutes. */
    val setAside: List<RuleHit>,
    val order: ExaminationOrder,
    val weightTies: List<WeightTie>,
)

/** La trace d'un nœud : son jugement, et la trace de sa propre clé. */
class NodeTrace(
    val key: CommandKey,
    val judgment: NodeJudgment,
    val own: DecisionTrace,
    /**
     * La trace de la clé du chemin qui ferme le nœud : le premier refus masqué depuis la commande racine pour un nœud [NodeJudgment.HIDDEN], le premier refus
     * sinon. C'est [own] quand la clé du nœud porte ce refus, et `null` sans refus sur le chemin.
     */
    val closing: DecisionTrace?,
)

/** Une règle qui répond d'une clé : l'ensemble de règles qui la porte, sa propre clé, qui couvre la clé demandée, et la règle. */
data class RuleHit(val owner: RuleSetOwner, val key: Key, val rule: Rule)

/**
 * Une égalité de poids qui a joué (2.6) : [other], groupe de départ de même poids que [winner], aurait changé la décision en passant devant lui. C'est
 * l'ordre des noms qui a tranché.
 */
data class WeightTie(val winner: String, val other: String, val weight: Int)

/** Une règle sans effet pour un sujet (2.7) : un `allow` de nœud sous le refus de [closingKey], clé de son chemin. */
data class IneffectiveRule(val hit: RuleHit, val closingKey: CommandKey)
