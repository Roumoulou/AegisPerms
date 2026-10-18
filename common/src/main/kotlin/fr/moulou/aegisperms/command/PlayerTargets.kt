// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.model.EffectiveState
import fr.moulou.aegisperms.model.ReadableForm
import net.minecraft.server.MinecraftServer
import java.util.UUID

/** Le joueur qu'une commande `/aegis` vise (cahier des charges, 5.4) : son UUID, qui est son identité, et son pseudo quand il est connu. */
data class PlayerTarget(val uuid: UUID, val name: String?) {

    /** La forme lisible sous laquelle le fichier des droits l'écrit (4.4). */
    val readableForm: String get() = ReadableForm.of(name, uuid)
}

/** Ce que la résolution d'un `<joueur>` rend. */
sealed interface TargetResolution {

    data class Found(val target: PlayerTarget) : TargetResolution

    /** Aucune source ne connaît ce pseudo. */
    data class Unknown(val input: String) : TargetResolution

    /** Les sources désignent plusieurs UUID : la commande se refuse, et le joueur s'écrit par son UUID. */
    data class Ambiguous(val input: String, val candidates: List<UUID>) : TargetResolution
}

/** Les sources qu'une résolution consulte : le serveur, son cache de profils, et les joueurs du fichier des droits. */
interface PlayerLookup {

    /** Le joueur en ligne de ce pseudo, ou `null` quand aucun ne l'est. */
    fun online(name: String): PlayerTarget?

    /** Le joueur que le cache de profils du serveur connaît sous ce pseudo. */
    fun cached(name: String): PlayerTarget?

    /** Le pseudo d'un UUID, par le joueur en ligne, le fichier des droits ou le cache de profils. */
    fun nameOf(uuid: UUID): String?

    /** Les joueurs du fichier des droits. */
    fun knownPlayers(): Collection<PlayerTarget>
}

/** Les sources d'un serveur et de son état en vigueur. */
class ServerLookup(private val server: MinecraftServer, private val state: EffectiveState?) : PlayerLookup {

    override fun online(name: String): PlayerTarget? = server.playerList.getPlayerByName(name)?.let { PlayerTarget(it.uuid, it.nameAndId().name()) }

    override fun cached(name: String): PlayerTarget? = runCatching { server.services().nameToIdCache().get(name).orElse(null) }.getOrNull()?.let { PlayerTarget(it.id(), it.name()) }

    override fun nameOf(uuid: UUID): String? = server.playerList.getPlayer(uuid)?.nameAndId()?.name()
        ?: state?.players?.get(uuid)?.name
        ?: runCatching { server.services().nameToIdCache().get(uuid).orElse(null)?.name() }.getOrNull()

    override fun knownPlayers(): Collection<PlayerTarget> = state?.players?.values?.map { PlayerTarget(it.uuid, it.name) }.orEmpty()
}

/**
 * La résolution d'un `<joueur>` (5.4) : un UUID écrit tel quel, avec ou sans tirets, désigne ce joueur, venu ou jamais venu ; un pseudo se cherche chez le
 * joueur en ligne, dans le cache de profils du serveur, qui suit les renommages, et dans les formes lisibles du fichier des droits, pseudo comparé sans la
 * casse. Deux UUID différents entre ces sources font refuser la commande, avec les UUID en présence.
 */
object PlayerTargets {

    private val UNDASHED_UUID = Regex("[0-9a-fA-F]{32}")

    fun resolve(input: String, lookup: PlayerLookup): TargetResolution {
        val text = input.trim()
        parseUuid(text)?.let { return TargetResolution.Found(PlayerTarget(it, lookup.nameOf(it))) }
        val candidates = LinkedHashMap<UUID, String?>()
        lookup.online(text)?.let { candidates[it.uuid] = it.name }
        lookup.cached(text)?.let { candidates.putIfAbsent(it.uuid, it.name) }
        for (known in lookup.knownPlayers()) if (known.name != null && known.name.equals(text, ignoreCase = true)) candidates.putIfAbsent(known.uuid, known.name)
        return when (candidates.size) {
            0 -> TargetResolution.Unknown(text)
            1 -> candidates.entries.single().let { TargetResolution.Found(PlayerTarget(it.key, it.value)) }
            else -> TargetResolution.Ambiguous(text, candidates.keys.toList())
        }
    }

    /** L'UUID que [text] écrit, en forme canonique ou sans tirets, ou `null`. */
    fun parseUuid(text: String): UUID? {
        if (UNDASHED_UUID.matches(text)) return ReadableForm.parseUuid(text.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
        return ReadableForm.parseUuid(text)
    }
}
