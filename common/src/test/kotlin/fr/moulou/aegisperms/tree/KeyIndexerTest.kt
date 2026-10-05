// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.tree.CommandNode
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

/** Les clés des nœuds d'un arbre (cahier des charges, 2.4), calculées sur des arbres Brigadier synthétiques, sans Minecraft. */
class KeyIndexerTest {

    @Test
    fun `un nœud prend la clé de son parent prolongée de son nom`() {
        val dispatcher = vanilla()
        val index = index(dispatcher)

        assertEquals(key("minecraft:command.teleport"), index.entry(dispatcher, "teleport")?.key)
        assertEquals(key("minecraft:command.teleport.location"), index.entry(dispatcher, "teleport", "location")?.key)
        assertEquals(key("minecraft:command.teleport.targets.location"), index.entry(dispatcher, "teleport", "targets", "location")?.key)
        assertTrue(index.entry(dispatcher, "teleport")!!.isRoot)
        assertFalse(index.entry(dispatcher, "teleport", "targets")!!.isRoot)
        assertNull(index.entries[dispatcher.root])
    }

    @Test
    fun `un argument s'écrit par son nom, exact et sensible à la casse`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("teleport").then(argument<Any, String>("facingEntity", StringArgumentType.word()).executes { 1 }))
        val facing = index(dispatcher).entry(dispatcher, "teleport", "facingEntity")!!.key

        assertEquals("minecraft:command.teleport.facingEntity", facing.text)
        assertNotEquals(key("minecraft:command.teleport.facingentity"), facing)
    }

    @Test
    fun `un point dans un nom de nœud s'échappe`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("home.set").executes { 1 })

        assertEquals("homes:command.home%2Eset", index(dispatcher, mapOf("home.set" to "homes")).entry(dispatcher, "home.set")?.key?.text)
    }

    @Test
    fun `le modid d'une clé est celui du mod propriétaire de sa commande racine`() {
        val dispatcher = vanilla()
        dispatcher.register(literal<Any>("home").then(argument<Any, String>("name", StringArgumentType.word()).executes { 1 }))
        val index = index(dispatcher, mapOf("home" to "homes"))

        assertEquals(key("homes:command.home.name"), index.entry(dispatcher, "home", "name")?.key)
        assertEquals(key("minecraft:command.gamemode.creative"), index.entry(dispatcher, "gamemode", "creative")?.key)
    }

    @Test
    fun `une branche greffée par un autre mod garde le modid de la racine`() {
        val dispatcher = vanilla()
        dispatcher.register(literal<Any>("gamemode").then(literal<Any>("spectate_all").executes { 2 }))

        assertEquals(key("minecraft:command.gamemode.spectate_all"), index(dispatcher).entry(dispatcher, "gamemode", "spectate_all")?.key)
        assertEquals(1, dispatcher.root.children.count { it.name == "gamemode" })
    }

    @Test
    fun `un alias de vanilla se juge sur la clé de sa cible et garde la sienne pour le catalogue`() {
        val dispatcher = vanilla()
        val index = index(dispatcher)
        val tp = index.entry(dispatcher, "tp")!!

        assertEquals(key("minecraft:command.teleport"), tp.key)
        assertEquals(key("minecraft:command.tp"), tp.ownKey)
        assertTrue(tp.isVanillaAlias)
        assertTrue(tp.isRoot)
        assertEquals(mapOf(key("minecraft:command.tp") to key("minecraft:command.teleport")), index.aliasKeys)
        assertEquals(listOf("teleport", "tp"), index.nodesByKey.getValue(key("minecraft:command.teleport")).map { it.name })
        assertFalse(index.entry(dispatcher, "teleport")!!.isVanillaAlias)
    }

    @Test
    fun `run garde sa clé sous execute`() {
        val dispatcher = vanilla()
        val run = index(dispatcher).entry(dispatcher, "execute", "run")!!

        assertEquals(key("minecraft:command.execute.run"), run.key)
        assertFalse(run.isVanillaAlias)
    }

    @Test
    fun `un fork garde sa clé`() {
        val dispatcher = vanilla()
        val index = index(dispatcher)

        assertEquals(key("minecraft:command.execute.as.targets"), index.entry(dispatcher, "execute", "as", "targets")?.key)
        assertEquals(key("minecraft:command.execute"), index.entry(dispatcher, "execute")?.key)
    }

    @Test
    fun `une racine qui redirige vers un nœud profond garde sa clé`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("whitelist").then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes { 1 })))
        dispatcher.root.addChild(literal<Any>("wla").redirect(dispatcher.findNode(listOf("whitelist", "add"))).build())
        val index = index(dispatcher)
        val wla = index.entry(dispatcher, "wla")!!

        assertEquals(key("minecraft:command.wla"), wla.key)
        assertFalse(wla.isVanillaAlias)
        assertTrue(index.aliasKeys.isEmpty())
    }

    @Test
    fun `un nœud rattaché à deux parents garde la clé de son premier chemin et se dit partagé`() {
        val dispatcher = CommandDispatcher<Any>()
        val amount = argument<Any, Int>("amount", IntegerArgumentType.integer()).executes { 1 }.build()
        dispatcher.register(literal<Any>("give").then(amount))
        dispatcher.register(literal<Any>("take").then(amount))
        val index = index(dispatcher)
        val entry = index.entries.getValue(amount)

        assertEquals(key("minecraft:command.give.amount"), entry.key)
        assertTrue(entry.shared)
        assertFalse(index.entry(dispatcher, "give")!!.shared)
        assertNull(index.nodesByKey[key("minecraft:command.take.amount")])
    }

    @Test
    fun `deux nœuds de même structure sous deux parents gardent chacun sa clé`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("give").then(argument<Any, Int>("amount", IntegerArgumentType.integer())))
        dispatcher.register(literal<Any>("take").then(argument<Any, Int>("amount", IntegerArgumentType.integer())))
        val index = index(dispatcher)
        val given = dispatcher.findNode(listOf("give", "amount"))
        val taken = dispatcher.findNode(listOf("take", "amount"))

        // Brigadier tient ces deux nœuds pour égaux : seule une table par identité les distingue.
        assertEquals(given, taken)
        assertEquals(key("minecraft:command.give.amount"), index.entries.getValue(given).key)
        assertEquals(key("minecraft:command.take.amount"), index.entries.getValue(taken).key)
        assertFalse(index.entries.getValue(taken).shared)
    }

    @Test
    fun `un nœud dont le nom ne fait pas une clé se juge avec son sous-arbre sur la clé de son ancêtre`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(
            literal<Any>("home")
                .then(argument<Any, String>("player name", StringArgumentType.word()).then(literal<Any>("set").executes { 1 }))
                .then(literal<Any>("list").executes { 1 }),
        )
        val index = index(dispatcher, mapOf("home" to "homes"))
        val home = key("homes:command.home")
        val unnamed = index.entry(dispatcher, "home", "player name")!!
        val below = index.entry(dispatcher, "home", "player name", "set")!!

        assertEquals(home, unnamed.key)
        assertTrue(unnamed.inheritsKey)
        assertEquals(home, below.key)
        assertTrue(below.inheritsKey)
        assertFalse(index.entry(dispatcher, "home")!!.inheritsKey)
        assertEquals(key("homes:command.home.list"), index.entry(dispatcher, "home", "list")?.key)
        assertEquals(listOf("home", "player name", "set"), index.nodesByKey.getValue(home).map { it.name })
    }

    @Test
    fun `une commande racine dont le nom ne fait pas une clé reste sans fiche`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("bad name").then(literal<Any>("below").executes { 1 }))
        dispatcher.register(literal<Any>("good").executes { 1 })
        dispatcher.root.addChild(literal<Any>("alias").redirect(dispatcher.root.getChild("bad name")).build())
        val index = index(dispatcher)

        assertNull(index.entry(dispatcher, "bad name"))
        assertNull(index.entry(dispatcher, "bad name", "below"))
        assertNull(index.entry(dispatcher, "alias"))
        assertEquals(setOf(key("minecraft:command.good")), index.nodesByKey.keys)
    }

    @Test
    fun `une forme et un alias se jugent sur la clé de leur cible, et leurs enfants gardent la leur`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("whitelist").then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes { 1 })))
        val whitelist = dispatcher.root.getChild("whitelist")
        val add = whitelist.getChild("add")
        val prefixed = CommandForms.nodeOf(PlannedForm("mc:whitelist", whitelist, listOf(whitelist)))
        val wla = CommandForms.nodeOf(PlannedForm("wla", add, listOf(whitelist, add)))
        dispatcher.root.addChild(prefixed)
        dispatcher.root.addChild(wla)
        val targets = IdentityHashMap<CommandNode<Any>, CommandNode<Any>>().apply { put(prefixed, whitelist); put(wla, add) }
        val sharing = IdentityHashMap<CommandNode<Any>, List<CommandNode<Any>>>().apply { put(whitelist, listOf(prefixed)); put(add, listOf(wla)) }
        val hidden = identitySetOf<CommandNode<Any>>().apply { add(whitelist) }

        val index = KeyIndexer.index(dispatcher.root, { Keys.MINECRAFT }, { CommandRequirement.NONE }, Forms(targets, sharing, hidden))

        assertEquals(key("minecraft:command.whitelist"), index.entries.getValue(prefixed).key)
        assertEquals(key("minecraft:command.whitelist.add"), index.entries.getValue(wla).key)
        assertTrue(index.entries.getValue(prefixed).isForm && index.entries.getValue(prefixed).isRoot)
        assertNull(index.entries.getValue(wla).ownKey)
        // Les enfants partagés gardent la clé du chemin de la cible, et ne se disent pas partagés pour autant.
        val sharedChild = index.entries.getValue(add.getChild("targets"))
        assertEquals(key("minecraft:command.whitelist.add.targets"), sharedChild.key)
        assertFalse(sharedChild.shared)
        assertTrue(index.entries.getValue(whitelist).hiddenUnderRequirePrefix)
        assertEquals(listOf<CommandNode<Any>>(wla), index.entries.getValue(add).forms)
        assertEquals(listOf("whitelist", "mc:whitelist"), index.nodesByKey.getValue(key("minecraft:command.whitelist")).map { it.name })
        assertTrue(index.aliasKeys.isEmpty())
    }

    private fun key(text: String): CommandKey = Keys.parse(text) as CommandKey

    private fun index(dispatcher: CommandDispatcher<Any>, owners: Map<String, String> = emptyMap()): KeyIndex<Any> =
        KeyIndexer.index(dispatcher.root, { owners[it.name] ?: Keys.MINECRAFT }, { CommandRequirement.NONE })

    private fun KeyIndex<Any>.entry(dispatcher: CommandDispatcher<Any>, vararg path: String): NodeEntry? = dispatcher.findNode(path.toList())?.let { entries[it] }

    /** Un arbre qui imite vanilla : `teleport` et son alias `tp`, `execute` avec `run` et le fork de `as`, `gamemode`. */
    private fun vanilla(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(
            literal<Any>("teleport")
                .then(argument<Any, String>("location", StringArgumentType.word()).executes { 1 })
                .then(argument<Any, String>("targets", StringArgumentType.word()).then(argument<Any, String>("location", StringArgumentType.word()).executes { 1 })),
        )
        dispatcher.register(literal<Any>("tp").redirect(teleport))
        val execute = dispatcher.register(literal<Any>("execute"))
        dispatcher.register(
            literal<Any>("execute")
                .then(literal<Any>("run").redirect(dispatcher.root))
                .then(literal<Any>("as").then(argument<Any, String>("targets", StringArgumentType.word()).fork(execute) { listOf(it.source) })),
        )
        dispatcher.register(literal<Any>("gamemode").then(literal<Any>("creative").executes { 1 }))
        return dispatcher
    }
}
