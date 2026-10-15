// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.aegisperms.validate.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les constats qui confrontent une configuration à l'arbre des commandes (cahier des charges, 4.5), un test par constat. L'index est celui d'un arbre
 * synthétique bâti sous les réglages livrés ; chaque test lui présente une configuration candidate.
 */
class TreeValidatorTest {

    companion object {
        private const val BOB = "Bob#3c0ac2a3-90a0-3bdb-a4a2-5c6fc0b5b1cd"
    }

    @Test
    fun `des réglages livrés sur un arbre sans histoire ne donnent aucun constat`() {
        assertTrue(issues(index()).isEmpty())
    }

    @Test
    fun `un alias dont la cible n'est pas une suite de littéraux est une erreur`() {
        val issues = issues(index(), aliases = mapOf("gmc" to "minecraft:gamemode creative"))

        assertEquals(listOf(Issue(IssueKind.ALIAS_TARGET_NOT_LITERALS, ConfigurationFile.SETTINGS, listOf("aliases", "gmc"), literals("gmc", "minecraft:gamemode creative", "creative"))), issues)
        assertEquals(Severity.ERROR, issues.single().severity)
    }

    @Test
    fun `un alias dont la cible s'écrit avec le préfixe tapé est une erreur, qui donne l'écriture attendue`() {
        val issues = issues(index(), aliases = mapOf("gm" to "mc:gamemode"))

        assertEquals(listOf(Issue(IssueKind.ALIAS_TARGET_TYPED_PREFIX, ConfigurationFile.SETTINGS, listOf("aliases", "gm"), literals("gm", "mc:gamemode", "minecraft:gamemode"))), issues)
        assertEquals(Severity.ERROR, issues.single().severity)
    }

    @Test
    fun `un alias dont le nom est pris est un avertissement`() {
        val issues = issues(index(), aliases = mapOf("list" to "minecraft:gamemode", "mc:list" to "minecraft:gamemode"))

        assertEquals(
            listOf(
                Issue(IssueKind.ALIAS_NAME_TAKEN, ConfigurationFile.SETTINGS, listOf("aliases", "list"), literals("list")),
                Issue(IssueKind.ALIAS_NAME_TAKEN, ConfigurationFile.SETTINGS, listOf("aliases", "mc:list"), literals("mc:list")),
            ),
            issues,
        )
        assertTrue(issues.all { it.severity == Severity.WARNING })
    }

    @Test
    fun `un alias dont la cible est introuvable est un avertissement`() {
        val issues = issues(index(), aliases = mapOf("v" to "vanish"))

        assertEquals(listOf(Issue(IssueKind.ALIAS_TARGET_MISSING, ConfigurationFile.SETTINGS, listOf("aliases", "v"), literals("v", "vanish"))), issues)
    }

    @Test
    fun `un alias ajouté, ou qui rend une forme nue, ne donne aucun constat, même déjà dans l'arbre`() {
        val aliases = mapOf("gm" to "minecraft:gamemode", "list" to "minecraft:list")
        // L'index s'est bâti avec ces alias : `gm` est une racine de l'arbre, et ne se prend pas son propre nom.
        val index = index(settings = Settings.DELIVERED.copy(aliases = aliases))

        assertEquals(listOf("mode"), index.root.getChild("gm").children.map { it.name })
        assertTrue(issues(index, aliases = aliases).isEmpty())
    }

    @Test
    fun `un préfixe qui cède devant le modid d'un mod chargé est un avertissement`() {
        val index = index(mods = FakeMods(loaded = setOf("minecraft", "homes", "mc")))
        val issues = issues(index, namespaces = NamespaceSettings(prefixes = mapOf("minecraft" to "mc", "homes" to "h")))

        assertEquals(listOf(Issue(IssueKind.PREFIX_YIELDS, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes", "minecraft"), literals("minecraft", "mc"))), issues)
    }

    @Test
    fun `une forme préfixée dont le nom est pris est un avertissement`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("mc:list").executes(HomeCommand()))

        assertEquals(listOf(Issue(IssueKind.PREFIXED_FORM_NAME_TAKEN, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes"), literals("list", "mc:list"))), issues(index(dispatcher)))
    }

    @Test
    fun `un rattachement qui vise une commande de vanilla est un avertissement`() {
        val issues = issues(index(), namespaces = NamespaceSettings(owners = mapOf("gamemode" to "homes")))

        assertEquals(listOf(Issue(IssueKind.OWNER_ASSIGNMENT_VANILLA, ConfigurationFile.SETTINGS, listOf("namespaces", "owners", "gamemode"), literals("gamemode", "homes"))), issues)
    }

    @Test
    fun `un rattachement vers un mod absent est un avertissement`() {
        val issues = issues(index(), namespaces = NamespaceSettings(owners = mapOf("home" to "absent")))

        assertEquals(listOf(Issue(IssueKind.OWNER_ASSIGNMENT_UNKNOWN_MOD, ConfigurationFile.SETTINGS, listOf("namespaces", "owners", "home"), literals("home", "absent"))), issues)
    }

    @Test
    fun `un rattachement d'une commande absente est un avertissement`() {
        val issues = issues(index(), namespaces = NamespaceSettings(owners = mapOf("vanish" to "homes")))

        assertEquals(listOf(Issue(IssueKind.OWNER_ASSIGNMENT_UNKNOWN_COMMAND, ConfigurationFile.SETTINGS, listOf("namespaces", "owners", "vanish"), literals("vanish"))), issues)
    }

    @Test
    fun `une commande sans mod propriétaire est un avertissement, jusqu'à son rattachement`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        val index = index(dispatcher)

        assertEquals(listOf(Issue(IssueKind.COMMAND_WITHOUT_OWNER, ConfigurationFile.SETTINGS, listOf("namespaces", "owners"), literals("orphan"))), issues(index))
        assertTrue(issues(index, namespaces = NamespaceSettings(owners = mapOf("orphan" to "homes"))).isEmpty())
    }

    @Test
    fun `une racine que plusieurs mods se partagent est un avertissement, jusqu'à son rattachement`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("home").then(literal<Any>("warp").executes(WarpsMod.command())))
        val index = index(dispatcher)

        assertEquals(listOf(Issue(IssueKind.SHARED_ROOT, ConfigurationFile.SETTINGS, listOf("namespaces", "owners"), literals("home", "homes", "warps"))), issues(index))
        assertTrue(issues(index, namespaces = NamespaceSettings(owners = mapOf("home" to "homes"))).isEmpty())
    }

    @Test
    fun `une racine de vanilla sur laquelle un mod greffe une branche ne donne aucun constat`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("list").then(literal<Any>("warps").executes(WarpsMod.command())))
        val index = index(dispatcher)

