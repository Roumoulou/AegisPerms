// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.Texts
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Les descriptions des commandes (cahier des charges, 5.10) : celles du jar, `assets\aegisperms\descriptions\fr_fr.json` et `en_us.json`, une clé de commande
 * vers une phrase, pour vanilla, `/aegis` et les mods qu'on connaît ; et celles de l'administrateur, `descriptions.json` dans le dossier de configuration, une
 * clé vers `{"fr": ..., "en": ...}`, qui l'emportent et servent aux mods que le jar ne connaît pas.
 */
class Descriptions(private val jar: Map<Lang, Map<String, String>>, private val custom: Map<String, Map<String, String>> = emptyMap()) {

    /** La description de [key] en [lang] : celle de l'administrateur d'abord, puis celle du jar ; `null` quand ni l'un ni l'autre n'en a. */
    fun of(key: CommandKey, lang: Lang): String? = custom[key.text]?.get(lang.code)?.takeIf { it.isNotBlank() } ?: jar[lang]?.get(key.text)

    /** Les descriptions lues, et ce qui a empêché de lire le fichier de l'administrateur, ou `null`. */
    class Loaded(val descriptions: Descriptions, val issue: String?)

    companion object {
        /** Le fichier de l'administrateur, dans le dossier de configuration. */
        const val FILE_NAME = "descriptions.json"

        private const val RESOURCE_DIRECTORY = "/assets/aegisperms/descriptions/"

        private val CUSTOM = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

        /** Les descriptions du jar, lues une fois. */
        val jar: Map<Lang, Map<String, String>> by lazy { Lang.entries.associateWith(::loadJar) }

        /** Le fichier de l'administrateur : un objet JSON strict, une clé vers un objet qui va d'un code de langue à une phrase. */
        fun parseCustom(content: String): Map<String, Map<String, String>> = Json.decodeFromString(CUSTOM, content.removePrefix("﻿"))

        /**
         * Les descriptions du jar et celles de l'administrateur, lues dans `descriptions.json` de [directory] : un fichier absent ne manque pas ; un fichier
         * illisible se dit dans [Loaded.issue], et les descriptions du jar servent seules.
         */
        fun load(directory: Path, jar: Map<Lang, Map<String, String>> = this.jar): Loaded {
            val path = directory.resolve(FILE_NAME)
            if (!Files.exists(path)) return Loaded(Descriptions(jar), null)
            return try {
                Loaded(Descriptions(jar, parseCustom(Files.readString(path, Charsets.UTF_8))), null)
            } catch (failure: Exception) {
                Loaded(Descriptions(jar), failure.toString())
            }
        }

        private fun loadJar(lang: Lang): Map<String, String> {
            val stream = Descriptions::class.java.getResourceAsStream(RESOURCE_DIRECTORY + lang.fileName + ".json") ?: return emptyMap()
            return stream.use { Texts.parse(it.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}
