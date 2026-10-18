// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

@file:Suppress("AvoidDuplicateDependencies") // IntelliJ souligne junit-jupiter, déclaré pour l'étage 0 et pour l'étage 1 : deux source sets, deux déclarations voulues.

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE common — le code du mod, partagé par toutes les versions du jeu
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Tout le mod, sauf le cœur (module core) et ce qu'une version du jeu ne partage
 *  pas avec les autres : le code qui touche au jeu, les mixins, les ressources, et
 *  ses trois étages de test. Il se compile UNE fois, contre la dernière release
 *  servie, et ne livre aucun jar : ce sont les modules de version qui assemblent le
 *  mod, chacun pour son jeu.
 *
 *  ┌───────────────────────────────────────────────────────────────────────────┐
 *  │  1  la version de compilation et les dépendances                          │
 *  │  2  les étages de test : le 0 joué ici, le 1 et le 2 rejoués par version  │
 *  │  3  le classpath des étages 0, 1 et 2                                     │
 *  │  4  ce que common publie pour les modules de version                      │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  Plugins :
 *    - aegisperms.loom-module   Loom, Kotlin et sa sérialisation, Java, dépôts,
 *                               identité
 * ════════════════════════════════════════════════════════════════════════════════
 */

plugins {
    id("aegisperms.loom-module")
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 1 — LA VERSION DE COMPILATION ET LES DÉPENDANCES
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  common se compile contre la dernière release de Minecraft servie, 26.3, et lit
 *  son catalogue, `mc263` : le code commun suit toujours la dernière release,
 *  jamais un snapshot. Ce qu'il nomme doit exister, sous le même nom, dans toutes
 *  les versions servies ; ce qu'une version plus ancienne n'a pas, ou nomme
 *  autrement, passe par un pont de version, que chaque module de version implémente
 *  contre son jeu. Compilé une fois, il tourne ensuite sur chacune, et les étages 1
 *  et 2 de chaque module de version le vérifient. À la release suivante, common
 *  monte au catalogue de celle-ci.
 *
 *  - minecraft                  : le jeu lui-même, fourni et câblé par Loom.
 *  - fabric-loader              : le chargeur : entrypoints et annotations de mixin.
 *  - fabric-api                 : les API haut niveau : events, commandes,
 *                                 permissions.
 *  - fabric-language-kotlin     : l'adaptateur Kotlin de Fabric ; il embarque le
 *                                 runtime Kotlin, kotlin-reflect,
 *                                 kotlinx-serialization et kotlinx-datetime.
 *  - kotlinx-serialization-json : fourni au runtime par fabric-language-kotlin,
 *                                 déclaré quand même pour compiler avec une version
 *                                 contrôlée.
 *  - aegisperms-core            : le cœur du mod, module de ce build.
 *  - storify                    : les fichiers JSON du mod, permissions.json et
 *                                 settings.json.
 *  - fabric-permissions-api     : l'API de permissions de lucko, dont le mod est le
 *                                 fournisseur.
 *
 *  Aucune de ces dépendances ne sort de common : ses classes seules partent vers les
 *  modules de version (section 4), qui déclarent les leurs et embarquent le cœur,
 *  Storify et l'API de lucko dans leur jar.
 * ════════════════════════════════════════════════════════════════════════════════
 */
dependencies {
    minecraft(mc263.minecraft)

    implementation(mc.fabric.loader)
    implementation(mc263.fabric.api)
    implementation(mc.fabric.language.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":aegisperms-core"))
    implementation(libs.storify)
    implementation(mc.fabric.permissions.api)
}

