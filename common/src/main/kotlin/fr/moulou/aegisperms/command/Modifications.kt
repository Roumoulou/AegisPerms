// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.command

import fr.moulou.aegisperms.command.PermissionsEdits.Refusal
import fr.moulou.aegisperms.command.PermissionsEdits.RuleVerb
import fr.moulou.aegisperms.files.BackupResult
import fr.moulou.aegisperms.files.ConfigurationRead
import fr.moulou.aegisperms.files.ConfigurationWrite
import fr.moulou.aegisperms.files.PublicationSource
import fr.moulou.aegisperms.files.RestoreResult
import fr.moulou.aegisperms.hook.FileThread
import fr.moulou.aegisperms.hook.Outcome
import fr.moulou.aegisperms.hook.Publisher
import fr.moulou.aegisperms.hook.TreeResend
import fr.moulou.aegisperms.model.Mode
import fr.moulou.aegisperms.model.OpLevel
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.aegisperms.model.PermissionsChanges
import fr.moulou.aegisperms.text.TextKeys.Command
import fr.moulou.aegisperms.validate.Issue
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Les modifications de `/aegis` (cahier des charges, 5.3) : la candidate se bâtit sur l'état en vigueur par `PermissionsEdits`, le fil des fichiers l'écrit,
 * la relit et la publie (4.8), et la réponse dit ce qui a changé, la génération et les avertissements nouveaux ; une modification refusée dit pourquoi et ne
 * touche à rien. `reload`, `backup`, `restore` et `mode` suivent le même chemin, le fichier des réglages n'étant jamais écrit, sauf par `restore` (4.10).
 */
internal object Modifications {

    private const val CHANGES_SHOWN = 8

    fun groupCreate(reply: Reply, name: String, weight: Int): Int = change(reply, { reply.text(Command.GROUP_CREATED, name, weight) }) { PermissionsEdits.createGroup(it, name, weight) }

    fun groupDelete(reply: Reply, name: String): Int = change(reply, { reply.text(Command.GROUP_DELETED, name) }) { PermissionsEdits.deleteGroup(it, name) }

    fun groupRename(reply: Reply, name: String, newName: String): Int = change(reply, { reply.text(Command.GROUP_RENAMED, name, newName) }) { PermissionsEdits.renameGroup(it, name, newName) }

    fun groupClone(reply: Reply, name: String, newName: String): Int = change(reply, { reply.text(Command.GROUP_CLONED, name, newName) }) { PermissionsEdits.cloneGroup(it, name, newName) }

    fun groupWeight(reply: Reply, name: String, weight: Int): Int = change(reply, { reply.text(Command.GROUP_WEIGHT_SET, name, weight) }) { PermissionsEdits.setWeight(it, name, weight) }

    fun groupParent(reply: Reply, name: String, parent: String, add: Boolean): Int = change(reply, { reply.text(if (add) Command.GROUP_PARENT_ADDED else Command.GROUP_PARENT_REMOVED, name, parent) }) {
        if (add) PermissionsEdits.addParent(it, name, parent) else PermissionsEdits.removeParent(it, name, parent)
    }

    /** `include-ops <niveau>|none` : le nom d'un niveau d'op (2.5), ou `none` pour retirer les membres d'office. */
    fun groupIncludeOps(reply: Reply, name: String, levelText: String): Int {
        val level = if (levelText == "none") null else OpLevel.entries.firstOrNull { it.name.equals(levelText, ignoreCase = true) } ?: return reply.refuse(Command.INCLUDE_OPS_INVALID, levelText)
        return change(reply, { if (level == null) reply.text(Command.GROUP_INCLUDE_OPS_NONE_SET, name) else reply.text(Command.GROUP_INCLUDE_OPS_SET, name, level.name.lowercase()) }) {
            PermissionsEdits.setIncludeOps(it, name, level)
        }
    }

    fun groupRule(reply: Reply, name: String, verb: RuleVerb, keyText: String): Int {
        val key = Queries.parseKey(reply, keyText) ?: return 0
        return change(reply, { ruleDone(reply, name, verb, key.text) }) { PermissionsEdits.setGroupRule(it, name, key, verb) }
    }

