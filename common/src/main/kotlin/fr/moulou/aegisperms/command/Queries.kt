// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.AegisPerms
import fr.moulou.aegisperms.files.CommandEntry
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Lifecycle
import fr.moulou.aegisperms.hook.OpLevels
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.MalformedKeyException
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.PermissionsChanges
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.resolve.Subject
import fr.moulou.aegisperms.text.TextKeys.Command
import fr.moulou.aegisperms.tree.IndexCatalog
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Les consultations de `/aegis` (cahier des charges, 5.3) : `status`, `catalog`, `search`, `group list` et `info`, `player info`, `validate`. Chacune lit
 * l'état en vigueur et répond à sa source seule (5.6) ; `validate` seule passe par le fil des fichiers.
 */
internal object Queries {

    /** Le plus de lignes que `catalog` montre, le reste se comptant (5.3). */
    private const val CATALOG_LINES = 30

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun status(reply: Reply): Int {
        val state = state(reply) ?: return 0
        val last = Publisher.lastPublication
        val permissions = state.permissions
        val rules = permissions.groups.values.sumOf { it.rules.size } + permissions.players.values.sumOf { it.rules.size }
        val lines = buildList {
            add(reply.text(Command.STATUS_VERSION, AegisPerms.version()))
            if (last != null) add(reply.text(Command.STATUS_GENERATION, last.generation, last.source.name.lowercase(), DATE.format(last.date)))
            add(reply.text(Command.STATUS_MODE, reply.text(modeKey(Publisher.mode()))))
            add(reply.text(Command.STATUS_READ_ONLY, reply.yesNo(last?.readOnly == true)))
            add(reply.text(Command.STATUS_WATCH, reply.yesNo(state.settings.watchFiles)))
            add(reply.text(if (Lifecycle.foreignAuthoritySeen) Command.STATUS_ENVELOPE_REPLACED else Command.STATUS_ENVELOPE_OK))
            if (last != null && last.fallback.isNotEmpty()) add(reply.text(Command.STATUS_FALLBACK, last.fallback.joinToString(", ") { it.fileName }))
            add(reply.text(Command.STATUS_COUNTS, permissions.groups.size, permissions.players.size, rules))
            add(reply.text(Command.STATUS_ISSUES, last?.report?.issues?.size ?: 0))
        }
        return reply.lines(lines)
    }

    /** Les clés du catalogue qui commencent par [prefix], trente lignes au plus et le compte du reste (5.3). */
    fun catalog(reply: Reply, server: MinecraftServer, prefix: String): Int {
        val index = Suggestions.indexOf(server) ?: return reply.refuse(Command.CATALOG_UNAVAILABLE)
        val wanted = prefix.trim()
        val commands = IndexCatalog(index, ApiKeyRequests.keys()).commands().filterKeys { it.startsWith(wanted) }.toSortedMap(CodePointOrder)
        val apiKeys = ApiKeyRequests.keys().filter { it.text.startsWith(wanted) }.sortedWith(compareBy(CodePointOrder) { it.text })
        if (commands.isEmpty() && apiKeys.isEmpty()) return reply.line(reply.text(Command.CATALOG_NONE, wanted))
        val lines = ArrayList<Component>()
        lines += reply.text(Command.CATALOG_TITLE, commands.size, apiKeys.size)
        var shown = 0
        for ((key, entry) in commands) {
            if (shown == CATALOG_LINES) break
            lines += reply.text(Command.CATALOG_ENTRY, key, Reply.join(marksOf(reply, entry), ", "))
            shown++
        }
        for (key in apiKeys) {
            if (shown == CATALOG_LINES) break
            lines += reply.text(Command.CATALOG_API_KEY, key.text, ApiKeyRequests.typeOf(key) ?: "?")
            shown++
        }
        val remaining = commands.size + apiKeys.size - shown
        if (remaining > 0) lines += reply.text(Command.CATALOG_MORE, remaining)
        return reply.lines(lines)
    }

