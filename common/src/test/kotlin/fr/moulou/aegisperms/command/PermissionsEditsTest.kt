// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.command.PermissionsEdits.Refusal
import fr.moulou.aegisperms.command.PermissionsEdits.RuleVerb
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.ReadableForm
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.text.TextKeys.Command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Les modifications des droits (cahier des charges, 5.3), comme des transformations pures : ce qu'elles font, et ce qu'elles refusent avant d'écrire. */
class PermissionsEditsTest {

    companion object {
        private val BOB: UUID = UUID.fromString("0b000000-0000-4000-8000-000000000001")
        private val BOB_FORM = ReadableForm.of("Bob", BOB)
        private val TELEPORT = Keys.parse("minecraft:command.teleport")
        private val KICK = Keys.parse("minecraft:command.kick")
    }

    private val permissions = Permissions(
        Permissions.SCHEMA_VERSION,
        groups = mapOf(
            "admin" to Group(weight = 100, parents = listOf("moderateur"), includeOps = OpLevel.OWNERS, rules = mapOf("command" to Rule.ALLOW), note = "les admins"),
            Group.DEFAULT to Permissions.DELIVERED.groups.getValue(Group.DEFAULT),
            "membre" to Group(weight = 10, rules = mapOf("minecraft:command.teleport" to Rule(RuleValue.ALLOW, note = "la tp"))),
            "moderateur" to Group(weight = 50, parents = listOf("membre"), rules = mapOf("minecraft:command.kick" to Rule.ALLOW)),
        ),
        players = mapOf(BOB_FORM to Player(groups = listOf("moderateur"))),
    )

    @Test
    fun `un groupe se crée avec son poids, et un nom mal formé ou déjà pris se refuse`() {
        val created = PermissionsEdits.createGroup(permissions, "vip", 80)
        assertEquals(Group(weight = 80), created.groups["vip"])
        assertEquals(Command.GROUP_NAME_MALFORMED, assertThrows(Refusal::class.java) { PermissionsEdits.createGroup(permissions, "Vip", 1) }.key)
        assertEquals(Command.GROUP_EXISTS, assertThrows(Refusal::class.java) { PermissionsEdits.createGroup(permissions, "membre", 1) }.key)
    }

    @Test
    fun `un groupe que rien ne référence se supprime, les autres disent ce qui les retient, et default jamais`() {
        val freed = PermissionsEdits.deleteGroup(PermissionsEdits.createGroup(permissions, "vip", 80), "vip")
        assertEquals(permissions, freed)
        val retained = assertThrows(Refusal::class.java) { PermissionsEdits.deleteGroup(permissions, "moderateur") }
        assertEquals(Command.GROUP_REFERENCED, retained.key)
        assertEquals("admin, $BOB_FORM", retained.arguments[1])
        assertEquals(Command.GROUP_DEFAULT_PROTECTED, assertThrows(Refusal::class.java) { PermissionsEdits.deleteGroup(permissions, Group.DEFAULT) }.key)
        assertEquals(Command.GROUP_UNKNOWN, assertThrows(Refusal::class.java) { PermissionsEdits.deleteGroup(permissions, "absent") }.key)
    }

    @Test
    fun `renommer un groupe suit ses références, dans les parents et chez les joueurs`() {
        val renamed = PermissionsEdits.renameGroup(permissions, "moderateur", "modo")
        assertFalse("moderateur" in renamed.groups)
        assertEquals(listOf("modo"), renamed.groups.getValue("admin").parents)
        assertEquals(listOf("modo"), renamed.players.getValue(BOB_FORM).groups)
        assertEquals(permissions.groups.getValue("moderateur"), renamed.groups.getValue("modo"))
        assertEquals(listOf("admin", Group.DEFAULT, "membre", "modo"), renamed.groups.keys.toList())
        assertEquals(Command.GROUP_NAME_TAKEN, assertThrows(Refusal::class.java) { PermissionsEdits.renameGroup(permissions, "membre", "admin") }.key)
        assertEquals(Command.GROUP_DEFAULT_PROTECTED, assertThrows(Refusal::class.java) { PermissionsEdits.renameGroup(permissions, Group.DEFAULT, "base") }.key)
    }

    @Test
    fun `cloner un groupe copie le poids, les parents, les règles et la note, mais ni include-ops ni les membres`() {
        val cloned = PermissionsEdits.cloneGroup(permissions, "admin", "staff")
        assertEquals(permissions.groups.getValue("admin").copy(includeOps = null), cloned.groups.getValue("staff"))
        assertEquals(permissions.players, cloned.players)
    }

