// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.text.TextKeys.Command
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionLevel

/**
 * `/aegis` et ses sous-commandes (cahier des charges, 5.3), de clés `aegisperms:command.aegis` et descendantes. La racine porte l'exigence vanilla
 * `admin-level`, pour que `/help` et le catalogue la rangent avec l'administration, et une commande du mod, pour que son mod propriétaire soit `aegisperms` ;
 * c'est `canUse` qui décide pour une source jugée, et chaque exécuteur refuse les sources que 5.2 écarte (E5). Les types d'argument sont ceux de vanilla, et
 * une clé ou une ligne vient en dernier, lue jusqu'au bout.
 */
object AegisCommand {

    private const val GROUP = "group"
    private const val PLAYER = "player"
    private const val KEY = "key"
    private const val LINE = "command"

    /** L'arbre de `/aegis`, bâti sur l'`admin-level` des réglages publiés à cet instant : un `admin-level` changé prend effet à la construction suivante. */
    fun tree(): LiteralArgumentBuilder<CommandSourceStack> {
        val level = PermissionLevel.byId(Publisher.settings().adminLevel.level)
        return Commands.literal(Keys.ADMINISTRATION_ROOT)
            .requires(Commands.hasPermission<CommandSourceStack>(PermissionCheck.Require(Permission.HasCommandLevel(level))))
            .executes { context -> run(context) { reply -> reply.line(reply.text(Command.USAGE)) } }
            .then(Commands.literal("status").executes { context -> run(context) { Queries.status(it) } })
            .then(
                Commands.literal("why").then(
                    Commands.argument(PLAYER, StringArgumentType.word()).suggests(Suggestions.PLAYERS).then(
                        Commands.argument(LINE, StringArgumentType.greedyString()).suggests(Suggestions.ROOTS)
                            .executes { context -> run(context) { reply -> withTarget(context, reply) { Explain.why(reply, context.source.server, it, string(context, LINE)) } } },
                    ),
                ),
            )
            .then(
                Commands.literal("check").then(
                    Commands.argument(PLAYER, StringArgumentType.word()).suggests(Suggestions.PLAYERS).then(
                        Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS)
                            .executes { context -> run(context) { reply -> withTarget(context, reply) { Explain.check(reply, context.source.server, it, string(context, KEY)) } } },
                    ),
                ),
            )
            .then(
                Commands.literal("catalog")
                    .executes { context -> run(context) { Queries.catalog(it, context.source.server, "") } }
                    .then(Commands.argument("prefix", StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Queries.catalog(it, context.source.server, string(context, "prefix")) } }),
            )
            .then(Commands.literal("search").then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Queries.search(it, string(context, KEY)) } }))
            .then(groupBranch())
            .then(playerBranch())
            .then(Commands.literal("validate").executes { context -> run(context) { Queries.validate(it) } })
    }

    private fun groupBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        val named = Commands.argument(GROUP, StringArgumentType.word()).suggests(Suggestions.GROUPS)
            .then(Commands.literal("info").executes { context -> run(context) { Queries.groupInfo(it, string(context, GROUP)) } })
        return Commands.literal("group")
            .then(Commands.literal("list").executes { context -> run(context) { Queries.groupList(it) } })
            .then(named)
    }

    private fun playerBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        val target = Commands.argument(PLAYER, StringArgumentType.word()).suggests(Suggestions.PLAYERS)
            .then(Commands.literal("info").executes { context -> run(context) { reply -> withTarget(context, reply) { Queries.playerInfo(reply, context.source.server, it) } } })
        return Commands.literal("player").then(target)
    }

    /** L'exécution d'une sous-commande : la source que 5.2 écarte reçoit un refus, les autres leur réponse. */
    private fun run(context: CommandContext<CommandSourceStack>, action: (Reply) -> Int): Int {
        val reply = Reply(context.source)
        if (!Administrators.mayAdminister(context.source)) return reply.refuse(Command.SOURCE_REFUSED)
        return action(reply)
    }

    /** Le joueur de l'argument `player`, résolu selon 5.4, ou le refus qui dit pourquoi il ne l'est pas. */
    private fun withTarget(context: CommandContext<CommandSourceStack>, reply: Reply, action: (PlayerTarget) -> Int): Int {
        val lookup = ServerLookup(context.source.server, Publisher.currentOrNull()?.state)
        return when (val resolution = PlayerTargets.resolve(string(context, PLAYER), lookup)) {
            is TargetResolution.Found -> action(resolution.target)
            is TargetResolution.Unknown -> reply.refuse(Command.PLAYER_UNKNOWN, resolution.input)
            is TargetResolution.Ambiguous -> reply.refuse(Command.PLAYER_AMBIGUOUS, resolution.input, resolution.candidates.joinToString(", "))
        }
    }

    private fun string(context: CommandContext<CommandSourceStack>, name: String): String = StringArgumentType.getString(context, name)
}
