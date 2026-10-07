// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.api

import fr.moulou.aegisperms.hook.ApiKeyRequests
import fr.moulou.aegisperms.hook.ApiRoute
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.model.Decision
import me.lucko.fabric.api.permissions.v0.OfflinePermissionCheckEvent
import me.lucko.fabric.api.permissions.v0.OptionRequestEvent
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent
import net.fabricmc.fabric.api.util.TriState
import java.util.Optional
import java.util.concurrent.CompletableFuture

/**
 * AegisPerms fournisseur de l'API de lucko (cahier des charges, 6.4), par trois de ses quatre événements : la demande sur une source, dont l'autorité se
 * lit par `permissions()`, la demande hors ligne par UUID, et les options, qu'AegisPerms n'a pas. `OfflineOptionRequestEvent` rend la réponse voulue sans
 * inscription (D8). Une erreur interne répond `DEFAULT` et se journalise une fois par clé et par génération (7.5).
 */
object LuckoPermissionProvider {

    /** Le nom de l'accroche au journal. */
    const val HOOK = "luckoApi"

    fun register() {
        PermissionCheckEvent.EVENT.register(PermissionCheckEvent { source, permission -> check(RequestSubject.of(source.permissions()), permission) })
        OfflinePermissionCheckEvent.EVENT.register(OfflinePermissionCheckEvent { uuid, permission -> CompletableFuture.completedFuture(check(RequestSubject.of(uuid), permission)) })
        OptionRequestEvent.EVENT.register(OptionRequestEvent { _, _ -> Optional.empty() })
    }

    /**
     * La réponse à la demande de [dotted] pour [subject] (6.4) : la chaîne à points se lit comme un identifiant, le premier segment pour espace de noms, et
     * entre au catalogue par la voie `lucko` ; une chaîne qui ne fait pas une clé reçoit `DEFAULT`, et le catalogue la signale refusée.
     */
    internal fun check(subject: RequestSubject, dotted: String): TriState {
        val key = ApiKeyRequests.keyOfDottedString(dotted) ?: return TriState.DEFAULT
        return try {
            ApiKeyRequests.request(key, ApiRoute.LUCKO)
            when (subject.decision(key)) {
                Decision.ALLOW -> TriState.TRUE
                Decision.DENY_VISIBLE, Decision.DENY_HIDDEN -> TriState.FALSE
                Decision.UNSET, null -> TriState.DEFAULT
            }
        } catch (error: Throwable) {
            ModLog.once(HOOK, key.text, error)
            TriState.DEFAULT
        }
    }
}
