// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.UUID

/** Le renommage d'un joueur du fichier (cahier des charges, 4.4) : sa forme lisible suit son pseudo, à la même place. */
class RenamingTest {

    private val bob: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000001")
    private val joe: UUID = UUID.fromString("0e000000-0000-4000-8000-000000000001")

    private val permissions = Permissions(
        Permissions.SCHEMA_VERSION,
        groups = mapOf(Group.DEFAULT to Group(), "vip" to Group(weight = 80)),
        players = mapOf(ReadableForm.of("Bob", bob) to Player(groups = listOf("vip")), ReadableForm.of("Joe", joe) to Player(note = "joe")),
    )

    @Test
    fun `la forme lisible suit le nouveau pseudo, à la même place et avec le même contenu`() {
        val renamed = Renaming.renamed(permissions, bob, "Bobby")
        assertEquals(listOf(ReadableForm.of("Bobby", bob), ReadableForm.of("Joe", joe)), renamed.players.keys.toList())
        assertEquals(Player(groups = listOf("vip")), renamed.players[ReadableForm.of("Bobby", bob)])
    }

    @Test
    fun `un joueur inconnu du fichier, ou déjà sous ce pseudo, ne change rien`() {
        assertSame(permissions, Renaming.renamed(permissions, UUID.fromString("0a000000-0000-4000-8000-000000000003"), "Nina"))
        assertSame(permissions, Renaming.renamed(permissions, bob, "Bob"))
    }
}
