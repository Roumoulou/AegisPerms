// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Le niveau d'op d'un sujet désigné par son UUID (cahier des charges, 6.3 et 6.4), pour les demandes d'API sans source de commande, posées depuis n'importe
 * quel fil : la liste des ops est une table que seul le fil serveur lit sans course. Cette table-ci est nourrie sur le fil serveur, par la liste des ops à
 * `SERVER_STARTED`, quand la liste des joueurs existe, et par l'enveloppe de chaque joueur quand elle se crée, donc à la connexion et à la commande qui suit un `/op` ou un `/deop`. Un UUID
 * inconnu est un non-op. Limite : un `/op` ou un `/deop` d'un joueur hors ligne n'est vu qu'à sa prochaine connexion ou au redémarrage.
 */
object OpLevels {

    private val levels = ConcurrentHashMap<UUID, Int>()

    /** Le niveau d'op de [uuid], de 0 à 4 ; 0 pour un joueur inconnu. */
    fun levelOf(uuid: UUID): Int = levels[uuid] ?: 0

    /** Retient le niveau d'op [level] de [uuid]. */
    fun record(uuid: UUID, level: Int) {
        if (level == 0) levels.remove(uuid) else levels[uuid] = level
    }

    /** Sur le fil serveur : la liste des ops remplace tout ce que la table savait. */
    fun readOps(server: MinecraftServer) {
        val read = HashMap<UUID, Int>()
        for (entry in server.playerList.ops.entries) read[(entry.user ?: continue).id()] = entry.permissions().level().id()
        // Les nouveaux niveaux d'abord, les absents ensuite : une demande posée pendant la lecture ne voit jamais la table vide.
        levels.putAll(read)
        levels.keys.retainAll(read.keys)
    }

    /** Oublie tout niveau. Réservé aux tests. */
    internal fun clear() = levels.clear()
}
