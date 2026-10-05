// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les clés du cahier des charges (2.4) : leur forme, leurs deux familles, ce qui les rend mal formées, ce qu'elles couvrent ; puis la conversion de 6.4. */
class KeysTest {

    @Test
    fun `la clé d'une commande s'écrit modid, command, puis les noms des nœuds`() {
        val location = commandKey("minecraft:command.teleport.targets.location")
        assertEquals("minecraft", location.namespace)
        assertEquals(listOf("teleport", "targets", "location"), location.segments)
        assertEquals("minecraft:command.teleport.targets.location", location.text)
        assertEquals("minecraft:command.op", Keys.command("minecraft", listOf("op")).text)
        assertEquals("aegisperms:command.aegis.reload", Keys.ADMINISTRATION.descendant("reload").text)
        assertEquals("homes:command.home", Keys.command("homes", emptyList()).descendant("home").text)
    }

    @Test
    fun `command et modid command sont les clés générales, sans nœud`() {
        val vanilla = commandKey("minecraft:command")
        assertSame(Keys.ALL_COMMANDS, Keys.parse("command"))
        assertNull(Keys.ALL_COMMANDS.namespace)
        assertTrue(Keys.ALL_COMMANDS.isGeneral)
        assertTrue(vanilla.isGeneral)
        assertFalse(vanilla.isNode)
        assertSame(Keys.ALL_COMMANDS, vanilla.directAncestor)
        assertNull(Keys.ALL_COMMANDS.directAncestor)
        assertTrue(commandKey("minecraft:command.op").isNode)
        assertFalse(commandKey("minecraft:command.op").isGeneral)
        assertThrows(IllegalStateException::class.java) { Keys.ALL_COMMANDS.descendant("op") }
    }

    @Test
    fun `un nom de nœud est exact et sensible à la casse`() {
        val facing = commandKey("minecraft:command.teleport.targets.facingEntity")
        assertEquals("facingEntity", facing.segments.last())
        assertNotEquals(facing, commandKey("minecraft:command.teleport.targets.facingentity"))
    }

    @Test
    fun `un point et un signe pour cent s'échappent dans un nom de nœud`() {
        val homeSet = Keys.command("homes", listOf("home.set"))
        assertEquals("homes:command.home%2Eset", homeSet.text)
        assertSame(homeSet, Keys.parse("homes:command.home%2Eset"))
        assertEquals(listOf("home.set"), homeSet.segments)
        assertEquals(listOf("50%"), commandKey("mod:command.50%25").segments)
        assertEquals("a%25b", Keys.escape("a%b"))
        assertEquals("teleport", Keys.escape("teleport"))
    }

    @Test
    fun `un échappement en minuscules ou inconnu fait une clé mal formée`() {
        assertEquals(KeyDefect.INVALID_ESCAPE, defectOf("homes:command.home%2eset"))
        assertEquals(KeyDefect.INVALID_ESCAPE, defectOf("mod:command.a%41"))
        assertEquals(KeyDefect.INVALID_ESCAPE, defectOf("mod:command.50%"))
    }

    @Test
    fun `une clé d'API se découpe aux barres obliques`() {
        val selectors = apiKey("minecraft:commands/entity_selectors")
        assertEquals("minecraft", selectors.namespace)
        assertEquals(listOf("commands", "entity_selectors"), selectors.segments)
        assertSame(Keys.ENTITY_SELECTORS, selectors)
        assertEquals("homes:home/limit", Keys.api("homes", listOf("home", "limit")).text)
        assertTrue(Keys.parse("homes:command/limit") is ApiKey)
    }

    @Test
    fun `une clé sans espace de noms autre que command est mal formée`() {
        assertEquals(KeyDefect.MISSING_NAMESPACE, defectOf("teleport"))
        assertEquals(KeyDefect.MISSING_NAMESPACE, defectOf("command.teleport"))
        assertEquals(KeyDefect.MISSING_NAMESPACE, defectOf(":command.op"))
        assertEquals(KeyDefect.MISSING_NAMESPACE, defectOf(""))
    }

