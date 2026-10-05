// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.storify.Defaultable
import fr.moulou.storify.core.StoreConfig
import fr.moulou.storify.core.StoreFactory
import java.nio.file.Path

/** Les deux stores de la configuration (cahier des charges, 4.11), dont Storify range les lignes sous le journal du mod (5.9). */
internal object Stores {

    private const val LOGGER_NAME = "aegisperms"

    /**
     * Le fichier des réglages de [directory]. Le mod ne l'écrit qu'une fois, quand il manque : son store est en lecture seule, où toute autre écriture
     * serait une faute du code, pas une discipline.
     */
    fun settings(directory: Path, format: JsonFileFormat = JsonFileFormat.SETTINGS): StoredFile<Settings> {
        val config = StoreConfig(readOnly = true, createIfMissing = true, withValidation = true, loggerName = LOGGER_NAME)
        return StoredFile(ConfigurationFile.SETTINGS, directory.resolve(ConfigurationFile.SETTINGS.fileName), format, Settings.serializer()) { path ->
            StoreFactory.createFromDefaultable<Settings, DeliveredSettings>(path.toString(), format, config)
        }
    }

    /** Le fichier des droits de [directory]. Le mod l'écrit tout de suite après chaque changement : rien n'attend un tick ni l'arrêt du serveur. */
    fun permissions(directory: Path, format: JsonFileFormat = JsonFileFormat.PERMISSIONS): StoredFile<Permissions> {
        val config = StoreConfig(withAutoSave = false, withShutdownHook = false, withValidation = true, loggerName = LOGGER_NAME)
        return StoredFile(ConfigurationFile.PERMISSIONS, directory.resolve(ConfigurationFile.PERMISSIONS.fileName), format, Permissions.serializer()) { path ->
            StoreFactory.createFromDefaultable<Permissions, DeliveredPermissions>(path.toString(), format, config)
        }
    }
}

/** Les réglages livrés (4.3), que Storify demande quand le fichier manque. */
internal class DeliveredSettings : Defaultable<Settings> {
    override fun getDefault(): Settings = Settings.DELIVERED
}

/** Les droits livrés (2.10), que Storify demande quand le fichier manque. */
internal class DeliveredPermissions : Defaultable<Permissions> {
    override fun getDefault(): Permissions = Permissions.DELIVERED
}
