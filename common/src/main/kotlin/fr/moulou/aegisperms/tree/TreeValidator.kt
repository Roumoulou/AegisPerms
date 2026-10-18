// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind

/**
 * Les constats qui confrontent une configuration à l'arbre des commandes (cahier des charges, 4.5) : ses alias, ses préfixes et ses rattachements, face aux
 * commandes de l'index en service. La configuration est une candidate, pas forcément celle sur laquelle l'index s'est bâti : ce qu'elle ferait ajouter à
 * l'arbre se décide à neuf, sur les commandes telles que leurs mods les ont enregistrées.
 *
 * Les constats sortent dans un ordre qui ne dépend pas de celui des entrées : les règles des droits, puis les alias, les préfixes et les rattachements des
 * réglages, puis ce que l'arbre dit de lui-même.
 */
object TreeValidator {

    private const val GROUPS = "groups"
    private const val PLAYERS = "players"
    private const val RULES = "rules"
    private const val ALIASES = "aliases"
    private const val NAMESPACES = "namespaces"
    private const val PREFIXES = "prefixes"
    private const val OWNERS = "owners"

    /** Où s'écrit le remède d'un constat que l'arbre fait de lui-même : un rattachement donne un mod propriétaire à une commande. */
    private val OWNERS_PATH = listOf(NAMESPACES, OWNERS)

    fun <S> issues(index: CommandIndex<S>, configuration: Configuration): List<Issue> {
        val settings = configuration.settings
        val detected = index.ownersWith(emptyMap())
        val owners = index.ownersWith(settings.namespaces.owners)
        val plan = CommandForms.plan(index.root, index.originals, owners, settings, index.mods)

        val issues = ArrayList<Issue>()
        checkOwnerAssignmentOldKeys(configuration, detected, owners, issues)
        checkAliases(settings, plan, issues)
        checkPrefixes(settings, index.mods, issues)
        checkAssignments(settings, detected, index.mods, issues)
        for ((bareName, formName) in plan.takenFormNames.sorted()) {
            issues += Issue(IssueKind.PREFIXED_FORM_NAME_TAKEN, ConfigurationFile.SETTINGS, listOf(NAMESPACES, PREFIXES), literals(bareName, formName))
        }
        for ((rootName, owner) in owners.sorted()) {
            when {
                owner.source == OwnerSource.UNKNOWN -> issues += Issue(IssueKind.COMMAND_WITHOUT_OWNER, ConfigurationFile.SETTINGS, OWNERS_PATH, literals(rootName))
                // Un rattachement fixe le mod propriétaire d'une racine partagée : c'est le remède que le constat demande, il se tait alors. Il se tait aussi
                // pour une racine de vanilla sur laquelle un mod greffe une branche (D21, cahier 10.6) : un rattachement ne peut pas la viser, et sa clé ne
                // dépend pas de l'ordre de chargement, vanilla s'enregistrant le premier ; le catalogue garde les co-propriétaires.
                owner.source == OwnerSource.DETECTED && owner.coOwners.isNotEmpty() && owner.modId != Keys.MINECRAFT -> {
                    issues += Issue(IssueKind.SHARED_ROOT, ConfigurationFile.SETTINGS, OWNERS_PATH, literals(rootName, owner.modId, owner.coOwners.joinToString(", ")))
                }
            }
        }
        return issues
    }

    /**
     * Une règle qui porte l'ancienne clé d'une commande rattachée (3.2) : son espace de noms est celui que les classes donnent à la racine, alors qu'un
     * rattachement valide lui en donne un autre. La règle ne couvrirait plus rien.
     */
    private fun checkOwnerAssignmentOldKeys(configuration: Configuration, detected: Map<String, Owner>, owners: Map<String, Owner>, issues: MutableList<Issue>) {
        fun check(ownerPath: List<String>, rules: Map<String, Rule>) {
            for (keyText in rules.keys.sortedWith(CodePointOrder)) {
                val key = Keys.parseOrNull(keyText) as? CommandKey ?: continue
                val rootName = key.segments.firstOrNull() ?: continue
                val assigned = owners[rootName]?.takeIf { it.source == OwnerSource.ASSIGNED } ?: continue
                if (key.namespace == assigned.modId || key.namespace != detected.getValue(rootName).modId) continue
                issues += Issue(IssueKind.OWNER_ASSIGNMENT_OLD_KEY, ConfigurationFile.PERMISSIONS, ownerPath + RULES + keyText, literals(keyText, Keys.command(assigned.modId, key.segments).text))
            }
        }
        for ((name, group) in configuration.permissions.groups.sorted()) check(listOf(GROUPS, name), group.rules)
        for ((readableForm, player) in configuration.permissions.players.sorted()) check(listOf(PLAYERS, readableForm), player.rules)
    }

    private fun checkAliases(settings: Settings, plan: FormPlan<*>, issues: MutableList<Issue>) {
        for ((name, outcome) in plan.aliasOutcomes.sorted()) {
            val target = settings.aliases.getValue(name)
            val path = listOf(ALIASES, name)
            when (outcome) {
                AliasOutcome.Added, AliasOutcome.RestoresBareForm -> {}
                AliasOutcome.NameTaken -> issues += Issue(IssueKind.ALIAS_NAME_TAKEN, ConfigurationFile.SETTINGS, path, literals(name))
                AliasOutcome.TargetMissing -> issues += Issue(IssueKind.ALIAS_TARGET_MISSING, ConfigurationFile.SETTINGS, path, literals(name, target))
                is AliasOutcome.TargetNotLiterals -> issues += Issue(IssueKind.ALIAS_TARGET_NOT_LITERALS, ConfigurationFile.SETTINGS, path, literals(name, target, outcome.word))
                is AliasOutcome.TypedPrefix -> issues += Issue(IssueKind.ALIAS_TARGET_TYPED_PREFIX, ConfigurationFile.SETTINGS, path, literals(name, target, outcome.expected))
            }
        }
    }

    private fun checkPrefixes(settings: Settings, mods: ModLocator, issues: MutableList<Issue>) {
        for ((modId, prefix) in settings.namespaces.prefixes.sorted()) {
            if (CommandForms.prefixOf(modId, settings.namespaces.prefixes, mods) != prefix) issues += Issue(IssueKind.PREFIX_YIELDS, ConfigurationFile.SETTINGS, listOf(NAMESPACES, PREFIXES, modId), literals(modId, prefix))
        }
    }

    /** Un rattachement ignoré (3.2) : sa commande n'est pas sur le serveur, elle est de vanilla, ou son mod n'est pas chargé. */
    private fun checkAssignments(settings: Settings, detected: Map<String, Owner>, mods: ModLocator, issues: MutableList<Issue>) {
        for ((rootName, modId) in settings.namespaces.owners.sorted()) {
            val path = OWNERS_PATH + rootName
            val owner = detected[rootName]
            when {
                owner == null -> issues += Issue(IssueKind.OWNER_ASSIGNMENT_UNKNOWN_COMMAND, ConfigurationFile.SETTINGS, path, literals(rootName))
                owner.modId == Keys.MINECRAFT -> issues += Issue(IssueKind.OWNER_ASSIGNMENT_VANILLA, ConfigurationFile.SETTINGS, path, literals(rootName, modId))
                !mods.isLoaded(modId) -> issues += Issue(IssueKind.OWNER_ASSIGNMENT_UNKNOWN_MOD, ConfigurationFile.SETTINGS, path, literals(rootName, modId))
            }
        }
    }

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)

    private fun <V> Map<String, V>.sorted(): List<Map.Entry<String, V>> = entries.sortedWith(compareBy(CodePointOrder) { it.key })
}
