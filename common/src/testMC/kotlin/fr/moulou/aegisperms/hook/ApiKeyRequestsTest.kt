// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.ApiKeyEntry
import fr.moulou.aegisperms.model.Keys
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Les clés d'API demandées, leurs voies au catalogue et les chaînes à points (cahier des charges, 4.6, 6.1 et 6.4). */
class ApiKeyRequestsTest {

    companion object {
        private const val DATE = "2026-10-07T00:00:00Z"
    }

    @BeforeEach
    fun forgetRequests() = ApiKeyRequests.clear()

    @Test
    fun `une clé venue par plusieurs voies porte ses voies jointes, dans l'ordre atome, Fabric, lucko`() {
        val key = Keys.api("homes", listOf("home", "limit"))

        assertTrue(ApiKeyRequests.request(key, ApiRoute.LUCKO))
        assertTrue(ApiKeyRequests.request(key, ApiRoute.ATOM))
        assertFalse(ApiKeyRequests.request(key, ApiRoute.ATOM))
        assertEquals("atom+lucko", ApiKeyRequests.typeOf(key))

        ApiKeyRequests.seed(mapOf(key.text to ApiKeyEntry("fabric", DATE)))
        assertEquals(mapOf(key.text to "atom+fabric+lucko"), ApiKeyRequests.types())
        assertFalse(ApiKeyRequests.request(key, ApiRoute.FABRIC))
    }

    @Test
    fun `le catalogue repris ne garde que des clés d'API, des voies connues et des chaînes refusées`() {
        ApiKeyRequests.seed(
            mapOf(
                "minecraft:command.teleport" to ApiKeyEntry("atom", DATE),
                "homes:home/set" to ApiKeyEntry("lucko+inconnue", DATE),
                "homes:home/warp" to ApiKeyEntry("inconnue", DATE),
                "mal formée" to ApiKeyEntry("atom", DATE),
                "essentialcommands" to ApiKeyEntry("lucko", DATE, refused = true),
            ),
        )

        assertEquals(mapOf("homes:home/set" to "lucko"), ApiKeyRequests.types())
        assertEquals(setOf(Keys.api("homes", listOf("home", "set"))), ApiKeyRequests.keys())
        assertEquals(mapOf("essentialcommands" to "lucko"), ApiKeyRequests.refusedTypes())
        assertNull(ApiKeyRequests.keyOfDottedString("essentialcommands"))
    }

    @Test
    fun `une chaîne à points se lit comme un identifiant, et celle qui n'en fait pas un est refusée une fois`() {
        var notified = 0
        ApiKeyRequests.onNewKey = { notified++ }

        assertSame(Keys.api("essentialcommands", listOf("home", "set")), ApiKeyRequests.keyOfDottedString("essentialcommands.home.set"))
        // Un chemin `command` à barres obliques est une clé d'API, distincte de la clé de commande (2.4).
        assertSame(Keys.api("minecraft", listOf("command", "teleport")), ApiKeyRequests.keyOfDottedString("minecraft.command.teleport"))
        assertNull(ApiKeyRequests.keyOfDottedString("essentialcommands"))
        assertNull(ApiKeyRequests.keyOfDottedString("essentialcommands"))
        assertNull(ApiKeyRequests.keyOfDottedString("Homes.set"))
        assertEquals(mapOf("essentialcommands" to "lucko", "Homes.set" to "lucko"), ApiKeyRequests.refusedTypes())
        assertEquals(2, notified)

        ApiKeyRequests.onNewKey = null
    }

    @Test
    fun `un identifiant du jeu donne sa clé d'API, et un chemin de commande n'en donne pas`() {
        assertSame(Keys.ENTITY_SELECTORS, ApiKeyRequests.keyOf(Identifier.fromNamespaceAndPath("minecraft", "commands/entity_selectors"), ApiRoute.ATOM))
        assertNull(ApiKeyRequests.keyOf(Identifier.fromNamespaceAndPath("minecraft", "command.teleport"), ApiRoute.FABRIC))
        assertNull(ApiKeyRequests.keyOf(Identifier.fromNamespaceAndPath("minecraft", "command"), ApiRoute.ATOM))
        assertEquals(mapOf("minecraft:command.teleport" to "fabric", "minecraft:command" to "atom"), ApiKeyRequests.refusedTypes(), "un identifiant qui ne fait pas une clé entre au catalogue, refusé, avec sa voie")
    }
}
