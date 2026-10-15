// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

/**
 * Les clés de traduction que le code nomme (cahier des charges, 5.7), hors celles des constats, `aegisperms.issue.<nature>`, que `IssueKind` et `KeyDefect`
 * portent, et celles du journal, `aegisperms.log.<événement>`, que `ModLog` porte. Les deux fichiers de langue portent chacune d'elles : l'étage 0 le vérifie
 * sur les constantes de cet objet et de ses objets imbriqués, l'étage 1 sur les événements du journal.
 */
object TextKeys {

    /** Le préfixe de toute clé du mod. */
    const val PREFIX = "aegisperms."

    /** Le préfixe des événements du journal (5.9). */
    const val LOG_PREFIX = PREFIX + "log."

    /** Le préfixe des constats (4.5). */
    const val ISSUE_PREFIX = PREFIX + "issue."

    /** Le message du refus visible (5.6), que la porte d'exécution envoie avec son repli. */
    const val REFUSAL = PREFIX + "refusal"

    /** Les réponses de `/aegis` (5.6), `aegisperms.command.<sous-commande>.<fait>`. */
    object Command {

        const val COMMAND_PREFIX = PREFIX + "command."

        const val SOURCE_REFUSED = COMMAND_PREFIX + "source_refused"
        const val NOT_PUBLISHED = COMMAND_PREFIX + "not_published"
        const val INTERNAL_ERROR = COMMAND_PREFIX + "internal_error"
        const val USAGE = COMMAND_PREFIX + "usage"
        const val YES = COMMAND_PREFIX + "yes"
        const val NO = COMMAND_PREFIX + "no"
        const val NOTE = COMMAND_PREFIX + "note"
        const val KEY_MALFORMED = COMMAND_PREFIX + "key_malformed"
        const val OWNER_GROUP = COMMAND_PREFIX + "owner.group"
        const val OWNER_PLAYER = COMMAND_PREFIX + "owner.player"
        const val MODE_ENFORCE = COMMAND_PREFIX + "mode.enforce"
        const val MODE_OBSERVE = COMMAND_PREFIX + "mode.observe"

        const val RULE_ENTRY = COMMAND_PREFIX + "rule.entry"
        const val RULE_NOTE = COMMAND_PREFIX + "rule.note"
        const val RULE_ALLOW = COMMAND_PREFIX + "rule.allow"
        const val RULE_DENY = COMMAND_PREFIX + "rule.deny"
        const val RULE_DENY_VISIBLE = COMMAND_PREFIX + "rule.deny_visible"
        const val RULE_DENY_HIDDEN = COMMAND_PREFIX + "rule.deny_hidden"
        const val RULE_NONE = COMMAND_PREFIX + "rule.none"

        const val STATUS_VERSION = COMMAND_PREFIX + "status.version"
        const val STATUS_GENERATION = COMMAND_PREFIX + "status.generation"
        const val STATUS_MODE = COMMAND_PREFIX + "status.mode"
        const val STATUS_READ_ONLY = COMMAND_PREFIX + "status.read_only"
        const val STATUS_WATCH = COMMAND_PREFIX + "status.watch"
        const val STATUS_ENVELOPE_OK = COMMAND_PREFIX + "status.envelope_ok"
        const val STATUS_ENVELOPE_REPLACED = COMMAND_PREFIX + "status.envelope_replaced"
        const val STATUS_FALLBACK = COMMAND_PREFIX + "status.fallback"
        const val STATUS_COUNTS = COMMAND_PREFIX + "status.counts"
        const val STATUS_ISSUES = COMMAND_PREFIX + "status.issues"

        const val CATALOG_UNAVAILABLE = COMMAND_PREFIX + "catalog.unavailable"
        const val CATALOG_NONE = COMMAND_PREFIX + "catalog.none"
        const val CATALOG_TITLE = COMMAND_PREFIX + "catalog.title"
        const val CATALOG_ENTRY = COMMAND_PREFIX + "catalog.entry"
        const val CATALOG_API_KEY = COMMAND_PREFIX + "catalog.api_key"
        const val CATALOG_MORE = COMMAND_PREFIX + "catalog.more"
        const val CATALOG_REQUIREMENT_NONE = COMMAND_PREFIX + "catalog.requirement_none"
        const val CATALOG_REQUIREMENT_LEVEL = COMMAND_PREFIX + "catalog.requirement_level"
        const val CATALOG_REQUIREMENT_MOD = COMMAND_PREFIX + "catalog.requirement_mod"
        const val CATALOG_EXECUTABLE = COMMAND_PREFIX + "catalog.executable"
        const val CATALOG_ALIAS_OF = COMMAND_PREFIX + "catalog.alias_of"
        const val CATALOG_SENSITIVE = COMMAND_PREFIX + "catalog.sensitive"
        const val CATALOG_SHARED = COMMAND_PREFIX + "catalog.shared"
        const val CATALOG_MORE_DEMANDING = COMMAND_PREFIX + "catalog.more_demanding"
        const val CATALOG_OWNER = COMMAND_PREFIX + "catalog.owner"
        const val CATALOG_CO_OWNERS = COMMAND_PREFIX + "catalog.co_owners"
        const val CATALOG_PREFIXED_FORM = COMMAND_PREFIX + "catalog.prefixed_form"
        const val CATALOG_ALIASES = COMMAND_PREFIX + "catalog.aliases"

