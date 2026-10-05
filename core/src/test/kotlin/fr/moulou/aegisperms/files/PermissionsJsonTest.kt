// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.Fixtures
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Le fichier des droits tel que le mod l'écrit et le lit (cahier des charges, 4.4). */
class PermissionsJsonTest {

    @TempDir
    lateinit var dir: Path

    /** Un chemin à espaces et à accents, dont les dossiers n'existent pas encore. */
    private val file: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("permissions.json")

    /** Des droits qui portent tout ce qu'un fichier peut écrire : des règles longues, une note de joueur, un joueur sans pseudo connu. */
    private val rich = Fixtures.permissions(
        groups = mapOf(
            "default" to Group(rules = mapOf("command" to Rule(RuleValue.DENY, visible = true, note = "Tout est fermé, et le joueur le sait."))),
            "sanction" to Group(weight = -5, parents = listOf("default"), includeOps = OpLevel.MODERATORS, rules = mapOf("minecraft:command.msg" to Rule(RuleValue.DENY, visible = false)), note = "Un poids négatif."),
        ),
        players = mapOf(
            "?#${Fixtures.uuidOf("Inconnu")}" to Player(groups = listOf("sanction"), note = "Ajouté par son UUID."),
            Fixtures.readableForm("Nom#dièse") to Player(rules = mapOf("minecraft:command.me" to Rule(RuleValue.ALLOW, note = "Il peut encore mimer."))),
        ),
    )

    private fun text(permissions: Permissions): String = JsonFileFormat.PERMISSIONS.encodeToText(Permissions.serializer(), permissions)

    private fun write(permissions: Permissions): String {
        JsonFileFormat.PERMISSIONS.encodeToPath(Permissions.serializer(), permissions, file)
        return Files.readString(file)
    }

    private fun read(text: String): Permissions {
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return JsonFileFormat.PERMISSIONS.decodeFromPath(Permissions.serializer(), file)
    }

