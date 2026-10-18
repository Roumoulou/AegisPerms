// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import fr.moulou.aegisperms.files.CommandEntry
import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.files.NodeKind
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.DemandingDescendant
import fr.moulou.aegisperms.validate.IssueKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le catalogue calculé depuis l'index (cahier des charges, 4.6), sur un arbre synthétique : ses entrées, et ce que la validation lui demande. */
class IndexCatalogTest {

    companion object {
        private val SETTINGS = Settings.DELIVERED.copy(aliases = mapOf("wla" to "minecraft:whitelist add", "dp" to "minecraft:datapack", "pack" to "datapack"))
    }

    @Test
    fun `chaque clé de commande a son entrée, avec la nature, l'exécution et l'exigence de son nœud`() {
        val commands = catalog().commands()

        assertEquals(CommandEntry(NodeKind.LITERAL, requirement = CommandRequirement.GAMEMASTERS, owner = "minecraft", prefixedForm = "mc:teleport"), commands["minecraft:command.teleport"])
        assertEquals(CommandEntry(NodeKind.ARGUMENT, executable = true), commands["minecraft:command.teleport.targets"])
        assertEquals(CommandEntry(NodeKind.LITERAL, executable = true), commands["minecraft:command.datapack.list"])
        assertEquals(
            setOf(
                "minecraft:command.teleport", "minecraft:command.teleport.targets", "minecraft:command.tp", "minecraft:command.datapack", "minecraft:command.datapack.list",
                "minecraft:command.datapack.create", "minecraft:command.datapack.create.name", "minecraft:command.function", "minecraft:command.function.name",
                "minecraft:command.whitelist", "minecraft:command.whitelist.add", "minecraft:command.whitelist.add.targets", "minecraft:command.give",
                "minecraft:command.give.amount", "minecraft:command.take", "homes:command.home", "homes:command.home.warp", "unknown:command.orphan",
            ),
            commands.keys,
        )
    }

    @Test
    fun `la clé d'un alias de vanilla a son entrée, qui nomme la clé de sa cible`() {
        val commands = catalog().commands()

        assertEquals(
            CommandEntry(NodeKind.LITERAL, requirement = CommandRequirement.GAMEMASTERS, aliasOf = "minecraft:command.teleport", owner = "minecraft"),
            commands["minecraft:command.tp"],
        )
        assertNull(commands.getValue("minecraft:command.teleport").aliasOf)
    }

    @Test
    fun `un nœud partagé et un nœud plus exigeant que son ancêtre se marquent`() {
        val commands = catalog().commands()

        assertTrue(commands.getValue("minecraft:command.give.amount").shared)
        assertFalse(commands.getValue("minecraft:command.give").shared)
        assertEquals(CommandEntry(NodeKind.LITERAL, requirement = CommandRequirement.ADMINS, moreDemanding = true), commands["minecraft:command.datapack.create"])
        assertFalse(commands.getValue("minecraft:command.datapack").moreDemanding)
        assertFalse(commands.getValue("minecraft:command.datapack.list").moreDemanding)
        // `add` n'exige rien lui-même : il n'est pas plus exigeant que `whitelist`.
        assertFalse(commands.getValue("minecraft:command.whitelist.add").moreDemanding)
    }

    @Test
    fun `une clé sensible porte le nom de sa raison`() {
        val commands = catalog().commands()

        assertEquals("functions", commands.getValue("minecraft:command.function").sensitive)
        assertEquals("lectern_book", commands.getValue("minecraft:command.give").sensitive)
        assertNull(commands.getValue("minecraft:command.teleport").sensitive)
    }

    @Test
    fun `une commande racine porte son mod propriétaire, ses co-propriétaires, sa forme préfixée et ses alias`() {
        val commands = catalog().commands()

        assertEquals(CommandEntry(NodeKind.LITERAL, executable = true, requirement = CommandRequirement.MOD_PREDICATE, owner = "homes", coOwners = listOf("warps"), prefixedForm = "homes:home"), commands["homes:command.home"])
        assertEquals(CommandEntry(NodeKind.LITERAL, executable = true), commands["homes:command.home.warp"])
        // Une commande sans mod propriétaire n'a ni propriétaire ni forme préfixée à écrire.
        assertEquals(CommandEntry(NodeKind.LITERAL, executable = true), commands["unknown:command.orphan"])
        assertEquals(listOf("dp", "pack"), commands.getValue("minecraft:command.datapack").aliases)
        assertEquals("mc:datapack", commands.getValue("minecraft:command.datapack").prefixedForm)
        // Un alias vers un littéral profond se range sous la clé de ce littéral.
        assertEquals(listOf("wla"), commands.getValue("minecraft:command.whitelist.add").aliases)
        assertTrue(commands.getValue("minecraft:command.whitelist").aliases.isEmpty())
    }

    @Test
    fun `le catalogue connaît les clés de l'index, leurs espaces de noms et la cible d'un alias de vanilla`() {
        val catalog = catalog()

        assertTrue(catalog.hasCommandKey(key("minecraft:command.teleport.targets")))
        assertFalse(catalog.hasCommandKey(key("minecraft:command.fly")))
        assertFalse(catalog.hasCommandKey(key("minecraft:command.tp")))
        assertEquals(key("minecraft:command.teleport"), catalog.aliasTarget(key("minecraft:command.tp")))
        assertNull(catalog.aliasTarget(key("minecraft:command.teleport")))
        for (namespace in listOf("minecraft", "homes", "unknown")) assertTrue(catalog.hasNamespace(namespace), namespace)
        assertFalse(catalog.hasNamespace("warps"))
    }