    private fun marksOf(reply: Reply, entry: CommandEntry): List<Component> = buildList {
        add(
            when (entry.requirement) {
                CommandRequirement.NONE -> reply.text(Command.CATALOG_REQUIREMENT_NONE)
                CommandRequirement.MOD_PREDICATE -> reply.text(Command.CATALOG_REQUIREMENT_MOD)
                else -> reply.text(Command.CATALOG_REQUIREMENT_LEVEL, entry.requirement.name.lowercase())
            },
        )
        if (entry.executable) add(reply.text(Command.CATALOG_EXECUTABLE))
        entry.aliasOf?.let { add(reply.text(Command.CATALOG_ALIAS_OF, it)) }
        entry.sensitive?.let { add(reply.text(Command.CATALOG_SENSITIVE, it)) }
        if (entry.shared) add(reply.text(Command.CATALOG_SHARED))
        if (entry.moreDemanding) add(reply.text(Command.CATALOG_MORE_DEMANDING))
        entry.owner?.let { add(reply.text(Command.CATALOG_OWNER, it)) }
        if (entry.coOwners.isNotEmpty()) add(reply.text(Command.CATALOG_CO_OWNERS, entry.coOwners.joinToString(", ")))
        entry.prefixedForm?.let { add(reply.text(Command.CATALOG_PREFIXED_FORM, it)) }
        if (entry.aliases.isNotEmpty()) add(reply.text(Command.CATALOG_ALIASES, entry.aliases.joinToString(", ")))
    }

    /** Les groupes et les joueurs qui portent une règle sur [keyText] ou sur une clé qui la couvre, avec la valeur de chaque règle (5.3). */
    fun search(reply: Reply, keyText: String): Int {
        val state = state(reply) ?: return 0
        val key = parseKey(reply, keyText) ?: return 0
        val covering = key.coveringChain.toSet()
        val hits = ArrayList<Triple<Component, Key, Rule>>()
        for ((name, ruleSet) in state.groupRules.entries.sortedWith(compareBy(CodePointOrder) { it.key })) {
            for ((ruleKey, rule) in ruleSet.rules) if (ruleKey in covering) hits += Triple(reply.text(Command.OWNER_GROUP, name), ruleKey, rule)
        }
        for (player in state.players.values.sortedWith(compareBy(CodePointOrder) { it.readableForm })) {
            for ((ruleKey, rule) in player.rules.rules) if (ruleKey in covering) hits += Triple(reply.text(Command.OWNER_PLAYER, player.readableForm), ruleKey, rule)
        }
        if (hits.isEmpty()) return reply.line(reply.text(Command.SEARCH_NONE, key.text))
        val lines = ArrayList<Component>()
        lines += reply.text(Command.SEARCH_TITLE, hits.size, key.text)
        for ((owner, ruleKey, rule) in hits) lines += reply.text(Command.SEARCH_ENTRY, owner, ruleKey.text, ruleValue(reply, rule))
        return reply.lines(lines)
    }

    /** Les groupes par poids décroissant puis nom (2.6), avec le poids, les parents et les membres d'office. */
    fun groupList(reply: Reply): Int {
        val state = state(reply) ?: return 0
        val groups = state.permissions.groups.entries.sortedWith(compareByDescending<Map.Entry<String, Group>> { it.value.weight }.thenBy(CodePointOrder) { it.key })
        val lines = ArrayList<Component>()
        lines += reply.text(Command.GROUP_LIST_TITLE, groups.size)
        for ((name, group) in groups) {
            val line = reply.text(Command.GROUP_LIST_ENTRY, name, group.weight, group.rules.size)
            if (group.parents.isNotEmpty()) line.append(reply.text(Command.GROUP_INHERITS, group.parents.joinToString(", ")))
            group.includeOps?.let { line.append(reply.text(Command.GROUP_INCLUDE_OPS, it.name.lowercase())) }
            lines += line
        }
        return reply.lines(lines)
    }

