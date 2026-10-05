// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ValidationReport

/** Ce que la lecture d'une configuration rend (cahier des charges, 4.5) : elle se lit et se valide entière, réglages et droits ensemble, ou elle est refusée. */
sealed interface ConfigurationRead {

    /** Les constats de la lecture et de la validation, chacun avec son fichier, son chemin JSON et, quand elle se trouve, sa ligne. */
    val report: ValidationReport

    /**
     * La configuration est valide et peut s'appliquer : [report] ne porte que des avertissements. [created] nomme les fichiers qui manquaient et que le mod
     * vient d'écrire avec les valeurs livrées.
     */
    data class Valid(val configuration: Configuration, override val report: ValidationReport, val created: Set<ConfigurationFile> = emptySet()) : ConfigurationRead

    /** La configuration est refusée : [report] porte au moins une erreur, et rien ne s'applique. */
    data class Refused(override val report: ValidationReport) : ConfigurationRead
}