    @Test
    fun `le fichier des droits du premier démarrage s'écrit à l'octet près`() {
        val expected = """
            {
              "schema-version": 1,
              "groups": {
                "admin": {
                  "include-ops": "owners",
                  "rules": {
                    "command": "allow"
                  },
                  "weight": 100
                },
                "default": {
                  "rules": {
                    "command": "deny",
                    "minecraft:command.help": "allow",
                    "minecraft:command.me": "allow",
                    "minecraft:command.msg": "allow"
                  }
                }
              },
              "players": {}
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(Permissions.DELIVERED))
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(file))
    }

    @Test
    fun `les personnages s'écrivent à l'octet près`() {
        val expected = """
            {
              "schema-version": 1,
              "groups": {
                "admin": {
                  "include-ops": "owners",
                  "parents": ["moderateur"],
                  "rules": {
                    "command": "allow"
                  },
                  "weight": 100
                },
                "default": {
                  "rules": {
                    "command": "deny",
                    "minecraft:command.help": "allow",
                    "minecraft:command.me": "allow",
                    "minecraft:command.msg": "allow"
                  }
                },
                "membre": {
                  "note": "Les joueurs réguliers.",
                  "rules": {
                    "minecraft:command.teleport": "allow",
                    "minecraft:command.teleport.location": "deny",
                    "minecraft:command.teleport.targets.location": "deny"
                  },
                  "weight": 10
                },
                "moderateur": {
                  "parents": ["membre"],
                  "rules": {
                    "minecraft:command.kick": "allow",
                    "minecraft:command.teleport": "deny",
                    "minecraft:commands/entity_selectors": "allow"
                  },
                  "weight": 50
                },
                "vip": {
                  "rules": {
                    "minecraft:command.teleport.targets.location": "allow"
                  },
                  "weight": 80
                }
              },
              "players": {
                "${Fixtures.readableForm("Alice")}": {
                  "groups": ["admin"]
                },
                "${Fixtures.readableForm("Bob")}": {
                  "groups": ["moderateur"]
                },
                "${Fixtures.readableForm("Joe")}": {
                  "groups": ["membre", "vip"]
                },
                "${Fixtures.readableForm("Jogger")}": {
                  "groups": ["membre"],
                  "rules": {
                    "minecraft:command.teleport.targets.location.rotation": "allow"
                  }
                }
              }
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(Fixtures.personas))
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(file))
    }

    @Test
    fun `les droits font l'aller-retour`() {
        for (permissions in listOf(Permissions.DELIVERED, Fixtures.personas, rich)) assertEquals(permissions, read(write(permissions)))
    }

    @Test
    fun `un poids à 0, une liste, un objet et un champ vides s'omettent, groups et players s'écrivent toujours`() {
        val bare = """
            {
              "schema-version": 1,
              "groups": {},
              "players": {}
            }
        """.trimIndent() + "\n"
        assertEquals(bare, text(Permissions(Permissions.SCHEMA_VERSION)))

        val emptyEntries = """
            {
              "schema-version": 1,
              "groups": {
                "default": {}
              },
              "players": {
                "${Fixtures.readableForm("Nina")}": {}
              }
            }
        """.trimIndent() + "\n"
        assertEquals(emptyEntries, text(Fixtures.permissions(groups = mapOf("default" to Group()), players = mapOf(Fixtures.readableForm("Nina") to Player()))))
    }

    @Test
    fun `une règle longue s'écrit note, value puis visible, et un groupe include-ops, note, parents, rules puis weight`() {
        val expected = """
            {
              "schema-version": 1,
              "groups": {
                "default": {
                  "rules": {
                    "command": {
                      "note": "Tout est fermé, et le joueur le sait.",
                      "value": "deny",
                      "visible": true
                    }
                  }
                },
                "sanction": {
                  "include-ops": "moderators",
                  "note": "Un poids négatif.",
                  "parents": ["default"],
                  "rules": {
                    "minecraft:command.msg": {
                      "value": "deny",
                      "visible": false
                    }
                  },
                  "weight": -5
                }
              },
              "players": {
                "?#${Fixtures.uuidOf("Inconnu")}": {
                  "groups": ["sanction"],
                  "note": "Ajouté par son UUID."
                },
                "${Fixtures.readableForm("Nom#dièse")}": {
                  "rules": {
                    "minecraft:command.me": {
                      "note": "Il peut encore mimer.",
                      "value": "allow"
                    }
                  }
                }
              }
            }
        """.trimIndent() + "\n"
        assertEquals(expected, text(rich))
    }

    @Test
    fun `deux droits égaux donnent le même texte, quel que soit l'ordre de leurs entrées`() {
        val personas = Fixtures.personas
        val reordered = Permissions(
            schemaVersion = personas.schemaVersion,
            groups = personas.groups.mapValues { it.value.copy(rules = it.value.rules.reversedEntries()) }.reversedEntries(),
            players = personas.players.mapValues { it.value.copy(rules = it.value.rules.reversedEntries()) }.reversedEntries(),
        )
        assertEquals(listOf("vip", "moderateur", "membre", "default", "admin"), reordered.groups.keys.toList())
        assertEquals(personas, reordered)
        assertEquals(text(personas), text(reordered))
    }

    @Test
    fun `les clés se trient par point de code, au-delà du plan de base`() {
        val lastOfBasicPlane = "￿"
        val beyondBasicPlane = "😀"
        // L'ordre naturel de String, par unité UTF-16, rangerait la paire de substitution avant U+FFFF.
        assertTrue(lastOfBasicPlane > beyondBasicPlane)

        val written = text(Fixtures.permissions(groups = mapOf(beyondBasicPlane to Group(), lastOfBasicPlane to Group(), "default" to Group())))
        val positions = listOf("default", lastOfBasicPlane, beyondBasicPlane).map { written.indexOf("\"$it\"") }
        assertTrue(positions.all { it >= 0 }, written)
        assertEquals(positions.sorted(), positions, written)
    }

    @Test
    fun `la mise en forme de l'administrateur ne survit pas à une écriture du mod, ses notes si`() {
        val byHand = """
            { "players": {},
              "groups": {
                "vip": { "weight": 80, "note": "Écrit à la main.", "rules": { "minecraft:command.teleport": { "value": "allow" } }, "parents": [] },
                "default": { "rules": {} }
              },
              "schema-version": 1 }
        """.trimIndent()
        val expected = """
            {
              "schema-version": 1,
              "groups": {
                "default": {},
                "vip": {
                  "note": "Écrit à la main.",
                  "rules": {
                    "minecraft:command.teleport": "allow"
                  },
                  "weight": 80
                }
              },
              "players": {}
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(read(byHand)))
    }

    private fun <V> Map<String, V>.reversedEntries(): Map<String, V> = entries.reversed().associate { it.key to it.value }
}
