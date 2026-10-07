// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.api

import com.mojang.serialization.Codec
import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.ApiRoute
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.model.Decision
import net.fabricmc.fabric.api.permission.v1.PermissionContext
import net.fabricmc.fabric.api.permission.v1.PermissionEvents
import net.fabricmc.fabric.api.permission.v1.PermissionNode
import net.minecraft.commands.CommandSourceStack

/**
 * AegisPerms fournisseur de l'API de permissions de Fabric (cahier des charges, 6.3), par `PermissionEvents.ON_REQUEST`, qui peut être appelé depuis
 * n'importe quel fil : `true` ou `false` selon la décision, `null` sous `unset` et pour tout ce qui n'est pas une demande booléenne d'un sujet, ce qui laisse
 * jouer le repli du consommateur. Une erreur interne répond `null` et se journalise une fois par clé et par génération (7.5).
 */
object FabricPermissionProvider {

    /** Le nom de l'accroche au journal. */
    const val HOOK = "fabricApi"

    fun register() {
        PermissionEvents.ON_REQUEST.register(object : PermissionEvents.OnRequest {
            override fun <T : Any> handlePermissionRequest(context: PermissionContext, permission: PermissionNode<T>): T? = answer(context, permission)
        })
    }

    /**
     * La réponse à une demande (6.3) : la clé est celle du nœud, écrite en entier, et entre au catalogue par la voie `fabric` ; seul le codec booléen est
     * servi, les valeurs typées étant à la deuxième version ; le sujet vient du contexte.
     */
    internal fun <T : Any> answer(context: PermissionContext, permission: PermissionNode<T>): T? {
        val key = ApiKeyRequests.keyOf(permission.key()) ?: return null
        return try {
            ApiKeyRequests.request(key, ApiRoute.FABRIC)
            if (permission.codec() !== Codec.BOOL) return null
            when (subjectOf(context).decision(key)) {
                Decision.ALLOW -> permission.cast(true)
                Decision.DENY_VISIBLE, Decision.DENY_HIDDEN -> permission.cast(false)
                Decision.UNSET, null -> null
            }
        } catch (error: Throwable) {
            ModLog.once(HOOK, key.text, error)
            null
        }
    }

    /**
     * La règle du sujet (6.3) : une source de commande se juge sur son autorité ; sans source de commande, un contexte de type `PLAYER` désigne son sujet par
     * son UUID, et les autres types ne reçoivent rien. Un contexte bâti sur une entité porte sous `COMMAND_SOURCE_STACK` le `CommandSource` du joueur et non
     * une source de commande : la valeur se lit sans transtypage, et ce contexte compte comme sans source.
     */
    internal fun subjectOf(context: PermissionContext): RequestSubject {
        @Suppress("UNCHECKED_CAST")
        val source: Any? = context.get(PermissionContext.COMMAND_SOURCE_STACK as PermissionContext.Key<Any>)
        if (source is CommandSourceStack) return RequestSubject.of(source.permissions())
        return if (context.type() == PermissionContext.Type.PLAYER) RequestSubject.of(context.uuid()) else RequestSubject.None
    }
}
