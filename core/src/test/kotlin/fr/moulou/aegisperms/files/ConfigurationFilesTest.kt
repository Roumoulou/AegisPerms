// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.validate.Catalog
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.DemandingDescendant
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.aegisperms.validate.Severity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** La configuration lue et validée entière, par les stores ou sans rien toucher (cahier des charges, 4.5 et 4.8). */
class ConfigurationFilesTest {

    @TempDir
    lateinit var dir: Path

    /** Le dossier de configuration, sous un chemin à espaces et à accents que rien n'a encore créé. */
    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val settingsPath: Path get() = config.resolve("settings.json")

    private val permissionsPath: Path get() = config.resolve("permissions.json")

    private val bob = Fixtures.readableForm("Bob")

    /** Une date ancienne, qu'une réécriture ferait sauter. */
    private val stamp = FileTime.fromMillis(1_700_000_000_000)

    /** Un catalogue qui connaît toutes les commandes, sauf `/fly`. */
    private val catalog = object : Catalog {
        override fun hasCommandKey(key: CommandKey): Boolean = key.text != "minecraft:command.fly"
        override fun hasNamespace(namespace: String): Boolean = true
        override fun nearestCommandKey(key: CommandKey): CommandKey? = null
        override fun appearedNear(key: CommandKey): List<CommandKey> = emptyList()
        override fun aliasTarget(key: CommandKey): CommandKey? = null
        override fun demandingDescendants(key: CommandKey): List<DemandingDescendant> = emptyList()
        override fun hasApiKey(key: ApiKey): Boolean = true
    }

    @Test
    fun `le premier rechargement d'un dossier vide crée les deux fichiers et rend la configuration livrée, sans constat`() {
        ConfigurationFiles(config).use { files ->
            val first = files.reload().valid()
            assertEquals(Configuration(Settings.DELIVERED, Permissions.DELIVERED), first.configuration)
            assertEquals(setOf(ConfigurationFile.SETTINGS, ConfigurationFile.PERMISSIONS), first.created)
            assertEquals(emptyList<Issue>(), first.report.issues)

            val second = files.reload().valid()
            assertEquals(first.configuration, second.configuration)
            assertEquals(emptySet<ConfigurationFile>(), second.created)
        }
        assertArrayEquals(DeliveredTexts.SETTINGS.toByteArray(Charsets.UTF_8), Files.readAllBytes(settingsPath))
        assertArrayEquals(DeliveredTexts.PERMISSIONS.toByteArray(Charsets.UTF_8), Files.readAllBytes(permissionsPath))
    }

