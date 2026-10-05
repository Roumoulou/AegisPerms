// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

import fr.moulou.aegisperms.Fixtures.issues
import fr.moulou.aegisperms.Fixtures.literals
import fr.moulou.aegisperms.Fixtures.permissions
import fr.moulou.aegisperms.Fixtures.readableForm
import fr.moulou.aegisperms.Fixtures.uuidOf
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.Player
import fr.moulou.aegisperms.model.Rule
import fr.moulou.aegisperms.model.RuleValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les constats sur la structure du fichier des droits (cahier des charges, 4.5) : les groupes, les joueurs, et les règles telles qu'elles sont écrites. */
class StructureIssuesTest {

    private val delivered = Permissions.DELIVERED.groups
    private val joe = readableForm("Joe")
    private val uuid = uuidOf("Joe").toString()

    @Test
    fun `default absent est une erreur`() {
        assertEquals(listOf(Issue(IssueKind.MISSING_DEFAULT, ConfigurationFile.PERMISSIONS, listOf("groups"))), issues(permissions(delivered - "default")))
    }

    @Test
    fun `un nom de groupe mal formé est une erreur`() {
        assertEquals(
            listOf(Issue(IssueKind.MALFORMED_GROUP_NAME, ConfigurationFile.PERMISSIONS, listOf("groups", "Mal Nommé"), literals("Mal Nommé"))),
            issues(permissions(delivered + ("Mal Nommé" to Group()))),
        )
    }

    @Test
    fun `un parent inconnu est une erreur`() {
        assertEquals(
            listOf(Issue(IssueKind.UNKNOWN_PARENT, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "parents", "1"), literals("vip", "fantome"))),
            issues(permissions(delivered + ("vip" to Group(parents = listOf("admin", "fantome"))))),
        )
    }

    @Test
    fun `un cycle d'héritage est une erreur, dite une fois`() {
        val cyclic = permissions(delivered + ("a" to Group(parents = listOf("b"))) + ("b" to Group(parents = listOf("a"))))

        assertEquals(listOf(Issue(IssueKind.INHERITANCE_CYCLE, ConfigurationFile.PERMISSIONS, listOf("groups", "a", "parents"), literals("a > b > a"))), issues(cyclic))
    }

    @Test
    fun `un groupe qui hérite de lui-même est un cycle`() {
        assertEquals(
            listOf(Issue(IssueKind.INHERITANCE_CYCLE, ConfigurationFile.PERMISSIONS, listOf("groups", "solo", "parents"), literals("solo > solo"))),
            issues(permissions(delivered + ("solo" to Group(parents = listOf("solo"))))),
        )
    }

    @Test
    fun `un parent nommé deux fois est un avertissement`() {
        assertEquals(
            listOf(Issue(IssueKind.DUPLICATE_PARENT, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "parents", "1"), literals("vip", "admin"))),
            issues(permissions(delivered + ("vip" to Group(parents = listOf("admin", "admin"))))),
        )
    }

    @Test
    fun `une clé de règle mal formée est une erreur, avec son défaut`() {
        val inGroup = permissions(delivered + ("vip" to Group(rules = mapOf("teleport" to Rule.ALLOW))))
        val inPlayer = permissions(delivered, mapOf(joe to Player(rules = mapOf("homes:command.home%2eset" to Rule.DENY))))

        assertEquals(
            listOf(
                Issue(
                    IssueKind.MALFORMED_KEY, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", "teleport"),
                    listOf(IssueArgument.Literal("teleport"), IssueArgument.Translatable("aegisperms.issue.malformed_key.missing_namespace")),
                ),
            ),
            issues(inGroup),
        )
        assertEquals(
            listOf(
                Issue(
                    IssueKind.MALFORMED_KEY, ConfigurationFile.PERMISSIONS, listOf("players", joe, "rules", "homes:command.home%2eset"),
                    listOf(IssueArgument.Literal("homes:command.home%2eset"), IssueArgument.Translatable("aegisperms.issue.malformed_key.invalid_escape")),
                ),
            ),
            issues(inPlayer),
        )
    }

    @Test
    fun `une forme lisible mal formée est une erreur`() {
        val malformed = permissions(delivered, mapOf("Joe" to Player(), "Joe#pas-un-uuid" to Player()))

        assertEquals(
            listOf(
                Issue(IssueKind.MALFORMED_READABLE_FORM, ConfigurationFile.PERMISSIONS, listOf("players", "Joe"), literals("Joe")),
                Issue(IssueKind.MALFORMED_READABLE_FORM, ConfigurationFile.PERMISSIONS, listOf("players", "Joe#pas-un-uuid"), literals("Joe#pas-un-uuid")),
            ),
            issues(malformed),
        )
    }

    @Test
    fun `un UUID non canonique est une erreur, avec sa forme canonique`() {
        val written = "Joe#${uuid.uppercase()}"

        assertEquals(
            listOf(Issue(IssueKind.NON_CANONICAL_UUID, ConfigurationFile.PERMISSIONS, listOf("players", written), literals(uuid.uppercase(), uuid))),
            issues(permissions(delivered, mapOf(written to Player()))),
        )
    }

    @Test
    fun `un UUID porté par deux joueurs est une erreur`() {
        val other = "Jo#$uuid"

        assertEquals(
            listOf(Issue(IssueKind.DUPLICATE_UUID, ConfigurationFile.PERMISSIONS, listOf("players", joe), literals(uuid, other, joe))),
            issues(permissions(delivered, mapOf(joe to Player(), other to Player()))),
        )
    }

    @Test
    fun `un groupe inconnu chez un joueur est une erreur`() {
        assertEquals(
            listOf(Issue(IssueKind.UNKNOWN_GROUP, ConfigurationFile.PERMISSIONS, listOf("players", joe, "groups", "1"), literals("fantome"))),
            issues(permissions(delivered, mapOf(joe to Player(groups = listOf("admin", "fantome"))))),
        )
    }

    @Test
    fun `un groupe inscrit deux fois chez un joueur est un avertissement`() {
        assertEquals(
            listOf(Issue(IssueKind.DUPLICATE_PLAYER_GROUP, ConfigurationFile.PERMISSIONS, listOf("players", joe, "groups", "1"), literals("admin"))),
            issues(permissions(delivered, mapOf(joe to Player(groups = listOf("admin", "admin"))))),
        )
    }

    @Test
    fun `default inscrit comme groupe direct est un avertissement`() {
        assertEquals(
            listOf(Issue(IssueKind.DEFAULT_AS_DIRECT_GROUP, ConfigurationFile.PERMISSIONS, listOf("players", joe, "groups", "0"))),
            issues(permissions(delivered, mapOf(joe to Player(groups = listOf("default"))))),
        )
    }

    @Test
    fun `visible sur un allow est un avertissement`() {
        val key = "minecraft:command.list"

        assertEquals(
            listOf(Issue(IssueKind.VISIBLE_ON_ALLOW, ConfigurationFile.PERMISSIONS, listOf("groups", "vip", "rules", key), literals(key))),
            issues(permissions(delivered + ("vip" to Group(rules = mapOf(key to Rule(RuleValue.ALLOW, visible = true)))))),
        )
    }
}
