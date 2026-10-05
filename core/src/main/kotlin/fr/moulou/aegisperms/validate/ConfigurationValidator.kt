// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.MalformedKeyException
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSet
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.model.SensitiveKeys
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.ExaminationOrder
import fr.moulou.aegisperms.resolve.Membership
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.StartingGroup
import fr.moulou.aegisperms.resolve.Subject
import java.util.UUID

/**
 * La validation d'une configuration entière, réglages et droits ensemble (cahier des charges, 4.5). Elle est déterministe : ses constats sortent dans le
 * même ordre quel que soit celui des entrées, les droits d'abord, groupes puis joueurs par nom croissant, puis les réglages, puis ceux que le catalogue tire
 * de l'arbre des commandes.
 *
 * Ce que seule la lecture d'un fichier sait dire, un fichier illisible, une clé inconnue ou écrite deux fois, une valeur hors d'un domaine fermé, se
 * constate à la lecture.
 */
object ConfigurationValidator {

    private const val GROUPS = "groups"
    private const val PLAYERS = "players"
    private const val RULES = "rules"
    private const val PARENTS = "parents"
    private const val ALIASES = "aliases"
    private const val NAMESPACES = "namespaces"
    private const val PREFIXES = "prefixes"
    private const val REQUIRE_PREFIX = "require-prefix"

    /** Un préfixe s'écrit avec les caractères d'un modid (3.3). */
    private val PREFIX = Regex("[a-z0-9_-]+")

    /** Valide [configuration]. [catalog] manque tant que le serveur n'a pas d'index : les constats qui en dépendent se taisent. */
    fun validate(configuration: Configuration, catalog: Catalog? = null): ValidationReport {
        val issues = mutableListOf<Issue>()
        checkGroups(configuration.permissions, issues)
        checkPlayers(configuration.permissions, issues)
        checkSettings(configuration.settings, issues)
        if (catalog != null) issues += catalog.treeIssues(configuration)

        // Les constats qui suivent demandent de résoudre, donc une configuration qui se compile : une erreur les arrête.
        if (issues.any { it.severity == Severity.ERROR }) return ValidationReport(issues)

        val resolver = Resolver(EffectiveState(0, configuration))
        checkGroupRules(resolver.state, catalog, issues)
        checkPlayerRules(resolver, catalog, issues)
        checkWeightTies(resolver, issues)
        return ValidationReport(issues)
    }

    /** Les cycles d'héritage, chacun écrit comme la suite des groupes qui revient à son premier. */
    internal fun inheritanceCycles(groups: Map<String, Group>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val done = HashSet<String>()
        val reported = HashSet<Set<String>>()
        fun visit(name: String, path: List<String>) {
            val index = path.indexOf(name)
            if (index >= 0) {
                val cycle = path.subList(index, path.size) + name
                if (reported.add(cycle.toSet())) cycles += cycle
                return
            }
            if (name in done) return
            val group = groups[name] ?: return
            for (parent in group.parents) visit(parent, path + name)
            done += name
        }
        for (name in groups.keys.sortedWith(CodePointOrder)) visit(name, emptyList())
        return cycles
    }

    private fun checkGroups(permissions: Permissions, issues: MutableList<Issue>) {
        val groups = permissions.groups
        if (Group.DEFAULT !in groups) issues += Issue(IssueKind.MISSING_DEFAULT, ConfigurationFile.PERMISSIONS, listOf(GROUPS))
        for ((name, group) in groups.sortedByKey()) {
            val groupPath = listOf(GROUPS, name)
            if (!Group.isValidName(name)) issues += Issue(IssueKind.MALFORMED_GROUP_NAME, ConfigurationFile.PERMISSIONS, groupPath, literals(name))
            val seen = HashSet<String>()
            group.parents.forEachIndexed { index, parent ->
                val parentPath = groupPath + PARENTS + index.toString()
                when {
                    !seen.add(parent) -> issues += Issue(IssueKind.DUPLICATE_PARENT, ConfigurationFile.PERMISSIONS, parentPath, literals(name, parent))
                    parent !in groups -> issues += Issue(IssueKind.UNKNOWN_PARENT, ConfigurationFile.PERMISSIONS, parentPath, literals(name, parent))
                }
            }
            checkWrittenRules(groupPath, group.rules, issues)
        }
        for (cycle in inheritanceCycles(groups)) {
            issues += Issue(IssueKind.INHERITANCE_CYCLE, ConfigurationFile.PERMISSIONS, listOf(GROUPS, cycle.first(), PARENTS), literals(cycle.joinToString(" > ")))
        }
    }

