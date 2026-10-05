// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.personas
import fr.moulou.aegisperms.Fixtures.resolver
import fr.moulou.aegisperms.Fixtures.silentDefault
import fr.moulou.aegisperms.Fixtures.state
import fr.moulou.aegisperms.Fixtures.subject
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** L'ordre d'examen des groupes d'un sujet (cahier des charges, 2.6). */
class ExaminationOrderTest {

    @Test
    fun `les groupes de départ sont les groupes directs, ceux dont le sujet est membre d'office, et default`() {
        assertEquals(
            listOf(StartingGroup("admin", Membership.EX_OFFICIO, 100), StartingGroup("moderateur", Membership.DIRECT, 50), StartingGroup("default", Membership.DEFAULT, 0)),
            order(personas, listOf("moderateur"), opLevel = 4).startingGroups,
        )

        // Un groupe direct dont le sujet est aussi membre d'office compte pour direct, et default reste default même inscrit.
        assertEquals(
            listOf(StartingGroup("admin", Membership.DIRECT, 100), StartingGroup("default", Membership.DEFAULT, 0)),
            order(personas, listOf("default", "admin"), opLevel = 4).startingGroups,
        )
    }

    @Test
    fun `les groupes de départ se rangent par poids décroissant, puis par nom croissant à poids égal`() {
        val permissions = permissions(mapOf(silentDefault, "beta" to Group(weight = 5), "alpha" to Group(weight = 5), "heavy" to Group(weight = 9)))

        assertEquals(listOf("heavy", "alpha", "beta", "default"), order(permissions, listOf("beta", "heavy", "alpha")).startingGroups.map { it.name })
    }

    @Test
    fun `à poids égal, default passe après les autres groupes de départ, quel que soit son nom`() {
        val permissions = permissions(Permissions.DELIVERED.groups + mapOf("builders" to Group(), "vip" to Group()))
        val order = order(permissions, listOf("vip", "builders"))

        assertEquals(listOf("builders", "vip", "default"), order.startingGroups.map { it.name })
        assertEquals(listOf("builders", "vip", "default"), order.names())
    }

    @Test
    fun `chaque groupe se parcourt en profondeur, ses parents dans l'ordre déclaré`() {
        val permissions = permissions(mapOf(silentDefault, "a" to Group(weight = 1, parents = listOf("c", "b")), "b" to Group(), "c" to Group(parents = listOf("d")), "d" to Group()))

        assertEquals(listOf("a", "c", "d", "b", "default"), order(permissions, listOf("a")).names())
    }

    @Test
    fun `un groupe direct aussi hérité passe après tous les groupes du sujet qui en héritent`() {
        assertEquals(listOf("admin", "moderateur", "membre", "default"), order(personas, listOf("membre", "admin")).names())
    }

    @Test
    fun `un losange d'héritage examine l'ancêtre commun une fois, après ses deux héritiers`() {
        val permissions = permissions(
            mapOf(silentDefault, "a" to Group(weight = 10, parents = listOf("b", "c")), "b" to Group(parents = listOf("d")), "c" to Group(parents = listOf("d")), "d" to Group()),
        )
        val order = order(permissions, listOf("a"))

        assertEquals(listOf("a", "b", "c", "d", "default"), order.names())
        assertEquals(listOf("a", "c", "d"), order.groups.first { it.name == "d" }.chain)
    }

    @Test
    fun `default passe après un groupe du sujet qui en hérite`() {
        val permissions = permissions(mapOf(silentDefault, "light" to Group(weight = -5, parents = listOf("default"))))

        // Par son poids, default passerait avant : l'héritage le renvoie après.
        assertEquals(listOf("default", "light"), order(permissions, listOf("light")).startingGroups.map { it.name })
        assertEquals(listOf("light", "default"), order(permissions, listOf("light")).names())
    }

    @Test
    fun `un joueur que le fichier ne connaît pas n'a que default et les groupes dont il est membre d'office`() {
        val resolver = resolver(personas)

        assertEquals(listOf("default"), resolver.order(subject("Nina")).names())
        assertEquals(listOf("admin", "moderateur", "membre", "default"), resolver.order(subject("Olga", opLevel = 4)).names())
    }

