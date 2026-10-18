// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import fr.moulou.aegisperms.hook.CommandsIndexHolder;
import fr.moulou.aegisperms.hook.Hooks;
import fr.moulou.aegisperms.tree.CommandIndex;
import java.util.Map;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trois accroches sur {@code Commands} (cahier des charges, 7.5). À la fin de sa construction, après le callback de Fabric et {@code setConsumer}, les
 * formes, les alias et l'index de son dispatcher, attachés à lui : au démarrage, ou sur un fil de fond pendant un {@code /reload}. La porte d'exécution, au
 * début de {@code performCommand}, par où passent le tchat, la console, le RCON, les blocs de commande et les panneaux. Et, dans l'arbre qu'un joueur
 * reçoit, la cible d'une redirection qui n'y figure pas, remplacée par la forme préfixée qui partage ses enfants (3.7).
 */
@Mixin(Commands.class)
public abstract class CommandsMixin implements CommandsIndexHolder {

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Unique
    private volatile CommandIndex<CommandSourceStack> aegisperms$index;

    /**
     * Vanilla recopie une redirection par la table des nœuds déjà recopiés, et la perd quand sa cible manque : sous la forme exigée, {@code execute as}
     * redirige vers la forme nue d'{@code execute}, que le joueur ne reçoit pas.
     */
    @WrapOperation(method = "fillUsableCommands", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object aegisperms$redirectTarget(Map<Object, Object> copies, Object target, Operation<Object> original) {
        Object copy = original.call(copies, target);
        return copy != null ? copy : Hooks.clientRedirect(copies, target);
    }

    @Override
    public CommandIndex<CommandSourceStack> aegisperms$getIndex() {
        return this.aegisperms$index;
    }

    @Override
    public void aegisperms$setIndex(CommandIndex<CommandSourceStack> index) {
        this.aegisperms$index = index;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void aegisperms$index(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        Hooks.onCommandsBuilt((Commands) (Object) this, this.dispatcher);
    }

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void aegisperms$gate(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (!Hooks.allowsExecution(parse)) ci.cancel();
    }
}