    private fun checkPlayers(permissions: Permissions, issues: MutableList<Issue>) {
        val readableFormOf = HashMap<UUID, String>()
        for ((readableForm, player) in permissions.players.sortedByKey()) {
            val playerPath = listOf(PLAYERS, readableForm)
            val uuidText = ReadableForm.split(readableForm)?.second
            val uuid = uuidText?.let(ReadableForm::parseUuid)
            when {
                uuidText == null || uuid == null -> issues += Issue(IssueKind.MALFORMED_READABLE_FORM, ConfigurationFile.PERMISSIONS, playerPath, literals(readableForm))
                uuid.toString() != uuidText -> issues += Issue(IssueKind.NON_CANONICAL_UUID, ConfigurationFile.PERMISSIONS, playerPath, literals(uuidText, uuid.toString()))
                else -> readableFormOf.putIfAbsent(uuid, readableForm)?.let { first ->
                    issues += Issue(IssueKind.DUPLICATE_UUID, ConfigurationFile.PERMISSIONS, playerPath, literals(uuidText, first, readableForm))
                }
            }
            val seen = HashSet<String>()
            player.groups.forEachIndexed { index, group ->
                val groupPath = playerPath + GROUPS + index.toString()
                when {
                    !seen.add(group) -> issues += Issue(IssueKind.DUPLICATE_PLAYER_GROUP, ConfigurationFile.PERMISSIONS, groupPath, literals(group))
                    group == Group.DEFAULT -> issues += Issue(IssueKind.DEFAULT_AS_DIRECT_GROUP, ConfigurationFile.PERMISSIONS, groupPath)
                    group !in permissions.groups -> issues += Issue(IssueKind.UNKNOWN_GROUP, ConfigurationFile.PERMISSIONS, groupPath, literals(group))
                }
            }
            checkWrittenRules(playerPath, player.rules, issues)
        }
    }

    /** Ce qui se constate d'une règle telle qu'elle est écrite, avant toute résolution. */
    private fun checkWrittenRules(ownerPath: List<String>, rules: Map<String, Rule>, issues: MutableList<Issue>) {
        for ((keyText, rule) in rules.sortedByKey()) {
            val rulePath = ownerPath + RULES + keyText
            try {
                Keys.parse(keyText)
            } catch (malformed: MalformedKeyException) {
                issues += Issue(IssueKind.MALFORMED_KEY, ConfigurationFile.PERMISSIONS, rulePath, listOf(IssueArgument.Literal(keyText), IssueArgument.Translatable(malformed.defect.translationKey)))
            }
            if (rule.value == RuleValue.ALLOW && rule.visible != null) issues += Issue(IssueKind.VISIBLE_ON_ALLOW, ConfigurationFile.PERMISSIONS, rulePath, literals(keyText))
        }
    }

