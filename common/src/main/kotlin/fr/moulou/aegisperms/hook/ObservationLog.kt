// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.hook

import fr.moulou.aegisperms.model.Decision
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Les écarts du mode observation (cahier des charges, 5.8 et 6.5) : la décision que le mod aurait appliquée là où vanilla répond autrement, dite une fois
 * par sujet, par clé et par génération, à l'exécution d'une commande, à une demande d'atome ou d'API seulement, jamais pour un envoi d'arbre ni pour une
 * complétion. Au-delà de soixante lignes dans la minute, le journal compte, et dit le total à la minute suivante.
 */
object ObservationLog {

    /** Le plus de lignes par minute (5.8). */
    const val MAX_LINES_PER_MINUTE = 60

    /** Ce que vanilla répond quand l'écart est noté : la commande s'exécute, l'atome est accordé ; ou il est refusé ; ou le consommateur va à son repli. */
    const val VANILLA_EXECUTES = "allow"
    const val VANILLA_REFUSES = "deny"
    const val VANILLA_DEFAULT = "default"

    /** L'horloge, en millisecondes ; un test la fixe. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Ce qui reçoit un événement et ses arguments à la place du journal du mod ; un test le pose, et `null` écrit au journal. */
    internal var sink: ((String, List<Any?>) -> Unit)? = null

    /** Les écarts déjà dits pour la génération [reportedGeneration], par sujet et par clé. */
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val reportedGeneration = AtomicLong(-1)

    private val lock = Any()
    private var minute = Long.MIN_VALUE
    private var linesThisMinute = 0
    private var suppressed = 0

    /** Un écart : pour [subject], sur [key], le mod aurait décidé [decision], là où vanilla répond [vanilla]. */
    fun deviation(subject: UUID, key: String, decision: Decision, vanilla: String) {
        val generation = Publisher.generation()
        val last = reportedGeneration.get()
        if (generation != last && reportedGeneration.compareAndSet(last, generation)) reported.clear()
        if (!reported.add("$subject|$key")) return
        synchronized(lock) {
            val now = clock() / 60_000
            if (now != minute) {
                if (suppressed > 0) emit(ModLog.OBSERVE_SUPPRESSED, listOf(suppressed))
                minute = now
                linesThisMinute = 0
                suppressed = 0
            }
            if (linesThisMinute >= MAX_LINES_PER_MINUTE) {
                suppressed++
                return
            }
            linesThisMinute++
        }
        emit(ModLog.OBSERVE_DEVIATION, listOf(subject, key, decision.name.lowercase(), vanilla))
    }

    /** Oublie tout. Réservé aux tests. */
    internal fun clear() {
        reported.clear()
        reportedGeneration.set(-1)
        synchronized(lock) {
            minute = Long.MIN_VALUE
            linesThisMinute = 0
            suppressed = 0
        }
    }

    private fun emit(event: String, arguments: List<Any?>) {
        val target = sink
        if (target != null) target(event, arguments) else ModLog.warn(event, *arguments.toTypedArray())
    }
}
