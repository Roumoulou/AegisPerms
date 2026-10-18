// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.resolve.NodeJudgment
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.text.Messages
import fr.moulou.aegisperms.text.TextKeys
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.LoaderMods
import fr.moulou.aegisperms.tree.NodeEntry
import fr.moulou.aegisperms.tree.NodeMark
import fr.moulou.aegisperms.tree.RootMark
import fr.moulou.aegisperms.tree.Unindexed
import fr.moulou.aegisperms.tree.identitySetOf
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet
import java.util.function.Predicate

/**
 * Les points d'accroche que les mixins appellent (cahier des charges, 7.5). Chacun écarte d'abord toute source non jugée, dont la réponse reste celle de
 * vanilla, sans rien lire de l'état en vigueur. Pour une source jugée, une erreur interne vaut un refus masqué, journalisé une fois par clé et par génération, parce
 * qu'un serveur fermé vaut mieux qu'un serveur ouvert à vanilla par accident.
 *
 * Les fonctions qui prennent une marque ou un `markOf` sont celles que l'étage 1 éprouve : sans mixin, un nœud ne porte pas de marque.
 */
object Hooks {

    // Les noms des accroches au journal.
    const val CAN_USE = "canUse"
    const val GATE = "gate"
    const val SUGGESTIONS = "suggestions"
    const val CLIENT_TREE = "clientTree"
    const val LATE_NODE = "lateNode"

    /** La clé de traduction du refus visible (D18), envoyée avec son repli dans la langue du joueur (5.7). */
    const val REFUSAL_KEY = TextKeys.REFUSAL

    private const val UNKNOWN_COMMAND = "command.unknown.command"
    private const val UNKNOWN_ARGUMENT = "command.unknown.argument"

    // ── L'autorité ───────────────────────────────────────────────────────────────

    /**
     * Le `PermissionSet` d'un joueur, enveloppé (6.2), et son niveau d'op retenu pour les demandes d'API par UUID. L'ensemble d'origine reste quand il n'est
     * pas celui d'un niveau d'op, ce qu'aucune version ne fait sans un autre mod : c'est le contrôle à la connexion qui le dit (6.6).
     */
    @JvmStatic
    fun envelope(player: ServerPlayer, original: PermissionSet): PermissionSet {
        if (original !is LevelBasedPermissionSet) return original
        OpLevels.record(player.uuid, original.level().id())
        return Envelope(player.uuid, original)
    }

    // ── La décision par nœud ─────────────────────────────────────────────────────

    /** La décision de `canUse` (7.5) : [original] est la réponse de l'exigence vanilla, évaluée sur la source telle qu'elle est. */
    @JvmStatic
    fun canUse(node: CommandNode<*>, source: Any?, original: Boolean): Boolean {
        if (source !is CommandSourceStack) return original
        val envelope = source.permissions() as? Envelope ?: return original
        return decide(markOf(node), node, envelope, source, original)
    }

    /**
     * La décision pour un nœud de marque [mark] et une source jugée (2.7, 2.8). La réponse vanilla [original] décide seule pour un nœud sans marque, celui
     * d'un arbre privé, et sous `unset` ; un arbre sans index est fermé ; un refus visible garde le nœud vu, et c'est la porte qui le refuse.
     */
    internal fun decide(mark: NodeMark?, node: CommandNode<*>, envelope: Envelope, source: CommandSourceStack, original: Boolean): Boolean {
        // En observation (5.8), l'arbre, les complétions et la forme exigée sont ceux de vanilla ; l'écart ne se note qu'à l'exécution.
        if (Publisher.mode() == Mode.OBSERVE) return original
        val entry = when (mark) {
            null, is RootMark -> return original
            Unindexed -> return false
            is NodeEntry -> mark
        }
        return try {
            val resolver = Publisher.current()
            if (entry.hiddenUnderRequirePrefix && resolver.state.settings.namespaces.requirePrefix) return false
            when (resolver.judgment(envelope.subject, envelope.level().id(), entry.key)) {
                NodeJudgment.HIDDEN -> false
                NodeJudgment.REFUSED -> true
                NodeJudgment.ALLOWED, NodeJudgment.ALLOWED_UNDER_REFUSAL -> entry.requirement != CommandRequirement.MOD_PREDICATE || testWithAllRights(node, source)
                NodeJudgment.UNSET -> original
            }
        } catch (error: Throwable) {
            ModLog.once(CAN_USE, entry.key.text, error)
            false
        }
    }

