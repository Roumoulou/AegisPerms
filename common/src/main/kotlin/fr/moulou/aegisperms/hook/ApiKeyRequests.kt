// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.ApiKeyEntry
import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.Keys
import net.minecraft.resources.Identifier
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
 * venue par plusieurs voies les porte jointes par `+`, dans l'ordre atome, Fabric, lucko. S'y ajoutent les chaînes à points que l'API de lucko a données sans
 * qu'elles fassent une clé, que le catalogue signale refusées sous leur texte (6.4). Ce que le catalogue gardait se reprend au démarrage, pour qu'un type
 * fusionné et une chaîne refusée survivent au redémarrage.
 *
 * Les deux tables d'identifiant vers clé servent au chemin des atomes et des demandes : après la première fois, rien ne découpe plus de chaîne. `request`
 * n'alloue rien quand la clé et sa voie sont déjà connues.
 */
object ApiKeyRequests {

    private const val SEPARATOR = "+"

    /** Ce que les tables retiennent d'un identifiant qui ne fait pas une clé d'API : elles ne gardent pas de `null`. */
    private val NOT_A_KEY = Any()

    private val routesByKey = ConcurrentHashMap<ApiKey, Set<ApiRoute>>()
    private val keysByIdentifier = ConcurrentHashMap<Identifier, Any>()
    private val keysByDottedString = ConcurrentHashMap<String, Any>()
    private val refused = ConcurrentHashMap.newKeySet<String>()

    /** Ce que le fil des fichiers pose pour apprendre qu'une clé, une voie pour elle ou une chaîne refusée est nouvelle : il écrit alors le catalogue (D12). */
    @Volatile
    internal var onNewKey: (() -> Unit)? = null

    /**
     * La clé d'API de l'identifiant [id] d'un atome ou d'un nœud de l'API de Fabric (6.2, 6.3), ou `null` quand il n'en fait pas une : le chemin `command`
     * est celui d'une clé de commande (2.4), et un tel identifiant reste à vanilla.
     */
    fun keyOf(id: Identifier): ApiKey? {
        val known = keysByIdentifier[id]
        if (known != null) return known as? ApiKey
        val parsed = Keys.parseOrNull(id.toString()) as? ApiKey
        keysByIdentifier.putIfAbsent(id, parsed ?: NOT_A_KEY)
        return parsed
    }

    /**
     * La clé d'API d'une chaîne à points de l'API de lucko (6.4), le premier segment pour espace de noms et les suivants pour chemin, ou `null` quand la
     * chaîne ne fait pas une clé : elle est alors retenue, refusée, pour le catalogue.
     */
    fun keyOfDottedString(dotted: String): ApiKey? {
        val known = keysByDottedString[dotted]
        if (known != null) return known as? ApiKey
        val parsed = Keys.fromDottedString(dotted)
        keysByDottedString.putIfAbsent(dotted, parsed ?: NOT_A_KEY)
        if (parsed == null && refused.add(dotted)) onNewKey?.invoke()
        return parsed
    }

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

    /** Reprend les entrées que le catalogue gardait : les clés d'API avec leurs voies, et les chaînes refusées. Ce qui n'est ni l'un ni l'autre est ignoré. */
    fun seed(entries: Map<String, ApiKeyEntry>) {
        for ((text, entry) in entries) {
            if (entry.refused) {
                refused += text
                keysByDottedString.putIfAbsent(text, NOT_A_KEY)
                continue
            }
            val key = Keys.parseOrNull(text) as? ApiKey ?: continue
            val routes = entry.type.split(SEPARATOR).mapNotNullTo(LinkedHashSet()) { ApiRoute.ofCatalogName(it) }
            if (routes.isNotEmpty()) routesByKey.merge(key, routes) { known, read -> known + read }
        }
    }

    /** Les clés demandées, chacune sous son texte avec son type, pour `catalog.json`. */
    fun types(): Map<String, String> = routesByKey.entries.associate { (key, routes) -> key.text to typeOf(routes) }

    /** Les chaînes à points refusées, chacune vers la voie lucko, pour `catalog.json`. */
    fun refusedTypes(): Map<String, String> = refused.associateWith { ApiRoute.LUCKO.catalogName }

    /** Le type de [key] au catalogue, ou `null` quand elle n'a jamais été demandée. */
    fun typeOf(key: ApiKey): String? = routesByKey[key]?.let(::typeOf)

    /** Les clés demandées. */
    fun keys(): Set<ApiKey> = routesByKey.keys.toSet()

    /** Oublie toute demande et toute chaîne refusée. Réservé aux tests. */
    internal fun clear() {
        routesByKey.clear()
        keysByDottedString.clear()
        refused.clear()
    }

    private fun typeOf(routes: Set<ApiRoute>): String = routes.sortedBy { it.ordinal }.joinToString(SEPARATOR) { it.catalogName }
}
