// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ValidationReport

/**
 * Ce que le démarrage rend (cahier des charges, 4.9) : toujours une configuration valide à appliquer, parce qu'un fichier illisible ou invalide laisse
 * la copie `last-good`, puis les valeurs livrées, s'appliquer à sa place. Le serveur ne reste jamais sans règles, ni ouvert à vanilla par accident.
 */
data class ConfigurationStart(
    val configuration: Configuration,
    val settingsSource: ConfigurationSource,
    val permissionsSource: ConfigurationSource,
    /** Les constats des fichiers en place, ceux que l'administrateur a à corriger : ils portent des erreurs dès qu'un fichier a été remplacé. */
    val report: ValidationReport,
    /** Les fichiers qui manquaient et que le mod vient d'écrire avec les valeurs livrées. */
    val created: Set<ConfigurationFile> = emptySet(),
) {

    /**
     * Vrai quand les droits ne viennent pas de leur fichier : ils s'appliquent, et les modifications par commande sont refusées jusqu'à ce qu'un
     * rechargement ou une restauration rende une configuration valide.
     */
    val readOnly: Boolean get() = permissionsSource != ConfigurationSource.FILE
}

/** D'où vient ce qui s'applique d'un fichier, au démarrage. */
enum class ConfigurationSource {

    /** Le fichier en place. */
    FILE,

    /** La copie `last-good`, la dernière configuration publiée. */
    LAST_GOOD,

    /** Les valeurs livrées : les réglages de `Settings.DELIVERED`, ou les deux groupes du premier démarrage (2.10). */
    DELIVERED,
}
