/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE mc-26.3 — le mod pour Minecraft 26.3
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Ce que le plugin aegisperms.game-version fait de ce module (le jar
 *  aegisperms-1.0.0+26.3.jar, le mod en développement, les étages 1 et 2 rejoués
 *  contre 26.3, la vérification de compatibilité) est décrit dans son en-tête, dans
 *  build-logic. Ici ne vit que ce qui tient à 26.3 :
 *
 *  ┌───────────────────────────────────────────────────────────────────────────┐
 *  │  1  les dépendances de son catalogue, mc263                               │
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
 *  SECTION 1 — LES DÉPENDANCES DE 26.3
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  - minecraft                  : le jeu, en 26.3.
 *  - fabric-api                 : la Fabric API publiée pour lui.
 *  - fabric-gametest-api        : le module GameTest de cette Fabric API, pour le
 *                                 serveur de l'étage 2.
 *
 *  Le reste (chargeur, Kotlin, le cœur, Storify, l'API de lucko, le code de common)
 *  vient du plugin de version.
 * ════════════════════════════════════════════════════════════════════════════════
 */
dependencies {
    minecraft(mc263.minecraft)
    implementation(mc263.fabric.api)
    "gametestRuntimeOnly"(mc263.fabric.gametest.api)
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 2 — LES ENVIRONNEMENTS DE DÉVELOPPEMENT : OUTFITTER
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Même mécanique que mc-26.2 (voir son build) : deux environnements, le serveur de
 *  test, une cible.
 *
 *  ── DEUX ENVIRONNEMENTS, VANILLA PURS ───────────────────────────────────────
 *  `client` et `server`, dans mc-26.3\run\.
 *
 *  L'entrepôt C:\MC-Kit a les profils 26.3, `vanilla` et `dev`, et ses maps : le client
 *  reçoit la sélection de outfitter.maps, le serveur le monde que le profil `dev`
 *  désigne.
 *
 *  ── LE SERVEUR DE TEST ──────────────────────────────────────────────────────
 *  `gameTest`, déclaré comme dans mc-26.2 : il tourne dans mc-26.3\run\game-test,
 *  repart à neuf à chaque run, et ne demande rien à l'entrepôt.
 *
 *  ── UNE CIBLE : LE SERVEUR DÉDIÉ ────────────────────────────────────────────
 *  `serverPur`, le serveur dédié 26.3 du classeur (05-instances\server-pur-26.3),
 *  avec le panier `serverPurBundle`.
 * ════════════════════════════════════════════════════════════════════════════════
 */
outfitter {
    minecraftVersion = mc263.versions.minecraft
    environments {
        register("client") { client(); profile = "vanilla" }
        register("server") { server(); profile = "dev" }
    }
    gameTests {
        register("gameTest")
    }
    deployTargets {
        register("serverPur") {
            directory = layout.settingsDirectory.dir("../../05-instances/server-pur-26.3/server")
            profile = "dev"
        }
    }
}

/* Le panier du serveur dédié : ces deux jars et rien d'autre (la configuration n'est pas transitive), aux versions que le mod a compilées. */
dependencies {
    "serverPurBundle"(mc263.fabric.api)
    "serverPurBundle"(mc.fabric.language.kotlin)
}