        const val SEARCH_TITLE = COMMAND_PREFIX + "search.title"
        const val SEARCH_ENTRY = COMMAND_PREFIX + "search.entry"
        const val SEARCH_NONE = COMMAND_PREFIX + "search.none"

        const val GROUP_LIST_TITLE = COMMAND_PREFIX + "group.list.title"
        const val GROUP_LIST_ENTRY = COMMAND_PREFIX + "group.list.entry"
        const val GROUP_INHERITS = COMMAND_PREFIX + "group.inherits"
        const val GROUP_INCLUDE_OPS = COMMAND_PREFIX + "group.include_ops"
        const val GROUP_UNKNOWN = COMMAND_PREFIX + "group.unknown"
        const val GROUP_DEFAULT_NOTE = COMMAND_PREFIX + "group.default_note"
        const val GROUP_INFO_TITLE = COMMAND_PREFIX + "group.info.title"
        const val GROUP_INFO_PARENTS = COMMAND_PREFIX + "group.info.parents"
        const val GROUP_INFO_CHILDREN = COMMAND_PREFIX + "group.info.children"
        const val GROUP_INFO_INCLUDE_OPS = COMMAND_PREFIX + "group.info.include_ops"
        const val GROUP_INFO_INCLUDE_OPS_NONE = COMMAND_PREFIX + "group.info.include_ops_none"
        const val GROUP_INFO_RULES = COMMAND_PREFIX + "group.info.rules"
        const val GROUP_INFO_RULES_NONE = COMMAND_PREFIX + "group.info.rules_none"
        const val GROUP_INFO_MEMBERS = COMMAND_PREFIX + "group.info.members"
        const val GROUP_INFO_MEMBERS_NONE = COMMAND_PREFIX + "group.info.members_none"

        const val PLAYER_UNKNOWN = COMMAND_PREFIX + "player.unknown"
        const val PLAYER_AMBIGUOUS = COMMAND_PREFIX + "player.ambiguous"
        const val PLAYER_INFO_TITLE = COMMAND_PREFIX + "player.info.title"
        const val PLAYER_INFO_ONLINE = COMMAND_PREFIX + "player.info.online"
        const val PLAYER_INFO_OFFLINE = COMMAND_PREFIX + "player.info.offline"
        const val PLAYER_INFO_ABSENT = COMMAND_PREFIX + "player.info.absent"
        const val PLAYER_INFO_GROUPS = COMMAND_PREFIX + "player.info.groups"
        const val PLAYER_INFO_GROUPS_NONE = COMMAND_PREFIX + "player.info.groups_none"
        const val PLAYER_INFO_EX_OFFICIO = COMMAND_PREFIX + "player.info.ex_officio"
        const val PLAYER_INFO_ORDER = COMMAND_PREFIX + "player.info.order"
        const val PLAYER_INFO_OWN_RULES = COMMAND_PREFIX + "player.info.own_rules"
        const val PLAYER_INFO_RULES = COMMAND_PREFIX + "player.info.rules"
        const val PLAYER_INFO_RULES_NONE = COMMAND_PREFIX + "player.info.rules_none"
        const val PLAYER_INFO_INEFFECTIVE = COMMAND_PREFIX + "player.info.ineffective"

        const val VALIDATE_VALID = COMMAND_PREFIX + "validate.valid"
        const val VALIDATE_REFUSED = COMMAND_PREFIX + "validate.refused"
        const val VALIDATE_CHANGES = COMMAND_PREFIX + "validate.changes"
        const val VALIDATE_NO_CHANGE = COMMAND_PREFIX + "validate.no_change"
        const val CHANGE_RULE = COMMAND_PREFIX + "change.rule"
    }
}
