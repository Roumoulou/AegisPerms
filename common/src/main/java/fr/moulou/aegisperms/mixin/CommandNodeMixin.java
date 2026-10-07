// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.tree.CommandNode;
import fr.moulou.aegisperms.hook.Hooks;
import fr.moulou.aegisperms.hook.NodeMarkHolder;
import fr.moulou.aegisperms.tree.NodeMark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La décision par nœud et les nœuds tardifs (cahier des charges, 7.5). {@code canUse} gouverne l'analyse, les complétions, {@code /help} et l'arbre envoyé au
 * client : la réponse vanilla est calculée d'abord, puis {@link Hooks#canUse} la garde ou la remplace pour une source jugée. Le nœud porte aussi la marque
 * d'AegisPerms, et un enfant ajouté sous un nœud marqué, après l'index, reçoit la sienne.
 */
@Mixin(CommandNode.class)
public abstract class CommandNodeMixin<S> implements NodeMarkHolder {

    @Unique
    private volatile NodeMark aegisperms$mark;

    @Override
    public NodeMark aegisperms$getMark() {
        return this.aegisperms$mark;
    }

    @Override
    public void aegisperms$setMark(NodeMark mark) {
        this.aegisperms$mark = mark;
    }

    @ModifyReturnValue(method = "canUse", at = @At("RETURN"))
    private boolean aegisperms$decide(boolean original, @Local(argsOnly = true) Object source) {
        return Hooks.canUse((CommandNode<?>) (Object) this, source, original);
    }

    @Inject(method = "addChild", at = @At("TAIL"))
    private void aegisperms$lateChild(CommandNode<S> node, CallbackInfo ci) {
        if (this.aegisperms$mark != null) Hooks.onChildAdded((CommandNode<?>) (Object) this, node);
    }
}