/*
Les ressources de common ne passent pas par ce module : chaque module de version
les traite avec les siennes, parce que fabric.mod.json s'y remplit pour sa version,
et que le mod doit les trouver dans un seul dossier. Les traiter aussi ici poserait
sur le classpath un second fabric.mod.json, gabarit non rempli.

common ne livre pas de jar : ses classes entrent dans celui de chaque version.

Les runs `client` et `server` que Loom crée pour tout module n'ont pas de sens ici,
sans mod ni fabric.mod.json : ils restent, inertes, et n'apparaissent pas dans
IntelliJ, Loom n'y générant les configurations de lancement que pour la racine.
*/
tasks.processResources { enabled = false }
tasks.jar { enabled = false }
tasks.named("sourcesJar") { enabled = false }

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 2 — LES TROIS ÉTAGES DE TEST
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Trois source sets de test, tous ici. Entre les deux premiers, c'est le
 *  COMPILATEUR qui tient la frontière.
 *
 *  ┌─ src/test ─ étage 0, joué ici ────────────────────────────────────────────┐
 *  │  La logique pure du mod : les clés calculées sur des arbres Brigadier     │
 *  │  synthétiques. AUCUN accès à Minecraft, et ce n'est pas une convention :  │
 *  │  un import du jeu ne compile pas. Indépendant de la version du jeu, il ne │
 *  │  se joue qu'une fois. Le cœur a son propre étage 0, dans son module.      │
 *  │  Tâche : gradlew :common:test                                             │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  ┌─ src/testMC ─ étage 1, rejoué par chaque module de version ───────────────┐
 *  │  Le jeu amorcé par fabric-loader-junit : les registres, et le code du mod │
 *  │  face aux classes du jeu, sans serveur. Compilé ici, ses classes partent  │
 *  │  dans `testMCElements` (section 4).                                       │
 *  │  Tâche : gradlew :mc-<version>:testMC                                     │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  ┌─ src/gametest ─ étage 2, rejoué par chaque module de version ─────────────┐
 *  │  Un vrai serveur de test, sans fenêtre : le mod, ses mixins appliqués, de │
 *  │  vrais joueurs simulés. Le mod de test s'assemble ici en un jar,          │
 *  │  `gametestElements` (section 4).                                          │
 *  │  Tâche : gradlew :mc-<version>:runGameTest                                │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  ── CE QUE L'ÉTAGE 1 NE DONNE PAS : LES MIXINS ──────────────────────────────
 *  Mesuré chez TravellingDimension, et refait sur deux cadres de test : une classe
 *  du jeu chargée depuis un test de l'étage 1 ne porte AUCUNE méthode de synthèse
 *  d'un mixin. Tout ce qui passe par un mixin se vérifie à l'étage 2.
 *
 *  ── COUPER L'HÉRITAGE NE SUFFIT PAS ─────────────────────────────────────────
 *  Les deux lignes `setExtendsFrom(emptyList())` ci-dessous coupent ce que Gradle
 *  fait hériter par défaut à `testImplementation`. Elles sont NÉCESSAIRES, mais pas
 *  SUFFISANTES : Loom pose Minecraft directement sur le source set, sans passer par
 *  les configurations. La reprise du classpath est en section 3, et c'est là que la
 *  frontière devient réelle.
 *
 *  ── LES DÉPENDANCES DES TESTS ───────────────────────────────────────────────
 *  - junit-jupiter              : l'écriture et l'exécution des tests. L'agrégat
 *                                 porte l'api, les tests paramétrés et le moteur :
 *                                 rien d'autre à déclarer.
 *  - junit-platform-launcher    : le lanceur, étage 0 : l'héritage coupé perd celui
 *                                 que Gradle aurait posé sur le classpath
 *                                 d'exécution.
 *  - aegisperms-core            : le cœur, étage 0 : l'héritage coupé le perd avec
 *                                 le reste, et les clés se calculent avec lui.
 *  - brigadier                  : étage 0, celui de la dernière release : de quoi
 *                                 bâtir des arbres de commandes sans Minecraft.
 *  - fabric-gametest-api        : le module GameTest de Fabric API, absent du jar
 *                                 agrégé : de quoi compiler l'étage 2. Chaque
 *                                 module de version pose le sien à l'exécution.
 * ════════════════════════════════════════════════════════════════════════════════
 */
val testMC: SourceSet = sourceSets.create("testMC")
val gametest: SourceSet = sourceSets.create("gametest")

/*
L'étage 1 est un module Kotlin à part : sans cette association, il ne voit pas les
membres `internal` de main, et ce que `hook` réserve aux tests (l'écrivain du journal,
l'oubli des demandes et de l'état publié) devrait être public. Le source set `test` la
reçoit du plugin Kotlin tout seul ; `testMC` doit la demander.
*/
kotlin {
    target.compilations.getByName("testMC").associateWith(target.compilations.getByName("main"))
}

configurations {
    named("testImplementation") { setExtendsFrom(emptyList()) }
    named("testRuntimeOnly") { setExtendsFrom(emptyList()) }
}

