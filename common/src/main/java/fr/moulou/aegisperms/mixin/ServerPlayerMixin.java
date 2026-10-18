// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.moulou.aegisperms.hook.Envelope;
import fr.moulou.aegisperms.hook.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * L'autorité (cahier des charges, 6.2 et 7.5) : le {@code PermissionSet} d'un joueur est enveloppé, et c'est l'enveloppe qui fait d'une source une source
 * jugée. Elle se réutilise tant que l'ensemble vanilla reste le même, l'un des singletons de niveau d'op, et se recrée sinon, pour qu'un {@code /op} ou un
 * {@code /deop} agisse à la commande suivante, comme en vanilla.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Unique
    private volatile Envelope aegisperms$envelope;

    @ModifyReturnValue(method = "permissions", at = @At("RETURN"))
    private PermissionSet aegisperms$enveloped(PermissionSet original) {
        Envelope cached = this.aegisperms$envelope;
        if (cached != null && cached.getVanilla() == original) return cached;
        PermissionSet enveloped = Hooks.envelope((ServerPlayer) (Object) this, original);
        if (enveloped instanceof Envelope envelope) this.aegisperms$envelope = envelope;
        return enveloped;
    }
}