    /** Évalue le prédicat de mod de [node] comme si la source avait tous les droits (2.8) : la seule branche du chemin chaud qui alloue (D6). */
    @Suppress("UNCHECKED_CAST")
    internal fun testWithAllRights(node: CommandNode<*>, source: CommandSourceStack): Boolean = (node.requirement as Predicate<CommandSourceStack>).test(source.withPermission(AllRights))

    // ── La porte d'exécution ─────────────────────────────────────────────────────

    /** La porte d'exécution (7.5). Vrai si la commande analysée dans [parse] peut s'exécuter ; sinon la source reçoit le message du refus. */
    @JvmStatic
    fun allowsExecution(parse: ParseResults<*>): Boolean = allowsExecution(parse, ::markOf)

    /**
     * La porte d'exécution pour [parse], les marques lues par [markOf] : la forme que l'étage 1 éprouve, sans mixin. En observation (5.8), la commande s'exécute quoi qu'il en soit, la forme exigée ne retire rien, et le refus
     * que le mod aurait opposé se journalise comme un écart (E11).
     */
    internal fun allowsExecution(parse: ParseResults<*>, markOf: (CommandNode<*>) -> NodeMark?): Boolean {
        val source = parse.context.source as? CommandSourceStack ?: return true
        val envelope = source.permissions() as? Envelope ?: return true
        val observing = Publisher.mode() == Mode.OBSERVE
        val root = parse.context.nodes.firstOrNull()?.node?.name ?: "?"
        val refusal = try {
            gateRefusal(parse, envelope, markOf, requirePrefix = !observing && Publisher.settings().namespaces.requirePrefix)
        } catch (error: Throwable) {
            ModLog.once(GATE, root, error)
            if (observing) return true
            GateRefusal(visible = false, deep = false, key = "?")
        }
        ModLog.debug(ModLog.DECISION, envelope.subject, root, refusal?.let { (if (it.visible) "deny-visible " else "deny-hidden ") + it.key } ?: "allow")
        if (refusal == null) return true
        if (observing) {
            ObservationLog.deviation(envelope.subject, refusal.key, if (refusal.visible) Decision.DENY_VISIBLE else Decision.DENY_HIDDEN, ObservationLog.VANILLA_EXECUTES)
            return true
        }
        val message = when {
            !refusal.visible -> Component.translatable(if (refusal.deep) UNKNOWN_ARGUMENT else UNKNOWN_COMMAND)
            else -> {
                val settings = Publisher.settings()
                settings.refusalMessage?.let { Component.literal(it) } ?: Messages.component(REFUSAL_KEY, Messages.languageOf(source, settings.language))
            }
        }
        source.sendFailure(message)
        return false
    }

    /**
     * Le refus que la porte oppose à [parse] pour une source jugée, ou `null` quand la commande peut s'exécuter : le chemin entier, redirections suivies,
     * chaque nœud lu par sa clé (2.7). La cible d'une redirection n'est pas tapée : la forme exigée ne la vise pas, sans quoi `/mc:execute as ... run`
     * s'arrêterait sur la forme nue d'`execute`, où ses sous-commandes redirigent (3.5).
     */
    internal fun gateRefusal(parse: ParseResults<*>, envelope: Envelope, markOf: (CommandNode<*>) -> NodeMark?): GateRefusal? =
        gateRefusal(parse, envelope, markOf, Publisher.current().state.settings.namespaces.requirePrefix)

