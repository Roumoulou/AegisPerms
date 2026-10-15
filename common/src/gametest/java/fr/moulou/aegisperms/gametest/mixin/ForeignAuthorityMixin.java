// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.moulou.aegisperms.gametest.ForeignAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Un autre gestionnaire de permissions, simulé (cahier des charges, 6.6 et 8.8) : d'une priorité plus haute que celle du mod, son accroche s'applique
 * après la sienne et remplace l'enveloppe par l'autorité étrangère tant que {@link ForeignAuthority#armed} est vrai.
 */
@Mixin(value = ServerPlayer.class, priority = 1100)
public abstract class ForeignAuthorityMixin {

    @ModifyReturnValue(method = "permissions", at = @At("RETURN"))
    private PermissionSet aegispermsGametest$foreign(PermissionSet original) {
        return ForeignAuthority.getArmed() ? ForeignAuthority.SET : original;
    }
}