dependencies {
    // ── Étage 0 : la logique pure, aucune dépendance au jeu ──────────────────
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(project(":aegisperms-core"))
    testImplementation(mc263.brigadier)

    // ── Étage 1 : de quoi compiler ; fabric-loader-junit vient au runtime, par module ──
    "testMCImplementation"(libs.junit.jupiter)

    // ── Étage 2 : de quoi compiler le mod de test ────────────────────────────
    "gametestCompileOnly"(mc263.fabric.gametest.api)
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 3 — LE CLASSPATH DES ÉTAGES 0, 1 ET 2
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Loom ajoute Minecraft DIRECTEMENT sur les source sets de test, sans passer par
 *  l'héritage des configurations, et il le fait en s'appliquant : ici, à
 *  l'application du plugin de convention, donc avant ce bloc. Couper `extendsFrom`
 *  ne retire donc rien ; reprendre le classpath ici, après lui, le fait.
 *
 *  ── `test` : LA FRONTIÈRE DEVIENT UNE ERREUR DE COMPILATION ─────────────────
 *  Son classpath est reconstruit à partir de sa seule configuration, donc JUnit, le
 *  cœur, Brigadier et la sortie de `main`. Éprouvé chez TravellingDimension en y
 *  glissant un import du jeu :
 *
 *      E: ScratchFrontiereTest.kt:3:12 Unresolved reference 'minecraft'.
 *
 *  ── LE PIÈGE DES DEUX CLASSPATH ─────────────────────────────────────────────
 *  Ils se reprennent SÉPARÉMENT. Bâtir celui d'exécution en partant de celui de
 *  compilation perd `junit-platform-launcher`, que Gradle ne pose QUE sur
 *  l'exécution : la tâche démarre alors sans savoir lancer quoi que ce soit, avec un
 *  message qui ne dit pas d'où vient le manque. C'est aussi pour cela que le lanceur
 *  est déclaré explicitement en section 2.
 *
 *  ── `testMC` ET `gametest` ──────────────────────────────────────────────────
 *  Ils compilent contre le code de `main` et contre son classpath. Leur exécution,
 *  elle, se bâtit dans chaque module de version, contre son jeu.
 * ════════════════════════════════════════════════════════════════════════════════
 */
sourceSets {
    named("test") {
        /* Compilation et exécution se reprennent séparément : voir « LE PIÈGE DES DEUX CLASSPATH » ci-dessus. */
        val isGameJar: (File) -> Boolean = { it.name.startsWith("minecraft-") }
        compileClasspath = configurations["testCompileClasspath"].filter { !isGameJar(it) } + sourceSets["main"].output
        runtimeClasspath = output + sourceSets["main"].output + configurations["testRuntimeClasspath"].filter { !isGameJar(it) }
    }
    listOf(testMC, gametest).forEach { testSourceSet ->
        testSourceSet.compileClasspath += sourceSets["main"].compileClasspath + sourceSets["main"].output
    }
}

tasks.test {
    useJUnitPlatform()
    description = "Étage 0 : la logique pure, sans Minecraft"

    /*
    Ceinture et bretelles : Minecraft n'est déjà plus au classpath, mais si le chargeur
    Fabric s'y trouvait un jour par transitivité, cette propriété l'empêche de chercher
    les points d'entrée du mod dans un environnement de jeu qui n'existe pas.
    */
    systemProperty("fabric.development", "false")

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 4 — CE QUE COMMON PUBLIE POUR LES MODULES DE VERSION
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Trois configurations consommables, sans dépendances : un module de version les
 *  lit par `project(path = ":common", configuration = ...)` et n'en reçoit que des
 *  fichiers, jamais le jeu ni la Fabric API de common.
 *
 *  - commonClasses       : les classes de main : de quoi compiler, lancer et
 *                          assembler le mod (plugin de version, sections 2 et 5).
 *  - testMCElements      : les classes de l'étage 1, rejouées contre chaque
 *                          version.
 *  - gametestElements    : le mod de test, assemblé en jar : fabric.mod.json et
 *                          tests ensemble, il se pose tel quel sur le classpath
 *                          d'un serveur de test.
 *
 *  Chaque dossier de classes (un pour Java, un pour Kotlin, par source set) part en
 *  artefact à part, avec la sortie du source set qui le produit : le module de
 *  version qui le lit déclenche ainsi la compilation de common. Les dossiers sont
 *  connus dès la configuration ; leur contenu, lui, ne l'est qu'après la
 *  compilation, et rien ici ne le lit avant. Mesuré chez TravellingDimension :
 *  publier `classesDirs.elements` d'un bloc échoue, parce que Gradle en interroge le
 *  contenu avant que compileKotlin ait tourné.
 * ════════════════════════════════════════════════════════════════════════════════
 */
fun ConfigurationPublications.classesOf(vararg sourceSetsToPublish: SourceSet) {
    sourceSetsToPublish.forEach { sourceSet ->
        sourceSet.output.classesDirs.files.forEach { classesDir -> artifact(classesDir) { builtBy(sourceSet.output) } }
    }
}

configurations.consumable("commonClasses") {
    outgoing.classesOf(sourceSets["main"])
}

configurations.consumable("testMCElements") {
    outgoing.classesOf(testMC)
}

val gametestJar = tasks.register<Jar>("gametestJar") {
    description = "Assemble le mod de test de l'étage 2, joué par le serveur de test de chaque module de version"
    archiveClassifier = "gametest"
    from(gametest.output)
}

configurations.consumable("gametestElements") {
    outgoing.artifact(gametestJar)
}
