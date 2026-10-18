// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.Texts
import fr.moulou.aegisperms.tree.ADMINS
import fr.moulou.aegisperms.tree.AegisCommand
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.FakeMods
import fr.moulou.aegisperms.tree.FakeRequirements
import fr.moulou.aegisperms.tree.GAMEMASTERS
import fr.moulou.aegisperms.tree.HomeCommand
import fr.moulou.aegisperms.tree.IndexCatalog
import fr.moulou.aegisperms.tree.OrphanCommand
import fr.moulou.aegisperms.tree.VanillaCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Le manuel des commandes (cahier des charges, 5.10), bâti et rendu sur un arbre synthétique : les sections, les tables, les arbres et leurs marques. Les mots
 * viennent des deux fichiers de langue lus dans `src\main\resources`, que l'étage 0 de `common` n'a pas sur son classpath : une clé absente y lève.
 */
class ManualTest {

    companion object {
        private val LANG_DIRECTORY = Paths.get("src", "main", "resources", "assets", "aegisperms", "lang")

        private val tables: Map<Lang, Map<String, String>> = Lang.entries.associateWith { Texts.parse(Files.readString(LANG_DIRECTORY.resolve(it.fileName + ".json"))) }

        private val texts = ManualRenderer.TextSource { key, lang, arguments -> Texts.format(tables.getValue(lang).getValue(key), arguments) }
    }

    private val descriptions = Descriptions(
        jar = mapOf(
            Lang.FR to mapOf("minecraft:command.teleport" to "Téléporte.", "minecraft:command.whitelist.add" to "Ajoute à la liste blanche."),
            Lang.EN to mapOf("minecraft:command.teleport" to "Teleports."),
        ),
        custom = mapOf("homes:command.home" to mapOf("fr" to "Rentre chez soi.", "en" to "Goes home.")),
    )

    @Test
    fun `le manuel range les mods, minecraft d'abord, chaque commande avec sa table et son arbre, sans section pour un alias`() {
        val text = render(Lang.FR)

        assertTrue(text.startsWith("# Les commandes du serveur\n\n> Écrit par AegisPerms 1.0.0, Minecraft 26.3, le 2026-10-18.\n> Mode enforce, forme exigée : oui ; 4 mods, "), text.lines().take(4).toString())
        val sections = listOf("## minecraft (Minecraft 26.3)", "## aegisperms", "## homes (Homes 1.2.0)", "## unknown").map { text.indexOf(it) }
        assertTrue(sections.all { it >= 0 } && sections == sections.sorted(), "minecraft d'abord, puis les mods par ordre alphabétique : $sections")
        assertTrue("| `minecraft` | Minecraft | 26.3 | 6 | " in text, "le sommaire compte les six commandes de vanilla, tp exclu")
        assertTrue("### /teleport\n\nTéléporte.\n\n- `teleport` : `minecraft:command.teleport`, niveau 2 (gamemasters)\n  - `<targets>` : `minecraft:command.teleport.targets`, s'exécute\n" in text, text)
        assertFalse("### /tp" in text, "un alias n'a pas de section")
        assertTrue("| `/teleport` | `/mc:teleport` | `/tp` | `minecraft:command.teleport` | niveau 2 (gamemasters) |  | Téléporte. |" in text, "la table donne la forme, l'alias, la clé, l'exigence et la description")
        assertTrue("### /home\n\nRentre chez soi.\n\n- `home` : `homes:command.home`, s'exécute\n" in text, "la description de l'administrateur l'emporte")
        assertTrue("- `orphan` : `unknown:command.orphan`, s'exécute\n" in text, "une commande sans mod propriétaire est sous unknown")
        assertTrue("### /aegis\n\n- `aegis` : `aegisperms:command.aegis`, niveau 3 (admins)\n  - `reload` : `aegisperms:command.aegis.reload`, s'exécute\n" in text, text)
    }

    @Test
    fun `un nœud dit ce qui compte, l'exigence qui change, l'alias de la table et la redirection, et un nœud partagé se cite sans se dérouler`() {
        val text = render(Lang.FR)

        assertTrue("- `datapack` : `minecraft:command.datapack`, niveau 2 (gamemasters)\n  - `create` : `minecraft:command.datapack.create`, s'exécute, niveau 3 (admins), plus exigeant que son ancêtre\n" in text, text)
        assertTrue("- `whitelist` : `minecraft:command.whitelist`, niveau 3 (admins)\n  - `add` : `minecraft:command.whitelist.add`, alias : `/wla`. Ajoute à la liste blanche.\n    - `<targets>` : `minecraft:command.whitelist.add.targets`, s'exécute\n" in text, text)
        assertTrue("  - `as` : `minecraft:command.execute.as`, sensible : impersonation\n    - `<targets>` : `minecraft:command.execute.as.targets`, redirige vers `minecraft:command.execute`\n  - `if` : `minecraft:command.execute.if`, s'exécute\n  - `run` : `minecraft:command.execute.run`, redirige vers toute commande\n" in text, text)
        assertTrue("- `list` : `minecraft:command.list`, s'exécute\n  - `page` : `minecraft:command.list.page`\n    - `<number>` : `minecraft:command.list.page.number`, s'exécute\n" in text, "le premier chemin déroule le nœud partagé")
        assertTrue("- `banlist` : `minecraft:command.banlist`, s'exécute, niveau 3 (admins)\n  - `page` : `minecraft:command.list.page`, partagé, décrit sous sa clé\n\n" in text, "le second chemin le cite seul")
        assertTrue(text.endsWith("\n") && "\r" !in text)
    }

