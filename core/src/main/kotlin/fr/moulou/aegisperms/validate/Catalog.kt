// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.CommandKey

/** Ce que la validation demande au catalogue (cahier des charges, 4.6), la liste des clés connues du serveur. Sans lui, les constats qui en dépendent se taisent. */
interface Catalog {

    /** Vrai pour la clé d'un nœud que le serveur connaît. */
    fun hasCommandKey(key: CommandKey): Boolean

    /** Vrai si au moins une commande porte l'espace de noms [namespace]. */
    fun hasNamespace(namespace: String): Boolean

    /** La clé connue la plus proche d'une clé absente, quand l'une vaut d'être proposée. */
    fun nearestCommandKey(key: CommandKey): CommandKey?

    /** Les clés apparues au même endroit du catalogue qu'une clé disparue. */
    fun appearedNear(key: CommandKey): List<CommandKey>

    /** La clé de la cible quand [key] est celle d'un alias de vanilla (`minecraft:command.tp` mène à `minecraft:command.teleport`), ou `null`. */
    fun aliasTarget(key: CommandKey): CommandKey?

    /** Les descendants de [key] plus exigeants que leur ancêtre (2.8). */
    fun demandingDescendants(key: CommandKey): List<DemandingDescendant>

    /** Vrai pour une clé d'API connue de vanilla ou déjà demandée. */
    fun hasApiKey(key: ApiKey): Boolean
}

/** Un nœud plus exigeant que son ancêtre, avec le niveau d'op que vanilla lui demande. */
data class DemandingDescendant(val key: CommandKey, val opLevel: Int)
