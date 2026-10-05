// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Le témoin de l'étage 0 de common : Brigadier seul suffit à bâtir et à parcourir un arbre de commandes, et rien du jeu n'est sur le classpath. */
class SyntheticTreeWitnessTest {

    @Test
    fun `un arbre Brigadier synthétique se bâtit et se parcourt sans Minecraft`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(
            literal<Any>("teleport").then(argument<Any, String>("targets", StringArgumentType.word()).then(argument<Any, String>("location", StringArgumentType.word()).executes { 1 })),
        )

        assertEquals("location", dispatcher.findNode(listOf("teleport", "targets", "location")).name)
        assertThrows(ClassNotFoundException::class.java) { Class.forName("net.minecraft.SharedConstants") }
    }
}
