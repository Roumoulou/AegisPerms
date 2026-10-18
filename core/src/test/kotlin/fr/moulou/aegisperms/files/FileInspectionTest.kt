// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.aegisperms.validate.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les constats que seule la lecture d'un fichier sait faire, sur son arbre (cahier des charges, 4.5). */
class FileInspectionTest {

    private val bob = Fixtures.readableForm("Bob")

    @Test
    fun `un fichier bien écrit ne donne aucun constat`() {
        val custom = """{ "schema-version": 1, "admin-level": "admins", "refusal-message": null, "namespaces": { "prefixes": { "homes": "h" } }, "aliases": { "wla": "whitelist add" } }"""
        for (text in listOf(DeliveredTexts.SETTINGS, custom)) assertEquals(emptyList<Issue>(), settingsIssues(text), text)

        val longRules = """{ "schema-version": 1, "groups": { "vip": { "include-ops": null, "note": null, "rules": { "command": { "note": "Fermé.", "value": "deny", "visible": true } } } } }"""
        val personas = JsonFileFormat.PERMISSIONS.encodeToText(Permissions.serializer(), Fixtures.personas)
        for (text in listOf(DeliveredTexts.PERMISSIONS, personas, longRules)) assertEquals(emptyList<Issue>(), permissionsIssues(text), text)
    }

    @Test
    fun `un champ inconnu est une erreur à chaque niveau, avec son chemin, et tous sortent en une passe`() {
        assertEquals(
            listOf(unknownKey(ConfigurationFile.SETTINGS, "admin_level"), unknownKey(ConfigurationFile.SETTINGS, "namespaces", "prefix")),
            settingsIssues("""{ "schema-version": 1, "admin_level": "admins", "namespaces": { "prefix": {} } }"""),
        )

        val text = """
            {
              "schema-version": 1,
              "groupes": {},
              "groups": { "vip": { "poids": 3, "rules": { "minecraft:command.op": { "value": "allow", "visibl": true } } } },
              "players": { "$bob": { "group": [] } }
            }
        """.trimIndent()
        val expected = listOf(
            unknownKey(ConfigurationFile.PERMISSIONS, "groupes"),
            unknownKey(ConfigurationFile.PERMISSIONS, "groups", "vip", "poids"),
            unknownKey(ConfigurationFile.PERMISSIONS, "groups", "vip", "rules", "minecraft:command.op", "visibl"),
            unknownKey(ConfigurationFile.PERMISSIONS, "players", bob, "group"),
        )
        assertEquals(expected, permissionsIssues(text))
        assertTrue(expected.all { it.severity == Severity.ERROR })
    }

    @Test
    fun `une valeur fermée inconnue est hors de son domaine, qui se dit par ses noms`() {
        val settings = ConfigurationFile.SETTINGS
        assertEquals(
            listOf(
                outOfClosedDomain(settings, listOf("admin-level"), "\"chefs\"", "moderators, gamemasters, admins, owners"),
                outOfClosedDomain(settings, listOf("denied-commands"), "\"Hidden\"", "hidden, visible"),
                outOfClosedDomain(settings, listOf("language"), "\"de\"", "auto, fr, en"),
                outOfClosedDomain(settings, listOf("mode"), "\"regarde\"", "enforce, observe"),
            ),
            settingsIssues("""{ "schema-version": 1, "admin-level": "chefs", "denied-commands": "Hidden", "language": "de", "mode": "regarde" }"""),
        )

        val permissions = ConfigurationFile.PERMISSIONS
        assertEquals(
            listOf(
                outOfClosedDomain(permissions, listOf("groups", "vip", "include-ops"), "\"ops\"", "moderators, gamemasters, admins, owners"),
                outOfClosedDomain(permissions, listOf("groups", "vip", "rules", "minecraft:command.op"), "\"peut-être\"", "allow, deny"),
                outOfClosedDomain(permissions, listOf("groups", "vip", "rules", "minecraft:command.stop", "value"), "\"Allow\"", "allow, deny"),
            ),
            groupIssues("""{ "include-ops": "ops", "rules": { "minecraft:command.op": "peut-être", "minecraft:command.stop": { "value": "Allow" } } }"""),
        )
    }

