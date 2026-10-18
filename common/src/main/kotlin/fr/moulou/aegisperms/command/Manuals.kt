// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.AegisPerms
import fr.moulou.aegisperms.files.ManualWrite
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.manual.Descriptions
import fr.moulou.aegisperms.manual.ManualBuilder
import fr.moulou.aegisperms.manual.ManualRenderer
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.TextKeys.Command
import fr.moulou.aegisperms.tree.IndexCatalog
import net.minecraft.server.MinecraftServer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * `/aegis manual [langue]` (cahier des charges, 5.10) : le manuel des commandes, bâti sur le fil serveur depuis l'index du `Commands` en service, le catalogue
 * et les descriptions, puis écrit par le fil des fichiers dans `manuals\` ; la réponse dit le fichier et les comptes. Sans langue, celle du demandeur.
 */
internal object Manuals {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun write(reply: Reply, server: MinecraftServer, languageCode: String?): Int {
        val lang = if (languageCode == null) reply.recipient.lang else Lang.entries.firstOrNull { it.code == languageCode } ?: return reply.refuse(Command.MANUAL_UNKNOWN_LANGUAGE, languageCode)
        val index = Suggestions.indexOf(server) ?: return reply.refuse(Command.CATALOG_UNAVAILABLE)
        val directory = FileThread.directory() ?: return reply.refuse(Command.NOT_PUBLISHED)
        val loaded = Descriptions.load(directory)
        val manual = ManualBuilder.build(
            index,
            IndexCatalog(index, ApiKeyRequests.keys()).commands(),
            loaded.descriptions,
            lang,
            modVersion = AegisPerms.version(),
            gameVersion = index.mods.versionOf(Keys.MINECRAFT) ?: "?",
            date = LocalDate.now().format(DATE),
            mode = Publisher.mode(),
            requirePrefix = Publisher.settings().namespaces.requirePrefix,
            descriptionsIssue = loaded.issue,
        )
        val text = ManualRenderer.render(manual)
        reply.later(FileThread.writeManual(lang.code, text)) { result ->
            when (result) {
                is ManualWrite.Done -> reply.line(reply.text(Command.MANUAL_DONE, "${ManualWrite.FOLDER}/${result.fileName}", manual.mods.size, manual.commandCount, manual.nodeCount))
                is ManualWrite.Failed -> reply.refuse(Command.MANUAL_FAILED, result.cause.toString())
            }
        }
        return 1
    }
}
