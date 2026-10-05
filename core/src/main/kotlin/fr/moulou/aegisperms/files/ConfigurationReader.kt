// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ConfigurationValidator
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.ValidationReport
import java.nio.file.Path

/**
 * La lecture d'une configuration entière (cahier des charges, 4.5) : ses deux fichiers lus, puis validés ensemble. Chaque constat de la validation y
 * reçoit la ligne de son chemin JSON, cherchée dans le texte du fichier qu'il vise.
 *
 * Une erreur de lecture arrête là : la validation a besoin des deux fichiers, et le rapport ne porte alors que les constats de lecture, ceux des droits
 * d'abord, dans l'ordre de la validation.
 */
internal class ConfigurationReader(private val settings: FileReader<Settings>, private val permissions: FileReader<Permissions>) {

    /** Lit et valide la configuration rangée dans [directory], sans store, sans rien créer ni retenir. */
    fun read(directory: Path, catalog: Catalog?): ConfigurationRead =
        combine(settings.read(directory.resolve(ConfigurationFile.SETTINGS.fileName)), permissions.read(directory.resolve(ConfigurationFile.PERMISSIONS.fileName)), catalog)

    /** Réunit les lectures des deux fichiers, d'où qu'elles viennent, et valide la configuration qu'elles font. */
    fun combine(settingsRead: FileRead<Settings>, permissionsRead: FileRead<Permissions>, catalog: Catalog?): ConfigurationRead {
        if (settingsRead !is FileRead.Loaded || permissionsRead !is FileRead.Loaded) return ConfigurationRead.Refused(ValidationReport(issuesOf(permissionsRead) + issuesOf(settingsRead)))

        val configuration = Configuration(settingsRead.data, permissionsRead.data)
        val texts = mapOf(ConfigurationFile.SETTINGS to settingsRead.text, ConfigurationFile.PERMISSIONS to permissionsRead.text)
        val report = ValidationReport(ConfigurationValidator.validate(configuration, catalog).issues.map { texts.getValue(it.file).withLine(it) })
        if (!report.isValid) return ConfigurationRead.Refused(report)

        val created = setOfNotNull(ConfigurationFile.SETTINGS.takeIf { settingsRead.created }, ConfigurationFile.PERMISSIONS.takeIf { permissionsRead.created })
        return ConfigurationRead.Valid(configuration, report, created)
    }

    private fun issuesOf(read: FileRead<*>): List<Issue> = (read as? FileRead.Refused)?.issues.orEmpty()
}
