// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Le témoin de l'étage 0 du cœur : il prouve que l'étage tourne, et que le cœur se teste sans rien du jeu sur son classpath. */
class CoreWitnessTest {

    @Test
    fun `le cœur se teste sans Minecraft, sans Fabric et sans Brigadier`() {
        listOf("net.minecraft.SharedConstants", "net.fabricmc.loader.api.FabricLoader", "com.mojang.brigadier.CommandDispatcher").forEach { className ->
            assertThrows(ClassNotFoundException::class.java) { Class.forName(className) }
        }
    }
}
