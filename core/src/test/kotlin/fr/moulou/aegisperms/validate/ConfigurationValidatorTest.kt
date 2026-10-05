// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.Fixtures.configuration
import fr.moulou.aegisperms.Fixtures.issues
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le déroulement de la validation d'une configuration entière (cahier des charges, 4.5). */
class ConfigurationValidatorTest {

    @Test
    fun `la configuration livrée ne donne aucun constat`() {
        assertTrue(issues(Permissions.DELIVERED).isEmpty())
    }

    @Test
    fun `les personnages sont valides, avec le seul avertissement de Jogger`() {
        val report = ConfigurationValidator.validate(configuration(personas))

        assertTrue(report.isValid)
        assertEquals(listOf(IssueKind.ALLOW_UNDER_REFUSAL), report.issues.map { it.kind })
        assertEquals(listOf("players", readableForm("Jogger"), "rules", "minecraft:command.teleport.targets.location.rotation"), report.issues.single().path)
    }

    @Test
    fun `une erreur arrête les constats qui demandent de résoudre`() {
        // Sans default, l'avertissement de Jogger ne se calcule pas : la configuration ne s'appliquera pas.
        assertEquals(listOf(IssueKind.MISSING_DEFAULT), issues(personas.copy(groups = personas.groups - "default")).map { it.kind })
    }

    @Test
    fun `un rapport est valide tant qu'il ne porte pas d'erreur`() {
        val flawed = permissions(Permissions.DELIVERED.groups + ("Mal Nommé" to Group()) + ("vip" to Group(parents = listOf("admin", "admin"))))
        val report = ConfigurationValidator.validate(configuration(flawed))

        assertFalse(report.isValid)
        assertEquals(listOf(IssueKind.MALFORMED_GROUP_NAME), report.errors.map { it.kind })
        assertEquals(listOf(IssueKind.DUPLICATE_PARENT), report.warnings.map { it.kind })
    }

    @Test
    fun `les constats sortent dans le même ordre, quel que soit celui des entrées`() {
        val written = personas.copy(
            groups = personas.groups +
                ("zeta" to Group(parents = listOf("membre", "membre"), rules = mapOf("minecraft:command.op" to Rule.ALLOW, "minecraft:command.execute" to Rule.ALLOW))) +
                ("beta" to Group(rules = mapOf("minecraft:command.list" to Rule(RuleValue.ALLOW, visible = false), "minecraft:command.kill" to Rule.DENY, "minecraft:command.kill.targets" to Rule.ALLOW))),
        )
        val reversed = Permissions(
            written.schemaVersion,
            written.groups.entries.reversed().associate { it.key to it.value.copy(rules = it.value.rules.entries.reversed().associate { rule -> rule.key to rule.value }) },
            written.players.entries.reversed().associate { it.key to it.value },
        )

        assertTrue(issues(written).size >= 8)
        assertEquals(issues(written), issues(reversed))
    }

    @Test
    fun `chaque nature de constat a son niveau et sa clé de traduction`() {
        assertEquals("aegisperms.issue.malformed_key", IssueKind.MALFORMED_KEY.translationKey)
        assertEquals(IssueKind.entries.size, IssueKind.entries.map { it.translationKey }.toSet().size)
        assertEquals(mapOf(Severity.ERROR to 19, Severity.WARNING to 13, Severity.REINFORCED to 2), IssueKind.entries.groupingBy { it.severity }.eachCount())
    }

    @Test
    fun `un constat dit son fichier par son nom`() {
        assertEquals("settings.json", ConfigurationFile.SETTINGS.fileName)
        assertEquals("permissions.json", ConfigurationFile.PERMISSIONS.fileName)
    }
}
