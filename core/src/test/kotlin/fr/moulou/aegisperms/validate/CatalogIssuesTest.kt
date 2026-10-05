// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.issues
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les constats que la validation tire du catalogue (cahier des charges, 4.5 et 4.6), éprouvés contre un catalogue de test. */
class CatalogIssuesTest {

    /** Quelques clés de vanilla, un alias, une commande plus exigeante sous `/datapack`, et un atome. */
    private val catalog = object : Catalog {
        private val known = setOf(
            "minecraft:command.help", "minecraft:command.me", "minecraft:command.msg", "minecraft:command.teleport", "minecraft:command.teleport.targets",
            "minecraft:command.datapack", "minecraft:command.datapack.create", "minecraft:command.home2",
        ).map(::commandKey).toSet()

        override fun hasCommandKey(key: CommandKey): Boolean = key in known

        override fun hasNamespace(namespace: String): Boolean = known.any { it.namespace == namespace }

        override fun nearestCommandKey(key: CommandKey): CommandKey? = if (key.text == "minecraft:command.telport") commandKey("minecraft:command.teleport") else null

        override fun appearedNear(key: CommandKey): List<CommandKey> = if (key.text == "minecraft:command.home") listOf(commandKey("minecraft:command.home2")) else emptyList()

        override fun aliasTarget(key: CommandKey): CommandKey? = if (key.text == "minecraft:command.tp") commandKey("minecraft:command.teleport") else null

        override fun demandingDescendants(key: CommandKey): List<DemandingDescendant> =
            if (key.text == "minecraft:command.datapack") listOf(DemandingDescendant(commandKey("minecraft:command.datapack.create"), 4)) else emptyList()

        override fun hasApiKey(key: ApiKey): Boolean = key == Keys.ENTITY_SELECTORS
    }

    @Test
    fun `les droits livrés ne donnent aucun constat face au catalogue`() {
        // La clé command, que default refuse et qu'admin ouvre, ne se cherche pas au catalogue.
        assertTrue(issues(Permissions.DELIVERED, catalog = catalog).isEmpty())
    }

    @Test
    fun `une clé de commande absente du catalogue est un avertissement`() {
        assertEquals(listOf(issue(IssueKind.UNKNOWN_COMMAND_KEY, "minecraft:command.fly", "minecraft:command.fly")), issuesOf("minecraft:command.fly" to Rule.ALLOW))
    }

    @Test
    fun `une clé de commande absente se dit avec la plus proche, quand le catalogue en propose une`() {
        assertEquals(
            listOf(issue(IssueKind.UNKNOWN_COMMAND_KEY_NEAR, "minecraft:command.telport", "minecraft:command.telport", "minecraft:command.teleport")),
            issuesOf("minecraft:command.telport" to Rule.ALLOW),
        )
    }

    @Test
    fun `la clé générale d'un mod sans commande est un avertissement`() {
        assertEquals(listOf(issue(IssueKind.UNKNOWN_COMMAND_KEY, "homes:command", "homes:command")), issuesOf("homes:command" to Rule.ALLOW))
        assertTrue(issuesOf("minecraft:command" to Rule.DENY).isEmpty())
    }

    @Test
    fun `une clé d'API ni connue de vanilla ni jamais demandée est un avertissement`() {
        assertEquals(listOf(issue(IssueKind.UNKNOWN_API_KEY, "homes:home/limit", "homes:home/limit")), issuesOf("homes:home/limit" to Rule.ALLOW))
        assertTrue(issuesOf("minecraft:commands/entity_selectors" to Rule.ALLOW).isEmpty())
    }

    @Test
    fun `la clé d'un alias de vanilla est un avertissement, avec celle de sa cible`() {
        // Le cas de la lignée : une règle sur tp ne couvre rien, c'est teleport qu'il faut écrire.
        assertEquals(
            listOf(issue(IssueKind.ALIAS_KEY, "minecraft:command.tp", "minecraft:command.tp", "minecraft:command.teleport")),
            issuesOf("minecraft:command.tp" to Rule.ALLOW),
        )
    }

    @Test
    fun `un allow qui couvre un descendant plus exigeant est un avertissement`() {
        assertEquals(
            listOf(issue(IssueKind.ALLOW_DEMANDING, "minecraft:command.datapack", "minecraft:command.datapack", "minecraft:command.datapack.create", "4")),
            issuesOf("minecraft:command.datapack" to Rule.ALLOW),
        )
        assertTrue(issuesOf("minecraft:command.datapack" to Rule.DENY).isEmpty())
    }

    @Test
    fun `un deny dont la clé n'existe plus est un avertissement renforcé`() {
        val issues = issuesOf("minecraft:command.fly" to Rule.DENY)

        assertEquals(listOf(issue(IssueKind.DENY_KEY_GONE, "minecraft:command.fly", "minecraft:command.fly")), issues)
        assertEquals(Severity.REINFORCED, issues.single().severity)
    }

    @Test
    fun `un deny dont la clé n'existe plus se dit avec les clés apparues au même endroit`() {
        val issues = issuesOf("minecraft:command.home" to Rule.DENY)

        assertEquals(listOf(issue(IssueKind.DENY_KEY_GONE_NEAR, "minecraft:command.home", "minecraft:command.home", "minecraft:command.home2")), issues)
        assertEquals(Severity.REINFORCED, issues.single().severity)
    }

    @Test
    fun `sans catalogue, ces constats se taisent`() {
        val rules = mapOf(
            "minecraft:command.fly" to Rule.ALLOW, "minecraft:command.tp" to Rule.ALLOW, "minecraft:command.datapack" to Rule.ALLOW, "homes:home/limit" to Rule.ALLOW,
            "minecraft:command.home" to Rule.DENY,
        )

        assertTrue(issues(permissions(Permissions.DELIVERED.groups + ("vip" to Group(rules = rules)))).isEmpty())
    }

    /** Les constats des droits livrés, un groupe `vip` en plus avec cette règle, face au catalogue. */
    private fun issuesOf(rule: Pair<String, Rule>): List<Issue> = issues(permissions(Permissions.DELIVERED.groups + ("vip" to Group(rules = mapOf(rule)))), catalog = catalog)

    /** Un constat sur la règle de `vip` écrite sous [key]. */
    private fun issue(kind: IssueKind, key: String, vararg arguments: String): Issue = Issue(kind, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", key), literals(*arguments))
}
