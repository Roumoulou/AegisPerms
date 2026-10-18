// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.suggestion.SuggestionProvider
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.CommandsIndexHolder
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.CodePointOrder
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.tree.CommandIndex
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.MinecraftServer

/**
 * Les complétions de `/aegis` (cahier des charges, 5.3) : les joueurs, en ligne et du fichier, les groupes, les clés du catalogue de l'index en service et
 * les clés d'API demandées, deux cents propositions au plus, les racines de commande. L'index se lit sur le `Commands` en service.
 */
internal object Suggestions {

    private const val MAX_KEYS = 200

    val PLAYERS = SuggestionProvider<CommandSourceStack> { context, builder ->
        val online = context.source.server.playerList.players.map { it.nameAndId().name() }
        val known = Publisher.currentOrNull()?.state?.players?.values?.mapNotNull { it.name }.orEmpty()
        SharedSuggestionProvider.suggest((online + known).distinct(), builder)
    }

    val GROUPS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(Publisher.currentOrNull()?.state?.permissions?.groups?.keys.orEmpty(), builder)
    }

    /** Les clés : `command`, la clé générale de chaque mod, les clés de commande de l'index, les clés d'API demandées ; filtrées par ce qui est tapé. */
    val KEYS = SuggestionProvider<CommandSourceStack> { context, builder ->
        val typed = builder.remaining
        val index = indexOf(context.source.server)
        val keys = sequence {
            yield(Keys.ALL_COMMANDS.text)
            if (index != null) {
                yieldAll(index.commandKeys.mapNotNullTo(HashSet()) { it.namespace }.map { Keys.command(it, emptyList()).text })
                yieldAll(index.commandKeys.map { it.text })
            }
            yieldAll(ApiKeyRequests.keys().map { it.text })
        }.filter { it.startsWith(typed) }.toSortedSet(CodePointOrder).take(MAX_KEYS)
        SharedSuggestionProvider.suggest(keys, builder)
    }

    val ROOTS = SuggestionProvider<CommandSourceStack> { context, builder ->
        SharedSuggestionProvider.suggest(context.source.server.commands.dispatcher.root.children.map { it.name }, builder)
    }

    /** Les niveaux d'op d'`include-ops` (2.5), et `none`. */
    val LEVELS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(OpLevel.entries.map { it.name.lowercase() } + "none", builder)
    }

    /** Les deux langues du mod, pour `manual` (5.10). */
    val LANGUAGES = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(Lang.entries.map { it.code }, builder) }

    /** Les dossiers de `backups\`, pour `restore` (4.10). */
    val BACKUPS = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(FileThread.backupNames(), builder) }

    /** L'index du `Commands` en service, ou `null` quand il a manqué. */
    fun indexOf(server: MinecraftServer): CommandIndex<CommandSourceStack>? = (server.commands as CommandsIndexHolder).`aegisperms$getIndex`()
}
