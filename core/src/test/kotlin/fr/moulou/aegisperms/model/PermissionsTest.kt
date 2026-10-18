// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les droits livrés au premier démarrage (cahier des charges, 2.10). */
class PermissionsTest {

    @Test
    fun `le premier démarrage livre default et admin`() {
        val delivered = Permissions.DELIVERED
        assertEquals(1, delivered.schemaVersion)
        assertEquals(setOf("default", "admin"), delivered.groups.keys)
        assertTrue(delivered.players.isEmpty())

        val defaultGroup = delivered.groups.getValue("default")
        assertEquals(0, defaultGroup.weight)
        assertTrue(defaultGroup.parents.isEmpty())
        assertNull(defaultGroup.includeOps)
        assertEquals(
            mapOf("command" to Rule.DENY, "minecraft:command.help" to Rule.ALLOW, "minecraft:command.msg" to Rule.ALLOW),
            defaultGroup.rules,
        )

        val admin = delivered.groups.getValue("admin")
        assertEquals(100, admin.weight)
        assertTrue(admin.parents.isEmpty())
        assertEquals(OpLevel.OWNERS, admin.includeOps)
        assertEquals(mapOf("command" to Rule.ALLOW), admin.rules)
    }

    @Test
    fun `les règles livrées portent des clés bien formées`() {
        for (group in Permissions.DELIVERED.groups.values) for (key in group.rules.keys) assertNotNull(Keys.parseOrNull(key), key)
    }
}
