// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** L'index d'un arbre de commandes (cahier des charges, 7.4 et 7.5), sur des arbres synthétiques, un loader simulé et une lecture d'exigence simulée. */
class CommandIndexTest {

    companion object {
        /** Les réglages livrés, sans les formes préfixées : l'arbre reste tel que ses mods l'ont enregistré. */
        private val WITHOUT_FORMS = Settings.DELIVERED.copy(namespaces = NamespaceSettings(enabled = false))
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
        val index = index(dispatcher, WITHOUT_FORMS.copy(namespaces = NamespaceSettings(enabled = false, owners = mapOf("home" to "warps"))))

        assertEquals(listOf(dispatcher.root.getChild("gamemode"), dispatcher.root.getChild("gm")), index.nodesOf(key("minecraft:command.gamemode")))
        assertTrue(index.nodesOf(key("minecraft:command.absent")).isEmpty())
        assertEquals(mapOf(key("minecraft:command.gm") to key("minecraft:command.gamemode")), index.aliasKeys)
        assertEquals(Owner("minecraft", OwnerSource.DETECTED, emptyList()), index.ownerOf("gamemode"))
        assertEquals(Owner("warps", OwnerSource.ASSIGNED, listOf("homes")), index.ownerOf("home"))
        assertNull(index.ownerOf("absent"))
        assertEquals(setOf(key("minecraft:command.gamemode"), key("minecraft:command.gamemode.creative"), key("warps:command.home")), index.commandKeys)
    }

    @Test
    fun `les formes et les alias des réglages s'ajoutent à l'arbre avant les clés`() {
        val dispatcher = tree()
        val index = index(dispatcher, Settings.DELIVERED.copy(aliases = mapOf("gmc" to "minecraft:gamemode creative")))
        val gamemode = dispatcher.root.getChild("gamemode")
        val prefixed = dispatcher.root.getChild("mc:gamemode")

        assertEquals(listOf("gamemode", "gm", "home"), index.originals.map { it.name })
        assertEquals(listOf("gamemode", "gm", "home", "mc:gamemode", "mc:gm", "homes:home", "gmc"), dispatcher.root.children.map { it.name })
        assertEquals(key("minecraft:command.gamemode"), index.entryOf(prefixed)?.key)
        assertTrue(index.entryOf(prefixed)!!.isForm)
        assertEquals(key("minecraft:command.gamemode.creative"), index.entryOf(dispatcher.root.getChild("gmc"))?.key)
        assertTrue(index.entryOf(gamemode)!!.hiddenUnderRequirePrefix)
        assertEquals(listOf(prefixed, dispatcher.root.getChild("mc:gm")), index.entryOf(gamemode)!!.forms)
        assertEquals(setOf("gamemode", "gm", "home"), index.owners.keys)
        assertEquals(AliasOutcome.Added, index.plan.aliasOutcomes["gmc"])
    }

    @Test
    fun `un enfant ajouté après l'index prend la clé de son parent prolongée de son nom, sous-arbre compris`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val gamemode = dispatcher.root.getChild("gamemode")
        val late = literal<Any>("late").requires(GAMEMASTERS).then(argument<Any, String>("name", StringArgumentType.word()).executes { 1 }).build()
        gamemode.addChild(late)