    @Test
    fun `une valeur d'un autre type que le sien est hors de son domaine`() {
        val settings = ConfigurationFile.SETTINGS
        assertEquals(
            listOf(
                outOfDomain(settings, listOf("refusal-message"), "12", ValueDomain.TEXT),
                outOfDomain(settings, listOf("namespaces", "enabled"), "1", ValueDomain.BOOLEAN),
                outOfDomain(settings, listOf("namespaces", "prefixes"), "[]", ValueDomain.OBJECT),
                outOfDomain(settings, listOf("aliases", "wla"), "3", ValueDomain.TEXT),
                outOfDomain(settings, listOf("watch-files"), "\"true\"", ValueDomain.BOOLEAN),
            ),
            settingsIssues("""{ "schema-version": 1, "refusal-message": 12, "namespaces": { "enabled": 1, "prefixes": [] }, "aliases": { "wla": 3 }, "watch-files": "true" }"""),
        )
        assertEquals(listOf(outOfDomain(settings, listOf("namespaces"), "\"oui\"", ValueDomain.OBJECT)), settingsIssues("""{ "schema-version": 1, "namespaces": "oui" }"""))

        val vip = listOf("groups", "vip")
        val permissions = ConfigurationFile.PERMISSIONS
        val cases = listOf(
            """{ "weight": "10" }""" to outOfDomain(permissions, vip + "weight", "\"10\"", ValueDomain.INTEGER),
            """{ "weight": 1.5 }""" to outOfDomain(permissions, vip + "weight", "1.5", ValueDomain.INTEGER),
            """{ "weight": null }""" to outOfDomain(permissions, vip + "weight", "null", ValueDomain.INTEGER),
            """{ "weight": 99999999999 }""" to outOfDomain(permissions, vip + "weight", "99999999999", ValueDomain.INTEGER),
            """{ "parents": "membre" }""" to outOfDomain(permissions, vip + "parents", "\"membre\"", ValueDomain.LIST),
            """{ "parents": ["membre", 1] }""" to outOfDomain(permissions, vip + "parents" + "1", "1", ValueDomain.TEXT),
            """{ "rules": [] }""" to outOfDomain(permissions, vip + "rules", "[]", ValueDomain.OBJECT),
            """{ "rules": { "command": 12 } }""" to outOfDomain(permissions, vip + "rules" + "command", "12", ValueDomain.RULE),
            """{ "rules": { "command": { "visible": true } } }""" to outOfDomain(permissions, vip + "rules" + "command", """{"visible":true}""", ValueDomain.RULE),
            """{ "rules": { "command": { "value": "deny", "visible": "oui" } } }""" to outOfDomain(permissions, vip + "rules" + "command" + "visible", "\"oui\"", ValueDomain.BOOLEAN),
            """"vip"""" to outOfDomain(permissions, vip, "\"vip\"", ValueDomain.OBJECT),
        )
        for ((group, expected) in cases) assertEquals(listOf(expected), groupIssues(group), group)

