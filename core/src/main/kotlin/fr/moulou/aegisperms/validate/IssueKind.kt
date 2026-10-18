// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

/**
 * La nature d'un constat (cahier des charges, 4.5), avec son niveau. Chaque nature dit les arguments que son texte attend, dans l'ordre : celui qui tient
 * les fichiers de langue (5.7) s'y fie.
 *
 * Les sept premières sont celles que seule la lecture d'un fichier sait constater : c'est elle qui les émet, avant toute validation. Celles qui confrontent
 * les réglages à l'arbre des commandes, des alias aux racines partagées, viennent de qui tient l'index, par [Catalog.treeIssues].
 */
enum class IssueKind(val severity: Severity) {

    /** Un fichier qui ne se lit pas : absent quand le mod le relit, verrouillé, ou un dossier à sa place. Argument : ce que le système en dit. */
    UNREADABLE_FILE(Severity.ERROR),

    /** Un fichier qui n'est pas du JSON strict, ou que sa structure fait refuser sans que la lecture sache dire où (4.2). Argument : ce que le parseur en dit. */
    MALFORMED_FILE(Severity.ERROR),

    /** Un champ écrit deux fois dans un même objet : un groupe, un joueur ou une règle en double (4.12). Argument : son nom. */
    DUPLICATE_KEY(Severity.ERROR),

    /** Un fichier sans `schema-version` (4.2). Sans argument. */
    MISSING_SCHEMA_VERSION(Severity.ERROR),

    /** Un fichier d'un numéro de structure plus récent que le mod. Arguments : le numéro du fichier, celui que le mod lit. */
    NEWER_SCHEMA_VERSION(Severity.ERROR),

    /** Une clé que le fichier ne connaît pas à cet endroit, une faute de frappe le plus souvent. Argument : la clé. */
    UNKNOWN_KEY(Severity.ERROR),

    /**
     * Une valeur hors de son domaine : une valeur fermée inconnue, `chefs` pour `admin-level`, ou une valeur d'un autre type que le sien, un nombre entre
     * guillemets. Arguments : la valeur telle qu'écrite, son domaine.
     */
    VALUE_OUT_OF_DOMAIN(Severity.ERROR),

    /** Le groupe `default` manque. Sans argument. */
    MISSING_DEFAULT(Severity.ERROR),

    /** Un nom de groupe qui porte autre chose que des minuscules, des chiffres, `_` et `-`. Argument : le nom. */
    MALFORMED_GROUP_NAME(Severity.ERROR),

    /** Un groupe hérite d'un groupe qui n'existe pas. Arguments : le groupe, le parent. */
    UNKNOWN_PARENT(Severity.ERROR),

    /** Un cycle d'héritage. Argument : le cycle, écrit `a > b > a`. */
    INHERITANCE_CYCLE(Severity.ERROR),

    /** Une clé de règle mal formée (2.4). Arguments : la clé telle qu'écrite, son défaut. */
    MALFORMED_KEY(Severity.ERROR),

    /** Une forme lisible qui n'a pas ses deux parts, ou dont l'UUID ne se lit pas. Argument : la forme telle qu'écrite. */
    MALFORMED_READABLE_FORM(Severity.ERROR),

    /** Un UUID qui se lit, sans être écrit en minuscules avec ses tirets. Arguments : l'UUID tel qu'écrit, sa forme canonique. */
    NON_CANONICAL_UUID(Severity.ERROR),

    /** Un UUID porté par deux joueurs. Arguments : l'UUID, la première forme lisible qui le porte, la seconde. */
    DUPLICATE_UUID(Severity.ERROR),

    /** Un joueur inscrit dans un groupe qui n'existe pas. Argument : le groupe. */
    UNKNOWN_GROUP(Severity.ERROR),

    /** Un nom d'alias vide, ou qui porte un blanc ou une barre oblique (3.6). Argument : le nom. */
    MALFORMED_ALIAS_NAME(Severity.ERROR),

    /** Un préfixe qui porte autre chose que des minuscules, des chiffres, `_` et `-` (3.3). Arguments : le modid, le préfixe. */
    MALFORMED_PREFIX(Severity.ERROR),

    /** Un préfixe donné à deux mods. Arguments : le préfixe, le premier modid, le second. */
    DUPLICATE_PREFIX(Severity.ERROR),

    /**
     * Un alias dont la cible n'est pas une suite de littéraux (3.6) : une valeur d'argument, le plus souvent. Arguments : le nom de l'alias, sa cible telle
     * qu'écrite, le mot qui n'est pas un littéral.
     */
    ALIAS_TARGET_NOT_LITERALS(Severity.ERROR),

    /** Un alias dont la cible s'écrit avec le préfixe qu'on tape au lieu du modid (3.6). Arguments : le nom de l'alias, sa cible telle qu'écrite, l'écriture attendue. */
    ALIAS_TARGET_TYPED_PREFIX(Severity.ERROR),

    /** Une règle qui porte l'ancienne clé d'une commande rattachée à un autre mod (3.2). Arguments : la clé de la règle, la clé que le rattachement lui donne. */
    OWNER_ASSIGNMENT_OLD_KEY(Severity.ERROR),

    /** Un groupe nomme deux fois le même parent. Arguments : le groupe, le parent. */
    DUPLICATE_PARENT(Severity.WARNING),

    /** Un groupe inscrit deux fois pour un joueur. Argument : le groupe. */
    DUPLICATE_PLAYER_GROUP(Severity.WARNING),

