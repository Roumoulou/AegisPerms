// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.text.TextKeys.Command
import java.util.UUID

/**
 * Les modifications des droits que `/aegis` fait (cahier des charges, 5.3), comme des transformations pures de [Permissions] : la candidate se compile sur
 * l'état en vigueur, et c'est la validation entière, à l'écriture, qui la juge (4.5). Ce qui se voit sans fichier se refuse ici, par une [Refusal] qui dit
 * sa clé de texte et ses arguments. Sans Minecraft, pour que l'étage 0 l'éprouve.
 */
internal object PermissionsEdits {

    /** Un refus connu avant d'écrire : la clé du texte qui le dit, et ses arguments. */
    class Refusal(val key: String, vararg val arguments: Any) : RuntimeException(key)

    /** Les cinq verbes d'une règle (5.3) : `deny` laisse la visibilité au réglage `denied-commands`, `deny-visible` et `deny-hidden` l'écrivent dans la règle. */
    enum class RuleVerb(val literal: String) {
        ALLOW("allow"),
        DENY("deny"),
        DENY_VISIBLE("deny-visible"),
        DENY_HIDDEN("deny-hidden"),
        UNSET("unset");

        /** [rules] après ce verbe sur [key] : la note d'une règle qui change reste. */
        fun apply(rules: Map<String, Rule>, key: Key, owner: String): Map<String, Rule> {
            val note = rules[key.text]?.note
            return when (this) {
                ALLOW -> rules + (key.text to Rule(RuleValue.ALLOW, note = note))
                DENY -> rules + (key.text to Rule(RuleValue.DENY, note = note))
                DENY_VISIBLE -> rules + (key.text to Rule(RuleValue.DENY, visible = true, note = note))
                DENY_HIDDEN -> rules + (key.text to Rule(RuleValue.DENY, visible = false, note = note))
                UNSET -> if (key.text in rules) rules - key.text else throw Refusal(Command.RULE_ABSENT, owner, key.text)
            }
        }
    }

    // ── Les groupes ───────────────────────────────────────────────────────────────

    fun createGroup(permissions: Permissions, name: String, weight: Int): Permissions {
        if (!Group.isValidName(name)) throw Refusal(Command.GROUP_NAME_MALFORMED, name)
        if (name in permissions.groups) throw Refusal(Command.GROUP_EXISTS, name)
        return permissions.copy(groups = permissions.groups + (name to Group(weight = weight)))
    }

    /** Supprime un groupe que rien ne référence ; `default`, un parent d'un autre groupe ou un groupe direct d'un joueur se refusent, avec ce qui le retient. */
    fun deleteGroup(permissions: Permissions, name: String): Permissions {
        if (name == Group.DEFAULT) throw Refusal(Command.GROUP_DEFAULT_PROTECTED)
        existing(permissions, name)
        val retainers = permissions.groups.filterValues { name in it.parents }.keys + permissions.players.filterValues { name in it.groups }.keys
        if (retainers.isNotEmpty()) throw Refusal(Command.GROUP_REFERENCED, name, retainers.joinToString(", "))
        return permissions.copy(groups = permissions.groups - name)
    }

    /** Renomme un groupe et chaque référence, dans les parents et chez les joueurs ; refuse `default` et un nom déjà pris. */
    fun renameGroup(permissions: Permissions, name: String, newName: String): Permissions {
        if (name == Group.DEFAULT) throw Refusal(Command.GROUP_DEFAULT_PROTECTED)
        existing(permissions, name)
        if (!Group.isValidName(newName)) throw Refusal(Command.GROUP_NAME_MALFORMED, newName)
        if (newName in permissions.groups) throw Refusal(Command.GROUP_NAME_TAKEN, newName)
        val groups = permissions.groups.entries.associate { (key, group) -> (if (key == name) newName else key) to group.copy(parents = group.parents.map { if (it == name) newName else it }) }
        val players = permissions.players.mapValues { (_, player) -> if (name in player.groups) player.copy(groups = player.groups.map { if (it == name) newName else it }) else player }
        return permissions.copy(groups = groups, players = players)
    }

    /** Crée [newName] avec le poids, les parents, les règles et la note de [name] ; ni `include-ops` ni les membres ne se copient. */
    fun cloneGroup(permissions: Permissions, name: String, newName: String): Permissions {
        val group = existing(permissions, name)
        if (!Group.isValidName(newName)) throw Refusal(Command.GROUP_NAME_MALFORMED, newName)
        if (newName in permissions.groups) throw Refusal(Command.GROUP_NAME_TAKEN, newName)
        return permissions.copy(groups = permissions.groups + (newName to group.copy(includeOps = null)))
    }

    fun setWeight(permissions: Permissions, name: String, weight: Int): Permissions = withGroup(permissions, name) { it.copy(weight = weight) }

