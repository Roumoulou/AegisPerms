// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.hook.CommandsIndexHolder
import fr.moulou.aegisperms.hook.Hooks
import fr.moulou.aegisperms.hook.NodeMarkHolder
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.NodeMark
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/*
Les outils du mod de test (cahier des charges, 8.5), écrits une fois : les états publiés en mémoire, les joueurs simulés et leur niveau d'op,
l'enregistreur, l'analyse d'une ligne, l'arbre que le client reçoit. Rien ici ne passe par les fichiers : c'est l'état en vigueur que les tests
posent, et le mod qui le lit.
*/

/** L'identifiant du mod de test, celui de son `fabric.mod.json`. */
const val TEST_MOD_ID = "aegisperms-gametest"

/** Le serveur de test d'un `GameTestHelper`. */
val GameTestHelper.server: MinecraftServer get() = level.server

/**
 * Un joueur simulé connecté au serveur, non op, dont la source porte l'enveloppe. La fabrique est dépréciée dans les trois versions servies sans substitut
 * commun qui connecte le joueur (cahier des charges, 8.5) : elle ira au pont de version le jour où une release la retire.
 */
@Suppress("DEPRECATION")
fun GameTestHelper.judgedPlayer(): ServerPlayer = makeMockServerPlayerInLevel()

/** La source de commande d'un joueur, telle que le serveur la crée : elle porte l'autorité du joueur à cet instant. */
val ServerPlayer.source: CommandSourceStack get() = createCommandSourceStack()

/** La forme lisible d'un joueur simulé, celle sous laquelle le fichier des droits l'écrirait. */
fun ServerPlayer.readableForm(): String = ReadableForm.of(nameAndId().name(), uuid)

/**
 * Les clés de traduction d'un composant, la sienne puis celles de ses frères, en profondeur : `sendFailure` enveloppe le message dans un composant vide
 * dont il est le frère.
 */
val Component.translationKeys: List<String> get() = listOfNotNull((contents as? TranslatableContents)?.key) + siblings.flatMap { it.translationKeys }

/**
 * Les états que les tests publient en mémoire, à la place du fil des fichiers : une génération neuve à chaque publication, loin de celles que le
 * serveur a comptées. L'enveloppe, `canUse` et les fournisseurs d'API ne lisent que ce qui est publié.
 */
object TestStates {

    private val generation = AtomicLong(10_000)

    /** Publie une configuration faite de [permissions] et de [settings], et rend l'état publié. */
    fun publish(permissions: Permissions, settings: Settings = Settings.DELIVERED): EffectiveState {
        val state = EffectiveState(generation.incrementAndGet(), Configuration(settings, permissions))
        Publisher.publish(state)
        return state
    }

    /** Remet l'état du premier démarrage : les deux groupes livrés et les réglages livrés. */
    fun reset(): EffectiveState = publish(Permissions.DELIVERED)
}

/** Les droits d'un test : les groupes livrés, dont `default` reçoit [defaultRules] à la place des siennes, puis [groups] et [players]. */
fun permissionsWith(defaultRules: Map<String, Rule>, groups: Map<String, Group> = emptyMap(), players: Map<String, Player> = emptyMap()): Permissions =
    Permissions(Permissions.SCHEMA_VERSION, Permissions.DELIVERED.groups + (Group.DEFAULT to Group(rules = defaultRules)) + groups, players)

/** Les règles du `default` livré, `command` refusé et `help`, `me`, `msg` ouverts, complétées de [rules]. */
fun deliveredDefaultPlus(vararg rules: Pair<String, Rule>): Map<String, Rule> = Permissions.DELIVERED.groups.getValue(Group.DEFAULT).rules + rules

/** Les ops du serveur de test, par sa liste des ops, comme `/op` et `/deop` le font. */
object Ops {

    /** Fait de [player] un op de niveau [level] : sans le niveau explicite, le serveur de test donnerait le niveau 0, son `operatorUserPermissions()` étant `ALL`. */
    fun opAs(server: MinecraftServer, player: ServerPlayer, level: PermissionLevel) {
        server.playerList.op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.forLevel(level)), Optional.of(false))
    }

    fun deop(server: MinecraftServer, player: ServerPlayer) = server.playerList.deop(player.nameAndId())

    /** Retire l'op à tout joueur en ligne : l'état d'avant chaque test. */
    fun deopAll(server: MinecraftServer) {
        for (player in server.playerList.players) {
            if (server.playerList.isOp(player.nameAndId())) server.playerList.deop(player.nameAndId())
        }
    }
}

/** L'enregistreur (cahier des charges, 8.1) : une `CommandSource` qui garde les messages qu'une commande lui envoie, succès comme refus. */
class Recorder : CommandSource {

    val messages = ArrayList<Component>()

    override fun sendSystemMessage(component: Component) {
        messages += component
    }

    override fun acceptsSuccess(): Boolean = true

    override fun acceptsFailure(): Boolean = true

    override fun shouldInformAdmins(): Boolean = false
}

/** Le dispatcher en service et les arbres construits à part. */
object Trees {

    private val fillUsableCommands = Commands::class.java.getDeclaredMethod("fillUsableCommands", CommandNode::class.java, CommandNode::class.java, Any::class.java, Map::class.java).apply { isAccessible = true }

