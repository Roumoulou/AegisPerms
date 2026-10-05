// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.model

/**
 * Un ensemble de règles (cahier des charges, 2.1) : les règles propres d'un joueur, ou celles d'un seul groupe, sans ses parents, sous leurs clés compilées.
 * Dans un ensemble, la règle la plus précise qui couvre une clé répond (2.6).
 */
class RuleSet private constructor(val rules: Map<Key, Rule>) {

    companion object {
        val EMPTY = RuleSet(emptyMap())

        /** L'ensemble des règles écrites sous le texte de leur clé. Lève [MalformedKeyException] à la première clé mal formée : la validation passe avant. */
        fun compile(rules: Map<String, Rule>): RuleSet = if (rules.isEmpty()) EMPTY else RuleSet(rules.entries.associate { Keys.parse(it.key) to it.value })
    }

    operator fun get(key: Key): Rule? = rules[key]

    /** La clé de la règle la plus précise qui couvre [key], ou `null` quand l'ensemble n'en dit rien. */
    fun mostPrecise(key: Key): Key? {
        if (rules.isEmpty()) return null
        for (candidate in key.coveringChain) if (rules.containsKey(candidate)) return candidate
        return null
    }
}
