// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les formes préfixées et les alias (cahier des charges, chapitre 3), ajoutés à des arbres synthétiques. La source d'une commande est son niveau d'op : 0
 * pour un non-op, 2 pour ce que vanilla réserve aux `gamemasters`, 3 pour les `admins`.
 */
class CommandFormsTest {

    @Test
    fun `le préfixe de vanilla est mc, et celui d'un autre mod son modid`() {
        val dispatcher = tree()
        apply(dispatcher)

        assertNotNull(dispatcher.root.getChild("mc:teleport"))
        assertNotNull(dispatcher.root.getChild("homes:home"))
        assertNull(dispatcher.root.getChild("minecraft:teleport"))
        assertEquals("mc", CommandForms.prefixOf("minecraft", Settings.DELIVERED.namespaces.prefixes, FakeMods()))
        assertEquals("homes", CommandForms.prefixOf("homes", Settings.DELIVERED.namespaces.prefixes, FakeMods()))
    }

    @Test
    fun `la table des préfixes remplace le préfixe d'un mod`() {
        val dispatcher = tree()
        apply(dispatcher, namespaces = NamespaceSettings(prefixes = mapOf("minecraft" to "mc", "homes" to "h")))

        assertNotNull(dispatcher.root.getChild("h:home"))
        assertNull(dispatcher.root.getChild("homes:home"))
        assertNotNull(dispatcher.root.getChild("mc:teleport"))
    }

    @Test
    fun `un préfixe qui est le modid d'un mod chargé cède la place au modid`() {
        val dispatcher = tree()
        val mods = FakeMods(loaded = setOf("minecraft", "homes", "mc"))
        apply(dispatcher, mods = mods)

        assertNotNull(dispatcher.root.getChild("minecraft:teleport"))
        assertNull(dispatcher.root.getChild("mc:teleport"))
        assertEquals("minecraft", CommandForms.prefixOf("minecraft", Settings.DELIVERED.namespaces.prefixes, mods))
    }

