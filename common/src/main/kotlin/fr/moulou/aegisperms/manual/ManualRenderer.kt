// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.manual

import fr.moulou.aegisperms.files.CommandRequirement
import fr.moulou.aegisperms.text.Lang
import fr.moulou.aegisperms.text.TextKeys
import fr.moulou.aegisperms.text.Texts

/**
 * Le rendu Markdown d'un manuel (cahier des charges, 5.10), dans la langue du manuel : une tête, le sommaire des mods, puis une section par mod, séparées par
 * `---`, chacune avec la table de ses commandes racines et, par commande, sa description et son arbre en liste indentée. Chaque mot vient des fichiers de
 * langue (5.7) ; le texte finit par une fin de ligne et n'en porte que des `LF`.
 */
object ManualRenderer {

    /** Le texte d'une clé en une langue, ses arguments remplis : [Texts.text] par défaut ; un test de l'étage 0, sans les ressources du jar, donne le sien. */
    fun interface TextSource {
        fun text(key: String, lang: Lang, arguments: List<Any?>): String
    }

    private val JAR: TextSource = TextSource { key, lang, arguments -> Texts.text(key, lang, *arguments.toTypedArray()) }

    fun render(manual: Manual, source: TextSource = JAR): String = Rendering(source).render(manual)

    private class Rendering(private val source: TextSource) {

        fun render(manual: Manual): String = buildString {
            val lang = manual.lang
            appendLine("# " + text(TextKeys.Manual.TITLE, lang))
            appendLine()
            appendLine("> " + text(TextKeys.Manual.GENERATED, lang, manual.modVersion, manual.gameVersion, manual.date))
            val requirePrefix = text(if (manual.requirePrefix) TextKeys.Command.YES else TextKeys.Command.NO, lang)
            appendLine("> " + text(TextKeys.Manual.STATE, lang, manual.mode.name.lowercase(), requirePrefix, manual.mods.size, manual.commandCount, manual.nodeCount))
            manual.descriptionsIssue?.let { appendLine("> " + text(TextKeys.Manual.DESCRIPTIONS_UNREADABLE, lang, it)) }
            appendLine()
            appendLine("## " + text(TextKeys.Manual.SUMMARY, lang))
            appendLine()
            appendLine(text(TextKeys.Manual.SUMMARY_HEADER, lang))
            appendLine("|---|---|---|---|---|")
            for (mod in manual.mods) appendLine("| `${mod.id}` | ${cell(mod.name)} | ${cell(mod.version)} | ${mod.commands.size} | ${mod.nodeCount} |")
            appendLine()
            appendLine(text(TextKeys.Manual.LEGEND, lang))
            for (mod in manual.mods) {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## " + titleOf(mod))
                appendLine()
                appendLine(text(TextKeys.Manual.COMMANDS_HEADER, lang))
                appendLine("|---|---|---|---|---|---|---|")
                for (command in mod.commands) appendLine(rowOf(command, lang))
                for (command in mod.commands) {
                    appendLine()
                    appendLine("### /" + command.name)
                    appendLine()
                    command.description?.let {
                        appendLine(it)
                        appendLine()
                    }
                    node(command.root, parent = null, depth = 0, lang)
                }
            }
        }

        private fun titleOf(mod: ManualMod): String {
            val detail = listOfNotNull(mod.name, mod.version).joinToString(" ")
            return if (detail.isEmpty()) mod.id else "${mod.id} ($detail)"
        }

        private fun rowOf(command: ManualCommand, lang: Lang): String {
            val root = command.root
            val cells = listOf(
                "`/${command.name}`",
                command.prefixedForm?.let { "`/$it`" }.orEmpty(),
                command.aliases.joinToString(", ") { "`/$it`" },
                "`${root.key.text}`",
                requirementOf(root.requirement, lang),
                root.sensitive.orEmpty(),
                cell(command.description),
            )
            return cells.joinToString(" | ", prefix = "| ", postfix = " |")
        }

        /** Une ligne par nœud : son nom, sa clé, ce qui compte, sa description ; la racine ne répète pas la sienne, dite sous le titre. */
        private fun StringBuilder.node(node: ManualNode, parent: ManualNode?, depth: Int, lang: Lang) {
            val marks = ArrayList<String>()
            if (node.executable) marks += text(TextKeys.Manual.EXECUTABLE, lang)
            if (node.requirement != CommandRequirement.NONE && node.requirement != parent?.requirement) marks += requirementOf(node.requirement, lang)
            if (node.inheritsKey) marks += text(TextKeys.Manual.INHERITED_KEY, lang)
            if (node.reference) marks += text(TextKeys.Manual.SHARED, lang)
            if (node.moreDemanding) marks += text(TextKeys.Manual.MORE_DEMANDING, lang)
            node.sensitive?.let { marks += text(TextKeys.Manual.SENSITIVE, lang, it) }
            node.redirect?.let { marks += redirectOf(it, lang) }
            if (node.aliases.isNotEmpty()) marks += text(TextKeys.Manual.ALIASES, lang, node.aliases.joinToString(", ") { "`/$it`" })
            append("  ".repeat(depth)).append("- ").append(if (node.literal) "`${node.name}`" else "`<${node.name}>`").append(" : `").append(node.key.text).append('`')
            if (marks.isNotEmpty()) append(", ").append(marks.joinToString(", "))
            if (depth > 0) node.description?.let { append(". ").append(it) }
            append('\n')
            for (child in node.children) node(child, node, depth + 1, lang)
        }

        private fun requirementOf(requirement: CommandRequirement, lang: Lang): String = when (requirement) {
            CommandRequirement.NONE -> text(TextKeys.Manual.REQUIREMENT_NONE, lang)
            CommandRequirement.MOD_PREDICATE -> text(TextKeys.Manual.REQUIREMENT_MOD, lang)
            else -> text(TextKeys.Manual.REQUIREMENT_LEVEL, lang, requirement.opLevel?.level, requirement.name.lowercase())
        }

        private fun redirectOf(redirect: ManualRedirect, lang: Lang): String = when (redirect) {
            ManualRedirect.AnyCommand -> text(TextKeys.Manual.REDIRECT_ANY, lang)
            is ManualRedirect.ToKey -> text(TextKeys.Manual.REDIRECT, lang, "`${redirect.key.text}`")
            is ManualRedirect.ToName -> text(TextKeys.Manual.REDIRECT, lang, "`/${redirect.name}`")
        }

        /** Une cellule de table : sans barre verticale ni saut de ligne, qui casseraient la ligne ; vide pour `null`. */
        private fun cell(value: String?): String = value.orEmpty().replace("|", "\\|").replace('\n', ' ')

        private fun text(key: String, lang: Lang, vararg arguments: Any?): String = source.text(key, lang, arguments.asList())
    }
}
