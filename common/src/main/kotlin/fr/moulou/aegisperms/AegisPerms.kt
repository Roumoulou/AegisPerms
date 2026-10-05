// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms

import net.fabricmc.api.DedicatedServerModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Le point d'entrée du mod, sur un serveur dédié seulement. Il dit au journal que le mod est chargé, et ne juge encore aucune commande. */
object AegisPerms : DedicatedServerModInitializer {
    const val MOD_ID = "aegisperms"

    private val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeServer() {
        logger.info("AegisPerms is loaded, no command is judged yet")
    }
}
