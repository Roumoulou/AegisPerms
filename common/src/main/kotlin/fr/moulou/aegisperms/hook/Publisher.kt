// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ValidationReport
import java.time.Instant

/**
 * La publication de l'état en vigueur (cahier des charges, 7.4) : une référence vers le résolveur de l'état, posée d'un bloc. L'enveloppe, `canUse` et les
 * fournisseurs d'API ne lisent qu'elle, depuis n'importe quel fil. Un état nouveau arrive avec un résolveur neuf, donc un cache vide : un calcul fait sur
 * l'ancien ne peut rien y écrire. C'est le fil des fichiers qui publie et qui compte les générations ; il retient aussi la dernière publication appliquée,
 * que `/aegis status` dit et que la connexion d'un op rappelle (E13).
 */
object Publisher {

    @Volatile
    private var resolver: Resolver? = null

    /** La dernière publication appliquée par le fil des fichiers, ou `null` tant qu'il n'a rien publié. */
    @Volatile
    var lastPublication: LastPublication? = null
        internal set

    /** Le résolveur de l'état en vigueur. Lève tant que rien n'est publié : pour une accroche, c'est une erreur interne, donc un refus (7.5). */
    fun current(): Resolver = resolver ?: throw IllegalStateException("No state has been published yet")

    /** Le résolveur de l'état en vigueur, ou `null` tant que rien n'est publié. */
    fun currentOrNull(): Resolver? = resolver

    /** La génération de l'état en vigueur, 0 tant que rien n'est publié. */
    fun generation(): Long = resolver?.state?.generation ?: 0

    /** Les réglages de l'état en vigueur, ou les réglages livrés tant que rien n'est publié : ceux sur lesquels un `Commands` bâtit ses formes et son index. */
    fun settings(): Settings = resolver?.state?.settings ?: Settings.DELIVERED

    /** Publie [state] d'un bloc et rend l'état qu'il remplace, pour dire ce qui change ; `null` à la première publication. */
    fun publish(state: EffectiveState): EffectiveState? {
        val previous = resolver?.state
        resolver = Resolver(state)
        return previous
    }

    /** Retire l'état publié et la dernière publication, comme avant la première publication. Réservé aux tests. */
    internal fun clear() {
        resolver = null
        lastPublication = null
    }
}

/**
 * Une publication appliquée (4.7) : sa génération, sa source et son instant, la lecture seule (4.9), les fichiers que le repli du démarrage a remplacés, et
 * les constats de la configuration publiée.
 */
class LastPublication(
    val generation: Long,
    val source: PublicationSource,
    val date: Instant,
    val readOnly: Boolean,
    val fallback: Set<ConfigurationFile>,
    val report: ValidationReport,
)
