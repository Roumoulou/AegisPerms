// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest

import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet

/**
 * L'autorité étrangère d'un autre gestionnaire de permissions (cahier des charges, 6.6 et 8.5) : tant qu'elle est armée, le mixin du mod de test la pose à la
 * place de ce que `ServerPlayer.permissions()` rend, enveloppe comprise. Un test l'arme le temps d'une connexion, pour que le contrôle de la connexion la voie.
 */
object ForeignAuthority {

    /** Vrai pendant qu'un test veut une autorité étrangère sur tout joueur dont `permissions()` est lu. */
    @JvmStatic
    @Volatile
    var armed: Boolean = false

    /** L'autorité posée : un ensemble de niveau 0 qui refuse tout, qu'aucune enveloppe ne reconnaît. */
    @JvmField
    val SET: PermissionSet = object : LevelBasedPermissionSet {
        override fun level(): PermissionLevel = PermissionLevel.ALL

        override fun hasPermission(permission: Permission): Boolean = false

        override fun union(other: PermissionSet): PermissionSet = other

        override fun toString(): String = "ForeignAuthority"
    }
}
