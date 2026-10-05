// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.DeniedCommands
import fr.moulou.aegisperms.model.Language
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.NamespaceSettings
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Settings
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Le fichier des réglages tel que le mod l'écrit et le lit (cahier des charges, 4.3). */
class SettingsJsonTest {

    @TempDir
    lateinit var dir: Path

    /** Un chemin à espaces et à accents, dont les dossiers n'existent pas encore. */
    private val file: Path get() = dir.resolve("serveur de l'été").resolve("config").resolve("settings.json")

    private val custom = Settings(
        schemaVersion = 1,
        adminLevel = OpLevel.ADMINS,
        deniedCommands = DeniedCommands.VISIBLE,
        refusalMessage = "Commande refusée.",
        language = Language.FR,
        mode = Mode.OBSERVE,
        namespaces = NamespaceSettings(enabled = false, requirePrefix = false, prefixes = mapOf("minecraft" to "mc", "homes" to "h"), owners = mapOf("home" to "homes")),
        aliases = mapOf("wla" to "whitelist add", "gm" to "gamemode"),
        watchFiles = false,
    )

    private fun write(settings: Settings): String {
        JsonFileFormat.SETTINGS.encodeToPath(Settings.serializer(), settings, file)
        return Files.readString(file)
    }

    private fun read(text: String): Settings {
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return JsonFileFormat.SETTINGS.decodeFromPath(Settings.serializer(), file)
    }

    @Test
    fun `le fichier des réglages du premier démarrage s'écrit à l'octet près`() {
        val expected = """
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
        assertEquals(expected, write(Settings.DELIVERED))
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(file))
    }

    @Test
    fun `chaque réglage s'écrit sous sa clé, un domaine fermé sous son nom de fichier, une table dans l'ordre de ses entrées`() {
        val expected = """
            {
              "schema-version": 1,
              "admin-level": "admins",
              "denied-commands": "visible",
              "refusal-message": "Commande refusée.",
              "language": "fr",
              "mode": "observe",
              "namespaces": {
                "enabled": false,
                "require-prefix": false,
                "prefixes": {
                  "minecraft": "mc",
                  "homes": "h"
                },
                "owners": {
                  "home": "homes"
                }
              },
              "aliases": {
                "wla": "whitelist add",
                "gm": "gamemode"
              },
              "watch-files": false
            }
        """.trimIndent() + "\n"
        assertEquals(expected, write(custom))
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), Files.readAllBytes(file))
    }

    @Test
    fun `les réglages font l'aller-retour`() {
        for (settings in listOf(Settings.DELIVERED, custom)) assertEquals(settings, read(write(settings)))
    }

    @Test
    fun `un fichier qui ne porte que schema-version vaut les réglages livrés`() {
        assertEquals(Settings.DELIVERED, read("{ \"schema-version\": 1 }"))
    }

    @Test
    fun `chaque valeur d'un domaine fermé se lit sous son nom de fichier`() {
        val adminLevels = mapOf("moderators" to OpLevel.MODERATORS, "gamemasters" to OpLevel.GAMEMASTERS, "admins" to OpLevel.ADMINS, "owners" to OpLevel.OWNERS)
        for ((name, level) in adminLevels) assertEquals(level, read("""{ "schema-version": 1, "admin-level": "$name" }""").adminLevel)
        assertEquals(OpLevel.entries.toSet(), adminLevels.values.toSet())

        val deniedCommands = mapOf("hidden" to DeniedCommands.HIDDEN, "visible" to DeniedCommands.VISIBLE)
        for ((name, value) in deniedCommands) assertEquals(value, read("""{ "schema-version": 1, "denied-commands": "$name" }""").deniedCommands)
        assertEquals(DeniedCommands.entries.toSet(), deniedCommands.values.toSet())

        val languages = mapOf("auto" to Language.AUTO, "fr" to Language.FR, "en" to Language.EN)
        for ((name, value) in languages) assertEquals(value, read("""{ "schema-version": 1, "language": "$name" }""").language)
        assertEquals(Language.entries.toSet(), languages.values.toSet())

        val modes = mapOf("enforce" to Mode.ENFORCE, "observe" to Mode.OBSERVE)
        for ((name, value) in modes) assertEquals(value, read("""{ "schema-version": 1, "mode": "$name" }""").mode)
        assertEquals(Mode.entries.toSet(), modes.values.toSet())
    }
}
