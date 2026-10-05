// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.aegisperms.validate.ConfigurationFile
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists

/**
 * La copie `last-good` (cahier des charges, 4.9) : la dernière configuration publiée, que le démarrage reprend quand un fichier en place est illisible ou
 * invalide.
 *
 * Elle s'écrit à côté puis remplace la précédente, gardée sous `last-good.old` jusqu'à ce que la nouvelle soit en place. Elle n'est reprise qu'entière :
 * un manifeste complet, ses deux fichiers lisibles, et une configuration qui se valide sans erreur.
 */
internal class LastGood(directory: Path, private val reader: ConfigurationReader, private val settingsFormat: JsonFileFormat, private val permissionsFormat: JsonFileFormat) {

    companion object {
        const val NAME = "last-good"

        const val OLD_NAME = "last-good.old"

        /** Les fichiers d'une copie, dans l'ordre où ils s'écrivent. */
        val FILES: List<String> = listOf(ConfigurationFile.SETTINGS.fileName, ConfigurationFile.PERMISSIONS.fileName)
    }

    val current: Path = directory.resolve(NAME)

    val previous: Path = directory.resolve(OLD_NAME)

    /** Écrit la copie de [configuration], publiée sous [generation]. Une panne remonte telle quelle, la copie précédente restant reprenable. */
    fun write(configuration: Configuration, generation: Long, now: Instant) {
        StagedDirectory.sweep(current)
        val staged = StagedDirectory.stage(current) { copy ->
            settingsFormat.encodeToPath(Settings.serializer(), configuration.settings, copy.resolve(ConfigurationFile.SETTINGS.fileName))
            permissionsFormat.encodeToPath(Permissions.serializer(), configuration.permissions, copy.resolve(ConfigurationFile.PERMISSIONS.fileName))
            Manifest(Manifest.SCHEMA_VERSION, FILES, generation, now.toString()).writeTo(copy)
        }
        try {
            if (current.exists()) {
                StagedDirectory.delete(previous)
                StagedDirectory.move(current, previous)
            }
            StagedDirectory.move(staged, current)
        } catch (failure: Exception) {
            runCatching { StagedDirectory.delete(staged) }
            throw failure
        }
        // La nouvelle copie est en place : l'ancienne ne sert plus. Si elle résiste, le passage suivant la retire.
        runCatching { StagedDirectory.delete(previous) }
    }

    /** La copie à reprendre : `last-good`, sinon `last-good.old`, qu'un remplacement interrompu aurait laissée ; `null` sans copie valide. */
    fun read(catalog: Catalog?): Copy? = readFrom(current, catalog) ?: readFrom(previous, catalog)

    private fun readFrom(directory: Path, catalog: Catalog?): Copy? {
        val manifest = Manifest.read(directory) ?: return null
        val generation = manifest.generation ?: return null
        if (!manifest.lists(FILES, directory)) return null
        val read = reader.read(directory, catalog) as? ConfigurationRead.Valid ?: return null
        return Copy(read.configuration, generation)
    }

    /** Une copie reprise : sa configuration, et la génération sous laquelle elle avait été publiée. */
    data class Copy(val configuration: Configuration, val generation: Long)
}
