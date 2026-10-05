// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.validate

/** Le résultat d'une validation (cahier des charges, 4.5) : ses constats, dans l'ordre où elle les fait. */
data class ValidationReport(val issues: List<Issue>) {

    val errors: List<Issue> get() = issues.filter { it.severity == Severity.ERROR }

    /** Les avertissements, renforcés compris. */
    val warnings: List<Issue> get() = issues.filter { it.severity != Severity.ERROR }

    /** Vrai sans erreur : la configuration peut s'appliquer. */
    val isValid: Boolean get() = issues.none { it.severity == Severity.ERROR }
}
