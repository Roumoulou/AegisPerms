// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.resolve.Resolver

/**
 * La publication de l'état en vigueur (cahier des charges, 7.4) : une référence vers le résolveur de l'état, posée d'un bloc. L'enveloppe, `canUse` et les
 * fournisseurs d'API ne lisent qu'elle, depuis n'importe quel fil. Un état nouveau arrive avec un résolveur neuf, donc un cache vide : un calcul fait sur
 * l'ancien ne peut rien y écrire.
 *
 * Sous cette forme, elle publie en mémoire seulement : le fil des fichiers, les générations, `state.json` et `last-good` viennent avec le cycle de vie.
 */
object Publisher {

    @Volatile
    private var resolver: Resolver? = null

    /** Le résolveur de l'état en vigueur. Lève tant que rien n'est publié : pour une accroche, c'est une erreur interne, donc un refus (7.5). */
    fun current(): Resolver = resolver ?: throw IllegalStateException("No state has been published yet")

    /** La génération de l'état en vigueur, 0 tant que rien n'est publié. */
    fun generation(): Long = resolver?.state?.generation ?: 0

    /** Publie [state] : son résolveur remplace le précédent, et les décisions suivantes se prennent sur lui. */
    fun publish(state: EffectiveState): Resolver {
        val fresh = Resolver(state)
        resolver = fresh
        return fresh
    }

    /** Retire l'état publié, comme avant la première publication. Réservé aux tests. */
    internal fun clear() {
        resolver = null
    }
}
