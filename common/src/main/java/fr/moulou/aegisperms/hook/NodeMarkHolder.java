// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook;

import fr.moulou.aegisperms.tree.NodeMark;

/**
 * Ce qu'un mixin greffe sur chaque {@code CommandNode} : la marque d'AegisPerms, retrouvée par identité dans le chemin chaud (cahier des charges, 7.4). La
 * fiche d'un nœud indexé, la marque de la racine du dispatcher indexé, celle d'un arbre dont l'index a manqué, ou rien pour un nœud que le mod n'a jamais
 * indexé, l'arbre privé d'un autre mod par exemple.
 */
public interface NodeMarkHolder {

    NodeMark aegisperms$getMark();

    void aegisperms$setMark(NodeMark mark);
}
