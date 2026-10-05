// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/** Une configuration (cahier des charges, 4.5) : les réglages et les droits ensemble, tels que leurs deux fichiers les écrivent. Elle se valide entière. */
data class Configuration(val settings: Settings, val permissions: Permissions)
