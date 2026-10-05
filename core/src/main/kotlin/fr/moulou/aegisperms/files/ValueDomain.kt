// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

/**
 * Le domaine d'une valeur qui n'est pas une liste fermée de noms, pour le constat d'une valeur hors de son domaine (cahier des charges, 4.5). Le texte de
 * chaque domaine vit sous sa clé de traduction, jamais dans le code (5.7). Un domaine fermé, lui, se dit par ses noms.
 */
enum class ValueDomain {

    /** Un entier, écrit sans guillemets. */
    INTEGER,

    /** `true` ou `false`, écrits sans guillemets. */
    BOOLEAN,

    /** Un texte, entre guillemets. */
    TEXT,

    /** Un objet, entre accolades. */
    OBJECT,

    /** Une liste, entre crochets. */
    LIST,

    /** Une règle : `"allow"`, `"deny"`, ou un objet qui porte `value`. */
    RULE,

    /** Un numéro de structure : un entier d'au moins 1, écrit sans guillemets. */
    SCHEMA_VERSION;

    /** La clé de traduction du domaine, sous celle du constat d'une valeur hors de son domaine. */
    val translationKey: String get() = "aegisperms.issue.value_out_of_domain." + name.lowercase()
}