    @Test
    fun `la clé la plus proche d'une clé absente est sa voisine de même parent, à quelques fautes de frappe près`() {
        val catalog = catalog()

        assertEquals(key("minecraft:command.teleport"), catalog.nearestCommandKey(key("minecraft:command.telport")))
        assertEquals(key("minecraft:command.teleport"), catalog.nearestCommandKey(key("minecraft:command.telport.targets")))
        assertEquals(key("minecraft:command.teleport.targets"), catalog.nearestCommandKey(key("minecraft:command.teleport.target")))
        assertEquals(key("minecraft:command.datapack.create"), catalog.nearestCommandKey(key("minecraft:command.datapack.crate.name")))
        assertNull(catalog.nearestCommandKey(key("minecraft:command.fly")))
        assertNull(catalog.nearestCommandKey(key("minecraft:command.teleport.rotation")))
        assertNull(catalog.nearestCommandKey(key("warps:command.teleport")))
    }

    @Test
    fun `les clés apparues au même endroit sont celles du catalogue précédent`() {
        val appeared = listOf("minecraft:command.teleport.destination", "minecraft:command.teleport.facing", "minecraft:command.give", "pas une clé", "homes:home/limit")
        val catalog = IndexCatalog(index(), appeared = appeared)

        assertEquals(listOf(key("minecraft:command.teleport.destination"), key("minecraft:command.teleport.facing")), catalog.appearedNear(key("minecraft:command.teleport.location")))
        assertEquals(listOf(key("minecraft:command.give")), catalog.appearedNear(key("minecraft:command.gift")))
        assertTrue(catalog.appearedNear(key("homes:command.house")).isEmpty())
        assertTrue(catalog().appearedNear(key("minecraft:command.teleport.location")).isEmpty())
    }

    @Test
    fun `les descendants plus exigeants d'une clé viennent avec leur niveau`() {
        val catalog = catalog()

        assertEquals(listOf(DemandingDescendant(key("minecraft:command.datapack.create"), 3)), catalog.demandingDescendants(key("minecraft:command.datapack")))
        assertTrue(catalog.demandingDescendants(key("minecraft:command.datapack.create")).isEmpty())
        assertTrue(catalog.demandingDescendants(key("minecraft:command.teleport")).isEmpty())
    }

    @Test
    fun `une clé d'API est connue quand vanilla la demande ou qu'un mod l'a demandée`() {
        val limit = Keys.parse("homes:home/limit") as ApiKey
        val catalog = IndexCatalog(index(), apiKeys = setOf(limit))

        assertTrue(catalog.hasApiKey(Keys.ENTITY_SELECTORS))
        assertTrue(catalog.hasApiKey(limit))
        assertFalse(catalog.hasApiKey(Keys.parse("homes:home/teleport") as ApiKey))
        // Les atomes du tchat sont déclarés et jamais demandés au serveur : vanilla ne les rend pas connus (6.2).
        assertFalse(catalog().hasApiKey(Keys.parse("minecraft:chat/send_messages") as ApiKey))
    }

    @Test
    fun `le catalogue rend à la validation les constats de l'arbre`() {
        val index = index()
        val configuration = Configuration(SETTINGS, Permissions.DELIVERED)
        val issues = IndexCatalog(index).treeIssues(configuration)

        assertEquals(TreeValidator.issues(index, configuration), issues)
        assertEquals(listOf(IssueKind.SHARED_ROOT, IssueKind.COMMAND_WITHOUT_OWNER), issues.map { it.kind })
    }

    private fun key(text: String): CommandKey = Keys.parse(text) as CommandKey

    private fun catalog(): IndexCatalog<Any> = IndexCatalog(index())

    private fun index(): CommandIndex<Any> = CommandIndex.build(tree().root, SETTINGS, FakeMods(), FakeRequirements)

    /**
     * Un arbre qui imite vanilla et ses voisins : `teleport` et son alias `tp` ; `datapack`, dont la branche `create` exige plus que lui ; `function`, clé
     * sensible ; `whitelist add`, littéral profond ; `give` et `take`, qui partagent un argument ; `home`, où un second mod a greffé `warp` ; `orphan`, sans
     * mod propriétaire.
     */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(literal<Any>("teleport").requires(GAMEMASTERS).then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand())))
        dispatcher.register(literal<Any>("tp").requires(GAMEMASTERS).redirect(teleport))
        dispatcher.register(
            literal<Any>("datapack").requires(GAMEMASTERS)
                .then(literal<Any>("list").executes(VanillaCommand()))
                .then(literal<Any>("create").requires(ADMINS).then(argument<Any, String>("name", StringArgumentType.word()).executes(VanillaCommand()))),
        )
        dispatcher.register(literal<Any>("function").requires(GAMEMASTERS).then(argument<Any, String>("name", StringArgumentType.word()).executes(VanillaCommand())))
        dispatcher.register(literal<Any>("whitelist").requires(ADMINS).then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand()))))
        val amount = argument<Any, Int>("amount", IntegerArgumentType.integer()).executes(VanillaCommand()).build()
        dispatcher.register(literal<Any>("give").requires(GAMEMASTERS).then(amount))
        dispatcher.register(literal<Any>("take").requires(GAMEMASTERS).then(amount))
        dispatcher.register(literal<Any>("home").requires(MOD_OPTION).executes(HomeCommand()))
        dispatcher.register(literal<Any>("home").then(literal<Any>("warp").executes(WarpsMod.command())))
        dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        return dispatcher
    }
}