    /** `default` inscrit comme groupe direct d'un joueur, à qui il s'applique déjà. Sans argument. */
    DEFAULT_AS_DIRECT_GROUP(Severity.WARNING),

    /** `visible` écrit sur un `allow`, où il est ignoré. Argument : la clé de la règle. */
    VISIBLE_ON_ALLOW(Severity.WARNING),

    /** Un `allow` sans effet, sous le refus d'une clé de son chemin (2.7). Arguments : la clé de la règle, la clé refusée. */
    ALLOW_UNDER_REFUSAL(Severity.WARNING),

    /**
     * Un `allow` de nœud qui ouvre des clés sensibles (2.9), une fois par raison. Arguments : la clé de la règle, les clés sensibles qu'elle ouvre pour
     * cette raison, séparées par des virgules, la raison.
     */
    ALLOW_SENSITIVE(Severity.WARNING),

    /**
     * Deux groupes de départ de même poids décident différemment d'une clé chez un joueur, et l'ordre des noms tranche (2.6). Arguments : le groupe qui
     * l'emporte, l'autre, leur poids, la première clé en désaccord.
     */
    WEIGHT_TIE(Severity.WARNING),

    /** `require-prefix` activé sans les formes préfixées, où il est ignoré (3.5). Sans argument. */
    REQUIRE_PREFIX_WITHOUT_FORMS(Severity.WARNING),

    /** Une clé de commande que le catalogue ne connaît pas. Argument : la clé. */
    UNKNOWN_COMMAND_KEY(Severity.WARNING),

    /** Une clé de commande que le catalogue ne connaît pas, quand il en propose une proche. Arguments : la clé, la plus proche. */
    UNKNOWN_COMMAND_KEY_NEAR(Severity.WARNING),

    /** Une clé d'API ni connue de vanilla ni jamais demandée. Argument : la clé. */
    UNKNOWN_API_KEY(Severity.WARNING),

    /** Une règle sur la clé d'un alias de vanilla, qui ne couvre rien (2.4). Arguments : la clé de l'alias, celle de sa cible. */
    ALIAS_KEY(Severity.WARNING),

    /** Un `allow` de nœud qui couvre un descendant plus exigeant (2.8). Arguments : la clé de la règle, celle du descendant, le niveau d'op qu'il demande. */
    ALLOW_DEMANDING(Severity.WARNING),

    /** Un alias dont le nom est déjà celui d'une commande racine ou d'une forme préfixée, ignoré (3.6). Argument : le nom de l'alias. */
    ALIAS_NAME_TAKEN(Severity.WARNING),

    /** Un alias dont la commande visée n'est pas sur le serveur, ignoré (3.6). Arguments : le nom de l'alias, sa cible telle qu'écrite. */
    ALIAS_TARGET_MISSING(Severity.WARNING),

    /** Un préfixe qui est le modid d'un autre mod chargé, et lui cède la place (3.3). Arguments : le modid à qui la table le donne, le préfixe. */
    PREFIX_YIELDS(Severity.WARNING),

    /** Une forme préfixée dont le nom est déjà pris à la racine, refusée (3.8). Arguments : le nom nu de la commande, le nom de la forme. */
    PREFIXED_FORM_NAME_TAKEN(Severity.WARNING),

    /** Un rattachement qui vise une commande racine de vanilla, ignoré (3.2). Arguments : la commande racine, le modid. */
    OWNER_ASSIGNMENT_VANILLA(Severity.WARNING),

    /** Un rattachement vers un mod qui n'est pas chargé, ignoré (3.2). Arguments : la commande racine, le modid. */
    OWNER_ASSIGNMENT_UNKNOWN_MOD(Severity.WARNING),

    /** Un rattachement d'une commande racine qui n'est pas sur le serveur, ignoré (3.2). Argument : la commande racine. */
    OWNER_ASSIGNMENT_UNKNOWN_COMMAND(Severity.WARNING),

    /** Une commande racine sans mod propriétaire connu : ses clés prennent l'espace de noms `unknown`, jusqu'à un rattachement (3.2). Argument : la commande racine. */
    COMMAND_WITHOUT_OWNER(Severity.WARNING),

    /**
     * Une commande racine que plusieurs mods se partagent : son mod propriétaire peut changer avec l'ordre de chargement, jusqu'à un rattachement (3.2).
     * Arguments : la commande racine, son mod propriétaire, les co-propriétaires, séparés par des virgules.
     */
    SHARED_ROOT(Severity.WARNING),

    /** Un `deny` dont la clé n'existe plus : ce qu'il fermait ne l'est peut-être plus. Argument : la clé. */
    DENY_KEY_GONE(Severity.REINFORCED),

    /** Un `deny` dont la clé n'existe plus, quand des clés sont apparues au même endroit du catalogue. Arguments : la clé, les clés apparues, séparées par des virgules. */
    DENY_KEY_GONE_NEAR(Severity.REINFORCED);

    /** La clé de traduction du texte du constat. */
    val translationKey: String get() = "aegisperms.issue." + name.lowercase()
}

/** Le niveau d'un constat (4.5) : une erreur refuse la configuration, un avertissement la laisse se publier, un avertissement renforcé se répète à chaque démarrage et à chaque connexion d'un op d'au moins `admin-level`. */
enum class Severity {

    /** La configuration est refusée. */
    ERROR,

    /** La configuration s'applique, et le constat se dit. */
    WARNING,

    /** Un avertissement répété à chaque démarrage et à chaque connexion d'un op d'au moins `admin-level`, jusqu'à correction. */
    REINFORCED,
}
