// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

import fr.moulou.aegisperms.model.Language
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * Les composants que le mod envoie (cahier des charges, 5.6 et 5.7). Pour un joueur, un composant porte sa clé de traduction et, en repli, le gabarit de la
 * langue du destinataire, que le client vanilla affiche faute de connaître la clé ; un resourcepack qui définit la clé l'emporte. Pour la console, le RCON et
 * le journal, le texte part résolu : le serveur connaît les clés du mod par son `en_us.json`, que la Fabric API charge côté serveur, et rendrait l'anglais à
 * la place du repli.
 *
 * La langue d'un destinataire suit le réglage `language` : imposée par `fr` ou `en` ; en `auto`, celle du client d'un joueur, et celle de la machine
 * virtuelle pour la console, le RCON et le journal.
 */
object Messages {

    /** Le composant à repli de [key] en [lang], ses arguments donnés au client, qui remplit le gabarit lui-même. Une clé sans gabarit se montre telle quelle. */
    fun component(key: String, lang: Lang, vararg arguments: Any): MutableComponent = Component.translatableWithFallback(key, Texts.template(key, lang) ?: key, *arguments)

    /** Le texte résolu de [key] en [lang], en composant littéral ; un argument qui est un composant y entre par son texte. */
    fun literal(key: String, lang: Lang, vararg arguments: Any): MutableComponent = Component.literal(Texts.text(key, lang, *arguments.map { if (it is Component) it.string else it }.toTypedArray()))

    /** Le fichier, le chemin JSON et la ligne d'un constat, `permissions.json:groups.vip.rules:12` ; sans chemin ni ligne, le fichier seul. */
    fun whereOf(issue: Issue): String = buildString {
        append(issue.file.fileName)
        if (issue.path.isNotEmpty()) append(':').append(issue.path.joinToString("."))
        issue.line?.let { append(':').append(it) }
    }

    /** La langue d'une source : celle du joueur qu'elle porte, sinon celle de la console. */
    fun languageOf(source: CommandSourceStack, setting: Language): Lang = forced(setting) ?: source.player?.let { languageOf(it, setting) } ?: consoleLanguage(setting)

    /** La langue d'un joueur : celle de son client, `fr_*` donnant le français. */
    fun languageOf(player: ServerPlayer, setting: Language): Lang = forced(setting) ?: Lang.ofCode(player.clientInformation().language())

    /** La langue de la console, du RCON et du journal : celle de la machine virtuelle. */
    fun consoleLanguage(setting: Language): Lang = forced(setting) ?: Lang.ofCode(Locale.getDefault().language)

    private fun forced(setting: Language): Lang? = when (setting) {
        Language.AUTO -> null
        Language.FR -> Lang.FR
        Language.EN -> Lang.EN
    }
}

/**
 * Un destinataire : sa langue, et la forme de ce qu'il reçoit, un composant à repli pour un joueur, dont le client traduit lui-même, un texte résolu pour la
 * console et le RCON, que le serveur rendrait sinon en anglais.
 */
class Recipient(val lang: Lang, val translatable: Boolean) {

    companion object {
        /** Le destinataire qu'est [source] : un joueur, ou la console. */
        fun of(source: CommandSourceStack, setting: Language): Recipient = source.player?.let { of(it, setting) } ?: console(setting)

        fun of(player: ServerPlayer, setting: Language): Recipient = Recipient(Messages.languageOf(player, setting), translatable = true)

        fun console(setting: Language): Recipient = Recipient(Messages.consoleLanguage(setting), translatable = false)
    }

    /** Le texte de [key] pour ce destinataire, ses arguments remplis. */
    fun text(key: String, vararg arguments: Any): MutableComponent = if (translatable) Messages.component(key, lang, *arguments) else Messages.literal(key, lang, *arguments)

    /** Un constat (4.5), tel qu'une réponse le répète : son fichier, son chemin et sa ligne, puis son texte. */
    fun issue(issue: Issue): MutableComponent = Component.literal(Messages.whereOf(issue) + " : ").append(text(issue.kind.translationKey, *argumentsOf(issue)))

    private fun argumentsOf(issue: Issue): Array<Any> = issue.arguments.map {
        when (it) {
            is IssueArgument.Literal -> it.text
            is IssueArgument.Translatable -> text(it.translationKey)
        }
    }.toTypedArray()
}
