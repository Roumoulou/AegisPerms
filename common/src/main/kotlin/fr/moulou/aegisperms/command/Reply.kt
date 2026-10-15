// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.text.Recipient
import fr.moulou.aegisperms.text.TextKeys
import fr.moulou.aegisperms.validate.Issue
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.util.concurrent.CompletableFuture

/**
 * Les réponses d'une commande `/aegis` à sa source (cahier des charges, 5.6) : une consultation répond à la source seule, ligne par ligne ; une
 * modification se diffuse aux ops et s'inscrit au journal du serveur, comme `/op` ; un refus tient en une ligne. Chaque texte passe par sa clé, dans la
 * langue du destinataire (5.7).
 */
internal class Reply(val source: CommandSourceStack) {

    companion object {
        private const val HOOK = "command"

        /** [parts] mis bout à bout, [separator] entre eux. */
        fun join(parts: List<Component>, separator: String): MutableComponent {
            val out = Component.empty()
            parts.forEachIndexed { index, part ->
                if (index > 0) out.append(separator)
                out.append(part)
            }
            return out
        }
    }

    val recipient: Recipient = Recipient.of(source, Publisher.settings().language)

    fun text(key: String, vararg arguments: Any): MutableComponent = recipient.text(key, *arguments)

    fun issue(issue: Issue): MutableComponent = recipient.issue(issue)

    fun yesNo(value: Boolean): MutableComponent = text(if (value) TextKeys.Command.YES else TextKeys.Command.NO)

    /** Une consultation : chaque ligne à la source seule. Rend 1, le résultat d'une commande qui a répondu. */
    fun lines(lines: List<Component>): Int {
        for (line in lines) source.sendSystemMessage(line)
        return 1
    }

    fun line(line: Component): Int = lines(listOf(line))

    /** Une modification : la réponse se diffuse aux ops en ligne et s'inscrit au journal du serveur, selon ses règles de jeu. */
    fun broadcast(line: Component): Int {
        source.sendSuccess({ line }, true)
        return 1
    }

    /** Un refus, en une ligne qui dit la cause. Rend 0, le résultat d'une commande refusée. */
    fun refuse(line: Component): Int {
        source.sendFailure(line)
        return 0
    }

    fun refuse(key: String, vararg arguments: Any): Int = refuse(text(key, *arguments))

    /**
     * Une réponse qui attend le fil des fichiers : [respond] court sur le fil serveur quand [future] a fini. Une panne du travail se journalise une fois et
     * répond un refus, pour ne pas laisser la source sans réponse.
     */
    fun <T> later(future: CompletableFuture<T>, respond: (T) -> Unit) {
        val server = source.server
        future.whenComplete { result, error ->
            server.execute {
                if (error != null) {
                    ModLog.once(HOOK, source.textName, error)
                    refuse(TextKeys.Command.INTERNAL_ERROR)
                } else {
                    respond(result)
                }
            }
        }
    }
}
