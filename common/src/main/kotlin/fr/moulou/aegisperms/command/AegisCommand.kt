// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import fr.moulou.aegisperms.command.PermissionsEdits.RuleVerb
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Mode
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
    private const val NAME = "name"
    private const val PARENT = "parent"
    private const val WEIGHT = "weight"
    private const val LEVEL = "level"
    private const val PLAYER = "player"
    private const val KEY = "key"
    private const val LINE = "command"
    private const val TEXT = "text"
    private const val FOLDER = "folder"

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
            .then(Commands.literal("search").then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Queries.search(it, string(context, KEY)) } }))
            .then(
                Commands.literal("catalog")
                    .executes { context -> run(context) { Queries.catalog(it, context.source.server, "") } }
                    .then(Commands.argument("prefix", StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Queries.catalog(it, context.source.server, string(context, "prefix")) } }),
            )
            .then(groupBranch())
            .then(playerBranch())
            .then(Commands.literal("reload").executes { context -> run(context) { Modifications.reload(it) } })
            .then(Commands.literal("validate").executes { context -> run(context) { Queries.validate(it) } })
            .then(
                Commands.literal("backup")
                    .executes { context -> run(context) { Modifications.backup(it, null) } }
                    .then(Commands.argument(NAME, StringArgumentType.word()).executes { context -> run(context) { Modifications.backup(it, string(context, NAME)) } }),
            )
            .then(Commands.literal("restore").then(Commands.argument(FOLDER, StringArgumentType.word()).suggests(Suggestions.BACKUPS).executes { context -> run(context) { Modifications.restore(it, string(context, FOLDER)) } }))
            .then(
                Commands.literal("mode")
                    .then(Commands.literal("observe").executes { context -> run(context) { Modifications.mode(it, Mode.OBSERVE) } })
                    .then(Commands.literal("enforce").executes { context -> run(context) { Modifications.mode(it, Mode.ENFORCE) } }),
            )
    }

    private fun groupBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        val named = Commands.argument(GROUP, StringArgumentType.word()).suggests(Suggestions.GROUPS)
            .then(Commands.literal("info").executes { context -> run(context) { Queries.groupInfo(it, string(context, GROUP)) } })
            .then(
                Commands.literal("create")
                    .executes { context -> run(context) { Modifications.groupCreate(it, string(context, GROUP), 0) } }
                    .then(Commands.argument(WEIGHT, IntegerArgumentType.integer()).executes { context -> run(context) { Modifications.groupCreate(it, string(context, GROUP), IntegerArgumentType.getInteger(context, WEIGHT)) } }),
            )
            .then(Commands.literal("delete").executes { context -> run(context) { Modifications.groupDelete(it, string(context, GROUP)) } })
            .then(Commands.literal("rename").then(Commands.argument(NAME, StringArgumentType.word()).executes { context -> run(context) { Modifications.groupRename(it, string(context, GROUP), string(context, NAME)) } }))
            .then(Commands.literal("clone").then(Commands.argument(NAME, StringArgumentType.word()).executes { context -> run(context) { Modifications.groupClone(it, string(context, GROUP), string(context, NAME)) } }))
            .then(Commands.literal("weight").then(Commands.argument(WEIGHT, IntegerArgumentType.integer()).executes { context -> run(context) { Modifications.groupWeight(it, string(context, GROUP), IntegerArgumentType.getInteger(context, WEIGHT)) } }))
            .then(
                Commands.literal("parent")
                    .then(Commands.literal("add").then(Commands.argument(PARENT, StringArgumentType.word()).suggests(Suggestions.GROUPS).executes { context -> run(context) { Modifications.groupParent(it, string(context, GROUP), string(context, PARENT), add = true) } }))
                    .then(Commands.literal("remove").then(Commands.argument(PARENT, StringArgumentType.word()).suggests(Suggestions.GROUPS).executes { context -> run(context) { Modifications.groupParent(it, string(context, GROUP), string(context, PARENT), add = false) } })),
            )
            .then(Commands.literal("include-ops").then(Commands.argument(LEVEL, StringArgumentType.word()).suggests(Suggestions.LEVELS).executes { context -> run(context) { Modifications.groupIncludeOps(it, string(context, GROUP), string(context, LEVEL)) } }))
            .then(
                Commands.literal("note").then(
                    Commands.argument(TEXT, StringArgumentType.string())
                        .executes { context -> run(context) { Modifications.groupNote(it, string(context, GROUP), string(context, TEXT), null) } }
                        .then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Modifications.groupNote(it, string(context, GROUP), string(context, TEXT), string(context, KEY)) } }),
                ),
            )
        for (verb in RuleVerb.entries) {
            named.then(Commands.literal(verb.literal).then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { Modifications.groupRule(it, string(context, GROUP), verb, string(context, KEY)) } }))
        }
        return Commands.literal("group")
            .then(Commands.literal("list").executes { context -> run(context) { Queries.groupList(it) } })
            .then(named)
    }

    private fun playerBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        val target: RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(PLAYER, StringArgumentType.word()).suggests(Suggestions.PLAYERS)
            .then(Commands.literal("info").executes { context -> run(context) { reply -> withTarget(context, reply) { Queries.playerInfo(reply, context.source.server, it) } } })
            .then(
                Commands.literal("group")
                    .then(Commands.literal("add").then(Commands.argument(GROUP, StringArgumentType.word()).suggests(Suggestions.GROUPS).executes { context -> run(context) { reply -> withTarget(context, reply) { Modifications.playerGroup(reply, it, string(context, GROUP), add = true) } } }))
                    .then(Commands.literal("remove").then(Commands.argument(GROUP, StringArgumentType.word()).suggests(Suggestions.GROUPS).executes { context -> run(context) { reply -> withTarget(context, reply) { Modifications.playerGroup(reply, it, string(context, GROUP), add = false) } } })),
            )
            .then(
                Commands.literal("note").then(
                    Commands.argument(TEXT, StringArgumentType.string())
                        .executes { context -> run(context) { reply -> withTarget(context, reply) { Modifications.playerNote(reply, it, string(context, TEXT), null) } } }
                        .then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { reply -> withTarget(context, reply) { Modifications.playerNote(reply, it, string(context, TEXT), string(context, KEY)) } } }),
                ),
            )
        for (verb in RuleVerb.entries) {
            target.then(Commands.literal(verb.literal).then(Commands.argument(KEY, StringArgumentType.greedyString()).suggests(Suggestions.KEYS).executes { context -> run(context) { reply -> withTarget(context, reply) { Modifications.playerRule(reply, it, verb, string(context, KEY)) } } }))
        }
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