    @Test
    fun `l'ordre d'examen des personnages est celui de leur table`() {
        val resolver = resolver(personas)

        assertEquals(listOf("membre", "default"), resolver.order(subject("Jogger")).names())
        assertEquals(listOf("vip", "membre", "default"), resolver.order(subject("Joe")).names())
        assertEquals(listOf("moderateur", "membre", "default"), resolver.order(subject("Bob")).names())
        assertEquals(listOf("admin", "moderateur", "membre", "default"), resolver.order(subject("Alice")).names())
        assertEquals(listOf("admin", "moderateur", "membre", "default"), resolver.order(subject("Olga", opLevel = 4)).names())
        assertEquals(listOf("default"), resolver.order(subject("Nina")).names())
    }

    @Test
    fun `un groupe examiné dit par quelle appartenance et par quel héritage le sujet l'atteint`() {
        assertEquals(
            listOf(
                OrderedGroup("admin", Membership.EX_OFFICIO, listOf("admin")),
                OrderedGroup("moderateur", Membership.EX_OFFICIO, listOf("admin", "moderateur")),
                OrderedGroup("membre", Membership.EX_OFFICIO, listOf("admin", "moderateur", "membre")),
                OrderedGroup("default", Membership.DEFAULT, listOf("default")),
            ),
            order(personas, emptyList(), opLevel = 4).groups,
        )
        assertEquals(OrderedGroup("vip", Membership.DIRECT, listOf("vip")), order(personas, listOf("membre", "vip")).groups.first())
    }

    @Test
    fun `un cycle d'héritage ne se résout pas`() {
        val permissions = permissions(mapOf(silentDefault, "a" to Group(parents = listOf("b")), "b" to Group(parents = listOf("a"))))

        assertThrows(IllegalStateException::class.java) { order(permissions, listOf("a")) }
    }

    @Test
    fun `un groupe inconnu ne se résout pas`() {
        assertThrows(NoSuchElementException::class.java) { order(personas, listOf("fantome")) }
        assertThrows(NoSuchElementException::class.java) { order(permissions(mapOf(silentDefault, "a" to Group(parents = listOf("fantome")))), listOf("a")) }
    }

    @Test
    fun `l'ordre est celui du parcours du cahier lu à la lettre, sur des graphes tirés au hasard`() {
        val random = Random(20261005)
        val names = (0 until 8).map { "g$it" }
        repeat(300) {
            val groups = names.mapIndexed { index, name ->
                // Des parents pris parmi les groupes suivants, et default : jamais de cycle.
                val parents = (names.drop(index + 1) + Group.DEFAULT).filter { random.nextInt(3) == 0 }.shuffled(random)
                name to Group(weight = random.nextInt(3) * 10, parents = parents, includeOps = if (random.nextInt(4) == 0) OpLevel.entries[random.nextInt(4)] else null)
            }.toMap() + silentDefault
            val directGroups = names.filter { random.nextInt(3) == 0 }.shuffled(random)
            val order = order(permissions(groups), directGroups, opLevel = random.nextInt(5))
            val startingNames = order.startingGroups.map { it.name }

            assertEquals(literalOrder(groups, startingNames), order.names(), "groupes $groups, groupes directs $directGroups")
            for (group in order.groups) {
                assertEquals(group.name, group.chain.last())
                assertTrue(group.chain.first() in startingNames)
                assertTrue(group.chain.zipWithNext().all { (heir, parent) -> parent in groups.getValue(heir).parents })
            }
        }
    }

    private fun order(permissions: Permissions, directGroups: List<String>, opLevel: Int = 0): ExaminationOrder = ExaminationOrder.of(state(permissions), directGroups, opLevel)

    private fun ExaminationOrder.names(): List<String> = groups.map { it.name }

    /** Le parcours de 2.6, tel qu'il s'écrit : chaque groupe de départ, puis ses parents dans l'ordre déclaré, et la dernière place de chacun. */
    private fun literalOrder(groups: Map<String, Group>, startingGroups: List<String>): List<String> {
        val visited = mutableListOf<String>()
        fun visit(name: String) {
            visited += name
            groups.getValue(name).parents.forEach(::visit)
        }
        startingGroups.forEach(::visit)
        return visited.filterIndexed { index, name -> visited.lastIndexOf(name) == index }
    }
}
