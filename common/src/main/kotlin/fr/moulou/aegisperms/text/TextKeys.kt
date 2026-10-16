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
        const val CHANGES_MORE = COMMAND_PREFIX + "change.more"

        const val NOTHING_CHANGED = COMMAND_PREFIX + "change.nothing"
        const val CHANGE_PUBLISHED = COMMAND_PREFIX + "change.published"
        const val CHANGE_WARNINGS = COMMAND_PREFIX + "change.warnings"
        const val CHANGE_REFUSED = COMMAND_PREFIX + "change.refused"
        const val FILE_MOVED = COMMAND_PREFIX + "change.file_moved"
        const val READ_ONLY = COMMAND_PREFIX + "change.read_only"
        const val WRITE_FAILED = COMMAND_PREFIX + "change.write_failed"

        const val GROUP_CREATED = COMMAND_PREFIX + "group.created"
        const val GROUP_EXISTS = COMMAND_PREFIX + "group.exists"
        const val GROUP_NAME_MALFORMED = COMMAND_PREFIX + "group.name_malformed"
        const val GROUP_NAME_TAKEN = COMMAND_PREFIX + "group.name_taken"
        const val GROUP_DELETED = COMMAND_PREFIX + "group.deleted"
        const val GROUP_DEFAULT_PROTECTED = COMMAND_PREFIX + "group.default_protected"
        const val GROUP_REFERENCED = COMMAND_PREFIX + "group.referenced"
        const val GROUP_RENAMED = COMMAND_PREFIX + "group.renamed"
        const val GROUP_CLONED = COMMAND_PREFIX + "group.cloned"
        const val GROUP_WEIGHT_SET = COMMAND_PREFIX + "group.weight_set"
        const val GROUP_PARENT_ADDED = COMMAND_PREFIX + "group.parent_added"
        const val GROUP_PARENT_REMOVED = COMMAND_PREFIX + "group.parent_removed"
        const val GROUP_PARENT_SELF = COMMAND_PREFIX + "group.parent_self"
        const val GROUP_PARENT_DEFAULT = COMMAND_PREFIX + "group.parent_default"
        const val GROUP_PARENT_CYCLE = COMMAND_PREFIX + "group.parent_cycle"
        const val GROUP_PARENT_ABSENT = COMMAND_PREFIX + "group.parent_absent"
        const val GROUP_INCLUDE_OPS_SET = COMMAND_PREFIX + "group.include_ops_set"
        const val GROUP_INCLUDE_OPS_NONE_SET = COMMAND_PREFIX + "group.include_ops_none_set"
        const val INCLUDE_OPS_INVALID = COMMAND_PREFIX + "group.include_ops_invalid"
        const val RULE_SET = COMMAND_PREFIX + "rule.set"
        const val RULE_UNSET = COMMAND_PREFIX + "rule.unset"
        const val RULE_ABSENT = COMMAND_PREFIX + "rule.absent"
        const val NOTE_SET = COMMAND_PREFIX + "note.set"
        const val NOTE_CLEARED = COMMAND_PREFIX + "note.cleared"
        const val PLAYER_GROUP_ADDED = COMMAND_PREFIX + "player.group_added"
        const val PLAYER_GROUP_REMOVED = COMMAND_PREFIX + "player.group_removed"
        const val PLAYER_GROUP_DEFAULT = COMMAND_PREFIX + "player.group_default"
        const val PLAYER_GROUP_ABSENT = COMMAND_PREFIX + "player.group_absent"

        const val RELOADED = COMMAND_PREFIX + "reload.done"
        const val RELOAD_REFUSED = COMMAND_PREFIX + "reload.refused"
        const val BACKUP_DONE = COMMAND_PREFIX + "backup.done"
        const val BACKUP_INVALID_NAME = COMMAND_PREFIX + "backup.invalid_name"
        const val BACKUP_NOTHING = COMMAND_PREFIX + "backup.nothing"
        const val BACKUP_FAILED = COMMAND_PREFIX + "backup.failed"
        const val RESTORED = COMMAND_PREFIX + "restore.done"
        const val RESTORED_SAVED_AS = COMMAND_PREFIX + "restore.saved_as"
        const val RESTORE_UNKNOWN = COMMAND_PREFIX + "restore.unknown"
        const val RESTORE_INCOMPLETE = COMMAND_PREFIX + "restore.incomplete"
        const val RESTORE_REFUSED = COMMAND_PREFIX + "restore.refused"
        const val RESTORE_FAILED = COMMAND_PREFIX + "restore.failed"
        const val MODE_SET = COMMAND_PREFIX + "mode.set"
        const val MODE_UNTIL_RELOAD = COMMAND_PREFIX + "mode.until_reload"
    }

    /** Les statuts et les verdicts de `why`, et les lignes de `check` (5.5), `aegisperms.why.*`. */
    object Why {

        const val WHY_PREFIX = PREFIX + "why."

        const val TITLE = WHY_PREFIX + "title"
        const val OBSERVE = WHY_PREFIX + "observe"
        const val LEADS_TO = WHY_PREFIX + "leads_to"
        const val ROW = WHY_PREFIX + "row"
        const val OFFLINE = WHY_PREFIX + "offline"
        const val SET_ASIDE = WHY_PREFIX + "set_aside"
        const val SET_ASIDE_ENTRY = WHY_PREFIX + "set_aside.entry"
        const val TIE = WHY_PREFIX + "tie"

        const val STATUS_SEEN = WHY_PREFIX + "status.seen"
        const val STATUS_HIDDEN = WHY_PREFIX + "status.hidden"
        const val STATUS_VISIBLE_REFUSAL = WHY_PREFIX + "status.visible_refusal"
        const val STATUS_SEEN_UNDER_VISIBLE_REFUSAL = WHY_PREFIX + "status.seen_under_visible_refusal"
        const val STATUS_UNSEEN_MOD = WHY_PREFIX + "status.unseen_mod"
        const val STATUS_UNSEEN_VANILLA = WHY_PREFIX + "status.unseen_vanilla"
        const val STATUS_OUT_OF_CATALOG = WHY_PREFIX + "status.out_of_catalog"
        const val STATUS_NOT_EVALUATED = WHY_PREFIX + "status.not_evaluated"
        const val STATUS_INEFFECTIVE = WHY_PREFIX + "status.ineffective"
        const val STATUS_BARE_FORM = WHY_PREFIX + "status.bare_form"

        const val DECISION_ALLOW = WHY_PREFIX + "decision.allow"
        const val DECISION_DENY_VISIBLE = WHY_PREFIX + "decision.deny_visible"
        const val DECISION_DENY_HIDDEN = WHY_PREFIX + "decision.deny_hidden"
        const val DECISION_UNSET = WHY_PREFIX + "decision.unset"
        const val DECISION_VANILLA = WHY_PREFIX + "decision.vanilla"

        const val ORIGIN_STEP0 = WHY_PREFIX + "origin.step0"
        const val ORIGIN_PLAYER = WHY_PREFIX + "origin.player"
        const val ORIGIN_GROUP = WHY_PREFIX + "origin.group"
        const val ORIGIN_NONE = WHY_PREFIX + "origin.none"
        const val ORIGIN_ANCESTOR = WHY_PREFIX + "origin.ancestor"
        const val ORIGIN_VANILLA = WHY_PREFIX + "origin.vanilla"
        const val ORIGIN_REQUIREMENT_NONE = WHY_PREFIX + "origin.requirement_none"
        const val ORIGIN_REQUIREMENT_LEVEL = WHY_PREFIX + "origin.requirement_level"
        const val ORIGIN_REQUIREMENT_MOD = WHY_PREFIX + "origin.requirement_mod"

        const val VERDICT_PERMITTED = WHY_PREFIX + "verdict.permitted"
        const val VERDICT_REFUSED_HIDDEN = WHY_PREFIX + "verdict.refused_hidden"
        const val VERDICT_REFUSED_VISIBLE = WHY_PREFIX + "verdict.refused_visible"
        const val VERDICT_REFUSED_VANILLA = WHY_PREFIX + "verdict.refused_vanilla"
        const val VERDICT_INCOMPLETE = WHY_PREFIX + "verdict.incomplete"

        const val CHECK_TITLE = WHY_PREFIX + "check.title"
        const val CHECK_ALIAS = WHY_PREFIX + "check.alias"
        const val CHECK_DECISION = WHY_PREFIX + "check.decision"
        const val CHECK_RULE = WHY_PREFIX + "check.rule"
        const val CHECK_STEP0 = WHY_PREFIX + "check.step0"
        const val CHECK_SET_ASIDE = WHY_PREFIX + "check.set_aside"
        const val CHECK_NODE_PERMITTED = WHY_PREFIX + "check.node_permitted"
        const val CHECK_NODE_PERMITTED_VANILLA = WHY_PREFIX + "check.node_permitted_vanilla"
        const val CHECK_NODE_CLOSED = WHY_PREFIX + "check.node_closed"
        const val CHECK_NODE_REFUSED = WHY_PREFIX + "check.node_refused"
        const val CHECK_NODE_HIDDEN = WHY_PREFIX + "check.node_hidden"
        const val CHECK_ABSENT = WHY_PREFIX + "check.absent"
        const val CHECK_API_ABSENT = WHY_PREFIX + "check.api_absent"
        const val MORE_DEMANDING = WHY_PREFIX + "more_demanding"
    }

    /** Ce que le mod dit aux ops d'au moins `admin-level` (4.8, 4.9), `aegisperms.notice.*`. */
    object Notice {

        const val NOTICE_PREFIX = PREFIX + "notice."

        const val FALLBACK = NOTICE_PREFIX + "fallback"
        const val REINFORCED = NOTICE_PREFIX + "reinforced"
        const val WATCH_APPLIED = NOTICE_PREFIX + "watch_applied"
        const val WATCH_REFUSED = NOTICE_PREFIX + "watch_refused"
    }
}
