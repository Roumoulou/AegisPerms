// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.ApiKey
import fr.moulou.aegisperms.model.Decision
import fr.moulou.aegisperms.model.Keys
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * L'enveloppe (cahier des charges, 6.2) : le `PermissionSet` qu'AegisPerms substitue à celui d'un joueur. Elle porte l'UUID du joueur et l'ensemble vanilla
 * qu'elle remplace, et c'est elle qui fait d'une source une source jugée (2.3).
 *
 * Elle répond à un atome par la décision du sujet sur la clé d'API de même nom, et rend tout le reste à l'ensemble vanilla : un niveau de commande, parce que
 * la décision sur un nœud se prend dans `canUse` (7.5) et que les pouvoirs hors commandes restent à vanilla (2.8) ; `union`, pour que la source qui sort d'une
 * élévation, `/function` lancé par un joueur, soit un ensemble vanilla et ne soit plus jugée. Elle est un `LevelBasedPermissionSet` du niveau d'op du joueur,
 * parce que l'API de Fabric lit le niveau d'une source par `instanceof`.
 *
 * Elle peut être interrogée depuis n'importe quel fil : elle ne lit que l'état en vigueur, publié d'un bloc. Une erreur interne vaut un refus, journalisé une
 * fois par clé et par génération (7.5).
 */
class Envelope(val subject: UUID, val vanilla: LevelBasedPermissionSet) : LevelBasedPermissionSet {

    companion object {
        /** Le nom de l'accroche au journal. */
        const val HOOK = "envelope"

        /** Ce que la table retient d'un identifiant qui ne fait pas une clé d'API : la table ne garde pas de `null`. */
        private val NOT_A_KEY = Any()

        /** La clé d'API de chaque atome déjà vu, pour que le chemin des atomes ne découpe aucune chaîne après la première demande. */
        private val keysByAtom = ConcurrentHashMap<Identifier, Any>()

        /**
         * La clé d'API de l'identifiant [id] d'un atome (6.2), ou `null` quand il n'en fait pas une : le chemin `command` est celui d'une clé de commande
         * (2.4), et un tel atome reste à vanilla.
         */
        fun apiKeyOf(id: Identifier): ApiKey? {
            val known = keysByAtom[id]
            if (known != null) return known as? ApiKey
            val parsed = Keys.parseOrNull(id.toString()) as? ApiKey
            keysByAtom.putIfAbsent(id, parsed ?: NOT_A_KEY)
            return parsed
        }
    }

    override fun level(): PermissionLevel = vanilla.level()

    override fun hasPermission(permission: Permission): Boolean {
        if (permission !is Permission.Atom) return vanilla.hasPermission(permission)
        return try {
            val key = apiKeyOf(permission.id()) ?: return vanilla.hasPermission(permission)
            ApiKeyRequests.request(key, ApiRoute.ATOM)
            when (Publisher.current().decision(subject, level().id(), key)) {
                Decision.ALLOW -> true
                Decision.DENY_VISIBLE, Decision.DENY_HIDDEN -> false
                Decision.UNSET -> vanilla.hasPermission(permission)
            }
        } catch (error: Throwable) {
            // Un serveur fermé vaut mieux qu'un serveur ouvert à vanilla par accident (7.5) : le refus, et la trace une fois.
            ModLog.once(HOOK, "${permission.id()}", error)
            false
        }
    }

    override fun union(other: PermissionSet): PermissionSet = vanilla.union(other)

    override fun toString(): String = "Envelope[$subject, $vanilla]"
}

/**
 * La source à tous droits (6.1) : le `PermissionSet` de la source avec laquelle AegisPerms évalue le prédicat d'un mod sous un `allow` (2.8), pour que ce qui
 * porte sur les droits passe et que le reste garde son sens. Il répond oui à tout, et une source qui le porte n'est pas jugée.
 */
object AllRights : LevelBasedPermissionSet {

    override fun level(): PermissionLevel = PermissionLevel.OWNERS

    override fun hasPermission(permission: Permission): Boolean = true

    /** Une union avec tous les droits garde tous les droits : le plafond d'une élévation ne vaut pas pendant cette évaluation. */
    override fun union(other: PermissionSet): PermissionSet = this

    override fun toString(): String = "AllRights"
}