    @Test
    fun `un constat de validation porte la ligne de son chemin, dans le fichier qu'il vise`() {
        put(
            permissionsPath,
            """{""",
            """  "schema-version": 1,""",
            """  "groups": {""",
            """    "default": {},""",
            """    "vip": {""",
            """      "parents": [""",
            """        "membre"""",
            """      ]""",
            """    }""",
            """  },""",
            """  "players": {""",
            """    "$bob": {""",
            """      "groups": ["staff"]""",
            """    }""",
            """  }""",
            """}""",
        )
        put(
            settingsPath,
            """{""",
            """  "schema-version": 1,""",
            """  "namespaces": {""",
            """    "prefixes": {""",
            """      "minecraft": "M C"""",
            """    }""",
            """  },""",
            """  "aliases": {""",
            """    "w l a": "whitelist add"""",
            """  }""",
            """}""",
        )
        val expected = listOf(
            Issue(IssueKind.UNKNOWN_PARENT, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "parents", "0"), literals("vip", "membre"), line = 7),
            Issue(IssueKind.UNKNOWN_GROUP, ConfigurationFile.PERMISSIONS, listOf("players", bob, "groups", "0"), literals("staff"), line = 13),
            Issue(IssueKind.MALFORMED_ALIAS_NAME, ConfigurationFile.SETTINGS, listOf("aliases", "w l a"), literals("w l a"), line = 9),
            Issue(IssueKind.MALFORMED_PREFIX, ConfigurationFile.SETTINGS, listOf("namespaces", "prefixes", "minecraft"), literals("minecraft", "M C"), line = 5),
        )
        ConfigurationFiles(config).use { files ->
            assertEquals(expected, files.reload().refused())
            assertEquals(expected, files.check().refused())
        }
    }

    @Test
    fun `default absent prend la ligne de groups, et un réglage laissé à son défaut celle de son ancêtre écrit`() {
        put(permissionsPath, """{""", """  "schema-version": 1,""", """  "groups": {""", """    "vip": {}""", """  }""", """}""")
        put(settingsPath, """{""", """  "schema-version": 1,""", """  "namespaces": {""", """    "enabled": false""", """  }""", """}""")
        ConfigurationFiles(config).use { files ->
            val expected = listOf(
                Issue(IssueKind.MISSING_DEFAULT, ConfigurationFile.PERMISSIONS, listOf("groups"), line = 3),
                // `require-prefix` n'est pas écrit : il vaut `true` à la livraison, et le constat montre `namespaces`.
                Issue(IssueKind.REQUIRE_PREFIX_WITHOUT_FORMS, ConfigurationFile.SETTINGS, listOf("namespaces", "require-prefix"), line = 3),
            )
            assertEquals(expected, files.reload().refused())
        }
    }

    @Test
    fun `un avertissement laisse la configuration valide, et porte sa ligne`() {
        put(
            permissionsPath,
            """{""",
            """  "schema-version": 1,""",
            """  "groups": {""",
            """    "default": {},""",
            """    "vip": {""",
            """      "rules": {""",
            """        "minecraft:command.op": "allow"""",
            """      }""",
            """    }""",
            """  }""",
            """}""",
        )
        ConfigurationFiles(config).use { files ->
            val read = files.reload().valid()
            assertTrue(read.report.isValid)
            val warning = read.report.issues.single()
            assertEquals(IssueKind.ALLOW_SENSITIVE, warning.kind)
            assertEquals(Severity.WARNING, warning.severity)
            assertEquals(listOf("groups", "vip", "rules", "minecraft:command.op"), warning.path)
            assertEquals(7, warning.line)
            assertEquals(setOf(ConfigurationFile.SETTINGS), read.created)
        }
    }

    @Test
    fun `les constats de lecture des deux fichiers se disent ensemble, ceux des droits d'abord, et la validation attend`() {
        put(permissionsPath, """{""", """  "schema-version": 1,""", """  "groups": {""", """    "vip": { "parents": ["membre"] },""", """  }""", """}""")
        put(settingsPath, """{""", """  "schema-version": 1,""", """  "admin_level": "admins"""", """}""")
        ConfigurationFiles(config).use { files ->
            val issues = files.reload().refused()
            assertEquals(listOf(IssueKind.MALFORMED_FILE, IssueKind.UNKNOWN_KEY), issues.map { it.kind })
            assertEquals(listOf(ConfigurationFile.PERMISSIONS, ConfigurationFile.SETTINGS), issues.map { it.file })
            assertEquals(3, issues.last().line)
            assertEquals(issues, files.check().refused())

            // Les droits corrigés de leur syntaxe gardent un parent inconnu, que la validation ne dit pas tant que les réglages ne se lisent pas.
            put(permissionsPath, """{""", """  "schema-version": 1,""", """  "groups": {""", """    "vip": { "parents": ["membre"] }""", """  }""", """}""")
            assertEquals(listOf(IssueKind.UNKNOWN_KEY), files.reload().refused().map { it.kind })

            put(settingsPath, """{""", """  "schema-version": 1,""", """  "admin-level": "admins"""", """}""")
            assertEquals(setOf(IssueKind.MISSING_DEFAULT, IssueKind.UNKNOWN_PARENT), files.reload().refused().map { it.kind }.toSet())
        }
    }

    @Test
    fun `le catalogue passe à la validation, et ses constats ont leur ligne`() {
        put(
            permissionsPath,
            """{""",
            """  "schema-version": 1,""",
            """  "groups": {""",
            """    "default": {""",
            """      "rules": {""",
            """        "minecraft:command.fly": "allow",""",
            """        "minecraft:command.help": "allow"""",
            """      }""",
            """    }""",
            """  }""",
            """}""",
        )
        ConfigurationFiles(config).use { files ->
            assertEquals(emptyList<Issue>(), files.reload().valid().report.issues)
            val expected = listOf(Issue(IssueKind.UNKNOWN_COMMAND_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "default", "rules", "minecraft:command.fly"), literals("minecraft:command.fly"), line = 6))
            assertEquals(expected, files.reload(catalog).valid().report.issues)
            assertEquals(expected, files.check(catalog).valid().report.issues)
        }
    }

    @Test
    fun `un constat que le catalogue tire de l'arbre a sa ligne, et son erreur fait refuser la configuration`() {
        put(settingsPath, """{""", """  "schema-version": 1,""", """  "aliases": {""", """    "gmc": "minecraft:gamemode creative"""", """  }""", """}""")
        val withTree = object : Catalog by catalog {
            override fun treeIssues(configuration: Configuration): List<Issue> = configuration.settings.aliases.map { (name, target) ->
                Issue(IssueKind.ALIAS_TARGET_NOT_LITERALS, ConfigurationFile.SETTINGS, listOf("aliases", name), literals(name, target, "creative"))
            }
        }
        ConfigurationFiles(config).use { files ->
            assertEquals(emptyList<Issue>(), files.reload().valid().report.issues)
            val expected = listOf(Issue(IssueKind.ALIAS_TARGET_NOT_LITERALS, ConfigurationFile.SETTINGS, listOf("aliases", "gmc"), literals("gmc", "minecraft:gamemode creative", "creative"), line = 4))
            assertEquals(expected, files.reload(withTree).refused())
            assertEquals(expected, files.check(withTree).refused())
        }
    }

    @Test
    fun `un rechargement refusé ne casse rien, et un fichier corrigé se recharge`() {
        put(settingsPath, """{ "schema-version": 1, "mode": "observe" }""")
        ConfigurationFiles(config).use { files ->
            assertEquals(Mode.OBSERVE, files.reload().valid().configuration.settings.mode)

            put(settingsPath, """{ "schema-version": 1, "mode": "observe", "aliases": { "w l a": "whitelist add" } }""")
            assertEquals(listOf(IssueKind.MALFORMED_ALIAS_NAME), files.reload().refused().map { it.kind })

            put(settingsPath, """{ "schema-version": 1, "mode": "observe", }""")
            assertEquals(listOf(IssueKind.MALFORMED_FILE), files.reload().refused().map { it.kind })

            put(settingsPath, """{ "schema-version": 1, "mode": "enforce" }""")
            assertEquals(Mode.ENFORCE, files.reload().valid().configuration.settings.mode)
        }
    }

    @Test
    fun `check ne crée pas un fichier absent`() {
        ConfigurationFiles(config).use { files ->
            val issues = files.check().refused()
            assertEquals(listOf(IssueKind.UNREADABLE_FILE, IssueKind.UNREADABLE_FILE), issues.map { it.kind })
            assertEquals(listOf(ConfigurationFile.PERMISSIONS, ConfigurationFile.SETTINGS), issues.map { it.file })
        }
        assertFalse(Files.exists(config))
    }

    @Test
    fun `check lit les fichiers tels qu'ils sont sur le disque, sans en changer un octet ni la date`() {
        val settingsText = "{ \"schema-version\": 1, \"mode\": \"observe\" }\n"
        val permissionsText = "{\n\t\"schema-version\": 1,\n\t\"groups\": { \"default\": {} }\n}\n"
        put(settingsPath, settingsText.trimEnd('\n'))
        put(permissionsPath, permissionsText.trimEnd('\n'))

        ConfigurationFiles(config).use { files ->
            val checked = files.check().valid()
            assertEquals(Mode.OBSERVE, checked.configuration.settings.mode)
            assertEquals(emptySet<ConfigurationFile>(), checked.created)

            // Le disque change sans rechargement : check le voit.
            put(settingsPath, """{ "schema-version": 1, "mode": "regarde" }""")
            assertEquals(listOf(IssueKind.VALUE_OUT_OF_DOMAIN), files.check().refused().map { it.kind })
            put(settingsPath, settingsText.trimEnd('\n'))
            assertEquals(checked.configuration, files.check().valid().configuration)
        }
        for ((path, text) in listOf(settingsPath to settingsText, permissionsPath to permissionsText)) {
            assertArrayEquals(text.toByteArray(Charsets.UTF_8), Files.readAllBytes(path))
            assertEquals(stamp, Files.getLastModifiedTime(path))
        }
    }

    private fun put(path: Path, vararg lines: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, lines.joinToString("\n", postfix = "\n"))
        Files.setLastModifiedTime(path, stamp)
    }

    private fun ConfigurationRead.valid(): ConfigurationRead.Valid = when (this) {
        is ConfigurationRead.Valid -> this
        is ConfigurationRead.Refused -> fail("La configuration est refusée : ${report.issues}")
    }

    private fun ConfigurationRead.refused(): List<Issue> = when (this) {
        is ConfigurationRead.Valid -> fail("La configuration est valide : $configuration")
        is ConfigurationRead.Refused -> report.issues
    }
}
