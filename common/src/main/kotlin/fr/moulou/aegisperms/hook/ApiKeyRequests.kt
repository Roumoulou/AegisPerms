// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.Keys
import java.util.concurrent.ConcurrentHashMap

/** La voie par laquelle une clé d'API est demandée (cahier des charges, 6.1) : un atome, l'API de Fabric ou l'API de lucko. C'est le `type` que le catalogue note (4.6). */
enum class ApiRoute(val catalogName: String) {
    ATOM("atom"),
    FABRIC("fabric"),
    LUCKO("lucko");

    companion object {
        /** La voie de nom [catalogName] au catalogue, ou `null`. */
        fun ofCatalogName(catalogName: String): ApiRoute? = entries.firstOrNull { it.catalogName == catalogName }
    }
}

/**
 * Les clés d'API demandées (cahier des charges, 4.6), chacune avec les voies par lesquelles elle l'a été : ce que le catalogue note sous `api-keys`. Une clé
 * venue par plusieurs voies les porte jointes par `+`, dans l'ordre atome, Fabric, lucko. Les clés que le catalogue gardait s'y reprennent au démarrage, pour
 * qu'un type fusionné survive au redémarrage.
 *
 * `request` est sur le chemin des atomes : il n'alloue rien quand la clé et sa voie sont déjà connues.
 */
object ApiKeyRequests {

    private const val SEPARATOR = "+"

    private val routesByKey = ConcurrentHashMap<ApiKey, Set<ApiRoute>>()

    /** Ce que le fil des fichiers pose pour apprendre qu'une clé, ou une voie pour elle, est nouvelle : il écrit alors le catalogue (D12). */
    @Volatile
    internal var onNewKey: (() -> Unit)? = null

    /** Inscrit la demande de [key] par [route]. Vrai quand la clé, ou cette voie pour elle, est nouvelle. */
    fun request(key: ApiKey, route: ApiRoute): Boolean {
        val known = routesByKey[key]
        if (known != null && route in known) return false
        var added = false
        routesByKey.compute(key) { _, routes ->
            if (routes != null && route in routes) {
                routes
            } else {
                added = true
                routes.orEmpty() + route
            }
        }
        if (added) onNewKey?.invoke()
        return added
    }

    /** Reprend les clés que le catalogue gardait, chaque texte de clé vers son type. Une clé qui n'est pas une clé d'API, ou une voie inconnue, est ignorée. */
    fun seed(types: Map<String, String>) {
        for ((keyText, type) in types) {
            val key = Keys.parseOrNull(keyText) as? ApiKey ?: continue
            val routes = type.split(SEPARATOR).mapNotNullTo(LinkedHashSet()) { ApiRoute.ofCatalogName(it) }
            if (routes.isNotEmpty()) routesByKey.merge(key, routes) { known, read -> known + read }
        }
    }

    /** Les clés demandées, chacune sous son texte avec son type, pour `catalog.json`. */
    fun types(): Map<String, String> = routesByKey.entries.associate { (key, routes) -> key.text to typeOf(routes) }

    /** Le type de [key] au catalogue, ou `null` quand elle n'a jamais été demandée. */
    fun typeOf(key: ApiKey): String? = routesByKey[key]?.let(::typeOf)

    /** Les clés demandées. */
    fun keys(): Set<ApiKey> = routesByKey.keys.toSet()

    /** Oublie toute demande. Réservé aux tests. */
    internal fun clear() = routesByKey.clear()

    private fun typeOf(routes: Set<ApiRoute>): String = routes.sortedBy { it.ordinal }.joinToString(SEPARATOR) { it.catalogName }
}
