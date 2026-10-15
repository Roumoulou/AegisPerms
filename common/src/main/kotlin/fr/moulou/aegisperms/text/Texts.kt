// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Les textes du mod (cahier des charges, 5.7) : les deux fichiers de langue, `assets\aegisperms\lang\fr_fr.json` et `en_us.json`, lus depuis le jar, et la
 * résolution d'une clé `aegisperms.<chemin>` en texte pour une langue. Le serveur dédié ne charge pas les fichiers de langue des mods : le mod lit les siens.
 * Sans Minecraft, pour que l'étage 0 l'éprouve ; les composants sont dans [Messages].
 *
 * Un gabarit remplit ses `%s` dans l'ordre des arguments, `%1$s` par rang, et `%%` écrit un `%` : la grammaire que le client applique lui-même à un repli.
 */
object Texts {

    private const val RESOURCE_DIRECTORY = "/assets/aegisperms/lang/"

    private val PLACEHOLDER = Regex("%(?:(\\d+)\\$)?([sd%])")

    private val tables: Map<Lang, Map<String, String>> by lazy { Lang.entries.associateWith(::load) }

    /** Le gabarit de [key] en [lang], ou `null` quand le fichier ne le porte pas. */
    fun template(key: String, lang: Lang): String? = tables.getValue(lang)[key]

    /** Les clés que le fichier de [lang] porte. */
    fun keys(lang: Lang): Set<String> = tables.getValue(lang).keys

    /** Le texte de [key] en [lang], son gabarit rempli par [arguments] ; une clé sans gabarit rend la clé suivie de ses arguments, pour que rien ne se perde. */
    fun text(key: String, lang: Lang, vararg arguments: Any?): String {
        val template = template(key, lang) ?: return (listOf(key) + arguments).joinToString(" ")
        return format(template, arguments.asList())
    }

    /** [template] rempli par [arguments] : `%s` et `%d` dans l'ordre, `%1$s` par rang, `%%` pour un `%` ; un gabarit sans valeur reste tel quel. */
    fun format(template: String, arguments: List<Any?>): String {
        var next = 0
        return PLACEHOLDER.replace(template) { match ->
            if (match.groupValues[2] == "%") return@replace "%"
            val rank = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()?.minus(1) ?: next++
            if (rank in arguments.indices) arguments[rank].toString() else match.value
        }
    }

    /** Le nombre de valeurs qu'un gabarit attend : ses gabarits dans l'ordre, ou le plus haut rang nommé. */
    fun arity(template: String): Int {
        var ordered = 0
        var highest = 0
        for (match in PLACEHOLDER.findAll(template)) {
            if (match.groupValues[2] == "%") continue
            val rank = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
            if (rank == null) ordered++ else highest = maxOf(highest, rank)
        }
        return maxOf(ordered, highest)
    }

    /** Un fichier de langue : un objet JSON plat, d'une clé vers son gabarit. */
    fun parse(content: String): Map<String, String> = Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), content)

    private fun load(lang: Lang): Map<String, String> {
        val stream = Texts::class.java.getResourceAsStream(RESOURCE_DIRECTORY + lang.fileName + ".json") ?: return emptyMap()
        return stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
    }
}
