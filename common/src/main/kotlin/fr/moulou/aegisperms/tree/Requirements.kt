// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fr.moulou.aegisperms.files.CommandRequirement
import java.util.function.Predicate

/** Ce que l'index demande pour lire l'exigence vanilla d'un nœud (cahier des charges, 2.8) : c'est le jeu qui sait la lire, et l'index ne dépend pas de lui. */
fun interface RequirementReader {

    fun read(requirement: Predicate<*>): CommandRequirement
}

/** Ce que Brigadier seul dit de l'exigence d'un nœud. */
object Requirements {

    /**
     * Le prédicat que Brigadier donne à un nœud sans exigence : une lambda sans capture, écrite une seule fois dans `ArgumentBuilder`, donc une seule instance
     * pour tous les littéraux et tous les arguments. Elle se reconnaît par identité.
     */
    val BRIGADIER_DEFAULT: Predicate<*> = LiteralArgumentBuilder.literal<Any>("aegisperms").requirement

    /** Vrai pour l'exigence d'un nœud qui n'en a pas reçu. */
    fun isDefault(requirement: Predicate<*>): Boolean = requirement === BRIGADIER_DEFAULT
}
