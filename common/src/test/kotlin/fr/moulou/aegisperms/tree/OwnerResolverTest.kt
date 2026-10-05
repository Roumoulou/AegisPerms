// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le mod propriétaire d'une commande racine (cahier des charges, 3.2), sur des arbres synthétiques et un loader simulé. */
class OwnerResolverTest {

    @Test
    fun `le mod propriétaire est celui de la classe de la commande de la racine`() {
        val dispatcher = CommandDispatcher<Any>()
        val home = dispatcher.register(literal<Any>("home").executes(HomeCommand()))

        assertEquals(Owner("homes", OwnerSource.DETECTED, emptyList()), resolver().ownerOf(home))
    }

    @Test
    fun `à défaut il est celui du premier descendant qui porte une commande`() {
        val dispatcher = CommandDispatcher<Any>()
        val home = dispatcher.register(literal<Any>("home").then(literal<Any>("help")).then(literal<Any>("set").executes(HomeCommand())))

        assertEquals(Owner("homes", OwnerSource.DETECTED, emptyList()), resolver().ownerOf(home))
    }

    @Test
    fun `une racine qui ne fait que rediriger prend le mod de sa cible`() {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(literal<Any>("teleport").then(literal<Any>("here").executes(VanillaCommand())))
        val tp = dispatcher.register(literal<Any>("tp").redirect(teleport))

        assertEquals(Owner("minecraft", OwnerSource.DETECTED, emptyList()), resolver().ownerOf(tp))
    }

    @Test
    fun `une racine qui redirige vers la racine de l'arbre n'emprunte le mod d'aucune autre commande`() {
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("teleport").executes(VanillaCommand()))
        val again = dispatcher.register(literal<Any>("again").redirect(dispatcher.root))

        assertEquals(Owner("unknown", OwnerSource.UNKNOWN, emptyList()), resolver().ownerOf(again))
    }

    @Test
    fun `une lambda se rattache à sa classe hôte`() {
        val dispatcher = CommandDispatcher<Any>()
        val warp = dispatcher.register(literal<Any>("warp").executes(WarpsMod.command()))

        assertEquals(Owner("warps", OwnerSource.DETECTED, emptyList()), resolver().ownerOf(warp))
        assertEquals("fr.moulou.Host", OwnerResolver.hostClassName("fr.moulou.Host\$\$Lambda/0x000001f8c1234567"))
        assertEquals("fr.moulou.Host", OwnerResolver.hostClassName("fr.moulou.Host\$\$Lambda\$12/0x000001f8c1234567"))
        assertEquals("fr.moulou.Host\$register\$1", OwnerResolver.hostClassName("fr.moulou.Host\$register\$1"))
    }

    @Test
    fun `une racine sans commande trouvée prend l'espace de noms unknown`() {
        val dispatcher = CommandDispatcher<Any>()
        val empty = dispatcher.register(literal<Any>("empty"))
        val orphan = dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))

        assertEquals(Owner("unknown", OwnerSource.UNKNOWN, emptyList()), resolver().ownerOf(empty))
        assertEquals(Owner("unknown", OwnerSource.UNKNOWN, emptyList()), resolver().ownerOf(orphan))
    }

    @Test
    fun `deux mods sur une racine font un propriétaire et des co-propriétaires`() {
        val dispatcher = CommandDispatcher<Any>()
        val home = dispatcher.register(literal<Any>("home").executes(HomeCommand()))
        dispatcher.register(literal<Any>("home").then(literal<Any>("warp").executes(WarpsMod.command())))

        assertEquals(Owner("homes", OwnerSource.DETECTED, listOf("warps")), resolver().ownerOf(home))

        // Brigadier garde la commande du dernier qui en a donné une : le propriétaire change avec l'ordre de chargement.
        dispatcher.register(literal<Any>("home").executes(WarpsMod.command()))
        assertEquals(Owner("warps", OwnerSource.DETECTED, emptyList()), resolver().ownerOf(home))
    }

    @Test
    fun `un rattachement donne son mod à une racine`() {
        val dispatcher = CommandDispatcher<Any>()
        val orphan = dispatcher.register(literal<Any>("orphan").executes(OrphanCommand()))
        val home = dispatcher.register(literal<Any>("home").executes(HomeCommand()))
        dispatcher.register(literal<Any>("home").then(literal<Any>("warp").executes(WarpsMod.command())))
        val resolver = resolver(assignedOwners = mapOf("orphan" to "homes", "home" to "warps"))

        assertEquals(Owner("homes", OwnerSource.ASSIGNED, emptyList()), resolver.ownerOf(orphan))
        assertEquals(Owner("warps", OwnerSource.ASSIGNED, listOf("homes")), resolver.ownerOf(home))
    }

    @Test
    fun `un rattachement vers un mod absent est ignoré`() {
        val dispatcher = CommandDispatcher<Any>()
        val home = dispatcher.register(literal<Any>("home").executes(HomeCommand()))

        assertEquals(Owner("homes", OwnerSource.DETECTED, emptyList()), resolver(assignedOwners = mapOf("home" to "absent")).ownerOf(home))
    }

    @Test
    fun `un rattachement ne vise pas une racine de vanilla`() {
        val dispatcher = CommandDispatcher<Any>()
        val teleport = dispatcher.register(literal<Any>("teleport").executes(VanillaCommand()))

        assertEquals(Owner("minecraft", OwnerSource.DETECTED, emptyList()), resolver(assignedOwners = mapOf("teleport" to "homes")).ownerOf(teleport))
    }

    private fun resolver(assignedOwners: Map<String, String> = emptyMap()): OwnerResolver = OwnerResolver(FakeMods(), assignedOwners)
}
