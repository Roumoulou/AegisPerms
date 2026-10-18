// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import java.util.concurrent.ConcurrentHashMap

/**
 * Les mods du loader (cahier des charges, 3.2 et 7.5) : le mod d'une classe se trouve par l'API publique du loader, `ModContainer.findPath`, sans pile
 * d'appels. Les classes de vanilla vivent dans le jar du jeu, que le loader tient pour le conteneur `minecraft`.
 */
object LoaderMods : ModLocator {

    /** Le conteneur du JDK, dont aucune commande ne vient. */
    private const val JAVA = "java"

    /** La réponse retenue pour une classe qu'aucun mod ne contient : la table ne garde pas de `null`. */
    private const val NONE = ""

    private val modIdByClassName = ConcurrentHashMap<String, String>()

    override fun modIdOf(className: String): String? = modIdByClassName.computeIfAbsent(className, ::locate).ifEmpty { null }

    override fun isLoaded(modId: String): Boolean = FabricLoader.getInstance().isModLoaded(modId)

    override fun nameOf(modId: String): String? = FabricLoader.getInstance().getModContainer(modId).map { it.metadata.name }.orElse(null)

    override fun versionOf(modId: String): String? = FabricLoader.getInstance().getModContainer(modId).map { it.metadata.version.friendlyString }.orElse(null)

    private fun locate(className: String): String {
        val resource = className.replace('.', '/') + ".class"
        val container = FabricLoader.getInstance().allMods.firstOrNull { it.metadata.id != JAVA && it.findPath(resource).isPresent } ?: return NONE
        return outermost(container).metadata.id
    }

    /** Le mod qui embarque [container], de jar imbriqué en jar imbriqué : une commande écrite dans une bibliothèque embarquée est celle du mod qui l'embarque. */
    private fun outermost(container: ModContainer): ModContainer = generateSequence(container) { it.containingMod.orElse(null) }.last()
}