    @Test
    fun `une clé d'API dont le chemin vaut command ou commence par command point est mal formée`() {
        val vanilla = commandKey("minecraft:command")
        assertEquals(KeyDefect.COMMAND_PATH, assertThrows(MalformedKeyException::class.java) { Keys.api("minecraft", listOf("command")) }.defect)
        assertSame(vanilla, Keys.parse("minecraft:command"))

        // Dans l'autre ordre : la clé d'API refusée ne prend pas la place de la clé de commande, lue après elle.
        assertEquals(KeyDefect.COMMAND_PATH, assertThrows(MalformedKeyException::class.java) { Keys.api("neverseen", listOf("command.limit")) }.defect)
        assertTrue(Keys.parse("neverseen:command.limit") is CommandKey)

        // Une clé d'API valide sous le segment `command` n'a pas de clé ancêtre : `foo:command` est une clé de commande.
        assertEquals(listOf("foo:command/bar"), apiKey("foo:command/bar").coveringChain.map { it.text })
    }

    @Test
    fun `chaque clé mal formée dit son défaut`() {
        val expected = mapOf(
            "teleport" to KeyDefect.MISSING_NAMESPACE,
            "Homes:home" to KeyDefect.INVALID_NAMESPACE,
            "homes:" to KeyDefect.EMPTY_PATH,
            "minecraft:command.teleport..targets" to KeyDefect.EMPTY_NODE_NAME,
            "minecraft:command.teleport." to KeyDefect.EMPTY_NODE_NAME,
            "minecraft:command.tele port" to KeyDefect.INVALID_NODE_NAME,
            "mod:command.50%" to KeyDefect.INVALID_ESCAPE,
            "homes:home//limit" to KeyDefect.EMPTY_SEGMENT,
            "homes:home/" to KeyDefect.EMPTY_SEGMENT,
            "homes:Home/Limit" to KeyDefect.INVALID_SEGMENT,
        )
        for ((text, defect) in expected) assertEquals(defect, defectOf(text), text)
        assertEquals(KeyDefect.entries.toSet() - KeyDefect.COMMAND_PATH, expected.values.toSet())
        assertNull(Keys.parseOrNull("pas une clé"))

        // Une clé bâtie depuis des noms de nœuds dit son défaut de la même façon.
        assertEquals(KeyDefect.INVALID_NODE_NAME, assertThrows(MalformedKeyException::class.java) { Keys.command("minecraft", listOf("tele port")) }.defect)
        assertEquals(KeyDefect.INVALID_NAMESPACE, assertThrows(MalformedKeyException::class.java) { Keys.command("Minecraft", emptyList()) }.defect)

        assertEquals("aegisperms.issue.malformed_key.invalid_escape", KeyDefect.INVALID_ESCAPE.translationKey)
    }

    @Test
    fun `un nom vide ou qui porte un blanc ne fait pas un nom de nœud`() {
        for (name in listOf("targets", "facingEntity", "home.set", "50%", "minecraft:max_minecart_speed")) assertTrue(Keys.isNodeName(name), name)
        for (name in listOf("", "player name", "tab\tname", "bell\u0007")) {
            assertFalse(Keys.isNodeName(name), name)
            assertThrows(MalformedKeyException::class.java) { Keys.command("homes", listOf("home", name)) }
        }
    }

    @Test
    fun `une clé couvre ses descendantes, jamais ses ancêtres`() {
        val teleport = commandKey("minecraft:command.teleport")
        val location = commandKey("minecraft:command.teleport.targets.location")
        assertTrue(teleport.covers(location))
        assertTrue(teleport.covers(teleport))
        assertFalse(location.covers(teleport))
        assertFalse(commandKey("minecraft:command.tele").covers(teleport))
        assertTrue(Keys.ALL_COMMANDS.covers(location))
        assertTrue(commandKey("minecraft:command").covers(location))
        assertFalse(commandKey("homes:command").covers(location))
        assertEquals(
            listOf("minecraft:command.teleport.targets.location", "minecraft:command.teleport.targets", "minecraft:command.teleport", "minecraft:command", "command"),
            location.coveringChain.map { it.text },
        )

        val limit = apiKey("homes:home/limit")
        assertTrue(apiKey("homes:home").covers(limit))
        assertFalse(limit.covers(apiKey("homes:home")))
        assertEquals(listOf("homes:home/limit", "homes:home"), limit.coveringChain.map { it.text })
    }

