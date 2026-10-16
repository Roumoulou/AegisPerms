// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.validate.Issue

/** Les avertissements qu'une modification fait naître (cahier des charges, 5.3) : ceux de la configuration écrite qui n'étaient pas dans celle d'avant. */
internal object Warnings {

    /**
     * Les constats de [current] absents de [previous], la ligne mise à part : une écriture du mod décale les lignes du fichier sans rien changer aux
     * constats, et un constat qui n'a bougé que de ligne n'est pas nouveau.
     */
    fun newSince(previous: Collection<Issue>, current: Collection<Issue>): List<Issue> {
        val known = previous.mapTo(HashSet()) { it.copy(line = null) }
        return current.filter { it.copy(line = null) !in known }
    }
}
