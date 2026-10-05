// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Un ensemble de nœuds, par identité. `CommandNode.equals` et `hashCode` sont structurels et récursifs : un ensemble ordinaire confondrait deux nœuds de même
 * forme, et recalculerait le hachage d'un sous-arbre entier à chaque ajout.
 */
internal fun <N> identitySetOf(): MutableSet<N> = Collections.newSetFromMap(IdentityHashMap())
