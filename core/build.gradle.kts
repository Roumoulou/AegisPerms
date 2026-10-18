// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE core — le cœur du mod, sans Minecraft
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Le modèle de permissions, la résolution, la validation et les fichiers : du
 *  Kotlin pour la JVM, qui ne connaît ni Minecraft, ni Fabric, ni Brigadier. Il se
 *  compile sans Loom, pour se tester sans le jeu, et son jar est le même dans les
 *  trois jars du mod, où l'include de Loom l'imbrique (section 2 du plugin de
 *  version).
 *
 *  Le projet Gradle s'appelle aegisperms-core et vit dans ce dossier, core : la
 *  raison est à la fin de settings.gradle.kts.
 *
 *  Ses tests sont de l'étage 0 : la logique pure, rien du jeu.
 *  Tâche : gradlew :aegisperms-core:test
 *
 *  Plugins, sans version : la racine charge build-logic, qui les porte aux versions
 *  des catalogues (build.gradle.kts de la racine).
 *    - kotlin-jvm             le langage
 *    - kotlin-serialization   les data classes des fichiers, derrière Storify
 * ════════════════════════════════════════════════════════════════════════════════
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
L'identité, lue dans le gradle.properties de la racine. L'inclusion de Loom nomme un
module du build par sa capacité groupe:nom:version (fr.moulou:aegisperms-core:1.0.0),
et refuse un module sans version : les deux se posent ici, ce module n'appliquant pas
le plugin de convention qui les pose pour les autres.
*/
group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get()

/*
Les dépôts de ce module, qu'il déclare lui-même faute d'appliquer Loom
(settings.gradle.kts, « OÙ VIVENT LES DÉPÔTS ») : Maven Central, et Repsy pour
Storify, filtré sur son groupe.
*/
repositories {
    mavenCentral()
    maven("https://repo.repsy.io/roumoulou/maven") {
        name = "Repsy (Storify)"
        content { includeGroup("fr.moulou") }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.storify)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/* La même cible Java que les modules du jeu : la clé `java` du catalogue libs (25). */
val targetJavaVersion = libs.versions.java.get().toInt()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(targetJavaVersion.toString())
}

tasks.test {
    useJUnitPlatform()
    description = "Étage 0 : la logique pure du cœur, sans Minecraft"

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
