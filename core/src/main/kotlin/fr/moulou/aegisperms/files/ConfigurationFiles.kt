// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ConfigurationValidator
import java.nio.file.Path

/**
 * Les fichiers de `config\aegisperms\` (cahier des charges, 4.2), le point d'entrée du package : ce que le mod et l'application de bureau appellent pour
 * lire, valider, écrire et sauvegarder une configuration.
 *
 * Elle s'appelle d'un seul fil, le fil des fichiers (7.4), et ne se protège pas contre un second. Rien de ce qui vient d'un fichier n'en sort en
 * exception (4.9) : chaque appel rend un résultat, qui porte ses constats.
 */
class ConfigurationFiles internal constructor(val directory: Path, settingsFormat: JsonFileFormat, permissionsFormat: JsonFileFormat) : AutoCloseable {

    private val settings = Stores.settings(directory, settingsFormat)

    private val permissions = Stores.permissions(directory, permissionsFormat)

    private val reader = ConfigurationReader(settings.reader, permissions.reader)

    /**
     * Les réglages de la dernière lecture par les stores, quand elle a rendu une configuration valide ; `null` sinon. Une écriture s'appuie sur eux, et
     * sans eux les droits sont en lecture seule.
     */
    private var validSettings: FileRead.Loaded<Settings>? = null

    constructor(directory: Path) : this(directory, JsonFileFormat.SETTINGS, JsonFileFormat.PERMISSIONS)

    /**
     * Relit les deux fichiers par leurs stores et valide la configuration entière (4.8). Le premier appel ouvre les stores, ce qui crée un fichier absent
     * avec les valeurs livrées, et tout appel construit un store qui n'a pas pu l'être plus tôt. Sans [catalog], les constats qui en dépendent se taisent.
     *
     * L'empreinte de chaque fichier est retenue, que la lecture soit valide ou refusée.
     */
    fun reload(catalog: Catalog? = null): ConfigurationRead {
        val settingsRead = settings.read()
        val read = reader.combine(settingsRead, permissions.read(), catalog)
        validSettings = (settingsRead as? FileRead.Loaded)?.takeIf { read is ConfigurationRead.Valid }
        return read
    }

    /** Lit et valide les deux fichiers tels qu'ils sont sur le disque, sans rien créer, ouvrir ni retenir : c'est `/aegis validate` (5.3). */
    fun check(catalog: Catalog? = null): ConfigurationRead = reader.read(directory, catalog)

    /**
     * Les fichiers qu'un autre que le mod a écrits, remplacés ou supprimés depuis leur dernière lecture par les stores : ce que la surveillance sonde
     * (4.8). Une écriture du mod n'en est pas un, et un rechargement, même refusé, remet le compte à zéro.
     */
    fun changedOutside(): Set<ConfigurationFile> = setOfNotNull(settings.file.takeIf { settings.hasChanged }, permissions.file.takeIf { permissions.hasChanged })

    /**
     * Écrit [newPermissions] dans le fichier des droits (4.8), le seul que le mod écrit : une modification par `/aegis`, le renommage d'un joueur.
     *
     * Les droits se valident d'abord avec les réglages en place, puis l'empreinte du fichier se compare à celle de sa dernière lecture : le mod n'écrit
     * pas sur un fichier qui a bougé. Le fichier s'écrit alors entier et atomiquement, puis se relit, si bien que la configuration rendue est celle du
     * disque, avec la ligne de chaque avertissement.
     */
    fun write(newPermissions: Permissions, catalog: Catalog? = null): ConfigurationWrite {
        val settingsRead = validSettings ?: return ConfigurationWrite.ReadOnly
        val proposed = ConfigurationValidator.validate(Configuration(settingsRead.data, newPermissions), catalog)
        if (!proposed.isValid) return ConfigurationWrite.Refused(proposed)
        if (permissions.hasChanged) return ConfigurationWrite.FileMoved

        val permissionsRead = try {
            permissions.write(newPermissions)
        } catch (failure: Exception) {
            return ConfigurationWrite.Failed(failure)
        }
        return when (val read = reader.combine(settingsRead, permissionsRead, catalog)) {
            is ConfigurationRead.Valid -> ConfigurationWrite.Written(read.configuration, read.report)
            // Un autre a remplacé le fichier entre l'écriture et sa relecture : ce qui se lit n'est plus valide, comme après un rechargement refusé.
            is ConfigurationRead.Refused -> {
                validSettings = null
                ConfigurationWrite.Refused(read.report)
            }
        }
    }

    /** Ferme les deux stores. Rien ne s'écrit : aucun n'a de modification en attente. */
    override fun close() {
        settings.close()
        permissions.close()
    }
}
