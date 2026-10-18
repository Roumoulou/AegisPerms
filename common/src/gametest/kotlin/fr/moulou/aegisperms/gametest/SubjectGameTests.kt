// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.Hooks
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel

/** Le sujet et les sources (cahier des charges, 2.3 et 8.5) : qui est jugé, et qui ne l'est pas, sur un vrai serveur. */
class SubjectGameTests : AegisGameTests() {

    /** Prouve que la source d'un joueur simulé porte l'enveloppe, avec son UUID et son niveau d'op, 0. */
    @GameTest
    fun playerSourceCarriesTheEnvelope(helper: GameTestHelper) {
        val player = helper.judgedPlayer()
        val authority = player.source.permissions()
        helper.assertTrue(authority is Envelope, "la source d'un joueur porte l'enveloppe, pas ${authority.javaClass.simpleName}")
        helper.assertValueEqual((authority as Envelope).subject, player.uuid, "l'enveloppe porte l'UUID du joueur")
        helper.assertValueEqual(authority.level().id(), 0, "un joueur simulé n'est pas op")
        helper.succeed()
    }

    /** Prouve que la console n'est jamais jugée, même quand `default` refuse `command` : pas d'enveloppe, `/stop` vu, la porte ouverte. */
    @GameTest
    fun consoleIsNeverJudged(helper: GameTestHelper) {
        val server = helper.server
        val console = server.createCommandSourceStack()
        helper.assertFalse(console.permissions() is Envelope, "la console ne porte pas d'enveloppe")
        helper.assertTrue(Trees.node(server, "stop").canUse(console), "la console garde /stop sous command à deny")
        helper.assertTrue(Hooks.allowsExecution(server.commands.dispatcher.parse("list", console)), "la porte laisse passer la console")
        helper.succeed()
    }

    /** Prouve qu'une source serveur qui désigne l'entité d'un joueur n'est pas jugée : le juge est le porteur de l'autorité, pas l'entité. */
    @GameTest
    fun serverSourceWithPlayerEntityIsNotJudged(helper: GameTestHelper) {
        val server = helper.server
        val source = server.createCommandSourceStack().withEntity(helper.judgedPlayer())
        helper.assertFalse(source.permissions() is Envelope, "une source serveur garde son autorité, même avec l'entité d'un joueur")
        helper.assertTrue(Trees.node(server, "kick").canUse(source), "elle voit /kick, que default refuse aux joueurs")
        helper.succeed()
    }

    /** Prouve qu'`execute as` garde le juge : une source empruntée à un autre joueur se juge sur l'émetteur. */
    @GameTest
    fun executeAsKeepsTheJudge(helper: GameTestHelper) {
        val server = helper.server
        val judged = helper.judgedPlayer()
        val other = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), players = mapOf(judged.readableForm() to Player(rules = mapOf("minecraft:command.help" to Rule.DENY)))))
        val help = Trees.node(server, "mc:help")
        helper.assertTrue(help.canUse(other.source), "l'autre joueur garde /mc:help, que default ouvre")
        helper.assertFalse(help.canUse(judged.source), "le joueur à la règle propre perd /mc:help")
        helper.assertFalse(help.canUse(judged.source.withEntity(other)), "execute as ne prête pas les droits de la cible : le juge reste l'émetteur")
        helper.assertTrue(help.canUse(other.source.withEntity(judged)), "et ne lui retire rien non plus")
        helper.succeed()
    }

    /**
     * Prouve que `withMaximumPermission`, le chemin de `/function`, sort de l'enveloppe : la source plafonnée n'est plus jugée et suit vanilla, qui ouvre
     * `/list` à tous là où `default` le refuse. Le plafond ne relève pas le niveau : `union` rend le plus bas, et un joueur de niveau 0 le reste.
     */
    @GameTest
    fun maximumPermissionLeavesTheEnvelope(helper: GameTestHelper) {
        val server = helper.server
        val judged = helper.judgedPlayer().source
        val capped = judged.withMaximumPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS))
        helper.assertFalse(capped.permissions() is Envelope, "la source plafonnée ne porte plus l'enveloppe")
        helper.assertFalse(Trees.node(server, "mc:list").canUse(judged), "jugé, le joueur n'a pas /list, que default refuse")
        helper.assertTrue(Trees.node(server, "list").canUse(capped), "plafonnée, sa source suit vanilla, qui ouvre /list à tous")
        helper.assertFalse(Trees.node(server, "seed").canUse(capped), "et garde son niveau 0 : /seed exige le niveau 2")
        helper.assertTrue(Hooks.allowsExecution(server.commands.dispatcher.parse("list", capped)), "la porte la laisse passer")
        helper.succeed()
    }

    /** Prouve qu'un `/op` puis un `/deop` renouvellent l'enveloppe et son niveau à la source suivante, et ce que chaque niveau donne au premier démarrage. */
    @GameTest
    fun opAndDeopRenewTheEnvelope(helper: GameTestHelper) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val kick = Trees.node(server, "mc:kick")
        Ops.opAs(server, player, PermissionLevel.ADMINS)
        val asAdmin = player.source.permissions()
        helper.assertTrue(asAdmin is Envelope, "un op porte l'enveloppe")
        helper.assertValueEqual((asAdmin as Envelope).level().id(), 3, "l'enveloppe d'un op de niveau 3 porte son niveau")
        helper.assertFalse(kick.canUse(player.source), "un op de niveau 3 retombe sur default au premier démarrage, qui refuse /mc:kick")
        Ops.opAs(server, player, PermissionLevel.OWNERS)
        helper.assertValueEqual((player.source.permissions() as Envelope).level().id(), 4, "l'enveloppe suit le nouveau niveau")
        helper.assertTrue(kick.canUse(player.source), "un op de niveau 4 est membre d'office d'admin, qui ouvre tout")
        Ops.deop(server, player)
        helper.assertValueEqual((player.source.permissions() as Envelope).level().id(), 0, "après deop, le niveau retombe à 0")
        helper.assertFalse(kick.canUse(player.source), "et /mc:kick se referme")
        helper.succeed()
    }
}
