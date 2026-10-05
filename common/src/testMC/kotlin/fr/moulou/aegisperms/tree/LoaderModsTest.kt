// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import fr.moulou.aegisperms.AegisPerms
import net.minecraft.server.commands.TeleportCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le mod d'une classe, demandé au loader amorcé (cahier des charges, 3.2 et 7.7) : le jeu est le conteneur `minecraft`, pour vanilla comme pour un mod.
 *
 * La classe d'un mod en développement ne s'éprouve pas ici. À cet étage, le conteneur du mod ne tient que son dossier de ressources : Loom ne donne le
 * regroupement de ses dossiers de classes (`fabric.classPathGroups`) qu'à ses runs. C'est l'étage 2 qui l'éprouve, sur un serveur de test.
 */
class LoaderModsTest {

    @Test
    fun `une commande de vanilla a le jeu pour mod`() {
        assertEquals("minecraft", LoaderMods.modIdOf(TeleportCommand::class.java.name))
    }

    @Test
    fun `une classe d'aucun mod n'a pas de mod`() {
        assertNull(LoaderMods.modIdOf(String::class.java.name))
        assertNull(LoaderMods.modIdOf("fr.moulou.aegisperms.Absent"))
    }

    @Test
    fun `le loader dit les mods chargés`() {
        assertTrue(LoaderMods.isLoaded("minecraft"))
        assertTrue(LoaderMods.isLoaded(AegisPerms.MOD_ID))
        assertFalse(LoaderMods.isLoaded("aegisperms-absent"))
    }
}