    private fun gateRefusal(parse: ParseResults<*>, envelope: Envelope, markOf: (CommandNode<*>) -> NodeMark?, requirePrefix: Boolean): GateRefusal? {
        val resolver = Publisher.current()
        var context: CommandContextBuilder<*>? = parse.context
        var first = true
        var deep = false
        // Dans le dispatcher en service, marqué à sa racine, un nœud sans fiche est une anomalie, journalisée une fois (7.5) ; vanilla garde la décision.
        val indexed = markOf(parse.context.rootNode) is RootMark
        while (context != null) {
            if (!first) refusalOf(context.rootNode, markOf, typed = false, requirePrefix, resolver, envelope)?.let { return GateRefusal(it.visible, deep, it.key) }
            for (parsed in context.nodes) {
                if (indexed && markOf(parsed.node) == null) ModLog.once(GATE, parsed.node.name, IllegalStateException("Node '${parsed.node.name}' of the served dispatcher has no mark"))
                refusalOf(parsed.node, markOf, typed = true, requirePrefix, resolver, envelope)?.let { return GateRefusal(it.visible, deep, it.key) }
                deep = true
            }
            context = context.child
            first = false
        }
        return null
    }

    /** Le refus d'un nœud, ou `null` sans refus : une commande s'utilise sans aucun refus sur son chemin (2.7). */
    private fun refusalOf(node: CommandNode<*>, markOf: (CommandNode<*>) -> NodeMark?, typed: Boolean, requirePrefix: Boolean, resolver: Resolver, envelope: Envelope): Refusing? {
        val entry = when (val mark = markOf(node)) {
            null, is RootMark -> return null
            Unindexed -> return Refusing(visible = false, key = node.name)
            is NodeEntry -> mark
        }
        if (typed && entry.hiddenUnderRequirePrefix && requirePrefix) return Refusing(visible = false, key = entry.key.text)
        val judgment = resolver.judgment(envelope.subject, envelope.level().id(), entry.key)
        return when {
            judgment == NodeJudgment.HIDDEN -> Refusing(visible = false, key = entry.key.text)
            judgment.hasVisibleRefusal -> Refusing(visible = true, key = entry.key.text)
            else -> null
        }
    }

    // ── Les complétions et l'arbre du client ─────────────────────────────────────

    /** Vrai si les suggestions de [node] se taisent pour la source de [context] : un nœud qu'une source jugée ne voit pas (7.5). */
    @JvmStatic
    fun hidesSuggestions(node: CommandNode<*>, context: CommandContext<*>): Boolean {
        val source = context.source as? CommandSourceStack ?: return false
        if (source.permissions() !is Envelope) return false
        return try {
            @Suppress("UNCHECKED_CAST")
            !(node as CommandNode<CommandSourceStack>).canUse(source)
        } catch (error: Throwable) {
            ModLog.once(SUGGESTIONS, node.name, error)
            true
        }
    }

    /** La copie qui remplace, dans l'arbre d'un joueur, une redirection vers [target] que cet arbre n'a pas (3.7), ou `null`. */
    @JvmStatic
    fun clientRedirect(copies: Map<Any?, Any?>, target: Any?): Any? = clientRedirect(copies, target, ::markOf)

    /**
     * La copie d'une forme qui partage les enfants de [target], au besoin au bout de ses redirections simples, quand [copies] la porte. `null` sinon, et
     * vanilla perd la redirection, comme sans le mod.
     */
    internal fun clientRedirect(copies: Map<Any?, Any?>, target: Any?, markOf: (CommandNode<*>) -> NodeMark?): Any? {
        try {
            val seen = identitySetOf<CommandNode<*>>()
            var current = target as? CommandNode<*>
            while (current != null && seen.add(current)) {
                val node: CommandNode<*> = current
                if (node !== target) copies[node]?.let { return it }
                (markOf(node) as? NodeEntry)?.forms?.forEach { form -> copies[form]?.let { return it } }
                current = if (node.children.isEmpty() && node.redirectModifier == null && !node.isFork) node.redirect else null
            }
        } catch (error: Throwable) {
            ModLog.once(CLIENT_TREE, (target as? CommandNode<*>)?.name ?: "?", error)
        }
        return null
    }

    // ── L'index, les formes et les alias ─────────────────────────────────────────

