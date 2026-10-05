// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import fr.moulou.aegisperms.validate.ValidationReport
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.name

/** L'état, la dernière tentative de publication et ses constats (cahier des charges, 4.7). */
class StateFileTest {

    @TempDir
    lateinit var dir: Path

    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val statePath: Path get() = config.resolve("state.json")

    /** Une horloge arrêtée à 12:05:09 UTC, sur un serveur à l'heure de Paris : la date de l'état est en UTC, pas à l'heure locale. */
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:05:09.750Z"), ZoneId.of("Europe/Paris"))

    private val none = ValidationReport(emptyList())

    private val refused = ValidationReport(
        listOf(
            Issue(IssueKind.UNKNOWN_PARENT, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "parents", "0"), literals("vip", "membre"), line = 7),
            Issue(
                IssueKind.MALFORMED_KEY,
                ConfigurationFile.PERMISSIONS,
                listOf("groups", "vip", "rules", "op"),
                listOf(IssueArgument.Literal("op"), IssueArgument.Translatable("aegisperms.issue.malformed_key.missing_namespace")),
            ),
            Issue(IssueKind.REQUIRE_PREFIX_WITHOUT_FORMS, ConfigurationFile.SETTINGS, listOf("namespaces", "require-prefix"), line = 3),
            Issue(IssueKind.DENY_KEY_GONE, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", "minecraft:command.fly"), literals("minecraft:command.fly")),
            Issue(IssueKind.MALFORMED_FILE, ConfigurationFile.SETTINGS, emptyList(), literals("Unexpected JSON token at offset 12"), line = 2),
        ),
    )

    private fun write(generation: Long, source: PublicationSource, result: PublicationResult, report: ValidationReport): String {
        ConfigurationFiles(config, clock).use { files -> assertEquals(GeneratedWrite.Done, files.writeState(generation, source, result, report)) }
        return Files.readString(statePath)
    }

    private fun readState(): StateFile? = ConfigurationFiles(config, clock).use { it.readState() }

    @Test
    fun `une publication appliquée sans constat s'écrit à l'octet près, à l'heure UTC`() {
        val expected = """
            {
              "schema-version": 1,
              "date": "2026-10-05T12:05:09Z",
              "generation": 1,
              "issues": [],
              "result": "applied",
              "source": "startup"
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(1, PublicationSource.STARTUP, PublicationResult.APPLIED, none))
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(statePath))
        // Ni temporaire ni fichier de configuration : écrire l'état n'ouvre aucun store.
        assertEquals(setOf("state.json"), Files.list(config).use { found -> found.map { it.name }.toList().toSet() })
    }

    @Test
    fun `une tentative refusée écrit ses constats sans texte, avec leur niveau, leur fichier, leur chemin, leur ligne et leurs arguments`() {
        val expected = """
            {
              "schema-version": 1,
              "date": "2026-10-05T12:05:09Z",
              "generation": 7,
              "issues": [
                {
                  "arguments": ["vip", "membre"],
                  "file": "permissions.json",
                  "kind": "unknown_parent",
                  "line": 7,
                  "path": ["groups", "vip", "parents", "0"],
                  "severity": "error"
                },
                {
                  "arguments": [
                    "op",
                    {
                      "translate": "aegisperms.issue.malformed_key.missing_namespace"
                    }
                  ],
                  "file": "permissions.json",
                  "kind": "malformed_key",
                  "path": ["groups", "vip", "rules", "op"],
                  "severity": "error"
                },
                {
                  "file": "settings.json",
                  "kind": "require_prefix_without_forms",
                  "line": 3,
                  "path": ["namespaces", "require-prefix"],
                  "severity": "warning"
                },
                {
                  "arguments": ["minecraft:command.fly"],
                  "file": "permissions.json",
                  "kind": "deny_key_gone",
                  "path": ["groups", "vip", "rules", "minecraft:command.fly"],
                  "severity": "reinforced"
                },
                {
                  "arguments": ["Unexpected JSON token at offset 12"],
                  "file": "settings.json",
                  "kind": "malformed_file",
                  "line": 2,
                  "severity": "error"
                }
              ],
              "result": "refused",
              "source": "reload"
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(7, PublicationSource.RELOAD, PublicationResult.REFUSED, refused))
    }

    @Test
    fun `l'état se relit, argument à traduire compris`() {
        write(7, PublicationSource.WATCH, PublicationResult.REFUSED, refused)
        val state = readState()
        assertEquals(StateFile.of(7, Instant.parse("2026-10-05T12:05:09Z"), PublicationSource.WATCH, PublicationResult.REFUSED, refused), state)
        assertEquals(listOf(IssueArgument.Literal("op"), IssueArgument.Translatable("aegisperms.issue.malformed_key.missing_namespace")), state?.issues?.get(1)?.arguments)
        // La nature d'un constat est la fin de sa clé de traduction.
        assertEquals(refused.issues.map { it.kind.translationKey }, state?.issues?.map { "aegisperms.issue." + it.kind })
    }

    @Test
    fun `chaque source et chaque résultat s'écrivent sous leur nom de fichier`() {
        val sources = mapOf("startup" to PublicationSource.STARTUP, "reload" to PublicationSource.RELOAD, "command" to PublicationSource.COMMAND, "watch" to PublicationSource.WATCH, "restore" to PublicationSource.RESTORE)
        val results = mapOf("applied" to PublicationResult.APPLIED, "refused" to PublicationResult.REFUSED)
        assertEquals(PublicationSource.entries.toSet(), sources.values.toSet())
        assertEquals(PublicationResult.entries.toSet(), results.values.toSet())
        for ((sourceName, source) in sources) {
            for ((resultName, result) in results) {
                val text = write(3, source, result, none)
                assertTrue(text.contains("  \"source\": \"$sourceName\"\n"), text)
                assertTrue(text.contains("  \"result\": \"$resultName\",\n"), text)
            }
        }
    }

    @Test
    fun `la dernière tentative remplace la précédente`() {
        assertNull(readState())
        write(4, PublicationSource.COMMAND, PublicationResult.APPLIED, none)
        val text = write(4, PublicationSource.WATCH, PublicationResult.REFUSED, refused)

        assertFalse(text.contains("applied"))
        assertFalse(text.contains("\"command\""))
        assertEquals(PublicationResult.REFUSED, readState()?.result)
        // Refusée, la tentative porte la génération restée en vigueur.
        assertEquals(4L, readState()?.generation)
    }

    @Test
    fun `un état abîmé ne se relit pas, et la tentative suivante le remplace`() {
        write(1, PublicationSource.STARTUP, PublicationResult.APPLIED, none)
        for (damaged in listOf("{ \"schema-version\": 1,", "{ \"schema-version\": 1, \"generation\": 1, \"date\": \"x\", \"source\": \"startup\", \"result\": \"applied\", \"issues\": [ { \"severity\": \"error\", \"kind\": \"k\", \"file\": \"f\", \"arguments\": [12] } ] }")) {
            Files.writeString(statePath, damaged)
            assertNull(readState(), damaged)
        }
        write(2, PublicationSource.RELOAD, PublicationResult.APPLIED, none)
        assertEquals(2L, readState()?.generation)
    }
}
