// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** La surveillance des fichiers (cahier des charges, 4.8), aux périodes raccourcies : un fichier écrit hors du mod se recharge, une écriture du mod non. */
class WatchTest {

    companion object {
        private const val VIP_PERMISSIONS = "{\n  \"schema-version\": 1,\n  \"groups\": {\n    \"default\": {},\n    \"vip\": {\n      \"weight\": 80\n    }\n  },\n  \"players\": {}\n}\n"
        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    // Écrits par le fil des fichiers, lus par celui du test.
    private val lines = CopyOnWriteArrayList<String>()
    private val outcomes = CopyOnWriteArrayList<Outcome<ConfigurationRead>>()

    @BeforeEach
    fun shorten() {
        ModLog.writer = LogWriter { lines += it.keyAndArguments() }
        ModLog.forgetReported()
        ApiKeyRequests.clear()
        Publisher.clear()
        FileThread.watchPeriodMillis = 50
        FileThread.watchDelayMillis = 50
        FileThread.watchListener = { outcomes += it }
    }

    @AfterEach
    fun restore() {
        FileThread.stop()
        FileThread.watchPeriodMillis = 2_000
        FileThread.watchDelayMillis = 1_000
        FileThread.watchListener = {}
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `un fichier écrit hors du mod se recharge de source watch, et le résultat part à qui écoute`(@TempDir directory: Path) {
        FileThread.start(directory)
        FileThread.startup(null)
        assertEquals(1, Publisher.generation())

        Files.writeString(directory.resolve("permissions.json"), VIP_PERMISSIONS)
        awaitUntil { Publisher.generation() == 2L }
        assertTrue(Publisher.current().state.permissions.groups.containsKey("vip"))
        awaitUntil { outcomes.isNotEmpty() }
        assertTrue(outcomes.single().result is ConfigurationRead.Valid)
        assertEquals(1L, outcomes.single().published?.previous?.generation)
        assertTrue(lines.any { it.startsWith("aegisperms.log.published 2 watch ") }, lines.toString())
    }

    @Test
    fun `une écriture du mod ne réveille pas la surveillance, et watch-files à false la tait`(@TempDir directory: Path) {
        FileThread.start(directory)
        FileThread.startup(null)
        FileThread.write(Permissions.DELIVERED.copy(groups = Permissions.DELIVERED.groups + ("vip" to Group(weight = 80))), PublicationSource.COMMAND).get()
        assertEquals(2, Publisher.generation())
        Thread.sleep(300)
        assertEquals(2, Publisher.generation(), "l'écriture du mod n'est pas un changement extérieur")
        assertTrue(outcomes.isEmpty())

        Files.writeString(directory.resolve("settings.json"), "{\n  \"schema-version\": 1,\n  \"watch-files\": false\n}\n")
        awaitUntil { Publisher.generation() == 3L }
        assertEquals(false, Publisher.settings().watchFiles)
        Files.writeString(directory.resolve("permissions.json"), VIP_PERMISSIONS)
        Thread.sleep(400)
        assertEquals(3, Publisher.generation(), "la surveillance coupée ne recharge plus")
    }

    private fun awaitUntil(timeoutMillis: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "la condition n'est pas remplie en $timeoutMillis ms : $lines")
            Thread.sleep(20)
        }
    }
}
