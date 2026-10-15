// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.text

import fr.moulou.aegisperms.hook.HookFixtures
import fr.moulou.aegisperms.hook.LogLine
import fr.moulou.aegisperms.hook.LogWriter
import fr.moulou.aegisperms.hook.ModLog
import fr.moulou.aegisperms.model.Language
import fr.moulou.aegisperms.validate.ConfigurationFile
import fr.moulou.aegisperms.validate.Issue
import fr.moulou.aegisperms.validate.IssueArgument
import fr.moulou.aegisperms.validate.IssueKind
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.Locale

/** Les composants et la langue (cahier des charges, 5.7), face au jeu : les fichiers de langue du jar, les événements du journal, le repli. */
class MessagesTest {

    companion object {
        private val DEFAULT_WRITER: LogWriter = ModLog.writer

        /** Les événements du journal : les constantes de `ModLog` écrites en snake case. */
        private val logEvents: List<String> = ModLog::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
            .filter { it.matches(Regex("[a-z_]+")) }

        @JvmStatic
        @BeforeAll
        fun bootstrap() = HookFixtures.bootstrap()
    }

    @AfterEach
    fun restore() {
        ModLog.writer = DEFAULT_WRITER
    }

    @Test
    fun `les fichiers de langue sont dans le jar, avec les mêmes clés`() {
        assertTrue(Texts.keys(Lang.FR).isNotEmpty())
        assertEquals(Texts.keys(Lang.FR), Texts.keys(Lang.EN))
    }

    @Test
    fun `chaque événement du journal a son texte dans les deux langues`() {
        assertTrue(logEvents.size >= 14, logEvents.toString())
        for (lang in Lang.entries) for (event in logEvents) assertTrue(Texts.template(TextKeys.LOG_PREFIX + event, lang) != null, "$lang : $event")
    }

    @Test
    fun `aucune clé des fichiers n'est orpheline`() {
        val named = TextKeys::class.java.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }.map { it.get(null) as String }.filter { !it.endsWith(".") }
        val known = IssueKind.entries.map { it.translationKey } + logEvents.map { TextKeys.LOG_PREFIX + it } + named
        assertEquals(emptySet<String>(), Texts.keys(Lang.FR) - known.toSet())
    }

    @Test
    fun `le réglage language impose la langue, et auto suit la machine pour la console`() {
        assertEquals(Lang.FR, Messages.consoleLanguage(Language.FR))
        assertEquals(Lang.EN, Messages.consoleLanguage(Language.EN))
        assertEquals(Lang.ofCode(Locale.getDefault().language), Messages.consoleLanguage(Language.AUTO))
    }

    @Test
    fun `un composant porte sa clé, le gabarit de la langue en repli et ses arguments`() {
        val component = Messages.component(IssueKind.UNKNOWN_GROUP.translationKey, Lang.EN, "vip")
        val contents = component.contents as TranslatableContents
        assertEquals(IssueKind.UNKNOWN_GROUP.translationKey, contents.key)
        assertEquals("Unknown group: %s", contents.fallback)
        assertEquals("Unknown group: vip", component.string)
    }

    /** Le serveur connaît les clés du mod par son `en_us.json`, que la Fabric API charge côté serveur : rendu par lui, un composant à repli parle anglais. */
    @Test
    fun `la console reçoit le texte résolu dans sa langue, un joueur un composant à repli`() {
        val console = Recipient(Lang.FR, translatable = false)
        val player = Recipient(Lang.FR, translatable = true)
        assertEquals("Groupe inconnu : vip", console.text(IssueKind.UNKNOWN_GROUP.translationKey, "vip").string)
        val forPlayer = player.text(IssueKind.UNKNOWN_GROUP.translationKey, "vip")
        assertEquals("Groupe inconnu : %s", (forPlayer.contents as TranslatableContents).fallback)
        assertEquals("Unknown group: vip", forPlayer.string)
    }

    @Test
    fun `un constat se répète avec son fichier, son chemin et sa ligne`() {
        val issue = Issue(IssueKind.UNKNOWN_GROUP, ConfigurationFile.PERMISSIONS, listOf("players", "Bob#uuid", "groups", "0"), listOf(IssueArgument.Literal("vip")), 12)
        assertEquals("permissions.json:players.Bob#uuid.groups.0:12", Messages.whereOf(issue))
        assertEquals("permissions.json:players.Bob#uuid.groups.0:12 : Groupe inconnu : vip", Recipient(Lang.FR, translatable = false).issue(issue).string)
        assertEquals("settings.json", Messages.whereOf(Issue(IssueKind.MISSING_DEFAULT, ConfigurationFile.SETTINGS, emptyList())))
    }

    @Test
    fun `le journal écrit le texte résolu et garde la clé et ses arguments`() {
        val lines = ArrayList<LogLine>()
        ModLog.writer = LogWriter { lines += it }
        ModLog.info(ModLog.FALLBACK, "settings.json", "last_good")
        val line = lines.single()
        assertEquals(TextKeys.LOG_PREFIX + ModLog.FALLBACK, line.key)
        assertEquals("aegisperms.log.fallback settings.json last_good", line.keyAndArguments())
        assertEquals(Texts.text(line.key, Messages.consoleLanguage(Language.AUTO), "settings.json", "last_good"), line.text)
        assertTrue(line.text.contains("settings.json") && !line.text.contains("aegisperms.log"), line.text)
    }
}
