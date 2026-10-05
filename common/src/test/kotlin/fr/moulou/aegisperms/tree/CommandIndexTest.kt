// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.context.CommandContext
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Predicate

/** L'index d'un arbre de commandes (cahier des charges, 7.4 et 7.5), sur des arbres synthétiques, un loader simulé et une lecture d'exigence simulée. */
class CommandIndexTest {

    companion object {
        private val GAMEMASTERS_ONLY = Predicate<Any> { false }
        private val PLAYERS_ONLY = Predicate<Any> { false }
    }

    @Test
    fun `chaque nœud reçoit sa fiche, avec sa clé et son exigence`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val gamemode = index.entryOf(dispatcher.root.getChild("gamemode"))!!
        val creative = index.entryOf(dispatcher.findNode(listOf("gamemode", "creative")))!!
        val home = index.entryOf(dispatcher.root.getChild("home"))!!

        assertEquals(key("minecraft:command.gamemode"), gamemode.key)
        assertEquals(CommandRequirement.GAMEMASTERS, gamemode.requirement)
        assertTrue(gamemode.isRoot)
        assertEquals(key("minecraft:command.gamemode.creative"), creative.key)
        assertEquals(CommandRequirement.NONE, creative.requirement)
        assertEquals(key("homes:command.home"), home.key)
        assertEquals(CommandRequirement.MOD_PREDICATE, home.requirement)
        assertEquals(4, index.entries.size)
    }

    @Test
    fun `la racine de l'arbre porte la marque de l'index, pas une fiche`() {
        val dispatcher = tree()
        val index = index(dispatcher)

        assertSame(index.rootMark, index.markOf(dispatcher.root))
        assertSame(index, index.rootMark.index)
        assertNull(index.entryOf(dispatcher.root))
        assertSame(index.entryOf(dispatcher.root.getChild("home")), index.markOf(dispatcher.root.getChild("home")))
        assertNull(index.markOf(literal<Any>("ailleurs").build()))
    }

    @Test
    fun `l'index rend les nœuds d'une clé et le mod propriétaire d'une racine`() {
        val dispatcher = tree()
        val index = index(dispatcher, assignedOwners = mapOf("home" to "warps"))

        assertEquals(listOf(dispatcher.root.getChild("gamemode"), dispatcher.root.getChild("gm")), index.nodesOf(key("minecraft:command.gamemode")))
        assertTrue(index.nodesOf(key("minecraft:command.absent")).isEmpty())
        assertEquals(mapOf(key("minecraft:command.gm") to key("minecraft:command.gamemode")), index.aliasKeys)
        assertEquals(Owner("minecraft", OwnerSource.DETECTED, emptyList()), index.ownerOf("gamemode"))
        assertEquals(Owner("warps", OwnerSource.ASSIGNED, listOf("homes")), index.ownerOf("home"))
        assertNull(index.ownerOf("absent"))
        assertEquals(setOf(key("minecraft:command.gamemode"), key("minecraft:command.gamemode.creative"), key("warps:command.home")), index.commandKeys)
    }

    @Test
    fun `un enfant ajouté après l'index prend la clé de son parent prolongée de son nom, sous-arbre compris`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val gamemode = dispatcher.root.getChild("gamemode")
        val late = literal<Any>("late").requires(GAMEMASTERS_ONLY).then(argument<Any, String>("name", StringArgumentType.word()).executes { 1 }).build()
        gamemode.addChild(late)

        val entries = CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, Reader) { false }

        assertEquals(listOf(late, late.getChild("name")), entries.map { it.first })
        assertEquals(listOf(key("minecraft:command.gamemode.late"), key("minecraft:command.gamemode.late.name")), entries.map { it.second.key })
        assertEquals(listOf(CommandRequirement.GAMEMASTERS, CommandRequirement.NONE), entries.map { it.second.requirement })
        assertTrue(entries.none { it.second.isRoot || it.second.inheritsKey || it.second.shared })
    }

    @Test
    fun `un enfant tardif dont le nom ne fait pas une clé se juge sur la clé de son parent`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val home = dispatcher.root.getChild("home")
        val late = argument<Any, String>("player name", StringArgumentType.word()).then(literal<Any>("set").executes { 1 }).build()
        home.addChild(late)

        val entries = CommandIndex.lateEntries(index.entryOf(home)!!, late, Reader) { false }

        assertEquals(listOf(key("homes:command.home"), key("homes:command.home")), entries.map { it.second.key })
        assertTrue(entries.all { it.second.inheritsKey })
    }

    @Test
    fun `un enfant tardif d'un alias de vanilla prolonge la clé propre de l'alias`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val late = literal<Any>("late").build()

        val entries = CommandIndex.lateEntries(index.entryOf(dispatcher.root.getChild("gm"))!!, late, Reader) { false }

        assertEquals(listOf(key("minecraft:command.gm.late")), entries.map { it.second.key })
    }

    @Test
    fun `un nœud qui porte déjà une marque la garde, lui et ce qui descend de lui`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val gamemode = dispatcher.root.getChild("gamemode")
        val known = literal<Any>("known").then(literal<Any>("below")).build()
        val late = literal<Any>("late").then(known).then(literal<Any>("fresh")).build()

        assertTrue(CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, Reader) { true }.isEmpty())

        val entries = CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, Reader) { it === known }
        assertEquals(listOf("late", "fresh"), entries.map { it.first.name })
        assertFalse(entries.any { it.first === known.getChild("below") })
    }

    private fun key(text: String): CommandKey = Keys.parse(text) as CommandKey

    private fun index(dispatcher: CommandDispatcher<Any>, assignedOwners: Map<String, String> = emptyMap()): CommandIndex<Any> =
        CommandIndex.build(dispatcher.root, assignedOwners, Mods, Reader)

    /** `gamemode` de vanilla, son alias `gm`, et `home`, la commande d'un mod. */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val gamemode = dispatcher.register(literal<Any>("gamemode").requires(GAMEMASTERS_ONLY).then(literal<Any>("creative").executes(VanillaCommand())))
        dispatcher.register(literal<Any>("gm").requires(GAMEMASTERS_ONLY).redirect(gamemode))
        dispatcher.register(literal<Any>("home").requires(PLAYERS_ONLY).executes(HomeCommand()))
        return dispatcher
    }

    private object Mods : ModLocator {

        private val modIdByClassName = mapOf(VanillaCommand::class.java.name to "minecraft", HomeCommand::class.java.name to "homes", WarpCommand::class.java.name to "warps")

        override fun modIdOf(className: String): String? = modIdByClassName[className]

        override fun isLoaded(modId: String): Boolean = modId in modIdByClassName.values
    }

    /** Une lecture d'exigence simulée : deux prédicats connus, et le prédicat par défaut de Brigadier. */
    private object Reader : RequirementReader {
        override fun read(requirement: Predicate<*>): CommandRequirement = when {
            Requirements.isDefault(requirement) -> CommandRequirement.NONE
            requirement === GAMEMASTERS_ONLY -> CommandRequirement.GAMEMASTERS
            else -> CommandRequirement.MOD_PREDICATE
        }
    }

    private class VanillaCommand : Command<Any> {
        override fun run(context: CommandContext<Any>): Int = 1
    }

    private class HomeCommand : Command<Any> {
        override fun run(context: CommandContext<Any>): Int = 1
    }

    private class WarpCommand : Command<Any> {
        override fun run(context: CommandContext<Any>): Int = 1
    }
}
