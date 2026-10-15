// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import com.mojang.brigadier.CommandDispatcher
import fr.moulou.aegisperms.files.ConfigurationFiles
import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.files.PublicationResult
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.model.Keys
import fr.moulou.aegisperms.model.Settings
import fr.moulou.aegisperms.tree.CommandIndex
import fr.moulou.aegisperms.tree.LoaderMods
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Le fil des fichiers face à un dossier de configuration (cahier des charges, 4.2, 4.6 à 4.9 et 7.4) : le démarrage en deux temps, les fichiers générés, les clés d'API, le rechargement. */
class FileThreadTest {

    companion object {
        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    private val lines = ArrayList<String>()

    /** Un arbre réduit, indexé sans mixin : sa commande n'a pas de mod connu du loader, et prend l'espace de noms `unknown`. */
    private val dispatcher = CommandDispatcher<CommandSourceStack>().apply { register(Commands.literal("teleport").executes { 1 }) }

    @BeforeEach
    fun capture() {
        ModLog.writer = LogWriter { lines += it.keyAndArguments() }
        ModLog.forgetReported()
        ApiKeyRequests.clear()
        Publisher.clear()
    }

    @AfterEach
    fun release() {
        FileThread.stop()
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `le démarrage publie la génération 0 puis la génération 1, et écrit les fichiers générés`(@TempDir directory: Path) {
        FileThread.start(directory)
        assertEquals(0, Publisher.generation())
        assertTrue(Files.exists(directory.resolve("settings.json")))
        assertTrue(Files.exists(directory.resolve("permissions.json")))

        FileThread.startup(index())
        assertEquals(1, Publisher.generation())
        assertTrue(Files.exists(directory.resolve("catalog.json")))
        assertTrue(Files.exists(directory.resolve("state.json")))
        assertTrue(Files.exists(directory.resolve("last-good").resolve("manifest.json")))
        val catalog = checkNotNull(ConfigurationFiles(directory).readCatalog())
        assertTrue(catalog.commands.containsKey("unknown:command.teleport"), catalog.commands.keys.toString())
        assertTrue("aegisperms.log.files_created settings.json, permissions.json" in lines, lines.toString())
        // Quatre constats : teleport sans mod propriétaire, et les trois clés que default ouvre, absentes de cet arbre réduit.
        assertTrue("aegisperms.log.published 1 startup enforce false 2 0 5 4" in lines, lines.toString())
        assertTrue(lines.any { it.startsWith("aegisperms.issue.command_without_owner settings.json:namespaces.owners:") && it.endsWith(" teleport") }, lines.toString())
        assertTrue(lines.any { it.startsWith("aegisperms.issue.unknown_command_key permissions.json:groups.default.rules.minecraft:command.help:") }, lines.toString())
    }

    @Test
    fun `une clé d'API demandée s'écrit au catalogue à sa première demande`(@TempDir directory: Path) {
        FileThread.start(directory)
        FileThread.startup(index())
        val key = Keys.api("homes", listOf("home", "limit"))

        ApiKeyRequests.request(key, ApiRoute.LUCKO)
        FileThread.drain()
        assertEquals("lucko", checkNotNull(ConfigurationFiles(directory).readCatalog()).apiKeys[key.text]?.type)

        ApiKeyRequests.request(key, ApiRoute.FABRIC)
        FileThread.drain()
        assertEquals("fabric+lucko", checkNotNull(ConfigurationFiles(directory).readCatalog()).apiKeys[key.text]?.type)
    }

    @Test
    fun `un rechargement refusé laisse l'état en vigueur, puis un rechargement valide publie la génération suivante`(@TempDir directory: Path) {
        FileThread.start(directory)
        FileThread.startup(null)
        val permissions = directory.resolve("permissions.json")

        Files.writeString(permissions, "{ pas du json")
        val refused = FileThread.reload(PublicationSource.RELOAD).get().result
        assertTrue(refused is ConfigurationRead.Refused)
        assertEquals(1, Publisher.generation())
        assertEquals(PublicationResult.REFUSED, ConfigurationFiles(directory).readState()?.result)
        assertTrue(lines.any { it.startsWith("aegisperms.log.refused reload 1 ") }, lines.toString())

        Files.writeString(permissions, "{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"default\": {}\n  },\n  \"players\": {}\n}\n")
        val valid = FileThread.reload(PublicationSource.RELOAD).get().result
        assertTrue(valid is ConfigurationRead.Valid)
        assertEquals(2, Publisher.generation())
        assertEquals(PublicationResult.APPLIED, ConfigurationFiles(directory).readState()?.result)
        assertTrue(lines.any { it.startsWith("aegisperms.log.published 2 reload enforce false 1 0 0 ") }, lines.toString())
    }

    private fun index(): CommandIndex<CommandSourceStack> = CommandIndex.build(dispatcher.root, Settings.DELIVERED, LoaderMods, VanillaRequirements)
}