    fun groupNote(reply: Reply, name: String, text: String, keyText: String?): Int {
        val key = keyText?.let { Queries.parseKey(reply, it) ?: return 0 }
        return change(reply, { reply.text(if (text.isBlank()) Command.NOTE_CLEARED else Command.NOTE_SET, name) }) { PermissionsEdits.setGroupNote(it, name, text, key) }
    }

    fun playerGroup(reply: Reply, target: PlayerTarget, group: String, add: Boolean): Int = change(reply, { reply.text(if (add) Command.PLAYER_GROUP_ADDED else Command.PLAYER_GROUP_REMOVED, target.readableForm, group) }) {
        if (add) PermissionsEdits.addPlayerGroup(it, target.uuid, target.readableForm, group) else PermissionsEdits.removePlayerGroup(it, target.uuid, target.readableForm, group)
    }

    fun playerRule(reply: Reply, target: PlayerTarget, verb: RuleVerb, keyText: String): Int {
        val key = Queries.parseKey(reply, keyText) ?: return 0
        return change(reply, { ruleDone(reply, target.readableForm, verb, key.text) }) { PermissionsEdits.setPlayerRule(it, target.uuid, target.readableForm, key, verb) }
    }

    fun playerNote(reply: Reply, target: PlayerTarget, text: String, keyText: String?): Int {
        val key = keyText?.let { Queries.parseKey(reply, it) ?: return 0 }
        return change(reply, { reply.text(if (text.isBlank()) Command.NOTE_CLEARED else Command.NOTE_SET, target.readableForm) }) { PermissionsEdits.setPlayerNote(it, target.uuid, target.readableForm, text, key) }
    }

    /** `/aegis reload` (4.8) : relit, valide, dit quelles règles changent, puis publie ; en échec, l'état en vigueur reste et les constats sont dits. */
    fun reload(reply: Reply): Int {
        if (Queries.state(reply) == null) return 0
        reply.later(FileThread.reload(PublicationSource.RELOAD)) { outcome ->
            when (val read = outcome.result) {
                is ConfigurationRead.Valid -> {
                    val published = outcome.published
                    val changes = published?.previous?.let { PermissionsChanges.between(it, published.state).rules }.orEmpty()
                    reply.broadcast(reply.text(Command.RELOADED, published?.state?.generation ?: Publisher.generation(), changes.size, read.report.warnings.size))
                    reply.lines(changeLines(reply, changes) + read.report.warnings.map(reply::issue))
                }
                is ConfigurationRead.Refused -> refuseWithIssues(reply, reply.text(Command.RELOAD_REFUSED, read.report.errors.size), read.report.issues)
            }
        }
        return 1
    }

    fun backup(reply: Reply, name: String?): Int {
        reply.later(FileThread.backup(name)) { result ->
            when (result) {
                is BackupResult.Done -> reply.broadcast(reply.text(Command.BACKUP_DONE, result.folder))
                BackupResult.InvalidName -> reply.refuse(Command.BACKUP_INVALID_NAME)
                BackupResult.NothingToBackUp -> reply.refuse(Command.BACKUP_NOTHING)
                is BackupResult.Failed -> reply.refuse(Command.BACKUP_FAILED, result.cause.toString())
            }
        }
        return 1
    }

    fun restore(reply: Reply, folder: String): Int {
        reply.later(FileThread.restore(folder)) { outcome ->
            when (val result = outcome.result) {
                is RestoreResult.Restored -> {
                    reply.broadcast(reply.text(Command.RESTORED, folder, outcome.published?.state?.generation ?: Publisher.generation()))
                    val lines = ArrayList<Component>()
                    result.savedAs?.let { lines += reply.text(Command.RESTORED_SAVED_AS, it) }
                    lines += result.report.warnings.map(reply::issue)
                    reply.lines(lines)
                }
                RestoreResult.UnknownBackup -> reply.refuse(Command.RESTORE_UNKNOWN, folder)
                RestoreResult.IncompleteBackup -> reply.refuse(Command.RESTORE_INCOMPLETE, folder)
                is RestoreResult.Refused -> refuseWithIssues(reply, reply.text(Command.RESTORE_REFUSED, result.report.errors.size), result.report.issues)
                is RestoreResult.Failed -> reply.refuse(Command.RESTORE_FAILED, result.cause.toString())
            }
        }
        return 1
    }

