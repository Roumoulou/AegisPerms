// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** L'ensemble de règles du cahier des charges (2.1), et la précision qui y décide (2.6). */
class RuleSetTest {

    @Test
    fun `dans un ensemble, la règle la plus précise qui couvre la clé répond`() {
        val rules = RuleSet.compile(mapOf("command" to Rule.DENY, "minecraft:command.help" to Rule.ALLOW))
        val help = Keys.parse("minecraft:command.help")

        assertSame(help, rules.mostPrecise(help))
        assertSame(help, rules.mostPrecise(Keys.parse("minecraft:command.help.command")))
        assertSame(Keys.ALL_COMMANDS, rules.mostPrecise(Keys.parse("minecraft:command.kick")))
        assertEquals(Rule.ALLOW, rules[help])
        assertEquals(Rule.DENY, rules[Keys.ALL_COMMANDS])
    }

    @Test
    fun `un ensemble qui ne couvre pas la clé n'en dit rien`() {
        val rules = RuleSet.compile(mapOf("minecraft:command.teleport.targets" to Rule.ALLOW))

        assertNull(rules.mostPrecise(Keys.parse("minecraft:command.teleport")))
        assertNull(rules.mostPrecise(Keys.parse("homes:command.home")))
        assertNull(RuleSet.compile(emptyMap()).mostPrecise(Keys.parse("minecraft:command.teleport")))
    }

    @Test
    fun `une règle sur command ne répond ni d'une clé d'API ni des clés de aegis`() {
        val rules = RuleSet.compile(mapOf("command" to Rule.ALLOW, "aegisperms:command" to Rule.ALLOW, "minecraft:commands" to Rule.DENY))

        assertNull(rules.mostPrecise(Keys.parse("aegisperms:command.aegis.reload")))
        assertNull(rules.mostPrecise(Keys.parse("homes:home/limit")))
        assertSame(Keys.parse("minecraft:commands"), rules.mostPrecise(Keys.ENTITY_SELECTORS))
    }

    @Test
    fun `une clé mal formée ne se compile pas`() {
        assertThrows(MalformedKeyException::class.java) { RuleSet.compile(mapOf("teleport" to Rule.ALLOW)) }
    }
}
