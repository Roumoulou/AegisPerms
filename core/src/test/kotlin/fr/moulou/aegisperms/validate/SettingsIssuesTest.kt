// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.Fixtures.issues
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les constats sur le fichier des réglages que les réglages suffisent à faire (cahier des charges, 4.5). */
class SettingsIssuesTest {

    @Test
    fun `les réglages livrés ne donnent aucun constat`() {
        assertTrue(issuesOf(Settings.DELIVERED).isEmpty())
    }

    @Test
    fun `un nom d'alias vide, ou qui porte un blanc ou une barre oblique, est une erreur`() {
        val settings = Settings.DELIVERED.copy(aliases = mapOf("g m" to "minecraft:gamemode", "a/b" to "minecraft:list", "" to "minecraft:me", "gm" to "minecraft:gamemode"))

        assertEquals(
            listOf(
                Issue(IssueKind.MALFORMED_ALIAS_NAME, ConfigurationFile.SETTINGS, listOf("aliases", ""), literals("")),
                Issue(IssueKind.MALFORMED_ALIAS_NAME, ConfigurationFile.SETTINGS, listOf("aliases", "a/b"), literals("a/b")),
                Issue(IssueKind.MALFORMED_ALIAS_NAME, ConfigurationFile.SETTINGS, listOf("aliases", "g m"), literals("g m")),
            ),
            issuesOf(settings),
        )
    }

    @Test
    fun `un préfixe mal formé est une erreur`() {
        val settings = withPrefixes("homes" to "", "minecraft" to "MC")

        assertEquals(
            listOf(
                Issue(IssueKind.MALFORMED_PREFIX, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes", "homes"), literals("homes", "")),
                Issue(IssueKind.MALFORMED_PREFIX, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes", "minecraft"), literals("minecraft", "MC")),
            ),
            issuesOf(settings),
        )
    }

    @Test
    fun `un préfixe donné à deux mods est une erreur`() {
        assertEquals(
            listOf(Issue(IssueKind.DUPLICATE_PREFIX, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes", "minecraft"), literals("mc", "homes", "minecraft"))),
            issuesOf(withPrefixes("minecraft" to "mc", "homes" to "mc")),
        )
    }

    @Test
    fun `require-prefix sans formes préfixées est un avertissement`() {
        val ignored = Settings.DELIVERED.copy(namespaces = NamespaceSettings(enabled = false, requirePrefix = true))
        val coherent = Settings.DELIVERED.copy(namespaces = NamespaceSettings(enabled = false, requirePrefix = false))

        assertEquals(listOf(Issue(IssueKind.REQUIRE_PREFIX_WITHOUT_FORMS, ConfigurationFile.SETTINGS, listOf("namespaces", "require-prefix"))), issuesOf(ignored))
        assertTrue(issuesOf(coherent).isEmpty())
    }

    /** Les constats de ces réglages, posés sur les droits livrés. */
    private fun issuesOf(settings: Settings): List<Issue> = issues(Permissions.DELIVERED, settings)

    private fun withPrefixes(vararg prefixes: Pair<String, String>): Settings = Settings.DELIVERED.copy(namespaces = NamespaceSettings(prefixes = prefixes.toMap()))
}
