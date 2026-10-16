// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.resolve.Resolver
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.ValidationReport
import java.time.Instant

/**
 * La publication de l'état en vigueur (cahier des charges, 7.4) : une référence vers le résolveur de l'état, posée d'un bloc. L'enveloppe, `canUse` et les
 * fournisseurs d'API ne lisent qu'elle, depuis n'importe quel fil. Un état nouveau arrive avec un résolveur neuf, donc un cache vide : un calcul fait sur
 * l'ancien ne peut rien y écrire. C'est le fil des fichiers qui publie et qui compte les générations ; il retient aussi la dernière publication appliquée,
 * que `/aegis status` dit et que la connexion d'un op rappelle (4.9).
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

    /** La génération de l'état en vigueur : 0 avant la première publication, et 0 aussi pour l'état provisoire du premier temps du démarrage (D9). */
    fun generation(): Long = resolver?.state?.generation ?: 0

    /** Le mode posé en mémoire par `/aegis mode`, jusqu'à la publication suivante (5.8) ; `null` quand c'est celui des réglages qui vaut. */
    @Volatile
    private var modeOverride: Mode? = null

    /** Les réglages de l'état en vigueur, ou les réglages livrés tant que rien n'est publié : ceux sur lesquels un `Commands` bâtit ses formes et son index. */
    fun settings(): Settings = resolver?.state?.settings ?: Settings.DELIVERED

    /** Le mode en vigueur (5.8) : celui que `/aegis mode` a posé, sinon celui des réglages publiés. */
    fun mode(): Mode = modeOverride ?: settings().mode

    /** Pose [mode] en mémoire, tout de suite ; la publication suivante reprend le mode du fichier (5.8). */
    fun setMode(mode: Mode) {
        modeOverride = mode
    }

    /**
     * Publie [state] d'un bloc et rend l'état qu'il remplace, pour dire ce qui change ; `null` à la première publication. Le mode posé en mémoire s'efface
     * quand [resetMode] le dit : à une publication qui relit le fichier des réglages, démarrage, rechargement, restauration, pas à une écriture du mod, qui
     * ne le relit pas (5.8).
     */
    fun publish(state: EffectiveState, resetMode: Boolean = true): EffectiveState? {
        val previous = resolver?.state
        resolver = Resolver(state)
        if (resetMode) modeOverride = null
        return previous
    }

    /** Retire l'état publié, la dernière publication et le mode posé, comme avant la première publication. Réservé aux tests. */
    internal fun clear() {
        resolver = null
        lastPublication = null
        modeOverride = null
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