    @Test
    fun `le manuel se rend en anglais avec ses propres mots et descriptions`() {
        val text = render(Lang.EN)

        assertTrue(text.startsWith("# Server commands\n\n> Written by AegisPerms 1.0.0, Minecraft 26.3, on 2026-10-18.\n"), text.lines().first())
        assertTrue("| Command | Prefixed form | Aliases | Key | Requirement | Sensitive | Description |" in text)
        assertTrue("### /teleport\n\nTeleports.\n\n- `teleport` : `minecraft:command.teleport`, level 2 (gamemasters)\n" in text, text)
        assertTrue("### /home\n\nGoes home.\n" in text)
        assertTrue("### /whitelist\n\n- `whitelist`" in text, "une commande sans description en anglais n'en montre pas")
    }

    @Test
    fun `une description de l'administrateur l'emporte sur le jar, et une vide le laisse parler`() {
        val custom = Descriptions(
            jar = mapOf(Lang.FR to mapOf("minecraft:command.teleport" to "Du jar."), Lang.EN to emptyMap()),
            custom = mapOf("minecraft:command.teleport" to mapOf("fr" to "", "en" to "From the admin.")),
        )
        val teleport = fr.moulou.aegisperms.model.Keys.parse("minecraft:command.teleport") as fr.moulou.aegisperms.model.CommandKey

        assertEquals("Du jar.", custom.of(teleport, Lang.FR))
        assertEquals("From the admin.", custom.of(teleport, Lang.EN))
        assertEquals(mapOf("homes:command.home" to mapOf("fr" to "Chez soi.")), Descriptions.parseCustom("﻿{\"homes:command.home\": {\"fr\": \"Chez soi.\"}}"))
    }

    private fun render(lang: Lang): String {
        val dispatcher = tree()
        val settings = Settings.DELIVERED.copy(aliases = mapOf("wla" to "minecraft:whitelist add", "tp" to "minecraft:teleport"))
        val index = CommandIndex.build(dispatcher.root, settings, FakeMods(), FakeRequirements)
        val manual = ManualBuilder.build(index, IndexCatalog(index).commands(), descriptions, lang, "1.0.0", "26.3", "2026-10-18", Mode.ENFORCE, requirePrefix = true)
        return ManualRenderer.render(manual, texts)
    }

    /** Vanilla, un mod et une commande sans mod ; `tp` est un alias, `page` est partagé entre `list` et `banlist`, `execute` redirige. */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(literal<Any>("teleport").requires(GAMEMASTERS).then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand(7))))
        dispatcher.register(literal<Any>("tp").requires(ADMINS).redirect(teleport))
        dispatcher.register(literal<Any>("whitelist").requires(ADMINS).then(literal<Any>("add").then(argument<Any, String>("targets", StringArgumentType.word()).executes(VanillaCommand(9)))))
        dispatcher.register(literal<Any>("datapack").requires(GAMEMASTERS).then(literal<Any>("create").requires(ADMINS).executes(VanillaCommand())))
        val execute = dispatcher.register(literal<Any>("execute").requires(GAMEMASTERS).then(literal<Any>("if").executes(VanillaCommand())))
        dispatcher.register(literal<Any>("execute").then(literal<Any>("run").redirect(dispatcher.root)).then(literal<Any>("as").then(argument<Any, String>("targets", StringArgumentType.word()).fork(execute) { listOf(it.source) })))
        val page = literal<Any>("page").then(argument<Any, Int>("number", IntegerArgumentType.integer()).executes(VanillaCommand())).build()
        dispatcher.register(literal<Any>("list").executes(VanillaCommand(3)).then(page))
        dispatcher.register(literal<Any>("banlist").requires(ADMINS).executes(VanillaCommand()).then(page))
        dispatcher.register(literal<Any>("home").executes(HomeCommand(13)))
        dispatcher.register(literal<Any>("aegis").requires(ADMINS).then(literal<Any>("reload").executes(AegisCommand())))
        dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        return dispatcher
    }
}
