// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.hook.HookFixtures.personas
import fr.moulou.aegisperms.hook.HookFixtures.readableForm
import fr.moulou.aegisperms.hook.HookFixtures.state
import fr.moulou.aegisperms.hook.HookFixtures.uuidOf
import fr.moulou.aegisperms.model.DeniedCommands
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.resolve.Subject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Les joueurs concernés par une publication (cahier des charges, 7.4 et 7.6) : ce que le renvoi des arbres décide, sans serveur. */
class TreeResendTest {

    @BeforeEach
    fun forgetRequests() = TreeResend.clear()

    @Test
    fun `un premier état, des réglages ou une structure des groupes qui changent concernent tout le monde`() {
        TreeResend.afterPublication(null, state(personas))
        assertTrue(TreeResend.wantsEveryone())

        TreeResend.clear()
        TreeResend.afterPublication(state(personas), state(personas, Settings.DELIVERED.copy(deniedCommands = DeniedCommands.VISIBLE), generation = 2))
        assertTrue(TreeResend.wantsEveryone())

        TreeResend.clear()
        TreeResend.afterPublication(state(personas), state(withGroup("vip", personas.groups.getValue("vip").copy(weight = 81)), generation = 2))
        assertTrue(TreeResend.wantsEveryone())

        TreeResend.clear()
        TreeResend.afterPublication(state(personas), state(personas, generation = 2))
        assertFalse(TreeResend.wantsEveryone())
        assertEquals(0, TreeResend.pending().size)
    }

    @Test
    fun `une règle changée ne concerne que les joueurs dont l'ordre d'examen passe par son ensemble`() {
        val member = personas.groups.getValue("membre")
        val next = state(withGroup("membre", member.copy(rules = member.rules + ("minecraft:command.me" to Rule.ALLOW))), generation = 2)
        TreeResend.afterPublication(state(personas), next)
        val resolver = Resolver(next)
        val pending = TreeResend.pending()

        assertFalse(TreeResend.wantsEveryone())
        assertEquals(1, pending.size)
        // Joe est membre ; Bob et Alice en héritent par moderateur ; Olga, op de niveau 4, est membre d'office d'admin, qui en hérite aussi ; Nina n'a que default.
        assertTrue(TreeResend.concerns(Subject(uuidOf("Joe"), 0), resolver, pending))
        assertTrue(TreeResend.concerns(Subject(uuidOf("Bob"), 0), resolver, pending))
        assertTrue(TreeResend.concerns(Subject(uuidOf("Alice"), 0), resolver, pending))
        assertTrue(TreeResend.concerns(Subject(uuidOf("Olga"), 4), resolver, pending))
        assertFalse(TreeResend.concerns(Subject(uuidOf("Nina"), 0), resolver, pending))
    }

    @Test
    fun `une règle propre ou une entrée de joueur ne concerne que ce joueur`() {
        val jogger = personas.players.getValue(readableForm("Jogger"))
        val next = state(personas.copy(players = personas.players + (readableForm("Jogger") to jogger.copy(rules = emptyMap())) + (readableForm("Nina") to Player(groups = listOf("vip")))), generation = 2)
        TreeResend.afterPublication(state(personas), next)
        val resolver = Resolver(next)
        val pending = TreeResend.pending()

        assertTrue(TreeResend.concerns(Subject(uuidOf("Jogger"), 0), resolver, pending))
        assertTrue(TreeResend.concerns(Subject(uuidOf("Nina"), 0), resolver, pending))
        assertFalse(TreeResend.concerns(Subject(uuidOf("Joe"), 0), resolver, pending))
    }

    private fun withGroup(name: String, group: Group): Permissions = personas.copy(groups = personas.groups + (name to group))
}