    /**
     * La fin de la construction d'un `Commands` (7.5) : les formes, les alias et l'index de son dispatcher, bâtis sur les réglages publiés à cet instant,
     * attachés à lui sans rien publier. Un index qui ne se construit pas ferme l'arbre aux sources jugées (D3).
     */
    @JvmStatic
    fun onCommandsBuilt(commands: Commands, dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = dispatcher.root
        try {
            val index = CommandIndex.build(root, Publisher.settings(), LoaderMods, VanillaRequirements)
            for ((node, entry) in index.entries) setMark(node, entry)
            setMark(root, index.rootMark)
            (commands as CommandsIndexHolder).`aegisperms$setIndex`(index)
        } catch (error: Throwable) {
            ModLog.error(ModLog.INDEX_FAILED, error)
            closeTree(root)
        }
    }

    /** Un enfant ajouté à un nœud marqué, après l'index (7.5, les nœuds tardifs) : il reçoit sa fiche, et une racine tardive sa forme préfixée (3.8). */
    @JvmStatic
    fun onChildAdded(parent: CommandNode<*>, child: CommandNode<*>) {
        // `addChild` fusionne un homonyme dans l'enfant déjà là, et le nœud reçu n'entre pas dans l'arbre. Un enfant déjà marqué, la forme que `addLateRoot` vient d'ajouter
        // ou un nœud repris tel quel d'un arbre précédent, garde sa marque.
        if (parent.getChild(child.name) !== child || markOf(child) != null) return
        try {
            when (val mark = markOf(parent)) {
                null -> return
                Unindexed -> closeTree(child)
                is RootMark -> addLateRoot(mark, child)
                is NodeEntry -> addLateChild(mark, child)
            }
        } catch (error: Throwable) {
            ModLog.once(LATE_NODE, child.name, error)
            closeTree(child)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addLateRoot(mark: RootMark, child: CommandNode<*>) {
        val index = mark.index as CommandIndex<CommandSourceStack>
        val late = index.lateRoot(child as CommandNode<CommandSourceStack>, ::markOf) ?: return
        // Une forme dont le nom est déjà pris se refuse avec le même constat qu'à la validation (3.8), la racine tardive n'y passant pas.
        late.takenFormName?.let { ModLog.issue(Issue(IssueKind.PREFIXED_FORM_NAME_TAKEN, ConfigurationFile.SETTINGS, emptyList(), listOf(IssueArgument.Literal(child.name), IssueArgument.Literal(it)))) }
        for ((node, entry) in late.entries) setMark(node, entry)
        // La forme est marquée avant d'entrer dans l'arbre : son ajout repasse ici et s'arrête à la garde.
        late.form?.let { index.root.addChild(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addLateChild(parent: NodeEntry, child: CommandNode<*>) {
        for ((node, entry) in CommandIndex.lateEntries(parent, child as CommandNode<Any?>, VanillaRequirements) { markOf(it) != null }) setMark(node, entry)
        // Les formes partagent les enfants de leur cible (3.4) : l'enfant tardif leur revient aussi.
        for (form in parent.forms) (form as CommandNode<Any?>).addChild(child)
    }

    /** Ferme l'arbre de [root], lui compris : chaque nœud reçoit la marque d'index manqué, qui vaut un refus masqué pour une source jugée (D3). */
    private fun closeTree(root: CommandNode<*>) {
        val seen = identitySetOf<CommandNode<*>>()
        val pending = ArrayDeque<CommandNode<*>>()
        pending += root
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (!seen.add(node)) continue
            setMark(node, Unindexed)
            pending += node.children
        }
    }

    /** La marque d'un nœud du serveur, posée par le mixin ; `null` pour un nœud d'un arbre que le mod n'a pas indexé. */
    internal fun markOf(node: CommandNode<*>): NodeMark? = (node as NodeMarkHolder).`aegisperms$getMark`()

    private fun setMark(node: CommandNode<*>, mark: NodeMark) = (node as NodeMarkHolder).`aegisperms$setMark`(mark)

    // ── Les types ────────────────────────────────────────────────────────────────

    /** Un refus à la porte : visible ou masqué, sur la commande racine ou sur un nœud plus profond, ce qui choisit le message de vanilla, et la clé qui refuse. */
    internal class GateRefusal(val visible: Boolean, val deep: Boolean, val key: String)

    /** Ce qu'un nœud oppose à l'exécution : un refus visible ou masqué, avec la clé qui le porte. */
    private class Refusing(val visible: Boolean, val key: String)
}
