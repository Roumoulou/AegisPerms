// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** Un dossier qui n'apparaît qu'entier : la copie `last-good`, une sauvegarde (cahier des charges, 4.9 et 4.10). */
class StagedDirectoryTest {

    @TempDir
    lateinit var dir: Path

    private val parent: Path get() = dir.resolve("serveur de l'été").resolve("aegisperms")

    private val target: Path get() = parent.resolve("last-good")

    @Test
    fun `un dossier s'écrit à côté, sous un nom temporaire, et n'apparaît qu'une fois mis en place`() {
        val staged = StagedDirectory.stage(target) { copy ->
            Files.writeString(copy.resolve("settings.json"), "{}")
            assertFalse(Files.exists(target))
        }
        assertEquals(parent, staged.parent)
        assertTrue(Regex("""last-good\.[0-9a-f]{8}\.tmp""").matches(staged.name), staged.name)
        assertFalse(Files.exists(target))

        StagedDirectory.move(staged, target)
        assertEquals("{}", Files.readString(target.resolve("settings.json")))
        assertEquals(setOf("last-good"), names())
    }

    @Test
    fun `une panne pendant l'écriture ne laisse rien, et remonte`() {
        assertThrows(IOException::class.java) {
            StagedDirectory.stage(target) { copy ->
                Files.writeString(copy.resolve("settings.json"), "{}")
                throw IOException("disk full")
            }
        }
        assertEquals(emptySet<String>(), names())
    }

    @Test
    fun `un dossier ne se met jamais en place par-dessus un autre`() {
        Files.createDirectories(target)
        Files.writeString(target.resolve("settings.json"), "ancien")
        val staged = StagedDirectory.stage(target) { copy -> Files.writeString(copy.resolve("settings.json"), "nouveau") }

        assertThrows(FileAlreadyExistsException::class.java) { StagedDirectory.move(staged, target) }
        assertEquals("ancien", Files.readString(target.resolve("settings.json")))
        assertEquals("nouveau", Files.readString(staged.resolve("settings.json")))
    }

    @Test
    fun `le balayage retire les temporaires orphelins de ce dossier, et eux seuls`() {
        val orphan = parent.resolve("last-good.0a1b2c3d.tmp")
        Files.createDirectories(orphan.resolve("sous-dossier"))
        Files.writeString(orphan.resolve("sous-dossier").resolve("settings.json"), "{}")
        Files.writeString(parent.resolve("last-good.89abcdef.tmp"), "un fichier au même motif")
        // Ce que le balayage ne reconnaît pas : un autre motif, un autre dossier, la copie et sa précédente.
        val kept = listOf("last-good.tmp", "last-good.0a1b2c3.tmp", "last-good.0a1b2c3z.tmp", "last-good.0a1b2c3d.tmp.bak", "backups.0a1b2c3d.tmp", "last-good", "last-good.old")
        for (name in kept) Files.createDirectories(parent.resolve(name))

        StagedDirectory.sweep(target)
        assertEquals(kept.toSet(), names())
    }

    @Test
    fun `balayer ou retirer ce qui n'existe pas est sans effet`() {
        StagedDirectory.sweep(dir.resolve("absent").resolve("last-good"))
        StagedDirectory.delete(dir.resolve("absent"))
        assertFalse(Files.exists(dir.resolve("absent")))
    }

    private fun names(): Set<String> = Files.list(parent).use { found -> found.map { it.name }.toList().toSet() }
}