        assertEquals(
            listOf(outOfDomain(permissions, listOf("players", bob), "[]", ValueDomain.OBJECT)),
            permissionsIssues("""{ "schema-version": 1, "players": { "$bob": [] } }"""),
        )
        assertEquals(
            listOf(outOfDomain(permissions, listOf("players", bob, "groups"), "{}", ValueDomain.LIST)),
            permissionsIssues("""{ "schema-version": 1, "players": { "$bob": { "groups": {} } } }"""),
        )
        assertEquals(listOf(outOfDomain(permissions, emptyList(), "[]", ValueDomain.OBJECT)), permissionsIssues("[]"))
    }

    @Test
    fun `une valeur trop longue est coupée dans le constat`() {
        val long = "x".repeat(200)
        val written = (settingsIssues("""{ "schema-version": 1, "namespaces": "$long" }""").single().arguments.first() as IssueArgument.Literal).text
        assertEquals("\"" + "x".repeat(59) + "...", written)
    }

    @Test
    fun `un numéro de structure absent, mal écrit ou plus récent arrête l'inspection`() {
        val settings = ConfigurationFile.SETTINGS
        val versionPath = listOf("schema-version")
        assertEquals(listOf(Issue(IssueKind.MISSING_SCHEMA_VERSION, settings, versionPath)), settingsIssues("""{ "admin_level": "admins" }"""))
        assertEquals(listOf(outOfDomain(settings, versionPath, "\"1\"", ValueDomain.SCHEMA_VERSION)), settingsIssues("""{ "schema-version": "1", "admin_level": "admins" }"""))
        assertEquals(listOf(outOfDomain(settings, versionPath, "0", ValueDomain.SCHEMA_VERSION)), settingsIssues("""{ "schema-version": 0, "admin_level": "admins" }"""))
        assertEquals(listOf(Issue(IssueKind.NEWER_SCHEMA_VERSION, settings, versionPath, literals("2", "1"))), settingsIssues("""{ "schema-version": 2, "admin_level": "admins" }"""))
    }

    @Test
    fun `un fichier d'un numéro plus ancien s'inspecte une fois migré`() {
        val migrations = SchemaMigrations(current = 2, steps = mapOf(1 to { root -> JsonObject(root.entries.associate { (key, value) -> (if (key == "ranks") "groups" else key) to value }) }))
        assertEquals(emptyList<Issue>(), permissionsIssues("""{ "schema-version": 1, "ranks": { "default": {} } }""", migrations))
        assertEquals(
            listOf(unknownKey(ConfigurationFile.PERMISSIONS, "groups", "default", "poids")),
            permissionsIssues("""{ "schema-version": 1, "ranks": { "default": { "poids": 1 } } }""", migrations),
        )
        assertEquals(listOf(unknownKey(ConfigurationFile.PERMISSIONS, "ranks")), permissionsIssues("""{ "schema-version": 2, "ranks": {} }""", migrations))
    }

    @Test
    fun `chaque domaine a sa clé de traduction`() {
        assertEquals("aegisperms.issue.value_out_of_domain.schema_version", ValueDomain.SCHEMA_VERSION.translationKey)
        assertEquals(ValueDomain.entries.size, ValueDomain.entries.map { it.translationKey }.toSet().size)
    }

    private fun settingsIssues(text: String): List<Issue> = FileInspection(ConfigurationFile.SETTINGS, SchemaMigrations(1)).inspect(Json.parseToJsonElement(text), Settings.serializer().descriptor)

    private fun permissionsIssues(text: String, migrations: SchemaMigrations = SchemaMigrations(1)): List<Issue> =
        FileInspection(ConfigurationFile.PERMISSIONS, migrations).inspect(Json.parseToJsonElement(text), Permissions.serializer().descriptor)

    /** Les constats d'un fichier des droits réduit à un groupe `vip`, dont [group] est le contenu. */
    private fun groupIssues(group: String): List<Issue> = permissionsIssues("""{ "schema-version": 1, "groups": { "vip": $group } }""")

    private fun unknownKey(file: ConfigurationFile, vararg path: String): Issue = Issue(IssueKind.UNKNOWN_KEY, file, path.toList(), literals(path.last()))

    private fun outOfDomain(file: ConfigurationFile, path: List<String>, written: String, domain: ValueDomain): Issue =
        Issue(IssueKind.VALUE_OUT_OF_DOMAIN, file, path, listOf(IssueArgument.Literal(written), IssueArgument.Translatable(domain.translationKey)))

    private fun outOfClosedDomain(file: ConfigurationFile, path: List<String>, written: String, names: String): Issue = Issue(IssueKind.VALUE_OUT_OF_DOMAIN, file, path, literals(written, names))
}
