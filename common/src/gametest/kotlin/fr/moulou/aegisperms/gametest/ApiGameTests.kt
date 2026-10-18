// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.Lifecycle
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import me.lucko.fabric.api.permissions.v0.Options
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.permissions.PermissionLevel
import java.util.UUID
import me.lucko.fabric.api.permissions.v0.Permissions as LuckoPermissions

/** Les API (cahier des charges, chapitre 6 et 8.5) : les deux fournisseurs face à de vrais consommateurs, et l'autorité étrangère. */
class ApiGameTests : AegisGameTests() {

    /** Prouve que la commande sous l'API de Fabric suit les règles : fermée sous `deny`, ouverte sous `allow`, au repli de niveau d'op du consommateur sous `unset`. */
    @GameTest
    fun fabricApiCommandFollowsTheRules(helper: GameTestHelper) {
        consumerCommandFollowsTheRules(helper, "aegistest_fabric", "$TEST_MOD_ID:api/fabric")
    }

    /** Prouve que la commande sous l'API de lucko suit les règles comme celle de l'API de Fabric, sa chaîne à points donnant la clé d'API. */
    @GameTest
    fun luckoApiCommandFollowsTheRules(helper: GameTestHelper) {
        consumerCommandFollowsTheRules(helper, "aegistest_lucko", "$TEST_MOD_ID:lucko")
    }

    /** Prouve que la demande d'un consommateur sous `execute as` se juge sur l'émetteur : la règle propre du juge répond, celle du joueur que `execute as` désigne ne compte pas. */
    @GameTest
    fun apiRequestUnderExecuteAsKeepsTheJudge(helper: GameTestHelper) {
        val judged = helper.judgedPlayer()
        val other = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), players = mapOf(judged.readableForm() to Player(rules = mapOf("$TEST_MOD_ID:lucko" to Rule.ALLOW)))))
        helper.assertTrue(LuckoPermissions.check(judged.source, GameTestCommands.LUCKO_DOTTED_STRING, false), "la règle propre du joueur lui répond vrai")
        helper.assertFalse(LuckoPermissions.check(other.source, GameTestCommands.LUCKO_DOTTED_STRING, false), "l'autre ne l'a pas, et le repli du consommateur vaut")
        helper.assertTrue(LuckoPermissions.check(judged.source.withEntity(other), GameTestCommands.LUCKO_DOTTED_STRING, false), "execute as garde le juge")
        helper.assertFalse(LuckoPermissions.check(other.source.withEntity(judged), GameTestCommands.LUCKO_DOTTED_STRING, false), "dans l'autre sens aussi")
        helper.succeed()
    }

    /** Prouve que la console n'est pas jugée par les API : le fournisseur ne répond pas, et le repli du consommateur décide, niveau d'op 4 compris. */
    @GameTest
    fun consoleIsNotJudgedByTheApis(helper: GameTestHelper) {
        val console = helper.server.createCommandSourceStack()
        helper.assertFalse(LuckoPermissions.check(console, GameTestCommands.LUCKO_DOTTED_STRING, false), "lucko : le repli faux vaut")
        helper.assertTrue(LuckoPermissions.check(console, GameTestCommands.LUCKO_DOTTED_STRING, true), "lucko : le repli vrai vaut")
        helper.assertTrue(LuckoPermissions.check(console, GameTestCommands.LUCKO_DOTTED_STRING, PermissionLevel.ADMINS), "lucko : le repli de niveau d'op lit le niveau d'op 4 de la console")
        val fabric = PermissionPredicates.require<PermissionContextOwner>(GameTestCommands.FABRIC_API_KEY, PermissionLevel.ADMINS)
        helper.assertTrue(fabric.test(console as PermissionContextOwner), "Fabric : le repli de niveau lit le niveau 4 de la console")
        helper.succeed()
    }

    /** Prouve qu'un joueur hors ligne, désigné par UUID, se juge sur ses règles, et qu'un UUID inconnu reste au repli. */
    @GameTest
    fun offlinePlayerIsJudgedByUuid(helper: GameTestHelper) {
        val player = helper.judgedPlayer()
        TestStates.publish(permissionsWith(deliveredDefaultPlus(), players = mapOf(player.readableForm() to Player(rules = mapOf("$TEST_MOD_ID:lucko" to Rule.ALLOW)))))
        helper.assertTrue(LuckoPermissions.check(player.uuid, GameTestCommands.LUCKO_DOTTED_STRING, false).get(), "un UUID connu des droits reçoit vrai")
        helper.assertFalse(LuckoPermissions.check(UUID.randomUUID(), GameTestCommands.LUCKO_DOTTED_STRING, false).get(), "un UUID inconnu reste au repli")
        helper.succeed()
    }

    /** Prouve que les options de l'API de lucko sont vides : AegisPerms n'en a pas. */
    @GameTest
    fun optionsAreEmpty(helper: GameTestHelper) {
        helper.assertTrue(Options.get(helper.judgedPlayer().source, "prefix").isEmpty, "aucune option")
        helper.succeed()
    }

    /** Prouve qu'une autorité étrangère, posée par un mixin d'une priorité plus haute, fait tomber le contrôle de la connexion (6.6), et que l'enveloppe revient désarmée. */
    @GameTest
    fun foreignAuthorityIsReportedAtJoin(helper: GameTestHelper) {
        ForeignAuthority.armed = true
        val player = try {
            helper.judgedPlayer()
        } finally {
            ForeignAuthority.armed = false
        }
        helper.assertTrue(Lifecycle.foreignAuthoritySeen, "le contrôle de la connexion a vu l'autorité étrangère")
        helper.assertTrue(player.source.permissions() is Envelope, "désarmée, l'enveloppe revient")
        helper.succeed()
    }

    /** Les trois états d'une commande de consommateur [command], dont la demande porte [apiKey] : `deny` par `command`, `allow` sur sa clé, puis `unset`. */
    private fun consumerCommandFollowsTheRules(helper: GameTestHelper, command: String, apiKey: String) {
        val server = helper.server
        val player = helper.judgedPlayer()
        val form = Trees.node(server, "$TEST_MOD_ID:$command")
        helper.assertFalse(form.canUse(player.source), "sous command à deny, la commande du consommateur est fermée")
        TestStates.publish(permissionsWith(deliveredDefaultPlus("$TEST_MOD_ID:command.$command" to Rule.ALLOW)))
        helper.assertTrue(form.canUse(player.source), "sous allow, le prédicat du mod s'évalue à tous droits et passe")
        helper.assertTrue(ApiKeyRequests.keys().any { it.text == apiKey }, "la clé d'API demandée, $apiKey, est inscrite pour le catalogue")
        TestStates.publish(permissionsWith(mapOf("minecraft:command.help" to Rule.ALLOW)))
        helper.assertFalse(form.canUse(player.source), "sous unset, un non-op retombe sur le repli du consommateur, le niveau 3")
        Ops.opAs(server, player, PermissionLevel.ADMINS)
        helper.assertTrue(form.canUse(player.source), "sous unset, un op de niveau 3 passe par le repli")
        helper.succeed()
    }
}
