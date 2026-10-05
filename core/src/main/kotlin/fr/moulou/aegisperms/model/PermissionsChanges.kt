// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Ce qui change dans les droits d'un état à l'autre : ce que `/aegis reload` dit avant de publier (cahier des charges, 4.8). Tout y est trié par point de
 * code, et un joueur se suit par son UUID, si bien qu'un pseudo renommé est un joueur modifié.
 */
data class PermissionsChanges(
    val groupsCreated: List<String>,
    val groupsDeleted: List<String>,
    /** Les groupes dont le poids, les parents, les membres d'office ou la note changent. */
    val groupsChanged: List<String>,
    /** Les joueurs ajoutés, sous leur forme lisible. */
    val playersAdded: List<String>,
    /** Les joueurs retirés, sous leur forme lisible. */
    val playersRemoved: List<String>,
    /** Les joueurs dont le pseudo, les groupes directs ou la note changent, sous leur forme lisible d'arrivée. */
    val playersChanged: List<String>,
    /** Les règles ajoutées, retirées ou modifiées : celles des groupes d'abord, puis celles des joueurs. */
    val rules: List<RuleChange>,
) {

    companion object {
        fun between(before: EffectiveState, after: EffectiveState): PermissionsChanges {
            val groupsBefore = before.permissions.groups
            val groupsAfter = after.permissions.groups
            val playersBefore = before.players
            val playersAfter = after.players

            val groupsChanged = groupsBefore.keys.intersect(groupsAfter.keys).filter { groupsBefore.getValue(it).copy(rules = emptyMap()) != groupsAfter.getValue(it).copy(rules = emptyMap()) }
            val playersChanged = playersBefore.keys.intersect(playersAfter.keys).filter {
                val a = playersBefore.getValue(it)
                val b = playersAfter.getValue(it)
                a.name != b.name || a.player.copy(rules = emptyMap()) != b.player.copy(rules = emptyMap())
            }

            val rules = buildList<RuleChange> {
                for (name in (groupsBefore.keys + groupsAfter.keys).sortedWith(CodePointOrder)) {
                    addRuleChanges(RuleSetOwner.OfGroup(name), before.groupRules[name], after.groupRules[name])
                }
                val owners = (playersBefore.keys + playersAfter.keys).map { (playersAfter[it] ?: playersBefore.getValue(it)).readableForm to it }
                for ((readableForm, uuid) in owners.sortedWith(compareBy(CodePointOrder) { it.first })) {
                    addRuleChanges(RuleSetOwner.OfPlayer(readableForm), playersBefore[uuid]?.rules, playersAfter[uuid]?.rules)
                }
            }

            return PermissionsChanges(
                groupsCreated = (groupsAfter.keys - groupsBefore.keys).sortedWith(CodePointOrder),
                groupsDeleted = (groupsBefore.keys - groupsAfter.keys).sortedWith(CodePointOrder),
                groupsChanged = groupsChanged.sortedWith(CodePointOrder),
                playersAdded = (playersAfter.keys - playersBefore.keys).map { playersAfter.getValue(it).readableForm }.sortedWith(CodePointOrder),
                playersRemoved = (playersBefore.keys - playersAfter.keys).map { playersBefore.getValue(it).readableForm }.sortedWith(CodePointOrder),
                playersChanged = playersChanged.map { playersAfter.getValue(it).readableForm }.sortedWith(CodePointOrder),
                rules = rules,
            )
        }

        private fun MutableList<RuleChange>.addRuleChanges(owner: RuleSetOwner, before: RuleSet?, after: RuleSet?) {
            val rulesBefore = before?.rules.orEmpty()
            val rulesAfter = after?.rules.orEmpty()
            for (key in (rulesBefore.keys + rulesAfter.keys).sortedWith(compareBy(CodePointOrder) { it.text })) {
                val ruleBefore = rulesBefore[key]
                val ruleAfter = rulesAfter[key]
                if (ruleBefore != ruleAfter) add(RuleChange(owner, key, ruleBefore, ruleAfter))
            }
        }
    }

    val isEmpty: Boolean
        get() = groupsCreated.isEmpty() && groupsDeleted.isEmpty() && groupsChanged.isEmpty() && playersAdded.isEmpty() && playersRemoved.isEmpty() && playersChanged.isEmpty() && rules.isEmpty()
}

/** Une règle qui change : ajoutée ([before] vide), retirée ([after] vide) ou modifiée. */
data class RuleChange(val owner: RuleSetOwner, val key: Key, val before: Rule?, val after: Rule?)