    fun groupInfo(reply: Reply, name: String): Int {
        val state = state(reply) ?: return 0
        val group = state.permissions.groups[name] ?: return reply.refuse(Command.GROUP_UNKNOWN, name)
        val children = state.permissions.groups.filterValues { name in it.parents }.keys.sortedWith(CodePointOrder)
        val members = state.players.values.filter { name in it.groups }.map { it.readableForm }.sortedWith(CodePointOrder)
        val lines = ArrayList<Component>()
        lines += reply.text(Command.GROUP_INFO_TITLE, name, group.weight)
        if (group.parents.isNotEmpty()) lines += reply.text(Command.GROUP_INFO_PARENTS, group.parents.joinToString(", "))
        if (children.isNotEmpty()) lines += reply.text(Command.GROUP_INFO_CHILDREN, children.joinToString(", "))
        lines += group.includeOps?.let { reply.text(Command.GROUP_INFO_INCLUDE_OPS, it.name.lowercase()) } ?: reply.text(Command.GROUP_INFO_INCLUDE_OPS_NONE)
        group.note?.let { lines += reply.text(Command.NOTE, it) }
        lines += rulesBlock(reply, group.rules, Command.GROUP_INFO_RULES, Command.GROUP_INFO_RULES_NONE)
        lines += if (members.isEmpty()) reply.text(Command.GROUP_INFO_MEMBERS_NONE) else reply.text(Command.GROUP_INFO_MEMBERS, members.joinToString(", "))
        if (name == Group.DEFAULT) lines += reply.text(Command.GROUP_DEFAULT_NOTE)
        return reply.lines(lines)
    }

    fun playerInfo(reply: Reply, server: MinecraftServer, target: PlayerTarget): Int {
        val resolver = Publisher.currentOrNull() ?: return reply.refuse(Command.NOT_PUBLISHED)
        val state = resolver.state
        val online = server.playerList.getPlayer(target.uuid)
        val level = online?.let { (it.permissions() as? LevelBasedPermissionSet)?.level()?.id() } ?: OpLevels.levelOf(target.uuid)
        val subject = Subject(target.uuid, level)
        val known = state.players[target.uuid]
        val exOfficio = state.exOfficioGroups(level)
        val lines = ArrayList<Component>()
        lines += reply.text(Command.PLAYER_INFO_TITLE, target.name ?: known?.name ?: "?", target.uuid)
        lines += reply.text(if (online != null) Command.PLAYER_INFO_ONLINE else Command.PLAYER_INFO_OFFLINE, level)
        if (known == null) {
            lines += reply.text(Command.PLAYER_INFO_ABSENT)
        } else {
            lines += if (known.groups.isEmpty()) reply.text(Command.PLAYER_INFO_GROUPS_NONE) else reply.text(Command.PLAYER_INFO_GROUPS, known.groups.joinToString(", "))
            known.player.note?.let { lines += reply.text(Command.NOTE, it) }
        }
        if (exOfficio.isNotEmpty()) lines += reply.text(Command.PLAYER_INFO_EX_OFFICIO, exOfficio.joinToString(", "))
        val order = resolver.order(subject).groups.map { it.name }
        val ownRules = known != null && known.player.rules.isNotEmpty()
        lines += reply.text(Command.PLAYER_INFO_ORDER, (if (ownRules) listOf(reply.text(Command.PLAYER_INFO_OWN_RULES).string) else emptyList<String>()).plus(order).joinToString(", "))
        lines += rulesBlock(reply, known?.player?.rules.orEmpty(), Command.PLAYER_INFO_RULES, Command.PLAYER_INFO_RULES_NONE)
        for (ineffective in resolver.ineffectiveRules(subject).filter { it.hit.owner is RuleSetOwner.OfPlayer }) {
            lines += reply.text(Command.PLAYER_INFO_INEFFECTIVE, ineffective.hit.key.text, ruleValue(reply, ineffective.hit.rule), ineffective.closingKey.text)
        }
        return reply.lines(lines)
    }

