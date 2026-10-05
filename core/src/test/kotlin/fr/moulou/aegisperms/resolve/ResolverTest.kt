// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.key
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.silentDefault
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.Fixtures.uuidOf
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.DeniedCommands
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner
import fr.moulou.aegisperms.model.RuleValue
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La résolution du cahier des charges (2.6) et le jugement d'un nœud (2.7), règle par règle ; puis les cas de la lignée (1.6). */
class ResolverTest {

    /** Les douze cas du test du prototype de 2025, transposés en clés d'aujourd'hui : celui de Jogger est le défaut qu'il ne voyait pas. */
    private val prototype: Permissions = permissions(
        groups = mapOf(
            silentDefault,
            "membre" to Group(
                weight = 10,
                rules = mapOf("minecraft:command.help" to Rule.ALLOW, "minecraft:command.teleport" to Rule.ALLOW, "minecraft:command.teleport.targets.location" to Rule.DENY),
            ),
            "vip" to Group(weight = 80, rules = mapOf("homes:command.sethome" to Rule.ALLOW, "minecraft:command.teleport.targets.location.rotation" to Rule.ALLOW)),
            "moderateur" to Group(weight = 50, parents = listOf("membre"), rules = mapOf("minecraft:command.kick" to Rule.ALLOW, "minecraft:command.teleport" to Rule.DENY)),
            "admin" to Group(
                weight = 100,
                parents = listOf("moderateur"),
                rules = mapOf("command" to Rule.ALLOW, "minecraft:command.kick.target_admin" to Rule.DENY, "minecraft:command.teleport.targets.location" to Rule.ALLOW),
            ),
        ),
        players = mapOf(
            readableForm("Jogger") to Player(groups = listOf("membre"), rules = mapOf("minecraft:command.teleport.targets.location.rotation" to Rule.ALLOW)),
            readableForm("Joe") to Player(groups = listOf("membre", "vip"), rules = mapOf("minecraft:command.teleport.targets.location" to Rule.ALLOW)),
            readableForm("Alice") to Player(groups = listOf("admin")),
        ),
    )

