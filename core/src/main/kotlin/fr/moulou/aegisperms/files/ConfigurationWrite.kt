// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.validate.ValidationReport

/** Ce que l'écriture du fichier des droits rend (cahier des charges, 4.8). Hors [Written], rien n'est écrit et l'état en vigueur reste. */
sealed interface ConfigurationWrite {

    /** Le fichier des droits est écrit, puis relu : [configuration] est celle du disque, à publier, et [report] ne porte que des avertissements. */
    data class Written(val configuration: Configuration, val report: ValidationReport) : ConfigurationWrite

    /** Les droits proposés ne font pas une configuration valide avec les réglages en place : [report] dit pourquoi. */
    data class Refused(val report: ValidationReport) : ConfigurationWrite

    /** Le fichier des droits a bougé depuis sa dernière lecture : un rechargement d'abord. */
    data object FileMoved : ConfigurationWrite

    /** Aucune configuration valide n'a été lue des fichiers en place : les droits sont en lecture seule, jusqu'au prochain rechargement valide (4.9). */
    data object ReadOnly : ConfigurationWrite

    /** L'écriture a échoué, et [cause] dit la panne. Le fichier précédent reste entier. */
    data class Failed(val cause: Exception) : ConfigurationWrite
}
