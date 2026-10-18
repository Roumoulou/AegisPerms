// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.SensitiveReason
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
import java.time.ZoneOffset
import kotlin.io.path.name

/** Le catalogue, ses clés de commande recalculées et ses clés d'API gardées (cahier des charges, 4.6). */
class CatalogFileTest {

    @TempDir
    lateinit var dir: Path

    private val config: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("aegisperms")

    private val catalogPath: Path get() = config.resolve("catalog.json")

    /** Le premier démarrage, avec des millièmes que le catalogue ne garde pas, puis un démarrage du lendemain. */
    private val firstStart = Clock.fixed(Instant.parse("2026-10-05T12:05:09.750Z"), ZoneOffset.UTC)

    private val nextStart = Clock.fixed(Instant.parse("2026-10-06T08:00:00Z"), ZoneOffset.UTC)

    private val commands = mapOf(
        "minecraft:command.tp" to CommandEntry(NodeKind.LITERAL, requirement = CommandRequirement.GAMEMASTERS, aliasOf = "minecraft:command.teleport", owner = "minecraft"),
        "minecraft:command.teleport" to CommandEntry(NodeKind.LITERAL, requirement = CommandRequirement.GAMEMASTERS, owner = "minecraft", prefixedForm = "mc:teleport", aliases = listOf("tp")),
        "minecraft:command.teleport.targets" to CommandEntry(NodeKind.ARGUMENT, executable = true, requirement = CommandRequirement.GAMEMASTERS, owner = "minecraft"),
        "minecraft:command.op" to CommandEntry(
            NodeKind.LITERAL,
            requirement = CommandRequirement.ADMINS,
            shared = true,
            moreDemanding = true,
            sensitive = CommandEntry.sensitiveName(SensitiveReason.OP_CHANGE),
            owner = "minecraft",
            coOwners = listOf("essentials"),
        ),
        "homes:command.home" to CommandEntry(NodeKind.LITERAL, executable = true, requirement = CommandRequirement.MOD_PREDICATE, owner = "homes", prefixedForm = "homes:home"),
    )