    @Test
    fun `l'étape 0 ouvre aegis aux ops d'au moins admin-level, quoi que disent les règles, et pas en dessous`() {
        val resolver = resolver(defaultWith("aegisperms:command.aegis" to Rule.DENY), Settings.DELIVERED.copy(adminLevel = OpLevel.ADMINS))
        val reload = commandKey("aegisperms:command.aegis.reload")

        assertEquals(Decision.ALLOW, resolver.decision(subject("Op", opLevel = 3), reload))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(subject("Op", opLevel = 4), reload))
        assertEquals(Decision.DENY_HIDDEN, resolver.decision(subject("Op", opLevel = 2), reload))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(subject("Op", opLevel = 2), reload))
    }

    @Test
    fun `l'étape 0 ne vaut que pour les clés de aegis`() {
        val resolver = resolver(defaultWith("command" to Rule.DENY))
        val owner = subject("Op", opLevel = 4)

        assertEquals(Decision.DENY_HIDDEN, resolver.decision(owner, key("minecraft:command.stop")))
        assertEquals(Decision.DENY_HIDDEN, resolver.decision(owner, key("aegisperms:command.other")))
        assertEquals(Decision.ALLOW, resolver.decision(owner, Keys.ADMINISTRATION))
    }

    @Test
    fun `sans règle qui réponde, la décision est unset`() {
        val resolver = resolver(permissions(mapOf(silentDefault)))
        val anyone = subject("Nina")

        assertEquals(Decision.UNSET, resolver.decision(anyone, key("minecraft:command.op")))
        assertEquals(NodeJudgment.UNSET, resolver.judgment(anyone, commandKey("minecraft:command.op")))
        assertEquals(Decision.UNSET, resolver.decision(anyone, Keys.ENTITY_SELECTORS))
    }

    @Test
    fun `la priorité passe avant la précision`() {
        // command à allow sur admin l'emporte sur teleport à deny de moderateur, dont admin hérite.
        assertEquals(Decision.ALLOW, resolver(personas).decision(subject("Alice"), key("minecraft:command.teleport")))
    }

    @Test
    fun `à l'intérieur d'un ensemble, la précision décide`() {
        val resolver = resolver(defaultWith("command" to Rule.DENY, "minecraft:command.help" to Rule.ALLOW))
        val anyone = subject("Nina")

        assertEquals(Decision.ALLOW, resolver.decision(anyone, key("minecraft:command.help")))
        assertEquals(Decision.DENY_HIDDEN, resolver.decision(anyone, key("minecraft:command.kick")))
    }

    @Test
    fun `les règles propres du sujet passent avant ses groupes`() {
        val permissions = personas.copy(players = personas.players + (readableForm("Nina") to Player(rules = mapOf("minecraft:command.help" to Rule.DENY))))

        assertEquals(Decision.DENY_HIDDEN, resolver(permissions).decision(subject("Nina"), key("minecraft:command.help")))
        assertEquals(Decision.ALLOW, resolver(permissions).decision(subject("Nina"), key("minecraft:command.msg")))
    }

    @Test
    fun `un groupe écrit sans poids répond avant default`() {
        val permissions = permissions(
            groups = Permissions.DELIVERED.groups + ("vip" to Group(rules = mapOf("minecraft:command.list" to Rule.ALLOW))),
            players = mapOf(readableForm("Joe") to Player(groups = listOf("vip"))),
        )

        // default refuse command au même poids, 0, et son nom précède vip : c'est pourtant vip qui répond.
        assertEquals(NodeJudgment.ALLOWED, resolver(permissions).judgment(subject("Joe"), commandKey("minecraft:command.list")))
    }

    @Test
    fun `un refus est masqué ou visible selon sa règle, sinon selon denied-commands`() {
        val permissions = defaultWith(
            "minecraft:command.seed" to Rule.DENY,
            "minecraft:command.list" to Rule(RuleValue.DENY, visible = true),
            "minecraft:command.me" to Rule(RuleValue.DENY, visible = false),
        )
        val hiddenByDefault = resolver(permissions)
        val visibleByDefault = resolver(permissions, Settings.DELIVERED.copy(deniedCommands = DeniedCommands.VISIBLE))
        val anyone = subject("Nina")

        assertEquals(NodeJudgment.HIDDEN, hiddenByDefault.judgment(anyone, commandKey("minecraft:command.seed")))
        assertEquals(NodeJudgment.REFUSED, visibleByDefault.judgment(anyone, commandKey("minecraft:command.seed")))
        assertEquals(NodeJudgment.REFUSED, hiddenByDefault.judgment(anyone, commandKey("minecraft:command.list")))
        assertEquals(NodeJudgment.HIDDEN, visibleByDefault.judgment(anyone, commandKey("minecraft:command.me")))
    }

    @Test
    fun `un refus masqué ferme tout ce qui descend de sa clé, même un allow posé plus bas`() {
        val resolver = resolver(defaultWith("minecraft:command.teleport" to Rule.DENY, "minecraft:command.teleport.targets" to Rule.ALLOW))
        val anyone = subject("Nina")

        assertEquals(Decision.ALLOW, resolver.decision(anyone, key("minecraft:command.teleport.targets")))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(anyone, commandKey("minecraft:command.teleport.targets")))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(anyone, commandKey("minecraft:command.teleport.targets.location")))
    }

    @Test
    fun `sous un refus visible, un allow posé plus bas reste jugé, et sans effet`() {
        val resolver = resolver(defaultWith("minecraft:command.kill" to Rule(RuleValue.DENY, visible = true), "minecraft:command.kill.targets" to Rule.ALLOW))
        val judgment = resolver.judgment(subject("Nina"), commandKey("minecraft:command.kill.targets"))

        assertEquals(NodeJudgment.REFUSED, resolver.judgment(subject("Nina"), commandKey("minecraft:command.kill")))
        assertEquals(NodeJudgment.ALLOWED_UNDER_REFUSAL, judgment)
        assertFalse(judgment.hasNoRefusal)
        assertTrue(judgment.hasVisibleRefusal)
        assertTrue(judgment.isAllowed)
    }

    @Test
    fun `un nœud n'est jamais sans règle sous un refus, qui le couvre`() {
        val resolver = resolver(defaultWith("minecraft:command.gamemode" to Rule(RuleValue.DENY, visible = true)))

        assertEquals(NodeJudgment.REFUSED, resolver.judgment(subject("Nina"), commandKey("minecraft:command.gamemode.gamemode")))
    }

    @Test
    fun `une clé d'API se décide par les règles de même clé, sans que command en réponde`() {
        val resolver = resolver(personas)

        assertEquals(Decision.ALLOW, resolver.decision(subject("Bob"), Keys.ENTITY_SELECTORS))
        // admin ouvre command, qui ne couvre pas l'atome : c'est moderateur, dont il hérite, qui répond.
        assertEquals(RuleHit(RuleSetOwner.OfGroup("moderateur"), Keys.ENTITY_SELECTORS, Rule.ALLOW), resolver.explain(subject("Alice"), Keys.ENTITY_SELECTORS).winner)
        // default refuse command, qui ne couvre pas l'atome non plus : vanilla décide.
        assertEquals(Decision.UNSET, resolver.decision(subject("Nina"), Keys.ENTITY_SELECTORS))
    }

    @Test
    fun `le niveau d'op change les groupes du sujet dès la décision suivante`() {
        val resolver = resolver(personas)
        val stop = commandKey("minecraft:command.stop")
        val gamemode = commandKey("minecraft:command.gamemode")

        // Olga, que le fichier ne connaît pas : avant son op, après, puis après son deop.
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(subject("Olga", opLevel = 0), stop))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(subject("Olga", opLevel = 4), stop))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(subject("Olga", opLevel = 0), stop))

        // Bob, que le fichier connaît : op de niveau 4, il devient membre d'office d'admin en plus de moderateur.
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(subject("Bob", opLevel = 0), gamemode))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(subject("Bob", opLevel = 4), gamemode))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(subject("Bob", opLevel = 0), gamemode))
    }

    @Test
    fun `un joueur oublié à sa déconnexion retrouve les mêmes décisions`() {
        val resolver = resolver(personas)
        val coordinates = commandKey("minecraft:command.teleport.targets.location")
        val before = resolver.judgment(subject("Joe"), coordinates)

        resolver.forget(uuidOf("Joe"))
        resolver.forget(uuidOf("Nina"))

        assertEquals(before, resolver.judgment(subject("Joe"), coordinates))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(subject("Nina"), commandKey("minecraft:command.help")))
    }

    @Test
    fun `seule une clé de nœud a un jugement`() {
        val resolver = resolver(personas)

        assertThrows(IllegalArgumentException::class.java) { resolver.judgment(subject("Joe"), Keys.ALL_COMMANDS) }
        assertThrows(IllegalArgumentException::class.java) { resolver.judgment(subject("Joe"), commandKey("minecraft:command")) }
    }

    @Test
    fun `prototype, Jogger, les coordonnées sont refusées, et la rotation aussi, qu'il ouvrait à tort`() {
        assertFalse(permitted("Jogger", "minecraft:command.teleport.targets.location"))
        assertFalse(permitted("Jogger", "minecraft:command.teleport.targets.location.rotation"))
    }

    @Test
    fun `prototype, Joe, sa règle propre l'emporte et vaut pour les descendantes de sa clé`() {
        assertTrue(permitted("Joe", "minecraft:command.teleport.targets.location"))
        assertTrue(permitted("Joe", "minecraft:command.teleport.targets.location.rotation"))
        assertTrue(permitted("Joe", "minecraft:command.teleport.targets.location.rotation.pitch"))
        assertTrue(permitted("Joe", "minecraft:command.teleport.target_player"))
        assertTrue(permitted("Joe", "homes:command.sethome"))
    }

    @Test
    fun `prototype, Alice, la précision décide dans admin, et sa priorité masque le refus de moderateur`() {
        assertTrue(permitted("Alice", "minecraft:command.teleport.targets.location"))
        assertTrue(permitted("Alice", "minecraft:command.teleport.target_player"))
        assertTrue(permitted("Alice", "othermod:command.customcommand.test"))
        assertFalse(permitted("Alice", "minecraft:command.kick.target_admin"))
    }

    @Test
    fun `prototype, Alice, une clé d'API sans règle reste à vanilla, là où il refusait ce qu'il ne connaissait pas`() {
        assertEquals(Decision.UNSET, resolver(prototype).decision(subject("Alice"), key("othermod:some/thing")))
    }

    /** Les droits d'un `default` seul, avec ces règles. */
    private fun defaultWith(vararg rules: Pair<String, Rule>): Permissions = permissions(mapOf(Group.DEFAULT to Group(rules = rules.toMap())))

    /** Vrai si, chez le prototype, le nœud est sans refus sur son chemin pour ce joueur. */
    private fun permitted(name: String, key: String): Boolean = resolver(prototype).judgment(subject(name), commandKey(key)).hasNoRefusal
}
