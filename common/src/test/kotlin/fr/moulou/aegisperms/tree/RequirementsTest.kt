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

    @Test
    fun `l'exigence d'un chemin sans exigence est le prédicat par défaut`() {
        val path = listOf(literal<Any>("whitelist").build(), literal<Any>("list").build())

        assertTrue(Requirements.isDefault(Requirements.ofPath(path)))
    }

    @Test
    fun `une seule exigence sur le chemin reste elle-même, donc lisible`() {
        val whitelist = literal<Any>("whitelist").requires(ADMINS).build()
        val add = literal<Any>("add").build()

        assertSame(ADMINS, Requirements.ofPath(listOf(whitelist, add)))
        assertSame(ADMINS, Requirements.ofPath(listOf(whitelist, add, whitelist)))
    }

    @Test
    fun `plusieurs exigences sur le chemin se conjuguent`() {
        val datapack = literal<Any>("datapack").requires(GAMEMASTERS).build()
        val create = literal<Any>("create").requires(ADMINS).build()
        val both = Requirements.ofPath(listOf(datapack, create))

        assertFalse(both.test(2))
        assertTrue(both.test(3))
        assertFalse(Requirements.ofPath(listOf(create, datapack)).test(2))
    }
}
