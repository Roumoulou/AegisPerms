// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.validate.ValidationReport

/** Ce qu'une restauration rend (cahier des charges, 4.10). Hors [Restored] et [Failed], les fichiers en place n'ont pas été touchés. */
sealed interface RestoreResult {

    /**
     * Les fichiers en place sont ceux de la sauvegarde, à l'octet : [configuration] est à publier, et [report] ne porte que des avertissements. [savedAs]
     * nomme le dossier où les fichiers d'avant ont été mis de côté, ou vaut `null` quand il n'y en avait aucun.
     */
    data class Restored(val configuration: Configuration, val report: ValidationReport, val savedAs: String?) : RestoreResult

    /** Aucune sauvegarde ne porte ce nom dans `backups\`. */
    data object UnknownBackup : RestoreResult

    /** La sauvegarde ne porte pas les deux fichiers : elle ne fait pas une configuration entière. */
    data object IncompleteBackup : RestoreResult

    /** La sauvegarde ne se valide pas comme une configuration entière : [report] dit pourquoi. */
    data class Refused(val report: ValidationReport) : RestoreResult

    /**
     * La mise de côté des fichiers en place, ou leur remplacement, a échoué, et [cause] dit la panne. Les deux fichiers ne se remplacent pas d'un seul
     * geste : après une panne au remplacement, un rechargement dit ce qui s'applique.
     */
    data class Failed(val cause: Exception) : RestoreResult
}