    @Test
    fun `le catalogue s'écrit à l'octet près, trié, sans ce qui vaut son défaut, et se relit`() {
        val update = update(firstStart, apiKeys = mapOf("minecraft:commands/entity_selectors" to "boolean", "homes:home/limit" to "integer"))
        assertEquals(GeneratedWrite.Done, update.write)

        val expected = """
            {
              "schema-version": 1,
              "api-keys": {
                "homes:home/limit": {
                  "first-requested": "2026-10-05T12:05:09Z",
                  "type": "integer"
                },
                "minecraft:commands/entity_selectors": {
                  "first-requested": "2026-10-05T12:05:09Z",
                  "type": "boolean"
                }
              },
              "commands": {
                "homes:command.home": {
                  "executable": true,
                  "kind": "literal",
                  "owner": "homes",
                  "prefixed-form": "homes:home",
                  "requirement": "mod-predicate"
                },
                "minecraft:command.op": {
                  "co-owners": ["essentials"],
                  "kind": "literal",
                  "more-demanding": true,
                  "owner": "minecraft",
                  "requirement": "admins",
                  "sensitive": "op_change",
                  "shared": true
                },
                "minecraft:command.teleport": {
                  "aliases": ["tp"],
                  "kind": "literal",
                  "owner": "minecraft",
                  "prefixed-form": "mc:teleport",
                  "requirement": "gamemasters"
                },
                "minecraft:command.teleport.targets": {
                  "executable": true,
                  "kind": "argument",
                  "owner": "minecraft",
                  "requirement": "gamemasters"
                },
                "minecraft:command.tp": {
                  "alias-of": "minecraft:command.teleport",
                  "kind": "literal",
                  "owner": "minecraft",
                  "requirement": "gamemasters"
                }
              }
            }
        """.trimIndent() + "\n"
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(catalogPath))
        assertEquals(update.catalog, readCatalog())
        // Ni temporaire ni fichier de configuration : écrire le catalogue n'ouvre aucun store.
        assertEquals(setOf("catalog.json"), Files.list(config).use { found -> found.map { it.name }.toList().toSet() })
    }

    @Test
    fun `un catalogue sans clé écrit ses deux tables vides`() {
        update(firstStart, commands = emptyMap())
        val expected = """
            {
              "schema-version": 1,
              "api-keys": {},
              "commands": {}
            }
        """.trimIndent() + "\n"
        assertEquals(expected, Files.readString(catalogPath))
    }

    @Test
    fun `un premier catalogue n'a rien à comparer`() {
        assertNull(readCatalog())
        val update = update(firstStart)
        assertNull(update.changes)
        assertFalse(update.previousUnreadable)
        assertEquals(commands, update.catalog.commands)
    }

    @Test
    fun `les clés d'API se gardent d'un démarrage à l'autre avec leur date, et une clé nouvelle prend celle de l'horloge`() {
        update(firstStart, apiKeys = mapOf("homes:home/limit" to "integer"))

        // Au démarrage suivant, le mod ne demande pas tout de suite la clé d'hier : elle reste.
        val second = update(nextStart, apiKeys = mapOf("minecraft:commands/entity_selectors" to "boolean")).catalog
        assertEquals(
            mapOf(
                "homes:home/limit" to ApiKeyEntry("integer", "2026-10-05T12:05:09Z"),
                "minecraft:commands/entity_selectors" to ApiKeyEntry("boolean", "2026-10-06T08:00:00Z"),
            ),
            second.apiKeys,
        )
        assertEquals(second.apiKeys, update(nextStart).catalog.apiKeys)

        // Redemandée par une autre voie, une clé garde sa première date.
        val third = update(nextStart, apiKeys = mapOf("homes:home/limit" to "boolean")).catalog
        assertEquals(ApiKeyEntry("boolean", "2026-10-05T12:05:09Z"), third.apiKeys.getValue("homes:home/limit"))
        assertEquals(third, readCatalog())
    }

    @Test
    fun `une clé dont le chemin porte un point est marquée, et une chaîne à points refusée entre sous son texte`() {
        update(firstStart, commands = emptyMap(), apiKeys = mapOf("homes:home.limit" to "fabric", "homes:home/limit" to "lucko"), refused = mapOf("essentialcommands" to "lucko"))

        val expected = """
            {
              "schema-version": 1,
              "api-keys": {
                "essentialcommands": {
                  "first-requested": "2026-10-05T12:05:09Z",
                  "refused": true,
                  "type": "lucko"
                },
                "homes:home.limit": {
                  "dotted-path": true,
                  "first-requested": "2026-10-05T12:05:09Z",
                  "type": "fabric"
                },
                "homes:home/limit": {
                  "first-requested": "2026-10-05T12:05:09Z",
                  "type": "lucko"
                }
              },
              "commands": {}
            }
        """.trimIndent() + "\n"
        assertEquals(expected, Files.readString(catalogPath))
        assertEquals(ApiKeyEntry("fabric", "2026-10-05T12:05:09Z", dottedPath = true), readCatalog()?.apiKeys?.get("homes:home.limit"))
    }

    @Test
    fun `une chaîne refusée se garde d'un démarrage à l'autre, avec sa première date`() {
        update(firstStart, refused = mapOf("essentialcommands" to "lucko"))

        val kept = update(nextStart).catalog.apiKeys
        assertEquals(mapOf("essentialcommands" to ApiKeyEntry("lucko", "2026-10-05T12:05:09Z", refused = true)), kept)
        assertEquals(kept, update(nextStart, refused = mapOf("essentialcommands" to "lucko")).catalog.apiKeys)
        assertEquals(kept, readCatalog()?.apiKeys)
    }

    @Test
    fun `les clés de commande se remplacent, et les apparues et les disparues se disent, triées`() {
        update(firstStart)
        val next = commands - "minecraft:command.tp" - "minecraft:command.op" + ("minecraft:command.zzz" to CommandEntry(NodeKind.LITERAL)) + ("homes:command.sethome" to CommandEntry(NodeKind.LITERAL))

        val update = update(nextStart, commands = next)
        assertEquals(CatalogChanges(appeared = listOf("homes:command.sethome", "minecraft:command.zzz"), disappeared = listOf("minecraft:command.op", "minecraft:command.tp")), update.changes)
        assertEquals(next, update.catalog.commands)
        assertEquals(next, readCatalog()?.commands)

        // Le même calcul, rejoué : rien n'apparaît ni ne disparaît.
        assertTrue(update(nextStart, commands = next).changes?.isEmpty == true)
    }

    @Test
    fun `un catalogue illisible est régénéré sans ses dates, et le résultat le dit`() {
        update(firstStart, apiKeys = mapOf("homes:home/limit" to "integer"))
        for (damaged in listOf("{ \"schema-version\": 1, \"commands\": {", "{ \"schema-version\": 2 }", "")) {
            Files.writeString(catalogPath, damaged)
            assertNull(readCatalog(), damaged)

            val update = update(nextStart, apiKeys = mapOf("homes:home/limit" to "integer"))
            assertTrue(update.previousUnreadable, damaged)
            assertNull(update.changes, damaged)
            assertEquals(GeneratedWrite.Done, update.write, damaged)
            // La clé d'hier est redemandée : sa première date est perdue avec l'ancien catalogue.
            assertEquals(mapOf("homes:home/limit" to ApiKeyEntry("integer", "2026-10-06T08:00:00Z")), update.catalog.apiKeys, damaged)
            assertEquals(update.catalog, readCatalog(), damaged)
        }
    }

    @Test
    fun `la nature d'un nœud, une exigence et une raison s'écrivent sous leur nom de fichier`() {
        val requirements = mapOf(
            "none" to CommandRequirement.NONE,
            "moderators" to CommandRequirement.MODERATORS,
            "gamemasters" to CommandRequirement.GAMEMASTERS,
            "admins" to CommandRequirement.ADMINS,
            "owners" to CommandRequirement.OWNERS,
            "mod-predicate" to CommandRequirement.MOD_PREDICATE,
        )
        assertEquals(CommandRequirement.entries.toSet(), requirements.values.toSet())
        for ((name, requirement) in requirements) {
            for ((kindName, kind) in mapOf("literal" to NodeKind.LITERAL, "argument" to NodeKind.ARGUMENT)) {
                Files.createDirectories(config)
                Files.writeString(catalogPath, """{ "schema-version": 1, "commands": { "a:command.b": { "kind": "$kindName", "requirement": "$name" } } }""")
                assertEquals(CommandEntry(kind, requirement = requirement), readCatalog()?.commands?.get("a:command.b"), "$kindName, $name")
            }
        }
        for (level in OpLevel.entries) assertEquals(level, CommandRequirement.of(level).opLevel)
        assertEquals(listOf(null, null), listOf(CommandRequirement.NONE.opLevel, CommandRequirement.MOD_PREDICATE.opLevel))
        assertEquals("command_blocks", CommandEntry.sensitiveName(SensitiveReason.COMMAND_BLOCKS))
    }

    private fun update(clock: Clock, commands: Map<String, CommandEntry> = this.commands, apiKeys: Map<String, String> = emptyMap(), refused: Map<String, String> = emptyMap()): CatalogUpdate =
        ConfigurationFiles(config, clock).use { files -> files.updateCatalog(commands, apiKeys, refused) }

    private fun readCatalog(): CatalogFile? = ConfigurationFiles(config, nextStart).use { it.readCatalog() }
}
