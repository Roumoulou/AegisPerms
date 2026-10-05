// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import java.util.UUID

/**
 * L'état en vigueur (cahier des charges, 4.1 et 7.4) : une configuration validée et compilée, immuable, avec sa génération, le numéro qui change à chaque
 * publication. Il se construit d'une configuration que la validation (4.5) a admise, jamais à sa place : une clé mal formée, une forme lisible illisible ou
 * un UUID porté par deux joueurs lèvent une exception.
 */
class EffectiveState(val generation: Long, val configuration: Configuration) {

    val settings: Settings get() = configuration.settings

    val permissions: Permissions get() = configuration.permissions

    /** Les ensembles de règles des groupes, sous leur nom, chacun sans les règles de ses parents. */
    val groupRules: Map<String, RuleSet> = configuration.permissions.groups.mapValues { RuleSet.compile(it.value.rules) }

    /** Les joueurs que le fichier des droits connaît, par UUID. */
    val players: Map<UUID, KnownPlayer> = configuration.permissions.players.map { KnownPlayer.compile(it.key, it.value) }.associateBy { it.uuid }

    private val exOfficioGroupsByOpLevel: List<List<String>> = (0..OpLevel.OWNERS.level).map { opLevel ->
        configuration.permissions.groups.filterValues { it.includeOps?.isReachedBy(opLevel) == true }.keys.sortedWith(CodePointOrder)
    }

    init {
        require(players.size == configuration.permissions.players.size) { "Two players of the permissions share a UUID" }
    }

    /** Les groupes dont un joueur de niveau d'op [opLevel], de 0 à 4, est membre d'office (2.5), par nom croissant. */
    fun exOfficioGroups(opLevel: Int): List<String> = exOfficioGroupsByOpLevel[opLevel]

    /** La décision qu'une règle impose : un `deny` qui ne dit pas sa visibilité suit le réglage `denied-commands` (2.7), et un `allow` ignore la sienne. */
    fun decisionOf(rule: Rule): Decision = when (rule.value) {
        RuleValue.ALLOW -> Decision.ALLOW
        RuleValue.DENY -> if (rule.visible ?: (settings.deniedCommands == DeniedCommands.VISIBLE)) Decision.DENY_VISIBLE else Decision.DENY_HIDDEN
    }
}

/** Un joueur que le fichier des droits connaît, compilé : son identité, tirée de sa forme lisible, et son ensemble de règles. */
class KnownPlayer private constructor(
    val uuid: UUID,
    /** Le pseudo que porte la forme lisible, ou `null` quand elle n'en connaît pas. */
    val name: String?,
    val readableForm: String,
    /** Le joueur, tel que le fichier des droits l'écrit. */
    val player: Player,
    /** Ses règles propres, compilées. */
    val rules: RuleSet,
) {

    companion object {
        internal fun compile(readableForm: String, player: Player): KnownPlayer {
            val (name, uuidText) = requireNotNull(ReadableForm.split(readableForm)) { "Malformed readable form '$readableForm'" }
            val uuid = requireNotNull(ReadableForm.parseUuid(uuidText)?.takeIf { it.toString() == uuidText }) { "Malformed or non-canonical UUID in readable form '$readableForm'" }
            return KnownPlayer(uuid, name, readableForm, player, RuleSet.compile(player.rules))
        }
    }

    /** Les groupes directs, dans l'ordre écrit. */
    val groups: List<String> get() = player.groups
}