    @Test
    fun `un parent s'ajoute une fois, et un cycle, le groupe lui-même ou default se refusent`() {
        val withVip = PermissionsEdits.createGroup(permissions, "vip", 80)
        val withParent = PermissionsEdits.addParent(withVip, "membre", "vip")
        assertEquals(listOf("vip"), withParent.groups.getValue("membre").parents)
        assertEquals(withParent, PermissionsEdits.addParent(withParent, "membre", "vip"))
        // admin hérite de moderateur, qui hérite de membre : membre ne peut pas hériter d'admin, ni vip de membre une fois membre sous vip.
        assertEquals(Command.GROUP_PARENT_CYCLE, assertThrows(Refusal::class.java) { PermissionsEdits.addParent(permissions, "membre", "admin") }.key)
        assertEquals(Command.GROUP_PARENT_CYCLE, assertThrows(Refusal::class.java) { PermissionsEdits.addParent(withParent, "vip", "membre") }.key)
        assertEquals(Command.GROUP_PARENT_SELF, assertThrows(Refusal::class.java) { PermissionsEdits.addParent(permissions, "membre", "membre") }.key)
        assertEquals(Command.GROUP_PARENT_DEFAULT, assertThrows(Refusal::class.java) { PermissionsEdits.addParent(permissions, "membre", Group.DEFAULT) }.key)
        assertEquals(Command.GROUP_PARENT_ABSENT, assertThrows(Refusal::class.java) { PermissionsEdits.removeParent(permissions, "membre", "admin") }.key)
        assertEquals(emptyList<String>(), PermissionsEdits.removeParent(permissions, "admin", "moderateur").groups.getValue("admin").parents)
    }

    @Test
    fun `les verbes d'une règle écrivent sa valeur et sa visibilité, gardent sa note, et unset la retire`() {
        val rules = permissions.groups.getValue("membre").rules
        assertEquals(Rule(RuleValue.DENY, note = "la tp"), RuleVerb.DENY.apply(rules, TELEPORT, "membre").getValue(TELEPORT.text))
        assertEquals(Rule(RuleValue.DENY, visible = true, note = "la tp"), RuleVerb.DENY_VISIBLE.apply(rules, TELEPORT, "membre").getValue(TELEPORT.text))
        assertEquals(Rule(RuleValue.DENY, visible = false, note = "la tp"), RuleVerb.DENY_HIDDEN.apply(rules, TELEPORT, "membre").getValue(TELEPORT.text))
        assertEquals(Rule.ALLOW, RuleVerb.ALLOW.apply(rules, KICK, "membre").getValue(KICK.text))
        assertTrue(RuleVerb.UNSET.apply(rules, TELEPORT, "membre").isEmpty())
        assertEquals(Command.RULE_ABSENT, assertThrows(Refusal::class.java) { RuleVerb.UNSET.apply(rules, KICK, "membre") }.key)
    }

    @Test
    fun `une note se pose sur un groupe ou sur l'une de ses règles, et un texte blanc l'efface`() {
        assertEquals("les modos", PermissionsEdits.setGroupNote(permissions, "moderateur", "les modos", null).groups.getValue("moderateur").note)
        assertNull(PermissionsEdits.setGroupNote(permissions, "admin", " ", null).groups.getValue("admin").note)
        assertEquals("expulse", PermissionsEdits.setGroupNote(permissions, "moderateur", "expulse", KICK).groups.getValue("moderateur").rules.getValue(KICK.text).note)
        assertEquals(Command.RULE_ABSENT, assertThrows(Refusal::class.java) { PermissionsEdits.setGroupNote(permissions, "membre", "x", KICK) }.key)
    }

    @Test
    fun `un joueur entre au fichier à sa première règle et en sort quand il n'a plus rien`() {
        val nina = UUID.fromString("0a000000-0000-4000-8000-000000000002")
        val ninaForm = ReadableForm.of("Nina", nina)
        val entered = PermissionsEdits.setPlayerRule(permissions, nina, ninaForm, KICK, RuleVerb.ALLOW)
        assertEquals(Player(rules = mapOf(KICK.text to Rule.ALLOW)), entered.players[ninaForm])
        val left = PermissionsEdits.setPlayerRule(entered, nina, ninaForm, KICK, RuleVerb.UNSET)
        assertFalse(ninaForm in left.players)
        assertEquals(Command.RULE_ABSENT, assertThrows(Refusal::class.java) { PermissionsEdits.setPlayerRule(permissions, nina, ninaForm, KICK, RuleVerb.UNSET) }.key)
    }

    @Test
    fun `un joueur du fichier garde sa forme lisible, rejoint et quitte un groupe, et default se refuse`() {
        val renamedBob = PlayerTarget(BOB, "Bobby")
        val joined = PermissionsEdits.addPlayerGroup(permissions, renamedBob.uuid, renamedBob.readableForm, "membre")
        assertEquals(listOf("moderateur", "membre"), joined.players.getValue(BOB_FORM).groups)
        assertFalse(renamedBob.readableForm in joined.players)
        assertEquals(joined, PermissionsEdits.addPlayerGroup(joined, BOB, BOB_FORM, "membre"))
        val left = PermissionsEdits.removePlayerGroup(joined, BOB, BOB_FORM, "moderateur")
        assertEquals(listOf("membre"), left.players.getValue(BOB_FORM).groups)
        assertEquals(Command.PLAYER_GROUP_ABSENT, assertThrows(Refusal::class.java) { PermissionsEdits.removePlayerGroup(permissions, BOB, BOB_FORM, "membre") }.key)
        assertEquals(Command.PLAYER_GROUP_DEFAULT, assertThrows(Refusal::class.java) { PermissionsEdits.addPlayerGroup(permissions, BOB, BOB_FORM, Group.DEFAULT) }.key)
        assertEquals(Command.GROUP_UNKNOWN, assertThrows(Refusal::class.java) { PermissionsEdits.addPlayerGroup(permissions, BOB, BOB_FORM, "absent") }.key)
        assertEquals(BOB_FORM, PermissionsEdits.readableFormOf(permissions, BOB))
    }
}
