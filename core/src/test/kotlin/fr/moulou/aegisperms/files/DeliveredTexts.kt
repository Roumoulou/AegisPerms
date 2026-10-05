// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

/** Les deux fichiers que le mod crée au premier démarrage, à l'octet près (cahier des charges, 4.3 et 4.4). */
object DeliveredTexts {

    val SETTINGS: String = """
        {
          "schema-version": 1,
          "admin-level": "owners",
          "denied-commands": "hidden",
          "refusal-message": null,
          "language": "auto",
          "mode": "enforce",
          "namespaces": {
            "enabled": true,
            "require-prefix": true,
            "prefixes": {
              "minecraft": "mc"
            },
            "owners": {}
          },
          "aliases": {},
          "watch-files": true
        }
    """.trimIndent() + "\n"

    val PERMISSIONS: String = """
        {
          "schema-version": 1,
          "groups": {
            "admin": {
              "include-ops": "owners",
              "rules": {
                "command": "allow"
              },
              "weight": 100
            },
            "default": {
              "rules": {
                "command": "deny",
                "minecraft:command.help": "allow",
                "minecraft:command.me": "allow",
                "minecraft:command.msg": "allow"
              }
            }
          },
          "players": {}
        }
    """.trimIndent() + "\n"
}
