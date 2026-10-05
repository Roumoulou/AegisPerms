// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

/**
 * Un constat de la validation (cahier des charges, 4.5) : sa nature, le fichier et le chemin JSON de ce qu'il vise, et les arguments de son texte. Le texte
 * vit sous la clé de traduction de sa nature, jamais ici (5.7).
 */
data class Issue(
    val kind: IssueKind,
    val file: ConfigurationFile,
    /** Le chemin JSON de ce que le constat vise, segment par segment depuis la racine du fichier ; un rang de liste s'y écrit en chiffres. */
    val path: List<String>,
    /** Les arguments du texte, dans l'ordre que la nature du constat annonce. */
    val arguments: List<IssueArgument> = emptyList(),
    /** La ligne du fichier, quand sa lecture la donne. */
    val line: Int? = null,
) {

    val severity: Severity get() = kind.severity
}

/** Un argument du texte d'un constat : un texte à reprendre tel quel, ou un texte qui a sa propre clé de traduction. */
sealed interface IssueArgument {

    data class Literal(val text: String) : IssueArgument

    data class Translatable(val translationKey: String) : IssueArgument
}

/** Le fichier de configuration qu'un constat vise (4.2). */
enum class ConfigurationFile(val fileName: String) {
    SETTINGS("settings.json"),
    PERMISSIONS("permissions.json"),
}
