// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook;

import fr.moulou.aegisperms.tree.CommandIndex;
import net.minecraft.commands.CommandSourceStack;

/**
 * Ce qu'un mixin greffe sur {@code Commands} : l'index de son dispatcher, calculé à la fin de sa construction et attaché à lui sans rien publier ; il est
 * adopté au démarrage du serveur, ou à la fin d'un {@code /reload} réussi (cahier des charges, 7.5). Nul quand l'index a manqué : l'arbre est alors fermé.
 */
public interface CommandsIndexHolder {

    CommandIndex<CommandSourceStack> aegisperms$getIndex();

    void aegisperms$setIndex(CommandIndex<CommandSourceStack> index);
}
