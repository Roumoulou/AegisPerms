// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Les clés d'API demandées et leur type au catalogue (cahier des charges, 4.6 et 6.1). */
class ApiKeyRequestsTest {

    @BeforeEach
    fun forgetRequests() = ApiKeyRequests.clear()

    @Test
    fun `une clé venue par plusieurs voies porte ses voies jointes, dans l'ordre atome, Fabric, lucko`() {
        val key = Keys.api("homes", listOf("home", "limit"))

        assertTrue(ApiKeyRequests.request(key, ApiRoute.LUCKO))
        assertTrue(ApiKeyRequests.request(key, ApiRoute.ATOM))
        assertFalse(ApiKeyRequests.request(key, ApiRoute.ATOM))
        assertEquals("atom+lucko", ApiKeyRequests.typeOf(key))

        ApiKeyRequests.seed(mapOf(key.text to "fabric"))
        assertEquals(mapOf(key.text to "atom+fabric+lucko"), ApiKeyRequests.types())
        assertFalse(ApiKeyRequests.request(key, ApiRoute.FABRIC))
    }

    @Test
    fun `le catalogue repris ne garde que des clés d'API et des voies connues`() {
        ApiKeyRequests.seed(mapOf("minecraft:command.teleport" to "atom", "homes:home/set" to "lucko+inconnue", "homes:home/warp" to "inconnue", "mal formée" to "atom"))

        assertEquals(mapOf("homes:home/set" to "lucko"), ApiKeyRequests.types())
        assertEquals(setOf(Keys.api("homes", listOf("home", "set"))), ApiKeyRequests.keys())
    }
}
