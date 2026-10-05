// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.validate.Issue
import fr.moulou.storify.validation.ErrorLineLocator
import fr.moulou.storify.validation.PathSegment
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Le texte d'un fichier tel qu'il a été lu, sans son BOM : ses lignes et, quand il est du JSON strict, son arbre. Il donne à un constat la ligne de son
 * chemin JSON (cahier des charges, 4.5), par le localisateur de lignes de Storify.
 *
 * La ligne est donnée au mieux : le localisateur lit une clé par ligne, et rend pour une valeur écrite en ligne la ligne de la clé qui la porte. Un chemin
 * que le fichier n'écrit pas, une valeur laissée à son défaut, prend la ligne de son plus proche ancêtre écrit.
 */
internal class FileText private constructor(private val content: String, private val locator: ErrorLineLocator) {

    companion object {
        /** Le BOM UTF-8, tel qu'il se lit en tête d'un texte. */
        const val BOM = "﻿"

        fun of(text: String, locator: ErrorLineLocator): FileText = FileText(text.removePrefix(BOM), locator)

        /** Le texte de [path], lu comme Storify le lit. Une panne de lecture remonte telle quelle. */
        fun read(path: Path, format: JsonFileFormat): FileText = of(path.readText(), format.lineLocator())
    }

    /** L'arbre du texte, ou `null` quand il n'est pas du JSON strict : c'est le décodage qui dira pourquoi. */
    val tree: JsonElement? = try {
        Json.parseToJsonElement(content)
    } catch (_: SerializationException) {
        null
    }

    private val lines: List<String> = content.lines()

    /** Le constat avec la ligne de son chemin, s'il n'en porte pas déjà une et qu'elle se trouve. */
    fun withLine(issue: Issue): Issue = if (issue.line != null) issue else issue.copy(line = lineOf(issue.path))

    /** La ligne, à partir de 1, de ce que [path] désigne, ou de son plus proche ancêtre écrit ; `null` quand rien du chemin n'est écrit, ou sans arbre. */
    fun lineOf(path: List<String>): Int? {
        val segments = writtenSegments(path)
        for (length in segments.size downTo 1) {
            val line = try {
                locator.lineOf(lines, segments.subList(0, length))
            } catch (_: RuntimeException) {
                null
            }
            if (line != null) return line
        }
        return null
    }

    /**
     * Les segments de [path] que l'arbre écrit, jusqu'au premier qui manque. Le localisateur ne reçoit qu'eux : il ne sait pas où finit un objet, et
     * chercherait une clé absente jusque chez les voisins de son parent. C'est aussi l'arbre qui dit lequel est un rang de liste, qu'un chemin de constat
     * écrit en chiffres comme une clé, un groupe pouvant s'appeler `123`.
     */
    private fun writtenSegments(path: List<String>): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        var container: JsonElement = tree ?: return segments
        for (segment in path) {
            val parent = container
            val index = if (parent is JsonArray) segment.toIntOrNull() else null
            container = when {
                parent is JsonArray -> index?.let { parent.getOrNull(it) }
                parent is JsonObject -> parent[segment]
                else -> null
            } ?: break
            segments += if (index != null) PathSegment.Index(index) else PathSegment.Key(segment)
        }
        return segments
    }
}
