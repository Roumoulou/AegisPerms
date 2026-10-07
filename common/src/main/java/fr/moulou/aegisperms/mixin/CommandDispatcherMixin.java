// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import fr.moulou.aegisperms.hook.Hooks;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Deux accroches sur le dispatcher de Brigadier (cahier des charges, 7.5) : la porte d'exécution, pour un mod qui exécuterait une analyse par
 * {@code CommandDispatcher#execute} au lieu de {@code Commands#performCommand}, et les complétions, qui interrogent tous les enfants du dernier nœud atteint
 * sans tester {@code canUse} : un nœud qu'une source jugée ne voit pas ne fait plus tourner ses suggestions.
 */
@Mixin(CommandDispatcher.class)
public abstract class CommandDispatcherMixin<S> {

    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("HEAD"), cancellable = true)
    private void aegisperms$gate(ParseResults<S> parse, CallbackInfoReturnable<Integer> cir) {
        if (!Hooks.allowsExecution(parse)) cir.setReturnValue(0);
    }

    @WrapOperation(
            method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/tree/CommandNode;listSuggestions(Lcom/mojang/brigadier/context/CommandContext;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;")
    )
    private CompletableFuture<Suggestions> aegisperms$suggestions(CommandNode<S> node, CommandContext<S> context, SuggestionsBuilder builder, Operation<CompletableFuture<Suggestions>> original) {
        if (Hooks.hidesSuggestions(node, context)) return Suggestions.empty();
        return original.call(node, context, builder);
    }
}