    /** Le nœud de [path] dans le dispatcher en service, ou une exception qui le nomme. */
    fun node(server: MinecraftServer, vararg path: String): CommandNode<CommandSourceStack> =
        server.commands.dispatcher.findNode(path.toList()) ?: throw IllegalStateException("No node /${path.joinToString(" ")} in the dispatcher")

    /** L'exception de l'analyse de [line] pour [source], ou `null` quand la ligne s'analyse en entier : `dispatcherUnknownCommand` pour un refus masqué (2.7). */
    fun parseException(server: MinecraftServer, line: String, source: CommandSourceStack): CommandSyntaxException? = Commands.getParseException(server.commands.dispatcher.parse(line, source))

    /** Vrai quand [line] s'analyse en entier et passe la porte d'exécution pour [source], sans rien exécuter. */
    fun runs(dispatcher: CommandDispatcher<CommandSourceStack>, line: String, source: CommandSourceStack): Boolean {
        val parse = dispatcher.parse(line, source)
        return !parse.reader.canRead() && parse.exceptions.isEmpty() && Hooks.allowsExecution(parse)
    }

    fun buildContext(server: MinecraftServer): CommandBuildContext = CommandBuildContext.simple(server.registryAccess(), server.worldData.enabledFeatures())

    /**
     * Un `Commands` construit à part, sur les réglages publiés à cet instant : les formes et les alias d'une configuration, indexés par l'accroche de sa
     * construction, sans toucher au dispatcher en service.
     */
    fun buildCommands(server: MinecraftServer): Commands = Commands(Commands.CommandSelection.DEDICATED, buildContext(server))

    /** L'index attaché à [commands], ou `null` quand il a manqué. */
    fun indexOf(commands: Commands): CommandIndex<CommandSourceStack>? = (commands as CommandsIndexHolder).`aegisperms$getIndex`()

    /** La marque que le mod a posée sur [node]. */
    fun markOf(node: CommandNode<*>): NodeMark? = (node as NodeMarkHolder).`aegisperms$getMark`()

    /** L'arbre que [source] recevrait de [dispatcher], recopié par vanilla lui-même, par la méthode de `sendCommands`, mixins compris. */
    fun usableTree(dispatcher: CommandDispatcher<CommandSourceStack>, source: CommandSourceStack): RootCommandNode<CommandSourceStack> {
        val tree = RootCommandNode<CommandSourceStack>()
        val copies = HashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>>()
        copies[dispatcher.root] = tree
        fillUsableCommands.invoke(null, dispatcher.root, tree, source, copies)
        return tree
    }
}

/**
 * Les paquets d'arbre de commandes qu'un joueur simulé a reçus : sa connexion écrit dans un canal embarqué sans encodeur, où les paquets s'accumulent tels
 * quels (cahier des charges, 8.5 et 8.8). La connexion et son canal se lisent par réflexion, sur des champs présents sous ce nom dans les trois versions
 * servies.
 */
object Packets {

    private val connectionField = ServerCommonPacketListenerImpl::class.java.getDeclaredField("connection").apply { isAccessible = true }
    private val channelField = Connection::class.java.getDeclaredField("channel").apply { isAccessible = true }

    /** Les paquets d'arbre envoyés à [player], dans l'ordre. */
    fun commandsPackets(player: ServerPlayer): List<ClientboundCommandsPacket> {
        val connection = connectionField.get(player.connection) as Connection
        val channel = channelField.get(connection) as EmbeddedChannel
        return channel.outboundMessages().filterIsInstance<ClientboundCommandsPacket>()
    }

    /** L'arbre du dernier paquet envoyé à [player], décodé avec ses drapeaux par [FlagNodeBuilder]. */
    fun lastTree(server: MinecraftServer, player: ServerPlayer): RootCommandNode<Any> = commandsPackets(player).last().getRoot(Trees.buildContext(server), FlagNodeBuilder)
}

/** Le drapeau `executable` d'un nœud relu d'un paquet : il porte une commande. */
val CommandNode<*>.executableFlag: Boolean get() = command != null

/** Le drapeau `restricted` d'un nœud relu d'un paquet : son exigence refuse. */
@Suppress("UNCHECKED_CAST")
val CommandNode<*>.restrictedFlag: Boolean get() = !(this as CommandNode<Any>).requirement.test(Unit)

/** Le bâtisseur qui relit un paquet : les drapeaux `executable` et `restricted` d'un nœud entrent dans sa commande et dans son exigence. */
object FlagNodeBuilder : ClientboundCommandsPacket.NodeBuilder<Any> {

    override fun createLiteral(name: String): ArgumentBuilder<Any, *> = LiteralArgumentBuilder.literal(name)

    @Suppress("UNCHECKED_CAST")
    override fun createArgument(name: String, type: ArgumentType<*>, suggestionId: Identifier?): ArgumentBuilder<Any, *> = RequiredArgumentBuilder.argument(name, type as ArgumentType<Any>)

    override fun configure(builder: ArgumentBuilder<Any, *>, executable: Boolean, restricted: Boolean): ArgumentBuilder<Any, *> {
        if (executable) builder.executes { 0 }
        if (restricted) builder.requires { false }
        return builder
    }
}
