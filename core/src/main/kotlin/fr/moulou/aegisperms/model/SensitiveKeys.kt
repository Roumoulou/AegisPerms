// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/** Les clés sensibles de vanilla (cahier des charges, 2.9), que le catalogue marque et dont la validation avertit quand un `allow` en ouvre une. */
object SensitiveKeys {

    val all: List<SensitiveKey> = listOf(
        sensitive(SensitiveReason.FUNCTIONS, "function"),
        sensitive(SensitiveReason.FUNCTIONS, "schedule", "function"),
        sensitive(SensitiveReason.FUNCTIONS, "execute", "if", "function"),
        sensitive(SensitiveReason.FUNCTIONS, "execute", "unless", "function"),
        sensitive(SensitiveReason.FUNCTIONS, "reload"),
        sensitive(SensitiveReason.COMMAND_BLOCKS, "setblock"),
        sensitive(SensitiveReason.COMMAND_BLOCKS, "fill"),
        sensitive(SensitiveReason.COMMAND_BLOCKS, "clone"),
        sensitive(SensitiveReason.COMMAND_BLOCKS, "data"),
        sensitive(SensitiveReason.COMMAND_MINECART, "summon"),
        sensitive(SensitiveReason.COMMAND_MINECART, "execute", "summon"),
        sensitive(SensitiveReason.LECTERN_BOOK, "give"),
        sensitive(SensitiveReason.LECTERN_BOOK, "item"),
        sensitive(SensitiveReason.DATA_WRITING, "execute", "store"),
        sensitive(SensitiveReason.IMPERSONATION, "execute", "as"),
        sensitive(SensitiveReason.IMPERSONATION, "execute", "on"),
        sensitive(SensitiveReason.OP_CHANGE, "op"),
        sensitive(SensitiveReason.OP_CHANGE, "deop"),
    )

    private val reasons: Map<CommandKey, SensitiveReason> = all.associate { it.key to it.reason }

    /** La raison qui rend [key] sensible, ou `null`. */
    fun reasonFor(key: CommandKey): SensitiveReason? = reasons[key]

    private fun sensitive(reason: SensitiveReason, vararg path: String): SensitiveKey = SensitiveKey(Keys.command(Keys.MINECRAFT, path.toList()), reason)
}

/** Une clé sensible : ouverte à un joueur, elle lui donne plus que son nom ne le dit. */
data class SensitiveKey(val key: CommandKey, val reason: SensitiveReason)

/** Ce qu'une clé sensible donne en plus. Le texte de chaque raison vit sous sa clé de traduction, jamais dans le code (5.7). */
enum class SensitiveReason {

    /** L'exécution des fonctions des datapacks, que rien ne juge ensuite. */
    FUNCTIONS,

    /** Un panneau dont le texte porte une commande, ou un bloc de commande qui se déclenche seul. */
    COMMAND_BLOCKS,

    /** Un wagonnet de commande. */
    COMMAND_MINECART,

    /** Un livre dont les composants lisent, posé sur un pupitre, les données de toute entité. */
    LECTERN_BOOK,

    /** L'écriture de données de bloc, d'entité et de stockage, de scores et de barres de boss. */
    DATA_WRITING,

    /** Agir au nom de n'importe quel joueur, dans toutes les commandes qu'on peut utiliser. */
    IMPERSONATION,

    /** Faire d'un complice un op, ou retirer un op. */
    OP_CHANGE;

    /** La clé de traduction de la raison, sous celle du constat d'un `allow` sur une clé sensible. */
    val translationKey: String get() = "aegisperms.issue.allow_sensitive." + name.lowercase()
}
