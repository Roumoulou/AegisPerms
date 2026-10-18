// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.files

import fr.moulou.aegisperms.model.Group
import fr.moulou.aegisperms.model.Permissions
import fr.moulou.storify.validation.DuplicateKeyException
import fr.moulou.storify.validation.PathSegment
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Ce que le format des fichiers du mod ajoute à la lecture stricte de Storify : le numéro de structure et sa migration (cahier des charges, 4.2). */
class JsonFileFormatTest {

    @TempDir
    lateinit var dir: Path

    private val file: Path get() = dir.resolve("dossier à accents").resolve("permissions.json")

    /** Une structure 2 fictive, dont la 1 nommait `ranks` ce qu'elle nomme `groups`. */
    private val migrating = JsonFileFormat.permissions(
        SchemaMigrations(current = 2, steps = mapOf(1 to { root -> JsonObject(root.entries.associate { (key, value) -> (if (key == "ranks") "groups" else key) to value }) })),
    )

    @Test
    fun `le format refuse lui-même un fichier sans numéro de structure, au numéro mal écrit ou plus récent que le mod`() {
        assertEquals(SchemaVersion.Missing, assertThrows(SchemaVersionException::class.java) { read("""{ "groups": {} }""") }.version)
        assertTrue(assertThrows(SchemaVersionException::class.java) { read("""{ "schema-version": "1" }""") }.version is SchemaVersion.Invalid)
        assertTrue(assertThrows(SchemaVersionException::class.java) { read("""{ "schema-version": 0 }""") }.version is SchemaVersion.Invalid)
        assertEquals(SchemaVersion.Known(2), assertThrows(SchemaVersionException::class.java) { read("""{ "schema-version": 2 }""") }.version)
    }

    @Test
    fun `un fichier d'un numéro plus ancien se lit migré, au numéro courant`() {
        val migrated = read("""{ "schema-version": 1, "ranks": { "default": {} } }""", migrating)
        assertEquals(Permissions(schemaVersion = 2, groups = mapOf("default" to Group())), migrated)
        assertEquals(migrated, read("""{ "schema-version": 2, "groups": { "default": {} } }""", migrating))
    }

    @Test
    fun `un champ écrit deux fois est refusé dans un fichier à migrer comme dans un autre, avec son chemin`() {
        val twice = "{\n  \"schema-version\": 1,\n  \"ranks\": {\n    \"vip\": {},\n    \"vip\": {}\n  }\n}"
        val duplicate = assertThrows(DuplicateKeyException::class.java) { read(twice, migrating) }
        assertEquals("vip", duplicate.key)
        assertEquals(5, duplicate.line)
        assertEquals(listOf(PathSegment.Key("ranks"), PathSegment.Key("vip")), duplicate.path)
        assertEquals(5, assertThrows(DuplicateKeyException::class.java) { read(twice.replace("ranks", "groups")) }.line)
    }

    @Test
    fun `un texte mal formé est laissé au lecteur strict, qui le refuse`() {
        for (text in listOf("""{ "schema-version": 1, }""", """{ "schema-version": 1 // note""" + "\n}", "[]", "")) {
            val failure = assertThrows(SerializationException::class.java, { read(text) }, text)
            assertFalse(failure is SchemaVersionException, text)
        }
    }

    @Test
    fun `un BOM posé par un éditeur est toléré, migration comprise`() {
        val bom = "﻿"
        assertEquals(Permissions(schemaVersion = 1), read("""$bom{ "schema-version": 1 }"""))
        assertEquals(Permissions(schemaVersion = 2, groups = mapOf("default" to Group())), read("""$bom{ "schema-version": 1, "ranks": { "default": {} } }""", migrating))
    }

    private fun read(text: String, format: JsonFileFormat = JsonFileFormat.PERMISSIONS): Permissions {
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return format.decodeFromPath(Permissions.serializer(), file)
    }
}
