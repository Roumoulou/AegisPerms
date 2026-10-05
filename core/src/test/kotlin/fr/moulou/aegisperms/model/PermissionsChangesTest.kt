// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.state
import fr.moulou.aegisperms.Fixtures.uuidOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ce que `/aegis reload` dit des droits qui changent (cahier des charges, 4.8). */
class PermissionsChangesTest {

    @Test
    fun `deux états aux mêmes droits ne changent rien`() {
        val changes = PermissionsChanges.between(state(personas, generation = 1), state(personas, generation = 2))

        assertTrue(changes.isEmpty)
    }

    @Test
    fun `les groupes créés, supprimés et modifiés se disent par leur nom`() {
        val groups = personas.groups
        val after = personas.copy(
            groups = groups - "vip" +
                ("builders" to Group(weight = 20)) +
                ("membre" to groups.getValue("membre").copy(weight = 15)) +
                ("moderateur" to groups.getValue("moderateur").copy(includeOps = OpLevel.MODERATORS)) +
                ("admin" to groups.getValue("admin").copy(rules = emptyMap())),
            players = personas.players - readableForm("Joe"),
        )

        val changes = PermissionsChanges.between(state(personas), state(after))

        assertEquals(listOf("builders"), changes.groupsCreated)
        assertEquals(listOf("vip"), changes.groupsDeleted)
        // admin ne change que par ses règles : il n'est pas un groupe modifié.
        assertEquals(listOf("membre", "moderateur"), changes.groupsChanged)
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `les joueurs ajoutés, retirés et modifiés se disent par leur forme lisible`() {
        val players = personas.players
        val after = personas.copy(
            players = players - readableForm("Bob") +
                (readableForm("Zoe") to Player(groups = listOf("vip"))) +
                (readableForm("Alice") to players.getValue(readableForm("Alice")).copy(note = "La fondatrice.")) +
                (readableForm("Joe") to players.getValue(readableForm("Joe")).copy(groups = listOf("vip"))),
        )

        val changes = PermissionsChanges.between(state(personas), state(after))

        assertEquals(listOf(readableForm("Zoe")), changes.playersAdded)
        assertEquals(listOf(readableForm("Bob")), changes.playersRemoved)
        assertEquals(listOf(readableForm("Alice"), readableForm("Joe")), changes.playersChanged)
    }

    @Test
    fun `un pseudo renommé est un joueur modifié, pas un départ et une arrivée`() {
        val renamed = ReadableForm.of("Jogger2", uuidOf("Jogger"))
        val after = personas.copy(players = personas.players - readableForm("Jogger") + (renamed to personas.players.getValue(readableForm("Jogger"))))

        val changes = PermissionsChanges.between(state(personas), state(after))

        assertTrue(changes.playersAdded.isEmpty())
        assertTrue(changes.playersRemoved.isEmpty())
        assertEquals(listOf(renamed), changes.playersChanged)
        assertTrue(changes.rules.isEmpty())
    }

    @Test
    fun `une règle ajoutée, retirée ou modifiée se dit avec son ensemble de règles`() {
        val groups = personas.groups
        val jogger = readableForm("Jogger")
        val after = personas.copy(
            groups = groups + ("vip" to groups.getValue("vip").copy(rules = mapOf("minecraft:command.teleport.targets.location" to Rule.DENY, "minecraft:command.list" to Rule.ALLOW))),
            players = personas.players + (jogger to personas.players.getValue(jogger).copy(rules = emptyMap())),
        )

        val changes = PermissionsChanges.between(state(personas), state(after))

        assertEquals(
            listOf(
                RuleChange(RuleSetOwner.OfGroup("vip"), Keys.parse("minecraft:command.list"), null, Rule.ALLOW),
                RuleChange(RuleSetOwner.OfGroup("vip"), Keys.parse("minecraft:command.teleport.targets.location"), Rule.ALLOW, Rule.DENY),
                RuleChange(RuleSetOwner.OfPlayer(jogger), Keys.parse("minecraft:command.teleport.targets.location.rotation"), Rule.ALLOW, null),
            ),
            changes.rules,
        )
        assertTrue(changes.groupsChanged.isEmpty())
        assertTrue(changes.playersChanged.isEmpty())
    }

    @Test
    fun `les changements sortent triés par point de code, les groupes avant les joueurs`() {
        val before = permissions(Permissions.DELIVERED.groups)
        val after = permissions(
            groups = Permissions.DELIVERED.groups +
                ("zeta" to Group(rules = mapOf("minecraft:command.me" to Rule.ALLOW, "minecraft:command.list" to Rule.ALLOW))) +
                ("alpha" to Group(rules = mapOf("minecraft:command.seed" to Rule.DENY))),
            players = mapOf(readableForm("Zoe") to Player(rules = mapOf("minecraft:command.kick" to Rule.ALLOW)), readableForm("Abel") to Player(rules = mapOf("minecraft:command.kick" to Rule.DENY))),
        )

        val changes = PermissionsChanges.between(state(before), state(after))

        assertEquals(listOf("alpha", "zeta"), changes.groupsCreated)
        assertEquals(listOf(readableForm("Abel"), readableForm("Zoe")), changes.playersAdded)
        assertEquals(
            listOf(
                RuleSetOwner.OfGroup("alpha") to "minecraft:command.seed",
                RuleSetOwner.OfGroup("zeta") to "minecraft:command.list",
                RuleSetOwner.OfGroup("zeta") to "minecraft:command.me",
                RuleSetOwner.OfPlayer(readableForm("Abel")) to "minecraft:command.kick",
                RuleSetOwner.OfPlayer(readableForm("Zoe")) to "minecraft:command.kick",
            ),
            changes.rules.map { it.owner to it.key.text },
        )
    }
}
