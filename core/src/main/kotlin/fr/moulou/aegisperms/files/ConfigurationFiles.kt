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
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Les fichiers de `config\aegisperms\` (cahier des charges, 4.2), le point d'entrée du package : ce que le mod et l'application de bureau appellent pour
 * lire, valider, écrire et sauvegarder une configuration.
 *
 * Elle s'appelle d'un seul fil, le fil des fichiers (7.4), et ne se protège pas contre un second. Rien de ce qui vient d'un fichier n'en sort en
 * exception (4.9) : chaque appel rend un résultat, qui porte ses constats ou sa panne. L'heure vient de [clock], qu'un test fixe.
 */
class ConfigurationFiles internal constructor(val directory: Path, private val clock: Clock, settingsFormat: JsonFileFormat, permissionsFormat: JsonFileFormat) : AutoCloseable {

    private val settings = Stores.settings(directory, settingsFormat)

    private val permissions = Stores.permissions(directory, permissionsFormat)

    private val reader = ConfigurationReader(settings.reader, permissions.reader)

    private val lastGood = LastGood(directory, reader, settingsFormat, permissionsFormat)

    private val backupFiles = Backups(directory, reader)

    /**
     * Les réglages sur lesquels une écriture des droits s'appuie : ceux de la dernière lecture valide par les stores, ou ceux que le démarrage applique
     * quand les droits, eux, viennent de leur fichier. `null` met les droits en lecture seule.
     */
    private var writableWith: FileRead.Loaded<Settings>? = null

    constructor(directory: Path, clock: Clock = Clock.systemDefaultZone()) : this(directory, clock, JsonFileFormat.SETTINGS, JsonFileFormat.PERMISSIONS)

    /**
     * Lit la configuration au démarrage du serveur (4.9), et rend toujours de quoi appliquer des règles. Un fichier absent se crée avec les valeurs
     * livrées. Un fichier illisible ou invalide n'est ni corrigé ni réécrit : ce qui s'applique à sa place vient de la copie `last-good`, ou, sans copie
     * valide, des valeurs livrées, et la combinaison se revalide à chaque pas. Des droits venus d'ailleurs que de leur fichier sont en lecture seule.
     */
    fun start(catalog: Catalog? = null): ConfigurationStart {
        val reads = readByStores(catalog)
        val live = reads.combined
        if (live is ConfigurationRead.Valid) return ConfigurationStart(live.configuration, ConfigurationSource.FILE, ConfigurationSource.FILE, live.report, live.created)

        val copy = lastGood.read(catalog)
        val sources = ConfigurationFile.entries.associateWithTo(mutableMapOf()) { ConfigurationSource.FILE }
        var faulty = live.report.errors.map { it.file }.toSet()
        while (true) {
            for (file in faulty) sources[file] = fallbackOf(sources.getValue(file), copy)
            val settingsSource = sources.getValue(ConfigurationFile.SETTINGS)
            val permissionsSource = sources.getValue(ConfigurationFile.PERMISSIONS)
            val candidate = Configuration(
                settings = when (settingsSource) {
                    ConfigurationSource.FILE -> (reads.settings as FileRead.Loaded).data
                    ConfigurationSource.LAST_GOOD -> checkNotNull(copy).configuration.settings
                    ConfigurationSource.DELIVERED -> Settings.DELIVERED
                },
                permissions = when (permissionsSource) {
                    ConfigurationSource.FILE -> (reads.permissions as FileRead.Loaded).data
                    ConfigurationSource.LAST_GOOD -> checkNotNull(copy).configuration.permissions
                    ConfigurationSource.DELIVERED -> Permissions.DELIVERED
                },
            )
            val errors = ConfigurationValidator.validate(candidate, catalog).errors
            // Les valeurs livrées sont le dernier repli : quand les deux fichiers y sont, la descente s'arrête.
            if (errors.isEmpty() || sources.values.all { it == ConfigurationSource.DELIVERED }) {
                if (permissionsSource == ConfigurationSource.FILE) {
                    val settingsText = (reads.settings as? FileRead.Loaded)?.text.takeIf { settingsSource == ConfigurationSource.FILE }
                    writableWith = FileRead.Loaded(candidate.settings, created = false, settingsText)
                }
                return ConfigurationStart(candidate, settingsSource, permissionsSource, live.report, createdOf(reads))
            }
            // Un fichier déjà aux valeurs livrées ne descend plus : s'il est seul en cause, c'est l'autre qui descend.
            faulty = errors.map { it.file }.filterTo(mutableSetOf()) { sources[it] != ConfigurationSource.DELIVERED }.ifEmpty { ConfigurationFile.entries.toSet() }
        }
    }

    /**
     * Relit les deux fichiers par leurs stores et valide la configuration entière (4.8). Le premier appel ouvre les stores, ce qui crée un fichier absent
     * avec les valeurs livrées, et tout appel construit un store qui n'a pas pu l'être plus tôt. Sans [catalog], les constats qui en dépendent se taisent.
     *
     * L'empreinte de chaque fichier est retenue, que la lecture soit valide ou refusée. Refusée, elle met les droits en lecture seule jusqu'à la
     * prochaine configuration valide : une modification compilée sur l'état en vigueur ne doit pas atterrir sur un fichier qui ne lui correspond plus.
     */
    fun reload(catalog: Catalog? = null): ConfigurationRead = readByStores(catalog).combined

    private fun readByStores(catalog: Catalog?): StoreReads {
        val settingsRead = settings.read()
        val permissionsRead = permissions.read()
        val combined = reader.combine(settingsRead, permissionsRead, catalog)
        writableWith = (settingsRead as? FileRead.Loaded)?.takeIf { combined is ConfigurationRead.Valid }
        return StoreReads(settingsRead, permissionsRead, combined)
    }

    private fun fallbackOf(source: ConfigurationSource, copy: LastGood.Copy?): ConfigurationSource =
        if (source == ConfigurationSource.FILE && copy != null) ConfigurationSource.LAST_GOOD else ConfigurationSource.DELIVERED

    private fun createdOf(reads: StoreReads): Set<ConfigurationFile> = setOfNotNull(
        ConfigurationFile.SETTINGS.takeIf { (reads.settings as? FileRead.Loaded)?.created == true },
        ConfigurationFile.PERMISSIONS.takeIf { (reads.permissions as? FileRead.Loaded)?.created == true },
    )

    /** Lit et valide les deux fichiers tels qu'ils sont sur le disque, sans rien créer, ouvrir ni retenir : c'est `/aegis validate` (5.3). */
    fun check(catalog: Catalog? = null): ConfigurationRead = reader.read(directory, catalog)

    /**
     * Les fichiers qu'un autre que le mod a écrits, remplacés ou supprimés depuis leur dernière lecture par les stores : ce que la surveillance sonde
     * (4.8). Une écriture du mod n'en est pas un, et un rechargement, même refusé, remet le compte à zéro.
     */
    fun changedOutside(): Set<ConfigurationFile> = setOfNotNull(settings.file.takeIf { settings.hasChanged }, permissions.file.takeIf { permissions.hasChanged })

    /**
     * Écrit [newPermissions] dans le fichier des droits (4.8), le seul qu'une commande modifie : une modification par `/aegis`, le renommage d'un joueur.
     *
     * Les droits se valident d'abord avec les réglages en place, puis l'empreinte du fichier se compare à celle de sa dernière lecture : le mod n'écrit
     * pas sur un fichier qui a bougé. Le fichier s'écrit alors entier et atomiquement, puis se relit, si bien que la configuration rendue est celle du
     * disque, avec la ligne de chaque avertissement.
     */
    fun write(newPermissions: Permissions, catalog: Catalog? = null): ConfigurationWrite {
        val settingsRead = writableWith ?: return ConfigurationWrite.ReadOnly
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
                writableWith = null
                ConfigurationWrite.Refused(read.report)
            }
        }
    }

    /**
     * Écrit dans `last-good\` la configuration qui vient d'être publiée sous [generation] (4.9) : c'est elle que le prochain démarrage reprendra si un
     * fichier ne se lit plus.
     */
    fun writeLastGood(configuration: Configuration, generation: Long): GeneratedWrite = try {
        lastGood.write(configuration, generation, clock.instant().truncatedTo(ChronoUnit.SECONDS))
        GeneratedWrite.Done
    } catch (failure: Exception) {
        GeneratedWrite.Failed(failure)
    }

    /**
     * Copie les deux fichiers en place dans `backups\<horodatage>[-nom]\`, à l'octet et avec un manifeste (4.10). L'horodatage est l'heure locale, sans
     * deux-points, et un dossier déjà là n'est jamais écrasé.
     */
    fun backup(name: String? = null): BackupResult {
        if (name != null && !Backups.isValidName(name)) return BackupResult.InvalidName
        return try {
            backupFiles.write(name, clock)?.let(BackupResult::Done) ?: BackupResult.NothingToBackUp
        } catch (failure: Exception) {
            BackupResult.Failed(failure)
        }
    }

    /** Les sauvegardes que [restore] peut reprendre, par le nom de leur dossier, triées. */
    fun backups(): List<String> = backupFiles.list()

    /**
     * Remplace les deux fichiers en place par ceux de la sauvegarde [folder] (4.10), à l'octet : la mise en forme de qui l'avait écrite revient avec elle.
     *
     * La sauvegarde se valide d'abord comme une configuration entière, et rien n'est touché si elle est refusée. Les fichiers en place sont alors mis de
     * côté dans une sauvegarde `-before-restore`, puis remplacés, puis relus par leurs stores : la configuration rendue est celle du disque, et les
     * droits redeviennent inscriptibles. C'est la seule écriture du fichier des réglages après le premier démarrage.
     */
    fun restore(folder: String, catalog: Catalog? = null): RestoreResult {
        val source = backupFiles.resolve(folder) ?: return RestoreResult.UnknownBackup
        when (val saved = backupFiles.read(source, catalog)) {
            null -> return RestoreResult.IncompleteBackup
            is ConfigurationRead.Refused -> return RestoreResult.Refused(saved.report)
            is ConfigurationRead.Valid -> {}
        }

        // Sans sauvegarde des fichiers en place, pas de remplacement : on ne remplace pas ce qu'on n'a pas su mettre de côté.
        val savedAs = try {
            backupFiles.write(Backups.BEFORE_RESTORE, clock)
        } catch (failure: Exception) {
            return RestoreResult.Failed(failure)
        }
        try {
            backupFiles.restore(source)
        } catch (failure: Exception) {
            // Un seul des deux fichiers a pu être remplacé : les stores se relisent, pour que rien ne s'écrive sur ce qui n'est plus lu.
            readByStores(catalog)
            return RestoreResult.Failed(failure)
        }
        return when (val read = reload(catalog)) {
            is ConfigurationRead.Valid -> RestoreResult.Restored(read.configuration, read.report, savedAs)
            is ConfigurationRead.Refused -> RestoreResult.Refused(read.report)
        }
    }

    /** Ferme les deux stores. Rien ne s'écrit : aucun n'a de modification en attente. */
    override fun close() {
        settings.close()
        permissions.close()
    }

    /** Les lectures des deux fichiers par leurs stores, et ce qu'elles font ensemble. */
    private class StoreReads(val settings: FileRead<Settings>, val permissions: FileRead<Permissions>, val combined: ConfigurationRead)
}