    @Test
    fun `une forme préfixée porte la commande et l'exigence de sa cible et partage ses enfants`() {
        val dispatcher = tree()
        apply(dispatcher)
        val teleport = dispatcher.root.getChild("teleport")
        val prefixed = dispatcher.root.getChild("mc:teleport")

        assertSame(teleport.getChild("targets"), prefixed.getChild("targets"))
        assertSame(GAMEMASTERS, prefixed.requirement)
        assertNull(prefixed.redirect)
        assertEquals(7, dispatcher.execute("mc:teleport Joe", 2))
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("mc:teleport Joe", 0) }
        // La forme d'une commande qui s'exécute seule s'exécute seule.
        assertSame(dispatcher.root.getChild("list").command, dispatcher.root.getChild("mc:list").command)
        assertEquals(3, dispatcher.execute("mc:list", 0))
    }

    @Test
    fun `un alias de vanilla ne reçoit pas de forme, et se masque avec sa cible`() {
        val dispatcher = tree()
        val plan = apply(dispatcher)
        val tp = dispatcher.root.getChild("tp")

        assertNull(dispatcher.root.getChild("mc:tp"))
        assertFalse("tp" in plan.prefixedForms)
        assertFalse("tp" in plan.takenFormNames)
        assertTrue(tp in plan.hiddenBareForms, "l'alias se masque avec /teleport, qui a sa forme")
        assertTrue(dispatcher.root.getChild("teleport") in plan.hiddenBareForms)
    }

    @Test
    fun `la table des alias rend un alias de vanilla, nu ou préfixé, sur l'exigence de sa cible seule`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("tp" to "minecraft:teleport", "mc:tp" to "minecraft:teleport"))
        val teleport = dispatcher.root.getChild("teleport")

        assertEquals(mapOf("mc:tp" to AliasOutcome.Added, "tp" to AliasOutcome.RestoresBareForm), plan.aliasOutcomes)
        assertFalse(dispatcher.root.getChild("tp") in plan.hiddenBareForms, "/tp est rendu aux joueurs")
        val prefixed = dispatcher.root.getChild("mc:tp")
        assertSame(teleport, plan.aliases.getValue("mc:tp").end)
        assertSame(teleport.getChild("targets"), prefixed.getChild("targets"))
        // `tp` exige le niveau 3 dans cet arbre : l'alias de la table, lui, ne porte que l'exigence de /teleport, le niveau 2.
        assertTrue(prefixed.canUse(2))
        assertEquals(7, dispatcher.execute("mc:tp Joe", 2))
    }

    @Test
    fun `la forme d'une racine qui redirige vers la racine de l'arbre garde cette redirection`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("again").requires(GAMEMASTERS).executes(VanillaCommand(11)).redirect(dispatcher.root))
        apply(dispatcher)
        val prefixed = dispatcher.root.getChild("mc:again")

        assertSame(dispatcher.root, prefixed.redirect)
        assertTrue(prefixed.children.isEmpty())
        assertSame(GAMEMASTERS, prefixed.requirement)
        assertEquals(3, dispatcher.execute("mc:again mc:list", 2))
    }

    @Test
    fun `la forme d'une redirection à modificateur la reproduit`() {
        val dispatcher = tree()
        val execute = dispatcher.root.getChild("execute")
        val elevated = dispatcher.register(literal<Any>("elevated").fork(execute) { listOf(4) })
        val plan = apply(dispatcher)
        val prefixed = dispatcher.root.getChild("mc:elevated")

        assertSame(elevated, plan.prefixedForms.getValue("elevated").end)
        assertSame(execute, prefixed.redirect)
        assertSame(elevated.redirectModifier, prefixed.redirectModifier)
        assertTrue(prefixed.isFork)
    }

    @Test
    fun `une commande sans mod propriétaire, ou dont le nom porte déjà un deux-points, n'a pas de forme`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("homes:old").executes(HomeCommand()))
        val plan = apply(dispatcher)

        assertEquals(setOf("teleport", "list", "gamemode", "whitelist", "execute", "home", "aegis"), plan.prefixedForms.keys, "tp, alias, n'en reçoit pas")
        assertNull(dispatcher.root.getChild("unknown:orphan"))
        assertNull(dispatcher.root.getChild("homes:homes:old"))
        assertFalse(dispatcher.root.getChild("orphan") in plan.hiddenBareForms)
        assertFalse(dispatcher.root.getChild("homes:old") in plan.hiddenBareForms)
    }

    @Test
    fun `sans les formes préfixées, seuls les alias s'ajoutent`() {
        val dispatcher = tree()
        val before = dispatcher.root.children.size
        val plan = apply(dispatcher, namespaces = NamespaceSettings(enabled = false), aliases = mapOf("gm" to "gamemode"))

        assertTrue(plan.prefixedForms.isEmpty())
        assertTrue(plan.hiddenBareForms.isEmpty())
        assertEquals(before + 1, dispatcher.root.children.size)
        assertEquals(5, dispatcher.execute("gm creative", 2))
    }

    @Test
    fun `une forme dont le nom est déjà pris à la racine est refusée`() {
        val dispatcher = tree()
        val squatter = dispatcher.register(literal<Any>("mc:list").executes(OrphanCommand()))
        val command = squatter.command
        val plan = apply(dispatcher)

        assertEquals(mapOf("list" to "mc:list"), plan.takenFormNames)
        assertNull(plan.prefixedForms["list"])
        // Rien n'a fusionné : le nœud en place garde sa commande, et la commande sans forme garde sa forme nue.
        assertSame(squatter, dispatcher.root.getChild("mc:list"))
        assertSame(command, squatter.command)
        assertFalse(dispatcher.root.getChild("list") in plan.hiddenBareForms)
    }

    @Test
    fun `la forme nue d'une commande qui a une forme préfixée se retire sous la forme exigée`() {
        val dispatcher = tree()
        val plan = apply(dispatcher)

        for (name in listOf("teleport", "tp", "list", "gamemode", "whitelist", "execute", "home")) assertTrue(dispatcher.root.getChild(name) in plan.hiddenBareForms, name)
        assertEquals(7, plan.hiddenBareForms.size)
        assertFalse(dispatcher.root.getChild("mc:teleport") in plan.hiddenBareForms)
    }

    @Test
    fun `aegis garde sa forme nue et reçoit sa forme préfixée`() {
        val dispatcher = tree()
        val plan = apply(dispatcher)

        assertNotNull(dispatcher.root.getChild("aegisperms:aegis"))
        assertSame(dispatcher.root.getChild("aegis").getChild("reload"), dispatcher.root.getChild("aegisperms:aegis").getChild("reload"))
        assertFalse(dispatcher.root.getChild("aegis") in plan.hiddenBareForms)
        // Une commande d'un autre mod qui s'appellerait `aegis` n'a pas ce privilège.
        assertFalse(CommandForms.isAdministration("aegis", Owner("homes", OwnerSource.DETECTED, emptyList())))
    }

    @Test
    fun `un alias du nom d'une commande lui rend sa forme nue`() {
        val dispatcher = tree()
        val before = dispatcher.root.children.size
        val plan = apply(dispatcher, namespaces = NamespaceSettings(enabled = false), aliases = mapOf("list" to "minecraft:list", "tp" to "minecraft:teleport"))
        val withForms = apply(tree(), aliases = mapOf("list" to "minecraft:list", "tp" to "minecraft:teleport"))

        assertEquals(mapOf("list" to AliasOutcome.RestoresBareForm, "tp" to AliasOutcome.RestoresBareForm), plan.aliasOutcomes)
        assertTrue(plan.aliases.isEmpty())
        assertEquals(before, dispatcher.root.children.size)
        // `tp` mène à `teleport` : l'alias rend `tp`, pas `teleport`.
        assertEquals(setOf("teleport", "gamemode", "whitelist", "execute", "home"), withForms.hiddenBareForms.map { it.name }.toSet())
    }

    @Test
    fun `un alias porte la commande de sa cible et partage ses enfants`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("gm" to "minecraft:gamemode", "ls" to "list", "h" to "homes:home"))
        val gm = dispatcher.root.getChild("gm")

        assertEquals(AliasOutcome.Added, plan.aliasOutcomes["gm"])
        assertSame(dispatcher.root.getChild("gamemode").getChild("mode"), gm.getChild("mode"))
        assertSame(GAMEMASTERS, gm.requirement)
        assertEquals(5, dispatcher.execute("gm creative", 2))
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("gm creative", 0) }
        assertEquals(3, dispatcher.execute("ls", 0))
        assertEquals(13, dispatcher.execute("h", 0))
        // Un alias n'a pas de forme préfixée, et reste visible sous la forme exigée.
        assertNull(dispatcher.root.getChild("mc:gm"))
        assertFalse(gm in plan.hiddenBareForms)
    }

    @Test
    fun `un alias vers un littéral profond garde l'exigence de tout son chemin`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("wla" to "minecraft:whitelist add"))
        val wla = dispatcher.root.getChild("wla")
        val add = dispatcher.root.getChild("whitelist").getChild("add")

        assertSame(add, plan.aliases.getValue("wla").end)
        assertSame(add.getChild("targets"), wla.getChild("targets"))
        // `add` n'exige rien lui-même : c'est `whitelist` qui porte le niveau 3.
        assertSame(ADMINS, wla.requirement)
        assertFalse(wla.canUse(2))
        assertEquals(9, dispatcher.execute("wla Joe", 3))
    }

    @Test
    fun `la cible d'un alias qui redirige est ramenée à son nœud final`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("t" to "minecraft:tp"))
        val teleport = dispatcher.root.getChild("teleport")
        val t = dispatcher.root.getChild("t")

        assertSame(teleport, plan.aliases.getValue("t").end)
        assertNull(t.redirect)
        assertSame(teleport.getChild("targets"), t.getChild("targets"))
        assertEquals(7, dispatcher.execute("t Joe", 3))
    }

    @Test
    fun `un alias dont le nom est pris est refusé avant l'enregistrement`() {
        val dispatcher = tree()
        val list = dispatcher.root.getChild("list")
        val command = list.command
        val plan = apply(dispatcher, aliases = mapOf("list" to "minecraft:gamemode", "mc:teleport" to "minecraft:gamemode"))

        assertEquals(mapOf("list" to AliasOutcome.NameTaken, "mc:teleport" to AliasOutcome.NameTaken), plan.aliasOutcomes)
        assertTrue(plan.aliases.isEmpty())
        // Rien n'a fusionné : `list` garde sa commande et n'a reçu aucun enfant.
        assertSame(command, list.command)
        assertTrue(list.children.isEmpty())
        assertTrue(list in plan.hiddenBareForms)
    }

    @Test
    fun `un alias dont la cible est introuvable est ignoré`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("v" to "vanish", "w" to "warps:warp", "x" to "homes:list", "y" to ""))

        assertEquals(setOf(AliasOutcome.TargetMissing), plan.aliasOutcomes.values.toSet())
        assertEquals(4, plan.aliasOutcomes.size)
        for (name in listOf("v", "w", "x", "y")) assertNull(dispatcher.root.getChild(name))
    }

    @Test
    fun `un alias dont la cible n'est pas une suite de littéraux est ignoré`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("gmc" to "minecraft:gamemode creative", "wlj" to "whitelist add targets"))

        assertEquals(AliasOutcome.TargetNotLiterals("creative"), plan.aliasOutcomes["gmc"])
        assertEquals(AliasOutcome.TargetNotLiterals("targets"), plan.aliasOutcomes["wlj"])
        assertNull(dispatcher.root.getChild("gmc"))
        assertNull(dispatcher.root.getChild("wlj"))
    }

    @Test
    fun `un alias dont la cible s'écrit avec le préfixe tapé est ignoré, et dit l'écriture attendue`() {
        val dispatcher = tree()
        val plan = apply(dispatcher, aliases = mapOf("gm" to "mc:gamemode", "wla" to "mc:whitelist add"))

        assertEquals(AliasOutcome.TypedPrefix("minecraft:gamemode"), plan.aliasOutcomes["gm"])
        assertEquals(AliasOutcome.TypedPrefix("minecraft:whitelist add"), plan.aliasOutcomes["wla"])
        assertNull(dispatcher.root.getChild("gm"))
    }

    @Test
    fun `décider n'ajoute rien, et les alias se décident dans l'ordre de leurs noms`() {
        val dispatcher = tree()
        val before = dispatcher.root.children.map { it.name }
        val originals = dispatcher.root.children.toList()
        val settings = Settings.DELIVERED.copy(aliases = mapOf("b" to "list", "a" to "list", "é" to "list", "B" to "list"))

        val plan = CommandForms.plan(dispatcher.root, originals, owners(dispatcher), settings, FakeMods())

        assertEquals(before, dispatcher.root.children.map { it.name })
        assertEquals(listOf("B", "a", "b", "é"), plan.aliases.keys.toList())
    }

    private fun owners(dispatcher: CommandDispatcher<Any>, mods: ModLocator = FakeMods()): Map<String, Owner> {
        val resolver = OwnerResolver(mods, emptyMap())
        return dispatcher.root.children.associate { it.name to resolver.ownerOf(it) }
    }

    /** Décide puis ajoute les formes et les alias de ces réglages, et rend ce qui a été décidé. */
    private fun apply(dispatcher: CommandDispatcher<Any>, namespaces: NamespaceSettings = NamespaceSettings(), aliases: Map<String, String> = emptyMap(), mods: ModLocator = FakeMods()): FormPlan<Any> {
        val plan = CommandForms.plan(dispatcher.root, dispatcher.root.children.toList(), owners(dispatcher, mods), Settings.DELIVERED.copy(namespaces = namespaces, aliases = aliases), mods)
        CommandForms.apply(dispatcher.root, plan)
        return plan
    }

    /**
     * Un arbre qui imite vanilla et deux mods. `teleport` exige le niveau 2 et son alias `tp` le niveau 3, pour que la conjonction se voie ; `gamemode` prend
     * la valeur de son mode en argument, comme en vanilla ; `whitelist` porte l'exigence que `add` n'a pas ; `execute` a `run` vers la racine et le fork de
     * `as` ; `home` est d'un mod, `aegis` du mod lui-même, et `orphan` n'a pas de mod propriétaire.
     */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(literal<Any>("teleport").requires(GAMEMASTERS).then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand(7))))
        dispatcher.register(literal<Any>("tp").requires(ADMINS).redirect(teleport))
        dispatcher.register(literal<Any>("list").executes(VanillaCommand(3)))
        dispatcher.register(literal<Any>("gamemode").requires(GAMEMASTERS).then(argument<Any, String>("mode", StringArgumentType.word()).executes(VanillaCommand(5))))
        dispatcher.register(literal<Any>("whitelist").requires(ADMINS).then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand(9)))))
        val execute = dispatcher.register(literal<Any>("execute").requires(GAMEMASTERS).then(literal<Any>("if").executes(VanillaCommand())))
        dispatcher.register(literal<Any>("execute").then(literal<Any>("run").redirect(dispatcher.root)).then(literal<Any>("as").then(argument<Any, String>("targets", StringArgumentType.word()).fork(execute) { listOf(it.source) })))
        dispatcher.register(literal<Any>("home").executes(HomeCommand(13)))
        dispatcher.register(literal<Any>("aegis").requires(ADMINS).then(literal<Any>("reload").executes(AegisCommand())))
        dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        return dispatcher
    }
}