    /** Ajoute un parent ; refuse le groupe lui-même, `default`, un parent inconnu et un cycle. */
    fun addParent(permissions: Permissions, name: String, parent: String): Permissions {
        existing(permissions, name)
        if (parent == name) throw Refusal(Command.GROUP_PARENT_SELF)
        if (parent == Group.DEFAULT) throw Refusal(Command.GROUP_PARENT_DEFAULT)
        existing(permissions, parent)
        if (reaches(permissions, from = parent, target = name)) throw Refusal(Command.GROUP_PARENT_CYCLE, parent, name)
        return withGroup(permissions, name) { group -> if (parent in group.parents) group else group.copy(parents = group.parents + parent) }
    }

    fun removeParent(permissions: Permissions, name: String, parent: String): Permissions = withGroup(permissions, name) { group ->
        if (parent !in group.parents) throw Refusal(Command.GROUP_PARENT_ABSENT, name, parent)
        group.copy(parents = group.parents - parent)
    }

    fun setIncludeOps(permissions: Permissions, name: String, level: OpLevel?): Permissions = withGroup(permissions, name) { it.copy(includeOps = level) }

    fun setGroupRule(permissions: Permissions, name: String, key: Key, verb: RuleVerb): Permissions = withGroup(permissions, name) { it.copy(rules = verb.apply(it.rules, key, name)) }

    /** La note du groupe, ou celle de l'une de ses règles ; un texte blanc l'efface. */
    fun setGroupNote(permissions: Permissions, name: String, text: String, key: Key?): Permissions = withGroup(permissions, name) { group ->
        if (key == null) group.copy(note = text.ifBlank { null }) else group.copy(rules = withNote(group.rules, key, text, name))
    }

    // ── Les joueurs ───────────────────────────────────────────────────────────────

    fun addPlayerGroup(permissions: Permissions, uuid: UUID, readableForm: String, group: String): Permissions {
        if (group == Group.DEFAULT) throw Refusal(Command.PLAYER_GROUP_DEFAULT)
        existing(permissions, group)
        return withPlayer(permissions, uuid, readableForm) { player -> if (group in player.groups) player else player.copy(groups = player.groups + group) }
    }

    fun removePlayerGroup(permissions: Permissions, uuid: UUID, readableForm: String, group: String): Permissions {
        if (group == Group.DEFAULT) throw Refusal(Command.PLAYER_GROUP_DEFAULT)
        return withPlayer(permissions, uuid, readableForm) { player ->
            if (group !in player.groups) throw Refusal(Command.PLAYER_GROUP_ABSENT, readableForm, group)
            player.copy(groups = player.groups - group)
        }
    }

    fun setPlayerRule(permissions: Permissions, uuid: UUID, readableForm: String, key: Key, verb: RuleVerb): Permissions =
        withPlayer(permissions, uuid, readableForm) { it.copy(rules = verb.apply(it.rules, key, readableForm)) }

    fun setPlayerNote(permissions: Permissions, uuid: UUID, readableForm: String, text: String, key: Key?): Permissions = withPlayer(permissions, uuid, readableForm) { player ->
        if (key == null) player.copy(note = text.ifBlank { null }) else player.copy(rules = withNote(player.rules, key, text, readableForm))
    }

    /** La forme lisible sous laquelle le fichier connaît [uuid], ou `null` quand il ne le connaît pas. */
    fun readableFormOf(permissions: Permissions, uuid: UUID): String? = permissions.players.keys.firstOrNull { ReadableForm.split(it)?.second == uuid.toString() }

    // ── Les briques ───────────────────────────────────────────────────────────────

    private fun existing(permissions: Permissions, name: String): Group = permissions.groups[name] ?: throw Refusal(Command.GROUP_UNKNOWN, name)

    private fun withGroup(permissions: Permissions, name: String, update: (Group) -> Group): Permissions =
        permissions.copy(groups = permissions.groups + (name to update(existing(permissions, name))))

    /**
     * Modifie un joueur, qui entre au fichier sous [readableForm] à sa première règle, à son premier groupe ou à sa première note, et en sort quand il n'a
     * plus rien (5.3). Un joueur déjà au fichier garde sa forme lisible.
     */
    private fun withPlayer(permissions: Permissions, uuid: UUID, readableForm: String, update: (Player) -> Player): Permissions {
        val form = readableFormOf(permissions, uuid) ?: readableForm
        val updated = update(permissions.players[form] ?: Player())
        val players = if (updated.groups.isEmpty() && updated.rules.isEmpty() && updated.note == null) permissions.players - form else permissions.players + (form to updated)
        return permissions.copy(players = players)
    }

    private fun withNote(rules: Map<String, Rule>, key: Key, text: String, owner: String): Map<String, Rule> {
        val rule = rules[key.text] ?: throw Refusal(Command.RULE_ABSENT, owner, key.text)
        return rules + (key.text to rule.copy(note = text.ifBlank { null }))
    }

    /** Vrai si [target] est [from] ou l'un de ses ancêtres par héritage : ajouter [from] aux parents de [target] fermerait un cycle. */
    private fun reaches(permissions: Permissions, from: String, target: String): Boolean {
        val seen = HashSet<String>()
        val pending = ArrayDeque<String>()
        pending += from
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (current == target) return true
            if (!seen.add(current)) continue
            permissions.groups[current]?.parents?.let { pending += it }
        }
        return false
    }
}