    private fun checkSettings(settings: Settings, issues: MutableList<Issue>) {
        for (name in settings.aliases.keys.sortedWith(CodePointOrder)) {
            if (name.isEmpty() || name.any { it.isWhitespace() || it == '/' }) issues += Issue(IssueKind.MALFORMED_ALIAS_NAME, ConfigurationFile.SETTINGS, listOf(ALIASES, name), literals(name))
        }
        val modIdOf = HashMap<String, String>()
        for ((modId, prefix) in settings.namespaces.prefixes.sortedByKey()) {
            val prefixPath = listOf(NAMESPACES, PREFIXES, modId)
            if (!PREFIX.matches(prefix)) {
                issues += Issue(IssueKind.MALFORMED_PREFIX, ConfigurationFile.SETTINGS, prefixPath, literals(modId, prefix))
            } else {
                modIdOf.putIfAbsent(prefix, modId)?.let { first -> issues += Issue(IssueKind.DUPLICATE_PREFIX, ConfigurationFile.SETTINGS, prefixPath, literals(prefix, first, modId)) }
            }
        }
        if (settings.namespaces.requirePrefix && !settings.namespaces.enabled) {
            issues += Issue(IssueKind.REQUIRE_PREFIX_WITHOUT_FORMS, ConfigurationFile.SETTINGS, listOf(NAMESPACES, REQUIRE_PREFIX))
        }
    }

