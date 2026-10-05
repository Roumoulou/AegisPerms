// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import fr.moulou.aegisperms.model.CommandKey
import fr.moulou.aegisperms.model.Configuration
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Key
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.Subject
import java.util.UUID

/** Les briques des tests du cœur, et les personnages du cahier des charges (2.11), le jeu d'essai commun. */
object Fixtures {

    /**
     * Les personnages, tels que 4.4 les écrit : `default` est celui du premier démarrage, et quatre joueurs sont écrits. Olga, seule op, et Nina ne figurent
     * pas dans le fichier des droits.
     */
    val personas: Permissions = permissions(
        groups = mapOf(
            "admin" to Group(weight = 100, parents = listOf("moderateur"), includeOps = OpLevel.OWNERS, rules = mapOf("command" to Rule.ALLOW)),
            "default" to Permissions.DELIVERED.groups.getValue("default"),
            "membre" to Group(
                weight = 10,
                rules = mapOf(
                    "minecraft:command.teleport" to Rule.ALLOW,
                    "minecraft:command.teleport.location" to Rule.DENY,
                    "minecraft:command.teleport.targets.location" to Rule.DENY,
                ),
                note = "Les joueurs réguliers.",
            ),
            "moderateur" to Group(
                weight = 50,
                parents = listOf("membre"),
                rules = mapOf("minecraft:command.kick" to Rule.ALLOW, "minecraft:command.teleport" to Rule.DENY, "minecraft:commands/entity_selectors" to Rule.ALLOW),
            ),
            "vip" to Group(weight = 80, rules = mapOf("minecraft:command.teleport.targets.location" to Rule.ALLOW)),
        ),
        players = mapOf(
            readableForm("Alice") to Player(groups = listOf("admin")),
            readableForm("Bob") to Player(groups = listOf("moderateur")),
            readableForm("Joe") to Player(groups = listOf("membre", "vip")),
            readableForm("Jogger") to Player(groups = listOf("membre"), rules = mapOf("minecraft:command.teleport.targets.location.rotation" to Rule.ALLOW)),
        ),
    )

    /** Un `default` sans règle, pour les tests où celui du premier démarrage, qui refuse `command`, répondrait à la place de ce qu'ils éprouvent. */
    val silentDefault: Pair<String, Group> = Group.DEFAULT to Group()

    /** Un UUID stable par pseudo, comme un serveur hors ligne le calcule. */
    fun uuidOf(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    fun readableForm(name: String): String = ReadableForm.of(name, uuidOf(name))

    fun permissions(groups: Map<String, Group>, players: Map<String, Player> = emptyMap()): Permissions = Permissions(Permissions.SCHEMA_VERSION, groups, players)

    fun state(permissions: Permissions, settings: Settings = Settings.DELIVERED, generation: Long = 1): EffectiveState = EffectiveState(generation, Configuration(settings, permissions))

    fun resolver(permissions: Permissions, settings: Settings = Settings.DELIVERED): Resolver = Resolver(state(permissions, settings))

    /** Le sujet d'un pseudo : non-op, sauf niveau d'op donné. */
    fun subject(name: String, opLevel: Int = 0): Subject = Subject(uuidOf(name), opLevel)

    fun key(text: String): Key = Keys.parse(text)

    fun commandKey(text: String): CommandKey = Keys.parse(text) as CommandKey
}
