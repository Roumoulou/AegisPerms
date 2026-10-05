// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

import java.util.UUID

/**
 * Le sujet d'une décision (cahier des charges, 2.3) : un joueur, par son UUID, et son niveau d'op, de 0 à 4. Le niveau d'op ouvre l'étape 0 et fait du sujet
 * un membre d'office des groupes qui en comptent (2.5, 2.6).
 */
data class Subject(val uuid: UUID, val opLevel: Int)