        val entries = CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, FakeRequirements) { false }

        assertEquals(listOf(late, late.getChild("name")), entries.map { it.first })
        assertEquals(listOf(key("minecraft:command.gamemode.late"), key("minecraft:command.gamemode.late.name")), entries.map { it.second.key })
        assertEquals(listOf(CommandRequirement.GAMEMASTERS, CommandRequirement.NONE), entries.map { it.second.requirement })
        assertTrue(entries.none { it.second.isRoot || it.second.inheritsKey || it.second.shared || it.second.isForm })
    }

    @Test
    fun `un enfant tardif dont le nom ne fait pas une clé se juge sur la clé de son parent`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val home = dispatcher.root.getChild("home")
        val late = argument<Any, String>("player name", StringArgumentType.word()).then(literal<Any>("set").executes { 1 }).build()
        home.addChild(late)

        val entries = CommandIndex.lateEntries(index.entryOf(home)!!, late, FakeRequirements) { false }

        assertEquals(listOf(key("homes:command.home"), key("homes:command.home")), entries.map { it.second.key })
        assertTrue(entries.all { it.second.inheritsKey })
    }

    @Test
    fun `un enfant tardif d'un alias de vanilla prolonge la clé propre de l'alias`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val late = literal<Any>("late").build()

        val entries = CommandIndex.lateEntries(index.entryOf(dispatcher.root.getChild("gm"))!!, late, FakeRequirements) { false }

        assertEquals(listOf(key("minecraft:command.gm.late")), entries.map { it.second.key })
    }

    @Test
    fun `un nœud qui porte déjà une marque la garde, lui et ce qui descend de lui`() {
        val dispatcher = tree()
        val index = index(dispatcher)
        val gamemode = dispatcher.root.getChild("gamemode")
        val known = literal<Any>("known").then(literal<Any>("below")).build()
        val late = literal<Any>("late").then(known).then(literal<Any>("fresh")).build()

        assertTrue(CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, FakeRequirements) { true }.isEmpty())

        val entries = CommandIndex.lateEntries(index.entryOf(gamemode)!!, late, FakeRequirements) { it === known }
        assertEquals(listOf("late", "fresh"), entries.map { it.first.name })
        assertFalse(entries.any { it.first === known.getChild("below") })
    }

    @Test
    fun `une racine tardive reçoit son mod, sa clé et sa forme préfixée`() {
        val dispatcher = tree()
        val index = index(dispatcher, Settings.DELIVERED)
        val warp = dispatcher.register(literal<Any>("warp").requires(MOD_OPTION).executes(WarpsMod.command()).then(literal<Any>("list").executes(WarpsMod.command())))

        val late = index.lateRoot(warp, index::markOf)!!
        val form = late.form!!
        val (ofWarp, ofList, ofForm) = late.entries.map { it.second }

        assertEquals(Owner("warps", OwnerSource.DETECTED, emptyList()), late.owner)
        assertEquals(listOf(warp, warp.getChild("list"), form), late.entries.map { it.first })
        assertEquals(key("warps:command.warp"), ofWarp.key)
        assertTrue(ofWarp.isRoot)
        assertTrue(ofWarp.hiddenUnderRequirePrefix)
        assertEquals(listOf(form), ofWarp.forms)
        assertEquals(key("warps:command.warp.list"), ofList.key)
        assertEquals("warps:warp", form.name)
        assertEquals(key("warps:command.warp"), ofForm.key)
        assertTrue(ofForm.isForm)
        assertSame(warp.getChild("list"), form.getChild("list"))
        assertSame(warp.command, form.command)
        // La forme s'ajoute à la racine une fois les fiches posées : ce n'est pas l'index qui le fait.
        assertNull(dispatcher.root.getChild("warps:warp"))
    }

    @Test
    fun `une racine tardive sans mod propriétaire, ou dont le nom de forme est pris, n'a pas de forme`() {
        val dispatcher = tree()
        val index = index(dispatcher, Settings.DELIVERED)
        val orphan = dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        dispatcher.register(literal<Any>("warps:warp").executes(OrphanCommand()))
        val warp = dispatcher.register(literal<Any>("warp").executes(WarpsMod.command()))
        val unnamed = dispatcher.register(literal<Any>("bad name").executes(WarpsMod.command()))

        val lateOrphan = index.lateRoot(orphan, index::markOf)!!
        val lateWarp = index.lateRoot(warp, index::markOf)!!

        assertNull(lateOrphan.form)
        assertEquals(key("unknown:command.orphan"), lateOrphan.entries.single().second.key)
        assertFalse(lateOrphan.entries.single().second.hiddenUnderRequirePrefix)
        assertNull(lateWarp.form)
        assertFalse(lateWarp.entries.single().second.hiddenUnderRequirePrefix)
        assertNotNull(dispatcher.root.getChild("warps:warp").command)
        assertNull(index.lateRoot(unnamed, index::markOf))
    }

    private fun key(text: String): CommandKey = Keys.parse(text) as CommandKey

    private fun index(dispatcher: CommandDispatcher<Any>, settings: Settings = WITHOUT_FORMS): CommandIndex<Any> = CommandIndex.build(dispatcher.root, settings, FakeMods(), FakeRequirements)

    /** `gamemode` de vanilla, son alias `gm`, et `home`, la commande d'un mod. */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val gamemode = dispatcher.register(literal<Any>("gamemode").requires(GAMEMASTERS).then(literal<Any>("creative").executes(VanillaCommand())))
        dispatcher.register(literal<Any>("gm").requires(GAMEMASTERS).redirect(gamemode))
        dispatcher.register(literal<Any>("home").requires(MOD_OPTION).executes(HomeCommand()))
        return dispatcher
    }
}