        // D21 : un rattachement ne peut pas viser une racine de vanilla, et sa clé ne dépend pas de l'ordre de chargement ; le catalogue garde le co-propriétaire.
        assertEquals(Owner("minecraft", OwnerSource.DETECTED, listOf("warps")), index.ownerOf("list"))
        assertTrue(issues(index).isEmpty())
    }

    @Test
    fun `une règle qui porte l'ancienne clé d'une commande rattachée est une erreur, qui donne la clé attendue`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("orphan").then(literal<Any>("sub").executes(OrphanCommand())))
        val permissions = Permissions.DELIVERED.copy(
            groups = Permissions.DELIVERED.groups + ("vip" to Group(rules = mapOf("unknown:command.orphan" to Rule.ALLOW, "homes:command.orphan.sub" to Rule.ALLOW, "unknown:command" to Rule.ALLOW, "pas une clé" to Rule.DENY))),
            players = mapOf(BOB to Player(rules = mapOf("unknown:command.orphan.sub" to Rule.DENY, "homes:command.home" to Rule.ALLOW))),
        )
        val index = index(dispatcher)
        val issues = issues(index, namespaces = NamespaceSettings(owners = mapOf("orphan" to "homes")), permissions = permissions)

        assertEquals(
            listOf(
                Issue(IssueKind.REASSIGNED_COMMAND_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", "unknown:command.orphan"), literals("unknown:command.orphan", "homes:command.orphan")),
                Issue(IssueKind.REASSIGNED_COMMAND_KEY, ConfigurationFile.PERMISSIONS, listOf("players", BOB, "rules", "unknown:command.orphan.sub"), literals("unknown:command.orphan.sub", "homes:command.orphan.sub")),
            ),
            issues,
        )
        assertTrue(issues.all { it.severity == Severity.ERROR })
        // Sans rattachement valide, l'ancienne clé reste la bonne.
        assertEquals(listOf(IssueKind.COMMAND_WITHOUT_OWNER), issues(index, permissions = permissions).map { it.kind })
        assertEquals(listOf(IssueKind.OWNER_ASSIGNMENT_UNKNOWN_MOD, IssueKind.COMMAND_WITHOUT_OWNER), issues(index, namespaces = NamespaceSettings(owners = mapOf("orphan" to "absent")), permissions = permissions).map { it.kind })
    }

    @Test
    fun `les constats sortent dans le même ordre quel que soit celui des entrées`() {
        val dispatcher = tree()
        dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        val index = index(dispatcher)
        val aliases = listOf("z" to "vanish", "a" to "vanish", "gmc" to "minecraft:gamemode creative")
        val owners = listOf("vanish" to "homes", "gamemode" to "homes")

        val forward = issues(index, namespaces = NamespaceSettings(owners = owners.toMap()), aliases = aliases.toMap())
        val backward = issues(index, namespaces = NamespaceSettings(owners = owners.reversed().toMap()), aliases = aliases.reversed().toMap())

        assertEquals(forward, backward)
        assertEquals(
            listOf(IssueKind.ALIAS_TARGET_MISSING, IssueKind.ALIAS_TARGET_NOT_LITERALS, IssueKind.ALIAS_TARGET_MISSING, IssueKind.OWNER_ASSIGNMENT_VANILLA, IssueKind.OWNER_ASSIGNMENT_UNKNOWN_COMMAND, IssueKind.COMMAND_WITHOUT_OWNER),
            forward.map { it.kind },
        )
    }

    private fun literals(vararg texts: String): List<IssueArgument> = texts.map(IssueArgument::Literal)

    private fun index(dispatcher: CommandDispatcher<Any> = tree(), settings: Settings = Settings.DELIVERED, mods: ModLocator = FakeMods()): CommandIndex<Any> =
        CommandIndex.build(dispatcher.root, settings, mods, FakeRequirements)

    /** Les constats que l'arbre de [index] tire de la configuration candidate faite de ces réglages et de ces droits. */
    private fun issues(index: CommandIndex<Any>, namespaces: NamespaceSettings = NamespaceSettings(), aliases: Map<String, String> = emptyMap(), permissions: Permissions = Permissions.DELIVERED): List<Issue> =
        TreeValidator.issues(index, Configuration(Settings.DELIVERED.copy(namespaces = namespaces, aliases = aliases), permissions))

    /** `gamemode`, dont le mode est une valeur d'argument, et `list`, de vanilla ; `home`, d'un mod. */
    private fun tree(): CommandDispatcher<Any> {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("gamemode").requires(GAMEMASTERS).then(argument<Any, String>("mode", StringArgumentType.word()).executes(VanillaCommand())))
        dispatcher.register(literal<Any>("list").executes(VanillaCommand()))
        dispatcher.register(literal<Any>("home").executes(HomeCommand()))
        return dispatcher
    }
}