    /** `/aegis mode observe|enforce` (5.8) : le mode change en mémoire, tout de suite, et les arbres repartent ; le fichier des réglages ne bouge pas. */
    fun mode(reply: Reply, mode: Mode): Int {
        if (Queries.state(reply) == null) return 0
        Publisher.setMode(mode)
        TreeResend.requestAll()
        reply.broadcast(reply.text(Command.MODE_SET, reply.text(Queries.modeKey(mode))))
        return reply.line(reply.text(Command.MODE_UNTIL_RELOAD, reply.text(Queries.modeKey(Publisher.settings().mode))))
    }

    // ── Le chemin d'une modification (E8) ────────────────────────────────────────

    /**
     * Une modification : [transform] bâtit la candidate sur les droits en vigueur, ou lève un [Refusal] ; une candidate égale aux droits en place ne s'écrit
     * pas ; sinon le fil des fichiers l'écrit, et la réponse vient quand il a fini : [done] en diffusion, puis la génération et les avertissements nouveaux.
     */
    private fun change(reply: Reply, done: () -> MutableComponent, transform: (Permissions) -> Permissions): Int {
        val current = Queries.state(reply) ?: return 0
        val candidate = try {
            transform(current.permissions)
        } catch (refusal: Refusal) {
            return reply.refuse(refusal.key, *refusal.arguments)
        }
        if (candidate == current.permissions) return reply.line(reply.text(Command.NOTHING_CHANGED))
        reply.later(FileThread.write(candidate, PublicationSource.COMMAND)) { outcome -> respond(reply, outcome, done) }
        return 1
    }

    private fun respond(reply: Reply, outcome: Outcome<ConfigurationWrite>, done: () -> MutableComponent) {
        when (val write = outcome.result) {
            is ConfigurationWrite.Written -> {
                reply.broadcast(done())
                val lines = ArrayList<Component>()
                lines += reply.text(Command.CHANGE_PUBLISHED, outcome.published?.state?.generation ?: Publisher.generation())
                val previousWarnings = outcome.before?.report?.warnings.orEmpty().toSet()
                val newWarnings = write.report.warnings.filter { it !in previousWarnings }
                if (newWarnings.isNotEmpty()) {
                    lines += reply.text(Command.CHANGE_WARNINGS, newWarnings.size)
                    lines += newWarnings.map(reply::issue)
                }
                reply.lines(lines)
            }
            is ConfigurationWrite.Refused -> refuseWithIssues(reply, reply.text(Command.CHANGE_REFUSED, write.report.errors.size), write.report.issues)
            ConfigurationWrite.FileMoved -> reply.refuse(Command.FILE_MOVED)
            ConfigurationWrite.ReadOnly -> reply.refuse(Command.READ_ONLY)
            is ConfigurationWrite.Failed -> reply.refuse(Command.WRITE_FAILED, write.cause.toString())
        }
    }

    private fun refuseWithIssues(reply: Reply, head: Component, issues: List<Issue>) {
        reply.refuse(head)
        reply.lines(issues.map(reply::issue))
    }

    private fun changeLines(reply: Reply, changes: List<fr.moulou.aegisperms.model.RuleChange>): List<Component> {
        val lines = ArrayList<Component>()
        for (change in changes.take(CHANGES_SHOWN)) lines += reply.text(Command.CHANGE_RULE, Queries.ownerOf(reply, change.owner), change.key.text, Queries.valueOrNone(reply, change.before), Queries.valueOrNone(reply, change.after))
        if (changes.size > CHANGES_SHOWN) lines += reply.text(Command.CHANGES_MORE, changes.size - CHANGES_SHOWN)
        return lines
    }

    private fun ruleDone(reply: Reply, owner: String, verb: RuleVerb, key: String): MutableComponent =
        if (verb == RuleVerb.UNSET) reply.text(Command.RULE_UNSET, owner, key) else reply.text(Command.RULE_SET, owner, key, verb.literal)
}
