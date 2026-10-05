// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Le manifeste d'une copie de la configuration (cahier des charges, 4.9 et 4.10), `manifest.json` : il s'écrit en dernier, si bien que sa présence dit que
 * les fichiers qu'il nomme sont écrits en entier.
 */
@Serializable
internal data class Manifest(
    @SerialName("schema-version") val schemaVersion: Int,
    /** Les fichiers de la copie, par leur nom. */
    val files: List<String>,
    /** La génération de la configuration publiée, pour la copie `last-good` ; une sauvegarde, qui copie des fichiers et non un état, n'en a pas. */
    val generation: Long? = null,
    /** L'instant de l'écriture, en ISO 8601, à la seconde et en UTC. */
    @SerialName("written-at") val writtenAt: String,
) {

    companion object {
        const val FILE_NAME = "manifest.json"

        /** Le numéro de structure que cette version lit et écrit. */
        const val SCHEMA_VERSION = 1

        /** Le manifeste de [directory], ou `null` quand il manque ou ne se lit pas : la copie est alors refusée. */
        fun read(directory: Path): Manifest? = try {
            JsonFileFormat.GENERATED.decodeFromPath(serializer(), directory.resolve(FILE_NAME))
        } catch (_: Exception) {
            null
        }
    }

    fun writeTo(directory: Path) = JsonFileFormat.GENERATED.encodeToPath(serializer(), this, directory.resolve(FILE_NAME))

    /** Vrai quand le manifeste nomme exactement [expected], et que chacun de ces fichiers est dans [directory]. */
    fun lists(expected: List<String>, directory: Path): Boolean = files.toSet() == expected.toSet() && expected.all { Files.isRegularFile(directory.resolve(it)) }
}
