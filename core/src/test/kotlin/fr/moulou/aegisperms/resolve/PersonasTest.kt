// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.commandKey
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleSetOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les personnages du cahier des charges (2.11) : chaque ligne de leur table, dans son ordre. Seule Olga est op, de niveau 4. */
class PersonasTest {

    private val resolver = resolver(personas)

    private val jogger = subject("Jogger")
    private val joe = subject("Joe")
    private val bob = subject("Bob")
    private val alice = subject("Alice")
    private val olga = subject("Olga", opLevel = 4)
    private val nina = subject("Nina")

    private val teleport = commandKey("minecraft:command.teleport")
    private val coordinates = commandKey("minecraft:command.teleport.targets.location")
    private val rotation = commandKey("minecraft:command.teleport.targets.location.rotation")
    private val reload = commandKey("aegisperms:command.aegis.reload")

    @Test
    fun `Jogger se téléporte aux coordonnées, refusée et masquée par membre`() {
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(jogger, coordinates))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("membre"), coordinates, Rule.DENY), resolver.explain(jogger, coordinates).winner)
    }

    @Test
    fun `Jogger ajoute la rotation, refusée et masquée, et sa règle propre reste sans effet`() {
        assertEquals(Decision.ALLOW, resolver.decision(jogger, rotation))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(jogger, rotation))
        assertEquals(
            listOf(IneffectiveRule(RuleHit(RuleSetOwner.OfPlayer(readableForm("Jogger")), rotation, Rule.ALLOW), coordinates)),
            resolver.ineffectiveRules(jogger),
        )
    }

    @Test
    fun `Joe se téléporte aux coordonnées, permise par vip qui passe avant membre`() {
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(joe, coordinates))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("vip"), coordinates, Rule.ALLOW), resolver.explain(joe, coordinates).winner)
    }

    @Test
    fun `Joe téléporte aux coordonnées sans cible, refusée et masquée par membre`() {
        val bareCoordinates = commandKey("minecraft:command.teleport.location")

        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(joe, bareCoordinates))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("membre"), bareCoordinates, Rule.DENY), resolver.explain(joe, bareCoordinates).winner)
    }

    @Test
    fun `Bob se téléporte vers Alice, refusée et masquée par moderateur examiné avant membre`() {
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(bob, teleport))
        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(bob, commandKey("minecraft:command.teleport.targets.destination")))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("moderateur"), teleport, Rule.DENY), resolver.explain(bob, teleport).winner)
    }

    @Test
    fun `Bob demande l'aide, permise par default`() {
        val help = commandKey("minecraft:command.help")

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(bob, help))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("default"), help, Rule.ALLOW), resolver.explain(bob, help).winner)
    }

    @Test
    fun `Bob expulse tout le monde, permise avec l'atome des sélecteurs`() {
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(bob, commandKey("minecraft:command.kick.targets")))
        assertEquals(Decision.ALLOW, resolver.decision(bob, Keys.ENTITY_SELECTORS))
    }

    @Test
    fun `Bob change de mode de jeu, refusée et masquée par default`() {
        val gamemode = commandKey("minecraft:command.gamemode.gamemode")

        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(bob, gamemode))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("default"), Keys.ALL_COMMANDS, Rule.DENY), resolver.explain(bob, gamemode).winner)
    }

    @Test
    fun `Alice se téléporte aux coordonnées, permise par admin examiné avant tout ce dont il hérite`() {
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(alice, coordinates))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("admin"), Keys.ALL_COMMANDS, Rule.ALLOW), resolver.explain(alice, coordinates).winner)
    }

    @Test
    fun `Alice recharge aegis, non permise, parce que command ne couvre pas aegis`() {
        assertEquals(Decision.UNSET, resolver.decision(alice, reload))
        assertEquals(NodeJudgment.UNSET, resolver.judgment(alice, reload))
        assertNull(resolver.explain(alice, reload).winner)
    }

    @Test
    fun `Olga arrête le serveur, permise comme membre d'office d'admin`() {
        val stop = commandKey("minecraft:command.stop")

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(olga, stop))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("admin"), Keys.ALL_COMMANDS, Rule.ALLOW), resolver.explain(olga, stop).winner)
        assertEquals(Membership.EX_OFFICIO, resolver.order(olga).groups.first().membership)
    }

    @Test
    fun `Olga recharge aegis, permise par l'étape 0`() {
        assertEquals(Decision.ALLOW, resolver.decision(olga, reload))
        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(olga, reload))
        assertTrue(resolver.explain(olga, reload).step0)
    }

    @Test
    fun `Nina envoie un message privé, permise par default, plus précis que son refus de command`() {
        val message = commandKey("minecraft:command.msg.targets.message")

        assertEquals(NodeJudgment.ALLOWED, resolver.judgment(nina, message))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("default"), commandKey("minecraft:command.msg"), Rule.ALLOW), resolver.explain(nina, message).winner)
    }

    @Test
    fun `Nina tire un nombre au hasard, refusée et masquée, alors que vanilla l'ouvre à tous`() {
        val range = commandKey("minecraft:command.random.value.range")

        assertEquals(NodeJudgment.HIDDEN, resolver.judgment(nina, range))
        assertEquals(RuleHit(RuleSetOwner.OfGroup("default"), Keys.ALL_COMMANDS, Rule.DENY), resolver.explain(nina, range).winner)
    }
}
