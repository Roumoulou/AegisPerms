// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE mc-26.1 — le mod pour la lignée 26.1 de Minecraft
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Ce que le plugin aegisperms.game-version fait de ce module (le jar
 *  aegisperms-1.0.0+26.1.2.jar, le mod en développement, les étages 1 et 2 rejoués
 *  contre 26.1.2, la vérification de compatibilité) est décrit dans son en-tête,
 *  dans build-logic. Ici ne vit que ce qui tient à 26.1 :
 *
 *  ┌───────────────────────────────────────────────────────────────────────────┐
 *  │  1  les dépendances de son catalogue, mc261                               │
 *  │  2  Outfitter : ses environnements, son serveur de test, sa cible         │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  Le module compile contre 26.1.2, la dernière release de la lignée, et le jar ne
 *  sert qu'elle ("minecraft": "~26.1.2").
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
 *  SECTION 1 — LES DÉPENDANCES DE 26.1
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  - minecraft                  : le jeu, en 26.1.2.
 *  - fabric-api                 : la Fabric API publiée pour lui.
 *  - fabric-gametest-api        : le module GameTest de cette Fabric API, pour le
 *                                 serveur de l'étage 2.
 *
 *  Le reste (chargeur, Kotlin, le cœur, Storify, l'API de lucko, le code de common)
 *  vient du plugin de version.
 * ════════════════════════════════════════════════════════════════════════════════
 */
dependencies {
    minecraft(mc261.minecraft)
    implementation(mc261.fabric.api)
    "gametestRuntimeOnly"(mc261.fabric.gametest.api)
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
 *  `client` et `server`, dans mc-26.1\run\.
 *
 *  L'entrepôt C:\MC-Kit a les profils 26.1.2, `vanilla` et `dev`, pas de maps 26.1.2 :
 *  le client démarre sans monde, le serveur génère le sien, ce qu'Outfitter annonce.
 *
 *  Pas de `serverModded` ici : MDTK n'est pas publié pour 26.1.2, le kit (08-modpacks)
 *  n'a que 26.2 et 26.3.
 *
 *  ── LE SERVEUR DE TEST ──────────────────────────────────────────────────────
 *  `gameTest`, déclaré comme dans mc-26.2 : il tourne dans mc-26.1\run\game-test,
 *  repart à neuf à chaque run, et ne demande rien à l'entrepôt.
 *
 *  ── UNE CIBLE : LE SERVEUR DÉDIÉ ────────────────────────────────────────────
 *  `serverPur`, le serveur dédié 26.1 du classeur (05-instances\server-pur-26.1),
 *  avec le panier `serverPurBundle`.
 * ════════════════════════════════════════════════════════════════════════════════
 */
outfitter {
    minecraftVersion = mc261.versions.minecraft
    environments {
        register("client") { client(); profile = "vanilla" }
        register("server") { server(); profile = "dev" }
    }
    gameTests {
        register("gameTest")
    }
    deployTargets {
        register("serverPur") {
            directory = layout.settingsDirectory.dir("../../05-instances/server-pur-26.1/server")
            profile = "dev"
        }
    }
}

/* Le panier du serveur dédié : ces deux jars et rien d'autre (la configuration n'est pas transitive), aux versions que le mod a compilées. */
dependencies {
    "serverPurBundle"(mc261.fabric.api)
    "serverPurBundle"(mc.fabric.language.kotlin)
}
