// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import fr.moulou.aegisperms.model.Keys

/** Comment le mod propriétaire d'une commande racine a été trouvé. */
enum class OwnerSource {

    /** Par la classe de la commande que la racine exécute, ou de son premier descendant qui en a une. */
    DETECTED,

    /** Par un rattachement des réglages. */
    ASSIGNED,

    /** Introuvable : la commande prend l'espace de noms `unknown`. */
    UNKNOWN,
}

/** Le mod propriétaire d'une commande racine (cahier des charges, 3.2), et ses co-propriétaires : les autres mods trouvés dans son arbre. */
data class Owner(val modId: String, val source: OwnerSource, val coOwners: List<String>) {

    companion object {
        /** L'espace de noms des clés d'une commande sans mod propriétaire connu. */
        const val UNKNOWN_NAMESPACE = "unknown"
    }
}

/** Ce que la recherche du mod propriétaire demande au loader. */
interface ModLocator {

    /** Le modid du mod dont le jar contient la classe de nom [className], ou `null` quand aucun ne la contient. */
    fun modIdOf(className: String): String?

    /** Vrai pour le modid d'un mod chargé. */
    fun isLoaded(modId: String): Boolean

    /** Le nom que le loader affiche pour le mod [modId], ou `null` quand il ne le connaît pas. */
    fun nameOf(modId: String): String? = null

    /** La version du mod [modId], telle que le loader la lit, ou `null` quand il ne le connaît pas. */
    fun versionOf(modId: String): String? = null
}

/**
 * La recherche du mod propriétaire d'une commande racine (3.2), la même pour vanilla et pour les mods : le mod dont le jar contient la classe qui définit la
 * commande exécutée par la racine ou, à défaut, par son premier descendant qui en a une. Une commande définie par une lambda est rattachée à sa classe hôte.
 *
 * Un rattachement des réglages, une commande racine vers un modid, l'emporte sur ce que les classes disent. Il nomme un mod chargé et ne vise pas une racine
 * de vanilla : sinon il est ignoré.
 */
class OwnerResolver(private val mods: ModLocator, private val assignedOwners: Map<String, String>) {

    companion object {
        private const val LAMBDA_MARKER = "\$\$Lambda"

        /** Le nom de la classe hôte d'une lambda, classe cachée nommée `Hôte$$Lambda/0x...` ou `Hôte$$Lambda$12/0x...` ; le nom lui-même pour toute autre classe. */
        fun hostClassName(className: String): String {
            val marker = className.indexOf(LAMBDA_MARKER)
            return if (marker > 0) className.substring(0, marker) else className
        }
    }

    fun ownerOf(root: CommandNode<*>): Owner {
        val found = LinkedHashSet<String>()
        collectMods(root, found, identitySetOf())
        val detected = firstCommandMod(root, identitySetOf())
        val assigned = assignedOwners[root.name]?.takeIf { detected != Keys.MINECRAFT && mods.isLoaded(it) }
        val modId = assigned ?: detected ?: return Owner(Owner.UNKNOWN_NAMESPACE, OwnerSource.UNKNOWN, found.toList())
        return Owner(modId, if (assigned != null) OwnerSource.ASSIGNED else OwnerSource.DETECTED, (found - modId).toList())
    }

    /**
     * Le mod de la commande du nœud ou, à défaut, du premier descendant qui en a une, en profondeur. Pour un nœud qui ne fait que rediriger, sans commande ni
     * enfant, c'est celui de sa cible, qui porte les commandes qu'il mène à exécuter. La racine de l'arbre n'est la cible de personne : elle mène à toutes les
     * commandes, et prêterait le mod de la première venue.
     */
    private fun firstCommandMod(node: CommandNode<*>, seen: MutableSet<CommandNode<*>>): String? {
        if (!seen.add(node)) return null
        modOf(node)?.let { return it }
        for (child in node.children) firstCommandMod(child, seen)?.let { return it }
        if (node.command == null && node.children.isEmpty()) node.redirect?.takeIf { it !is RootCommandNode<*> }?.let { return firstCommandMod(it, seen) }
        return null
    }

    /** Tous les mods dont une commande vit dans l'arbre du nœud, dans l'ordre de l'arbre. */
    private fun collectMods(node: CommandNode<*>, into: MutableSet<String>, seen: MutableSet<CommandNode<*>>) {
        if (!seen.add(node)) return
        modOf(node)?.let { into += it }
        for (child in node.children) collectMods(child, into, seen)
    }

    private fun modOf(node: CommandNode<*>): String? = node.command?.let { mods.modIdOf(hostClassName(it.javaClass.name)) }
}