    @Test
    fun `une clé de commande ne couvre jamais une clé d'API, et aucune clé ne couvre un espace de noms entier`() {
        val selectors = apiKey("minecraft:commands/entity_selectors")
        assertEquals(listOf("minecraft:commands/entity_selectors", "minecraft:commands"), selectors.coveringChain.map { it.text })
        assertNull(apiKey("minecraft:commands").directAncestor)
        assertFalse(apiKey("otherhomes:home").covers(apiKey("homes:home/limit")))
        assertTrue(commandKey("minecraft:command.teleport").coveringChain.none { it.text == "minecraft:commands" })
    }

    @Test
    fun `command et aegisperms command ne couvrent pas les clés de aegis`() {
        val reload = commandKey("aegisperms:command.aegis.reload")
        assertTrue(reload.isAdministration)
        assertTrue(Keys.ADMINISTRATION.isAdministration)
        assertFalse(commandKey("aegisperms:command").isAdministration)
        assertFalse(commandKey("minecraft:command.aegis").isAdministration)
        assertFalse(Keys.ALL_COMMANDS.covers(reload))
        assertFalse(commandKey("aegisperms:command").covers(reload))
        assertTrue(Keys.ADMINISTRATION.covers(reload))
        assertEquals(listOf("aegisperms:command.aegis.reload", "aegisperms:command.aegis"), reload.coveringChain.map { it.text })

        // L'exception ne vaut que pour `/aegis` : une autre commande du mod reste couverte par les clés générales.
        assertTrue(Keys.ALL_COMMANDS.covers(commandKey("aegisperms:command.other")))
    }

    @Test
    fun `les clés du chemin vont de la racine au nœud, sans les clés générales`() {
        val rotation = commandKey("minecraft:command.teleport.targets.location.rotation")
        assertEquals(
            listOf("minecraft:command.teleport", "minecraft:command.teleport.targets", "minecraft:command.teleport.targets.location", "minecraft:command.teleport.targets.location.rotation"),
            rotation.pathKeys.map { it.text },
        )
        assertSame(rotation, rotation.pathKeys.last())
        assertTrue(Keys.ALL_COMMANDS.pathKeys.isEmpty())
        assertTrue(commandKey("minecraft:command").pathKeys.isEmpty())
    }

    @Test
    fun `deux lectures d'une même clé rendent le même objet`() {
        assertSame(Keys.parse("minecraft:command.kick"), Keys.parse("minecraft:command.kick"))
        assertSame(Keys.command("minecraft", listOf("kick")), Keys.parse("minecraft:command.kick"))
        assertSame(Keys.api("homes", listOf("home", "limit")), Keys.parse("homes:home/limit"))
        assertEquals("minecraft:command.kick", Keys.parse("minecraft:command.kick").toString())
    }

    @Test
    fun `une chaîne à points de l'API de lucko se lit comme un identifiant`() {
        assertSame(Keys.parse("essentialcommands:home/set"), Keys.fromDottedString("essentialcommands.home.set"))
        assertSame(Keys.parse("homes:home"), Keys.fromDottedString("homes.home"))
        assertSame(Keys.parse("mymod:home/set"), Keys.fromDottedString("mymod.home/set"))
        assertSame(Keys.parse("foo:command/bar"), Keys.fromDottedString("foo.command.bar"))
    }

    @Test
    fun `une chaîne à points qui ne donne pas un identifiant valide ne donne pas de clé`() {
        val invalid = listOf(
            "essentialcommands", "", ".home", "essential..home", "essential.home.", "Essential.home", "essential.Home", "essential.ho me",
            "minecraft:commands/entity_selectors", "mod:a.b", "foo.command",
        )
        for (dotted in invalid) assertNull(Keys.fromDottedString(dotted), dotted)
    }

    private fun commandKey(text: String): CommandKey = Keys.parse(text) as CommandKey

    private fun apiKey(text: String): ApiKey = Keys.parse(text) as ApiKey

    private fun defectOf(text: String): KeyDefect = assertThrows(MalformedKeyException::class.java) { Keys.parse(text) }.defect
}
