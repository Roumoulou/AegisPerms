// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Les droits (cahier des charges, 4.4) : les groupes et les joueurs du fichier `permissions.json`, tels qu'il les écrit. Rien n'y est vérifié : c'est la
 * validation (4.5) qui en juge.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Permissions(
    /** Le numéro de la structure du fichier (4.2). Sans valeur par défaut : il s'écrit toujours, et un fichier qui ne le porte pas est refusé. */
    @SerialName("schema-version") val schemaVersion: Int,
    /** Les groupes, sous leur nom. Ils s'écrivent toujours, même vides, là où le fichier omet ce qui vaut sa valeur par défaut. */
    @EncodeDefault val groups: Map<String, Group> = emptyMap(),
    /** Les joueurs, sous leur forme lisible. Ils s'écrivent toujours, comme les groupes. */
    @EncodeDefault val players: Map<String, Player> = emptyMap(),
) {

    companion object {
        /** Le numéro de structure que cette version lit et écrit. */
        const val SCHEMA_VERSION = 1

        /** Les deux groupes du premier démarrage (2.10). */
        val DELIVERED = Permissions(
            schemaVersion = SCHEMA_VERSION,
            groups = mapOf(
                "admin" to Group(weight = 100, includeOps = OpLevel.OWNERS, rules = mapOf("command" to Rule.ALLOW)),
                Group.DEFAULT to Group(
                    rules = mapOf(
                        "command" to Rule.DENY,
                        "minecraft:command.help" to Rule.ALLOW,
                        "minecraft:command.me" to Rule.ALLOW,
                        "minecraft:command.msg" to Rule.ALLOW,
                    ),
                ),
            ),
        )
    }
}
