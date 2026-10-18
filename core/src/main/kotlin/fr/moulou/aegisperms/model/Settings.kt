// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Les réglages (cahier des charges, 4.3) : le contenu du fichier `settings.json`, tel qu'il l'écrit, chaque réglage sous son champ et dans l'ordre du
 * fichier. Les valeurs par défaut sont les valeurs livrées.
 */
@Serializable
data class Settings(
    /** Le numéro de la structure du fichier (4.2), sans valeur par défaut, comme celui des droits. */
    @SerialName("schema-version") val schemaVersion: Int,
    /** Le niveau d'op à partir duquel l'étape 0 garde `/aegis` ouverte (2.6). */
    @SerialName("admin-level") val adminLevel: OpLevel = OpLevel.OWNERS,
    /** La visibilité d'un refus dont la règle n'en dit rien (2.7). */
    @SerialName("denied-commands") val deniedCommands: DeniedCommands = DeniedCommands.HIDDEN,
    /** Le message du refus visible, ou `null` pour le message livré (5.6). */
    @SerialName("refusal-message") val refusalMessage: String? = null,
    val language: Language = Language.AUTO,
    val mode: Mode = Mode.ENFORCE,
    val namespaces: NamespaceSettings = NamespaceSettings(),
    /** La table des alias : un nom vers sa cible (3.6). */
    val aliases: Map<String, String> = emptyMap(),
    /** La surveillance des deux fichiers de configuration (4.8). */
    @SerialName("watch-files") val watchFiles: Boolean = true,
) {

    companion object {
        /** Le numéro de structure que cette version lit et écrit. */
        const val SCHEMA_VERSION = 1

        /** Les réglages du fichier créé au premier démarrage. */
        val DELIVERED = Settings(schemaVersion = SCHEMA_VERSION)
    }
}

/** Les réglages des commandes préfixées (chapitre 3). */
@Serializable
data class NamespaceSettings(
    /** L'activation des formes préfixées (3.4). */
    val enabled: Boolean = true,
    /** La forme exigée (3.5) : un joueur ne voit et ne tape que les formes préfixées ; sans effet quand les formes préfixées sont coupées. */
    @SerialName("require-prefix") val requirePrefix: Boolean = true,
    /** La table des préfixes : un modid vers le préfixe qui le remplace (3.3). */
    val prefixes: Map<String, String> = mapOf(Keys.MINECRAFT to "mc"),
    /** Les rattachements : une commande racine vers le modid du mod propriétaire qu'on lui donne à la main (3.2). */
    val owners: Map<String, String> = emptyMap(),
)

/** Le réglage `denied-commands`. */
@Serializable
enum class DeniedCommands {
    @SerialName("hidden") HIDDEN,
    @SerialName("visible") VISIBLE,
}

/** Le réglage `language` (5.7). */
@Serializable
enum class Language {
    @SerialName("auto") AUTO,
    @SerialName("fr") FR,
    @SerialName("en") EN,
}

/** Le réglage `mode` (5.8). */
@Serializable
enum class Mode {
    @SerialName("enforce") ENFORCE,
    @SerialName("observe") OBSERVE,
}
