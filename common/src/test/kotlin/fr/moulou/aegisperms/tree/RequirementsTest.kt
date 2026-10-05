// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Predicate

/** Ce que Brigadier seul dit de l'exigence d'un nœud (cahier des charges, 2.8). */
class RequirementsTest {

    @Test
    fun `le prédicat par défaut de Brigadier est une seule instance pour les littéraux et les arguments`() {
        val ofLiteral = literal<Any>("a").build().requirement
        val ofArgument = argument<Any, Int>("b", IntegerArgumentType.integer()).build().requirement

        assertSame(ofLiteral, ofArgument)
        assertTrue(Requirements.isDefault(ofLiteral))
        assertTrue(Requirements.isDefault(ofArgument))
        assertFalse(Requirements.isDefault(Predicate<Any> { true }))
        assertFalse(Requirements.isDefault(literal<Any>("c").requires { true }.build().requirement))
    }
}