    /** Relit et valide les deux fichiers sans rien appliquer, et répond comme `reload` : les constats, puis les règles qui changeraient (5.3). */
    fun validate(reply: Reply): Int {
        val current = state(reply) ?: return 0
        reply.later(FileThread.check()) { read ->
            val lines = ArrayList<Component>()
            when (read) {
                is ConfigurationRead.Valid -> {
                    lines += reply.text(Command.VALIDATE_VALID, read.report.warnings.size)
                    for (issue in read.report.warnings) lines += reply.issue(issue)
                    val changes = PermissionsChanges.between(current, EffectiveState(current.generation, read.configuration)).rules
                    if (changes.isEmpty()) {
                        lines += reply.text(Command.VALIDATE_NO_CHANGE)
                    } else {
                        lines += reply.text(Command.VALIDATE_CHANGES, changes.size)
                        for (change in changes) lines += reply.text(Command.CHANGE_RULE, ownerOf(reply, change.owner), change.key.text, valueOrNone(reply, change.before), valueOrNone(reply, change.after))
                    }
                }
                is ConfigurationRead.Refused -> {
                    lines += reply.text(Command.VALIDATE_REFUSED, read.report.errors.size)
                    for (issue in read.report.issues) lines += reply.issue(issue)
                }
            }
            reply.lines(lines)
        }
        return 1
    }

    // ── Les briques communes ────────────────────────────────────────────────────

    /** L'état en vigueur, ou un refus quand rien n'est publié encore. */
    fun state(reply: Reply): EffectiveState? = Publisher.currentOrNull()?.state ?: run {
        reply.refuse(Command.NOT_PUBLISHED)
        null
    }

    /** La clé que [text] écrit, ou un refus qui dit le défaut (2.4). */
    fun parseKey(reply: Reply, text: String): Key? = try {
        Keys.parse(text.trim())
    } catch (malformed: MalformedKeyException) {
        reply.refuse(Command.KEY_MALFORMED, malformed.input, reply.text(malformed.defect.translationKey))
        null
    }

    fun modeKey(mode: Mode): String = when (mode) {
        Mode.ENFORCE -> Command.MODE_ENFORCE
        Mode.OBSERVE -> Command.MODE_OBSERVE
    }

    fun ownerOf(reply: Reply, owner: RuleSetOwner): MutableComponent = when (owner) {
        is RuleSetOwner.OfGroup -> reply.text(Command.OWNER_GROUP, owner.name)
        is RuleSetOwner.OfPlayer -> reply.text(Command.OWNER_PLAYER, owner.readableForm)
    }

    /** La valeur d'une règle : `allow`, `deny`, ou un `deny` qui dit sa visibilité (2.7). */
    fun ruleValue(reply: Reply, rule: Rule): MutableComponent = when (rule.value) {
        RuleValue.ALLOW -> reply.text(Command.RULE_ALLOW)
        RuleValue.DENY -> when (rule.visible) {
            true -> reply.text(Command.RULE_DENY_VISIBLE)
            false -> reply.text(Command.RULE_DENY_HIDDEN)
            null -> reply.text(Command.RULE_DENY)
        }
    }

    fun valueOrNone(reply: Reply, rule: Rule?): MutableComponent = if (rule == null) reply.text(Command.RULE_NONE) else ruleValue(reply, rule)

    /** Les règles d'un ensemble, triées par clé, une ligne chacune avec sa note ; ou la ligne qui dit qu'il n'en a aucune. */
    fun rulesBlock(reply: Reply, rules: Map<String, Rule>, header: String, none: String): List<Component> {
        if (rules.isEmpty()) return listOf(reply.text(none))
        val lines = ArrayList<Component>()
        lines += reply.text(header, rules.size)
        for ((key, rule) in rules.entries.sortedWith(compareBy(CodePointOrder) { it.key })) {
            val line = reply.text(Command.RULE_ENTRY, key, ruleValue(reply, rule))
            rule.note?.let { line.append(reply.text(Command.RULE_NOTE, it)) }
            lines += line
        }
        return lines
    }
}
