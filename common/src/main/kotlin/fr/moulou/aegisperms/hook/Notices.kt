// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.model.PermissionsChanges
import fr.moulou.aegisperms.text.Recipient
import fr.moulou.aegisperms.text.TextKeys.Notice
import fr.moulou.aegisperms.validate.Severity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet

/**
 * Ce que le mod dit aux ops d'au moins `admin-level` en ligne (cahier des charges, 4.8, 4.9, 5.6) : le repli du démarrage tant qu'il dure et les
 * avertissements renforcés, à la connexion ; le résultat d'un rechargement par la surveillance, à tous. Chaque texte part dans la langue du destinataire.
 */
object Notices {

    /** À la connexion d'un op de niveau [level] : rien sous `admin-level` ; sinon le repli du démarrage et les avertissements renforcés de la dernière publication. */
    fun onJoin(player: ServerPlayer, level: Int) {
        val settings = Publisher.settings()
        if (level < settings.adminLevel.level) return
        val last = Publisher.lastPublication ?: return
        val recipient = Recipient.of(player, settings.language)
        if (last.fallback.isNotEmpty()) player.sendSystemMessage(recipient.text(Notice.FALLBACK, last.fallback.joinToString(", ") { it.fileName }))
        for (issue in last.report.issues) {
            if (issue.severity == Severity.REINFORCED) player.sendSystemMessage(recipient.text(Notice.REINFORCED, recipient.issue(issue)))
        }
    }

    /** Le résultat d'un rechargement par la surveillance (4.8), aux ops d'au moins `admin-level` en ligne : appliqué, avec ses nombres, ou refusé. */
    fun watch(server: MinecraftServer, outcome: Outcome<ConfigurationRead>) {
        val settings = Publisher.settings()
        for (admin in admins(server)) {
            val recipient = Recipient.of(admin, settings.language)
            val message = when (val read = outcome.result) {
                is ConfigurationRead.Valid -> {
                    val published = outcome.published
                    val changes = published?.previous?.let { PermissionsChanges.between(it, published.state).rules.size } ?: 0
                    recipient.text(Notice.WATCH_APPLIED, published?.state?.generation ?: Publisher.generation(), changes, read.report.warnings.size)
                }
                is ConfigurationRead.Refused -> recipient.text(Notice.WATCH_REFUSED, read.report.errors.size)
            }
            admin.sendSystemMessage(message)
        }
    }

    private fun admins(server: MinecraftServer): List<ServerPlayer> {
        val required = Publisher.settings().adminLevel.level
        return server.playerList.players.filter { levelOf(it) >= required }
    }

    private fun levelOf(player: ServerPlayer): Int = (player.permissions() as? LevelBasedPermissionSet)?.level()?.id() ?: 0
}