    private fun checkGroupRules(state: EffectiveState, catalog: Catalog?, issues: MutableList<Issue>) {
        for (name in state.groupRules.keys.sortedWith(CodePointOrder)) {
            val lineage = lineageOf(state, name)
            for ((key, rule) in state.groupRules.getValue(name).sortedRules()) {
                val rulePath = listOf(GROUPS, name, RULES, key.text)
                if (rule.value == RuleValue.ALLOW && key is CommandKey) {
                    refusedInLineage(lineage, key)?.let { issues += Issue(IssueKind.ALLOW_UNDER_REFUSAL, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, it.text)) }
                }
                checkRule(rulePath, key, rule, catalog, issues)
            }
        }
    }

    /** Les ensembles de règles d'un groupe et de ses ancêtres, dans l'ordre où ils s'examineraient pour un sujet qui n'aurait que lui. */
    private fun lineageOf(state: EffectiveState, name: String): List<RuleSet> =
        ExaminationOrder.from(state.permissions.groups, listOf(StartingGroup(name, Membership.DIRECT, 0))).groups.map { state.groupRules.getValue(it.name) }

    /** La première clé du chemin de [key], plus haut que lui, que [lineage] refuse. */
    private fun refusedInLineage(lineage: List<RuleSet>, key: CommandKey): CommandKey? = key.pathKeys.firstOrNull { pathKey ->
        pathKey !== key && lineage.firstNotNullOfOrNull { ruleSet -> ruleSet.mostPrecise(pathKey)?.let { ruleSet.rules.getValue(it) } }?.value == RuleValue.DENY
    }

    private fun checkPlayerRules(resolver: Resolver, catalog: Catalog?, issues: MutableList<Issue>) {
        for (player in resolver.state.players.values.sortedWith(compareBy(CodePointOrder) { it.readableForm })) {
            // Le fichier des droits ne dit pas qui est op : un joueur s'y juge comme un non-op.
            val ineffective = resolver.ineffectiveRules(Subject(player.uuid, 0)).filter { it.hit.owner is RuleSetOwner.OfPlayer }.associate { it.hit.key to it.closingKey }
            for ((key, rule) in player.rules.sortedRules()) {
                val rulePath = listOf(PLAYERS, player.readableForm, RULES, key.text)
                ineffective[key]?.let { issues += Issue(IssueKind.ALLOW_UNDER_REFUSAL, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, it.text)) }
                checkRule(rulePath, key, rule, catalog, issues)
            }
        }
    }

    /** Ce qui se constate d'une règle sans son ensemble : les clés sensibles qu'elle ouvre, et ce que le catalogue sait de sa clé. */
    private fun checkRule(rulePath: List<String>, key: Key, rule: Rule, catalog: Catalog?, issues: MutableList<Issue>) {
        if (rule.value == RuleValue.ALLOW && key is CommandKey && key.isNode) {
            // Une clé générale ouvre tout par définition : elle n'avertit pas.
            for ((reason, opened) in SensitiveKeys.all.filter { key.covers(it.key) }.groupBy { it.reason }) {
                val arguments = listOf(IssueArgument.Literal(key.text), IssueArgument.Literal(opened.joinToString(", ") { it.key.text }), IssueArgument.Translatable(reason.translationKey))
                issues += Issue(IssueKind.ALLOW_SENSITIVE, ConfigurationFile.PERMISSIONS, rulePath, arguments)
            }
        }
        if (catalog == null) return
        when (key) {
            is CommandKey -> checkCommandKey(rulePath, key, rule, catalog, issues)
            is ApiKey -> if (!catalog.hasApiKey(key)) issues += Issue(IssueKind.UNKNOWN_API_KEY, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text))
        }
    }

    private fun checkCommandKey(rulePath: List<String>, key: CommandKey, rule: Rule, catalog: Catalog, issues: MutableList<Issue>) {
        // La clé `command` n'a rien à chercher au catalogue.
        val namespace = key.namespace ?: return
        if (key.isNode) {
            catalog.aliasTarget(key)?.let {
                issues += Issue(IssueKind.ALIAS_KEY, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, it.text))
                return
            }
        }
        val known = if (key.isNode) catalog.hasCommandKey(key) else catalog.hasNamespace(namespace)
        when {
            !known && rule.value == RuleValue.DENY -> {
                val appeared = if (key.isNode) catalog.appearedNear(key) else emptyList()
                issues += if (appeared.isEmpty()) {
                    Issue(IssueKind.DENY_KEY_GONE, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text))
                } else {
                    Issue(IssueKind.DENY_KEY_GONE_NEAR, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, appeared.joinToString(", ") { it.text }))
                }
            }
            !known -> {
                val nearest = if (key.isNode) catalog.nearestCommandKey(key) else null
                issues += if (nearest == null) {
                    Issue(IssueKind.UNKNOWN_COMMAND_KEY, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text))
                } else {
                    Issue(IssueKind.UNKNOWN_COMMAND_KEY_NEAR, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, nearest.text))
                }
            }
            key.isNode && rule.value == RuleValue.ALLOW -> {
                for (descendant in catalog.demandingDescendants(key)) {
                    issues += Issue(IssueKind.ALLOW_DEMANDING, ConfigurationFile.PERMISSIONS, rulePath, literals(key.text, descendant.key.text, descendant.opLevel.toString()))
                }
            }
        }
    }

    /**
     * Les égalités de poids qui jouent chez un joueur du fichier : une par paire de groupes, avec la première clé en désaccord. Un joueur qui n'a pas deux
     * groupes de départ de même poids, `default` mis à part, passe sans calcul.
     */
    private fun checkWeightTies(resolver: Resolver, issues: MutableList<Issue>) {
        val state = resolver.state
        for (player in state.players.values.sortedWith(compareBy(CodePointOrder) { it.readableForm })) {
            val subject = Subject(player.uuid, 0)
            val order = resolver.order(subject)
            if (order.startingGroups.filter { it.name != Group.DEFAULT }.zipWithNext().none { (first, second) -> first.weight == second.weight }) continue
            val keys = (player.rules.rules.keys + order.groups.flatMap { state.groupRules.getValue(it.name).rules.keys }).sortedWith(compareBy(CodePointOrder) { it.text })
            val told = HashSet<Pair<String, String>>()
            for (key in keys) {
                for (tie in resolver.explain(subject, key).weightTies) {
                    if (!told.add(tie.winner to tie.other)) continue
                    issues += Issue(IssueKind.WEIGHT_TIE, ConfigurationFile.PERMISSIONS, listOf(PLAYERS, player.readableForm, GROUPS), literals(tie.winner, tie.other, tie.weight.toString(), key.text))
                }
            }
        }
    }

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)

    private fun <V> Map<String, V>.sortedByKey(): List<Map.Entry<String, V>> = entries.sortedWith(compareBy(CodePointOrder) { it.key })

    private fun RuleSet.sortedRules(): List<Map.Entry<Key, Rule>> = rules.entries.sortedWith(compareBy(CodePointOrder) { it.key.text })
}
