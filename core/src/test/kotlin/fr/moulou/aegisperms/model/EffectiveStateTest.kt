// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.state
import fr.moulou.aegisperms.Fixtures.uuidOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** L'état en vigueur du cahier des charges (4.1, 7.4) : une configuration compilée et sa génération. */
class EffectiveStateTest {

    @Test
    fun `l'état porte sa génération et sa configuration`() {
        val state = state(personas, generation = 7)

        assertEquals(7, state.generation)
        assertSame(personas, state.permissions)
        assertSame(Settings.DELIVERED, state.settings)
    }

    @Test
    fun `chaque groupe a son ensemble de règles, sans celles de ses parents`() {
        val state = state(personas)

        assertEquals(setOf("admin", "default", "membre", "moderateur", "vip"), state.groupRules.keys)
        assertEquals(
            setOf("minecraft:command.kick", "minecraft:command.teleport", "minecraft:commands/entity_selectors"),
            state.groupRules.getValue("moderateur").rules.keys.map { it.text }.toSet(),
        )
        assertEquals(Rule.DENY, state.groupRules.getValue("moderateur")[Keys.parse("minecraft:command.teleport")])
    }

    @Test
    fun `un joueur du fichier se retrouve par son UUID`() {
        val state = state(personas)

        assertEquals(setOf("Alice", "Bob", "Joe", "Jogger").map(::uuidOf).toSet(), state.players.keys)
        val jogger = state.players.getValue(uuidOf("Jogger"))
        assertEquals("Jogger", jogger.name)
        assertEquals(readableForm("Jogger"), jogger.readableForm)
        assertEquals(listOf("membre"), jogger.groups)
        assertEquals(Rule.ALLOW, jogger.rules[Keys.parse("minecraft:command.teleport.targets.location.rotation")])

        // Olga et Nina ne figurent pas dans le fichier des droits.
        assertNull(state.players[uuidOf("Olga")])
        assertNull(state.players[uuidOf("Nina")])
    }

    @Test
    fun `un joueur écrit sans pseudo connu n'a pas de nom`() {
        val uuid = uuidOf("Anonyme")
        val state = state(permissions(groups = Permissions.DELIVERED.groups, players = mapOf(ReadableForm.of(null, uuid) to Player())))

        assertNull(state.players.getValue(uuid).name)
    }

    @Test
    fun `les membres d'office d'un groupe sont les ops d'au moins son niveau`() {
        val personasState = state(personas)
        for (opLevel in 0..3) assertTrue(personasState.exOfficioGroups(opLevel).isEmpty(), "niveau d'op $opLevel")
        assertEquals(listOf("admin"), personasState.exOfficioGroups(4))

        val twoLevels = state(permissions(Permissions.DELIVERED.groups + ("staff" to Group(includeOps = OpLevel.MODERATORS))))
        assertTrue(twoLevels.exOfficioGroups(0).isEmpty())
        assertEquals(listOf("staff"), twoLevels.exOfficioGroups(1))
        assertEquals(listOf("staff"), twoLevels.exOfficioGroups(3))
        assertEquals(listOf("admin", "staff"), twoLevels.exOfficioGroups(4))
    }

    @Test
    fun `un refus suit denied-commands quand sa règle ne dit pas sa visibilité`() {
        val hiddenByDefault = state(personas)
        val visibleByDefault = state(personas, Settings.DELIVERED.copy(deniedCommands = DeniedCommands.VISIBLE))

        assertEquals(Decision.DENY_HIDDEN, hiddenByDefault.decisionOf(Rule.DENY))
        assertEquals(Decision.DENY_VISIBLE, visibleByDefault.decisionOf(Rule.DENY))

        assertEquals(Decision.DENY_VISIBLE, hiddenByDefault.decisionOf(Rule(RuleValue.DENY, visible = true)))
        assertEquals(Decision.DENY_HIDDEN, visibleByDefault.decisionOf(Rule(RuleValue.DENY, visible = false)))

        assertEquals(Decision.ALLOW, visibleByDefault.decisionOf(Rule(RuleValue.ALLOW, visible = true)))
    }

    @Test
    fun `une configuration que la validation refuserait ne se compile pas`() {
        val delivered = Permissions.DELIVERED.groups
        val uuid = uuidOf("Joe")

        assertThrows(MalformedKeyException::class.java) { state(permissions(delivered + ("vip" to Group(rules = mapOf("teleport" to Rule.ALLOW))))) }
        assertThrows(IllegalArgumentException::class.java) { state(permissions(delivered, mapOf("Joe" to Player()))) }
        assertThrows(IllegalArgumentException::class.java) { state(permissions(delivered, mapOf("Joe#${uuid.toString().uppercase()}" to Player()))) }
        assertThrows(IllegalArgumentException::class.java) { state(permissions(delivered, mapOf("Joe#$uuid" to Player(), "Jo#$uuid" to Player()))) }
    }
}
