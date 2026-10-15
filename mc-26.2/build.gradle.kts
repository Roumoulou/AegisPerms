/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE mc-26.2 — le mod pour Minecraft 26.2
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Ce que le plugin aegisperms.game-version fait de ce module (le jar
 *  aegisperms-1.0.0+26.2.jar, le mod en développement, les étages 1 et 2 rejoués
 *  contre 26.2, la vérification de compatibilité) est décrit dans son en-tête, dans
 *  build-logic. Ici ne vit que ce qui tient à 26.2 :
 *
 *  ┌───────────────────────────────────────────────────────────────────────────┐
 *  │  1  les dépendances de son catalogue, mc262                               │
 *  │  2  Outfitter : ses environnements, son serveur de test, sa cible         │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  Le module n'a aucune source : aucune différence entre les versions servies
 *  n'exige un pont de version.
 *
 *  Plugins, sans version : la racine les a chargés (build.gradle.kts de la racine).
 *    - aegisperms.game-version   le module de version (build-logic)
 *    - fr.moulou.outfitter       les environnements de développement, le serveur de
 *                                test et les déploiements (pris sur Repsy)
 * ════════════════════════════════════════════════════════════════════════════════
 */

plugins {
    id("aegisperms.game-version")
    id("fr.moulou.outfitter")
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 1 — LES DÉPENDANCES DE 26.2
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  - minecraft                  : le jeu, en 26.2.
 *  - fabric-api                 : la Fabric API publiée pour lui.
 *  - fabric-gametest-api        : le module GameTest de cette Fabric API, pour le
 *                                 serveur de l'étage 2.
 *
 *  Le reste (chargeur, Kotlin, le cœur, Storify, l'API de lucko, le code de common)
 *  vient du plugin de version.
 * ════════════════════════════════════════════════════════════════════════════════
 */
dependencies {
    minecraft(mc262.minecraft)
    implementation(mc262.fabric.api)
    "gametestRuntimeOnly"(mc262.fabric.gametest.api)
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 2 — LES ENVIRONNEMENTS DE DÉVELOPPEMENT : OUTFITTER
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Le plugin Outfitter (pris sur Repsy : voir settings.gradle.kts) prépare chaque
 *  environnement avant son run, serveur de test compris, et déploie le jar. Ce bloc
 *  ne déclare que ce qui est propre à ce module : la version de Minecraft, qui
 *  choisit le dossier de l'entrepôt C:\MC-Kit, les trois environnements avec leur profil,
 *  le serveur de test, la cible de déploiement et le panier du serveur dédié. Tout
 *  le reste vient des clés `outfitter.*` : gradle.properties de la racine pour ce
 *  qui vaut sur chaque poste (l'entrepôt C:\MC-Kit, les maps, le monde du serveur, les
 *  logs), machine.properties pour ce qui est propre à la personne (le joueur), ou à un
 *  poste qui range le kit ailleurs. Outfitter lit le machine.properties de la racine,
 *  puis celui de ce module s'il existe, qui le surcharge clé par clé.
 *
 *  ── DEUX ENVIRONNEMENTS, VANILLA PURS ───────────────────────────────────────
 *  `server`, profil `dev`, est le serveur dédié de développement : Loom y charge le
 *  mod depuis le classpath, sans aucun mod tiers. `client`, profil `vanilla`, est le
 *  client qui s'y connecte : le mod est `"environment": "server"`, le chargeur ne le
 *  charge pas côté client, et c'est ce qu'a un joueur, un client vanilla. Le dossier
 *  est run\<nom-en-kebab-case> DANS ce module (mc-26.2\run\server) : les mondes
 *  d'une version ne se mélangent pas à ceux d'une autre.
 *
 *  ── UN ENVIRONNEMENT MODDÉ : LE MODPACK MDTK ────────────────────────────────
 *  `serverModded`, profil `dev`, est le serveur de dev AVEC le modpack, là où le mod
 *  se voit face à des mods tiers (cahier, 7.3). Outfitter y copie les mods côté
 *  serveur de l'instance de référence de ce module, le solo publié de MDTK pour 26.2
 *  dans le kit (outfitter.reference_instance_dir, dans le gradle.properties de ce
 *  module), sauf Fabric API, FLK et le mod lui-même, que Loom porte déjà ; puis il y
 *  pose le contenu du modpack par PackTool (outfitter.content_project dans le
 *  gradle.properties de la racine ; le lanceur et l'atelier de l'outil, propres au
 *  poste, dans machine.properties). Sans PackTool déclaré, le serveur démarre sans ce
 *  contenu, et Outfitter le dit. Un environnement moddé ne reçoit pas les packs du
 *  profil. Son dossier est mc-26.2\run\server-modded, et `runServerModded` le lance.
 *
 *  ── LE SERVEUR DE TEST : CRÉÉ PAR LE PLUGIN DE VERSION, DÉCLARÉ ICI ─────────
 *  `gameTest`, le serveur de l'étage 2, est le run que le plugin de version crée
 *  (sa section 7), et qu'il refuse de laisser sans cette déclaration. Outfitter le
 *  fait tourner dans son dossier, mc-26.2\run\game-test, hors de build\, le remet à
 *  neuf avant chaque run (`freshGameTest` : world\ et config\ retirés) et range le
 *  rapport de ses tests dans build\test-results\gameTest\TEST-gameTest.xml. Il ne
 *  reçoit rien de l'entrepôt : un clone sans C:\MC-Kit le rejoue tel quel.
 *
 *  Pour ne jouer qu'une partie des tests, un motif à jokers sur leur identifiant,
 *  <mod>:<classe>_<méthode> en snake_case, l'argument entre guillemets : sans eux,
 *  PowerShell le coupe au point.
 *
 *      gradlew :mc-26.2:runGameTest "-Poutfitter.gametest_filter=aegisperms-gametest:witness_*"
 *
 *  ── UNE CIBLE : LE SERVEUR DÉDIÉ ────────────────────────────────────────────
 *  `serverPur`, le serveur dédié 26.2 du classeur (05-instances\server-pur-26.2),
 *  pour éprouver le jar hors de Gradle : il reçoit le jar ET le panier
 *  `serverPurBundle`, Fabric API et FLK aux versions de ce module, sans leurs
 *  dépendances. Son chemin part de la racine du build, pas de ce module.
 *
 *  Les tâches, groupe `outfitter` : sync<Env>Profile, sync<Env>Worlds, fresh<Env>,
 *  prepare<Env>, deployTo<Cible>, setup<Cible>, resetEnvironments, resetWorlds,
 *  outfitterLog4jConfigs. Le détail, condition, geste et marqueur de chacune : la
 *  doc du plugin.
 * ════════════════════════════════════════════════════════════════════════════════
 */
outfitter {
    minecraftVersion = mc262.versions.minecraft
    environments {
        register("client") { client(); profile = "vanilla" }
        register("server") { server(); profile = "dev" }
        register("serverModded") { server(); profile = "dev"; modded = true }
    }
    gameTests {
        register("gameTest")
    }
    deployTargets {
        register("serverPur") {
            directory = layout.settingsDirectory.dir("../../05-instances/server-pur-26.2/server")
            profile = "dev"
        }
    }
}

/* Le panier du serveur dédié : ces deux jars et rien d'autre (la configuration n'est pas transitive), aux versions que le mod a compilées. */
dependencies {
    "serverPurBundle"(mc262.fabric.api)
    "serverPurBundle"(mc.fabric.language.kotlin)
}
