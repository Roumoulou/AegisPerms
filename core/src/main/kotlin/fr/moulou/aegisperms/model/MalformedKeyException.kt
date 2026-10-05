// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/** Une clé qui ne suit pas la grammaire du cahier des charges (2.4) : le texte reçu et son défaut. */
class MalformedKeyException(val input: String, val defect: KeyDefect) : IllegalArgumentException("Malformed key '$input': $defect")

/** Ce qui rend une clé mal formée. Le texte de chaque défaut vit sous sa clé de traduction, jamais dans le code (5.7). */
enum class KeyDefect {

    /** Ni la clé `command`, ni un espace de noms devant un deux-points. */
    MISSING_NAMESPACE,

    /** Un espace de noms qui porte autre chose que des minuscules, des chiffres, `_`, `-` et `.`. */
    INVALID_NAMESPACE,

    /** Rien après le deux-points. */
    EMPTY_PATH,

    /** Un nom de nœud vide : deux points de suite, ou un point final. */
    EMPTY_NODE_NAME,

    /** Un nom de nœud qui porte un blanc ou un caractère de contrôle. */
    INVALID_NODE_NAME,

    /** Un `%` qui n'ouvre ni `%25` ni `%2E`. */
    INVALID_ESCAPE,

    /** Un segment vide dans une clé d'API : deux barres obliques de suite, ou une barre finale. */
    EMPTY_SEGMENT,

    /** Un segment de clé d'API qui porte autre chose que des minuscules, des chiffres, `_`, `-` et `.`. */
    INVALID_SEGMENT,

    /** Une clé d'API dont le chemin vaut `command` ou commence par `command.`, ce qui est l'écriture d'une clé de commande. */
    COMMAND_PATH;

    /** La clé de traduction du défaut, sous celle du constat d'une clé mal formée. */
    val translationKey: String get() = "aegisperms.issue.malformed_key." + name.lowercase()
}
