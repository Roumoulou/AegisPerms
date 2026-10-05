/*
════════════════════════════════════════════════════════════════════════════════
 LE MOD EN MODULES : UN CŒUR, UN CODE COMMUN, UN MODULE PAR VERSION DU JEU
════════════════════════════════════════════════════════════════════════════════

Les modules sont des enfants directs de cette racine :

  core       le cœur du mod, sans Minecraft ni Loom : le modèle de permissions, la
             résolution, la validation, les fichiers. Ses tests sont de l'étage 0,
             et son jar est imbriqué dans celui de chaque version. Le projet Gradle
             s'appelle aegisperms-core : voir la fin de ce fichier.
  common     le code du mod et ses tests, compilés UNE fois, contre la dernière
             release servie (26.3). Il ne livre aucun jar.
  mc-26.1    ce qui tient à la lignée 26.1 : ses environnements et, le jour où une
             différence entre les versions l'exige, son pont de version. Il compile
             contre 26.1.2, la dernière release de sa lignée, assemble son jar et
             rejoue contre son jeu les tests du jeu (étages 1 et 2).
  mc-26.2    la même chose pour 26.2.
  mc-26.3    la même chose pour 26.3.

Le nom d'un module de version est un contrat : `mc-<lignée>`. Le plugin de
convention aegisperms.game-version vérifie que le Minecraft que le module déclare
appartient à cette lignée, et tire de ce Minecraft la version du jar
(1.0.0+26.1.2, 1.0.0+26.2...).

La racine ne construit rien : `gradlew build` lancé ici joue le build de chaque
module, donc l'étage 0 des tests une fois (core et common), et les étages 1 et 2
une fois par version du jeu.

Le code commun suit la dernière RELEASE de Minecraft, jamais un snapshot, une
pre-release ou une release candidate. À chaque release, common monte au catalogue
de celle-ci, et ce que les versions plus anciennes n'ont pas, ou nomment autrement,
part dans leur pont de version. Servir une version de plus : son catalogue
gradle/mc-<lignée>.versions.toml et sa ligne dans versionCatalogs, un module
mc-<lignée> calqué sur un autre, puis ce que la compilation, la vérification de
compatibilité (checkCommonCompatibility) et les tests révèlent. En abandonner une :
retirer son module et son catalogue.

════════════════════════════════════════════════════════════════════════════════
 OÙ VIVENT LES DÉPÔTS, ET POURQUOI AUCUN N'EST ICI
════════════════════════════════════════════════════════════════════════════════

`pluginManagement` SERT, et il est le seul bloc de dépôts de ce fichier. La
résolution des plugins est SÉPARÉE de celle des dépendances : c'est par lui que
Loom lui-même est trouvé, qu'Outfitter l'est sur Repsy et que build-logic est
inclus, et sans lui rien ne démarre.

`dependencyResolutionManagement { repositories { } }` ne servirait à rien EN
L'ÉTAT, et c'est mesuré chez TravellingDimension, le modèle de ce build. En
s'appliquant à un module, Loom y pose six dépôts, ses trois caches plus Fabric,
Mojang et mavenCentral. Or `PREFER_PROJECT`, le défaut de Gradle, ne fusionne pas :
il regarde si le projet a des dépôts, et si oui il ignore ceux d'ici. Entièrement,
sans rien journaliser.

    Conclusion en vigueur : les dépôts de dépendances des modules qui appliquent
    Loom se déclarent dans le plugin de convention aegisperms.loom-module
    (build-logic), et lui seul. core, qui n'applique pas Loom, déclare les siens
    dans son build.gradle.kts.

Une déclaration par module multiplierait les listes : le plugin de convention en
tient une seule, appliquée à chaque module. La sortie par le greffon
`fabric-loom-repositories` est écartée : elle sortirait la version de Loom du
catalogue `mc` pour la dupliquer ici.
════════════════════════════════════════════════════════════════════════════════
*/

pluginManagement {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }

    /*
    BUILD-LOGIC, les plugins de convention du mod (aegisperms.loom-module et
    aegisperms.game-version), écrits en scripts Kotlin précompilés. Inclus ici, ils
    s'appliquent par leur id, sans version : c'est build-logic qui fixe celles de Loom
    et de Kotlin, depuis les catalogues.
    */
    includeBuild("build-logic")

    /*
    OUTFITTER, le plugin des environnements de développement, pris sur Repsy, le dépôt
    Maven de l'auteur, en lecture anonyme, à la version du catalogue libs. Jamais en
    build composite : un clone se construit sans le projet voisin. Un -SNAPSHOT n'est
    rafraîchi qu'une fois par vingt-quatre heures, --refresh-dependencies force la
    reprise.
    */
    repositories { maven("https://repo.repsy.io/roumoulou/maven") { name = "Repsy" } }
}

/*
Les catalogues de versions, séparés par leur rythme de changement.

`libs` est chargé automatiquement depuis `gradle/libs.versions.toml` : l'outillage
du projet, Kotlin, Java, JUnit, la sérialisation, Storify, Outfitter, la publication.

`mc` porte la chaîne Fabric commune à toutes les versions servies : Loom, le
chargeur, l'adaptateur Kotlin et l'API de permissions de lucko.

Un catalogue par lignée du jeu porte ce qui suit CETTE lignée : Minecraft, la
Fabric API publiée pour lui et son module GameTest, Brigadier. Les clés sont les
mêmes d'un catalogue de version à l'autre, et chaque module lit le sien en
accesseurs typés (`mc262.fabric.api`) ; common lit celui de la dernière release.
Un catalogue vaut pour tout le build : c'est le module qui choisit lequel il lit.
*/
dependencyResolutionManagement {
    versionCatalogs {
        create("mc") {
            from(files("gradle/minecraft.versions.toml"))
        }
        create("mc261") {
            from(files("gradle/mc-26.1.versions.toml"))
        }
        create("mc262") {
            from(files("gradle/mc-26.2.versions.toml"))
        }
        create("mc263") {
            from(files("gradle/mc-26.3.versions.toml"))
        }
    }
}

rootProject.name = "AegisPerms"

/*
Le cœur s'appelle aegisperms-core et vit dans le dossier core. Le nom du projet
compte : l'inclusion de Loom tire du groupe et du nom l'identifiant du jar imbriqué,
fr_moulou_aegisperms-core. Un projet nommé core donnerait fr_moulou_core à chaque mod
de l'auteur qui aurait un cœur, et le chargeur ne garde qu'un mod par identifiant.
*/
include("aegisperms-core", "common", "mc-26.1", "mc-26.2", "mc-26.3")
project(":aegisperms-core").projectDir = file("core")
