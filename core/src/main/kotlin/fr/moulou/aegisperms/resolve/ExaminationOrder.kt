// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group

/**
 * L'ordre d'examen des groupes d'un sujet (cahier des charges, 2.6) : les groupes de départ, rangés par poids décroissant puis par nom croissant, chacun
 * parcouru en profondeur, ses parents dans l'ordre déclaré, et un groupe atteint plusieurs fois gardé à sa dernière place. Un groupe passe ainsi après tous
 * les groupes du sujet qui en héritent.
 *
 * À poids égal, `default` passe après les autres groupes de départ, quel que soit son nom : il est le repli de tout joueur, et un groupe écrit sans poids
 * doit répondre avant lui.
 */
class ExaminationOrder private constructor(
    /** Les groupes de départ, dans leur ordre. */
    val startingGroups: List<StartingGroup>,
    /** Les groupes, dans l'ordre où ils s'examinent, chacun une fois. */
    val groups: List<OrderedGroup>,
) {

    companion object {
        /**
         * L'ordre d'un sujet de niveau d'op [opLevel] dont les groupes directs sont [directGroups] : ses groupes de départ sont ceux-là, ceux dont il est
         * membre d'office, et `default`.
         */
        fun of(state: EffectiveState, directGroups: List<String>, opLevel: Int): ExaminationOrder {
            val groups = state.permissions.groups
            val memberships = LinkedHashMap<String, Membership>()
            for (name in directGroups) memberships[name] = Membership.DIRECT
            for (name in state.exOfficioGroups(opLevel)) memberships.putIfAbsent(name, Membership.EX_OFFICIO)
            memberships[Group.DEFAULT] = Membership.DEFAULT
            val startingGroups = memberships.map { StartingGroup(it.key, it.value, groups.getValue(it.key).weight) }
            return from(groups, startingGroups.sortedWith(compareByDescending<StartingGroup> { it.weight }.thenBy { it.name == Group.DEFAULT }.thenBy(CodePointOrder) { it.name }))
        }

        /**
         * L'ordre que donnent des groupes de départ déjà rangés. Un groupe inconnu ou un cycle d'héritage, que la validation refuse (4.5), lèvent une
         * exception.
         *
         * Le parcours de 2.6 repasse par un ancêtre commun autant de fois qu'un chemin y mène. Celui-ci rend le même ordre en visitant chaque groupe une
         * fois : la dernière place d'un groupe dans le parcours est sa première dans le parcours inversé, où les groupes de départ et les parents se
         * prennent du dernier au premier et où un groupe se note après ses parents.
         */
        internal fun from(groups: Map<String, Group>, startingGroups: List<StartingGroup>): ExaminationOrder {
            val reversed = ArrayList<OrderedGroup>()
            val done = HashSet<String>()
            val path = ArrayList<String>()
            fun visit(name: String, membership: Membership) {
                if (name in done) return
                check(name !in path) { "Inheritance cycle through group '$name'" }
                path += name
                val chain = path.toList()
                for (parent in groups.getValue(name).parents.asReversed()) visit(parent, membership)
                path.removeAt(path.lastIndex)
                done += name
                reversed += OrderedGroup(name, membership, chain)
            }
            for (start in startingGroups.asReversed()) visit(start.name, start.membership)
            return ExaminationOrder(startingGroups, reversed.asReversed().toList())
        }
    }
}

/** Un groupe de départ : l'appartenance qui l'y met, et son poids. */
data class StartingGroup(val name: String, val membership: Membership, val weight: Int)

/** Un groupe à sa place dans l'ordre d'examen : l'héritage qui y mène, du groupe de départ jusqu'à lui, et l'appartenance du sujet à ce groupe de départ. */
data class OrderedGroup(val name: String, val membership: Membership, val chain: List<String>)

/** Ce qui met un groupe parmi les groupes de départ d'un sujet. Un groupe direct dont le sujet est aussi membre d'office compte pour direct. */
enum class Membership {
    DIRECT,
    EX_OFFICIO,
    DEFAULT,
}
