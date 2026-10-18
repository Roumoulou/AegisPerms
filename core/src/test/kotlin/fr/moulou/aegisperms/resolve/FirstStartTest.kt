// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Permissions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ce que donnent les deux groupes du premier démarrage (cahier des charges, 2.10). La console et le RCON, jamais jugés, ne passent pas par le cœur. */
class FirstStartTest {

    private val resolver = resolver(Permissions.DELIVERED)

    private val reload = commandKey("aegisperms:command.aegis.reload")

    @Test
    fun `un joueur non-op n'a que les deux commandes de default`() {
        val player = subject("Nina")

        assertEquals(listOf("default"), resolver.order(player).groups.map { it.name })
        for (opened in listOf("help", "msg.targets.message")) assertEquals(NodeJudgment.ALLOWED, resolver.judgment(player, commandKey("minecraft:command.$opened")), opened)
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("minecraft:command.me.action")), "me ne s'ouvre plus par défaut")
        // Ni ce que vanilla ouvrait à tous, ni aucune commande d'un mod.
        for (closed in listOf("list", "teammsg", "trigger", "random.value", "gamemode")) assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("minecraft:command.$closed")), closed)
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(player, commandKey("homes:command.home")))
        assertEquals(Decision.UNSET, resolver.decision(player, reload))
    }

    @Test
    fun `un op de niveau 4 a toutes les commandes, et aegis par l'étape 0`() {
        val owner = subject("Olga", opLevel = 4)

        assertEquals(listOf("admin", "default"), resolver.order(owner).groups.map { it.name })
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(owner, commandKey("minecraft:command.stop")))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(owner, commandKey("homes:command.home")))
        assertEquals(Decision.ALLOW, resolver.decision(owner, reload))
        assertTrue(resolver.explain(owner, reload).step0)
    }

    @Test
    fun `un op de niveau 1 à 3 retombe sur default`() {
        for (opLevel in 1..3) {
            val op = subject("Otto", opLevel)

            assertEquals(listOf("default"), resolver.order(op).groups.map { it.name }, "niveau $opLevel")
            assertEquals(NodeJudgment.ALLOWED, resolver.judgment(op, commandKey("minecraft:command.help")), "niveau $opLevel")
            assertEquals(NodeJudgment.HIDDEN, resolver.judgment(op, commandKey("minecraft:command.gamemode")), "niveau $opLevel")
            assertEquals(Decision.UNSET, resolver.decision(op, reload), "niveau $opLevel")
        }
    }

    @Test
    fun `sans le groupe admin, l'étape 0 garde aegis aux ops d'au moins admin-level`() {
        val withoutAdmin = resolver(permissions(Permissions.DELIVERED.groups - "admin"))
        val owner = subject("Olga", opLevel = 4)

        assertEquals(Decision.ALLOW, withoutAdmin.decision(owner, reload))
        assertEquals(NodeJudgment.HIDDEN, withoutAdmin.judgment(owner, commandKey("minecraft:command.stop")))
    }
}
