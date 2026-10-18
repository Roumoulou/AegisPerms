// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.api

import fr.moulou.aegisperms.hook.Envelope
import fr.moulou.aegisperms.hook.ObservationLog
import fr.moulou.aegisperms.hook.OpLevels
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.resolve.Subject
import net.minecraft.server.permissions.PermissionSet
import java.util.UUID
import fr.moulou.aegisperms.hook.AllRights as AllRightsSet

/**
 * Le sujet d'une demande d'API (cahier des charges, 6.3), la règle commune aux deux fournisseurs : ce qui est demandé pour une source se juge sur son
 * autorité, jamais sur l'entité qu'elle désigne, et un joueur désigné par UUID, en ligne ou hors ligne, se juge sur son UUID et le niveau d'op retenu.
 */
sealed interface RequestSubject {

    companion object {

        /** Le sujet que donne l'autorité [authority] d'une source. */
        fun of(authority: PermissionSet): RequestSubject = when (authority) {
            is Envelope -> Judged(Subject(authority.subject, authority.level().id()))
            AllRightsSet -> AllRights
            else -> None
        }

        /** Le sujet d'un joueur désigné par [uuid], en ligne ou hors ligne. */
        fun of(uuid: UUID): RequestSubject = Judged(Subject(uuid, OpLevels.levelOf(uuid)))
    }

    /** La décision du sujet pour [key] (2.6), ou `null` quand le fournisseur ne répond pas et laisse le consommateur à son repli. */
    fun decision(key: ApiKey): Decision?

    /**
     * Une source jugée, par l'enveloppe qu'elle porte, ou un joueur désigné par UUID : la décision répond. En observation (6.5), le fournisseur ne répond pas
     * et le consommateur suit son repli, mais une décision autre qu'`unset` se journalise comme un écart.
     */
    class Judged(val subject: Subject) : RequestSubject {

        override fun decision(key: ApiKey): Decision? {
            val decision = Publisher.current().decision(subject, key)
            if (Publisher.mode() != Mode.OBSERVE) return decision
            if (decision != Decision.UNSET) ObservationLog.deviation(subject.uuid, key.text, decision, ObservationLog.VANILLA_DEFAULT)
            return null
        }
    }

    /** La source à tous droits (6.1) : oui à tout. */
    data object AllRights : RequestSubject {

        override fun decision(key: ApiKey): Decision = Decision.ALLOW
    }

    /** Une source non jugée, la console, le RCON, un bloc de commande, une fonction, ou une autorité étrangère : le fournisseur ne répond pas. */
    data object None : RequestSubject {

        override fun decision(key: ApiKey): Decision? = null
    }
}
