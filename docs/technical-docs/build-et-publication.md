# Le build et la publication d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Pour qui construit le mod, le lance en développement, joue ses tests ou le publie. L'architecture du code est dans `architecture.md`. Les scripts de build
portent eux-mêmes, en tête de chaque section, le pourquoi de ce qu'ils font : cette doc dit où chercher et quoi lancer.

## 1. Le projet Gradle

Gradle vient par le wrapper ; il faut Java 25. Le build est celui de TravellingDimension, le mod de l'auteur, recopié puis adapté : un cœur sans le jeu, un
code commun compilé une fois, un module par version du jeu.

| Chemin | Contenu |
|---|---|
| `settings.gradle.kts` | les dépôts des plugins, Maven Central, Fabric, le portail Gradle et Repsy ; l'inclusion de `build-logic` ; les catalogues `mc`, `mc261`, `mc262` et `mc263` ; les cinq modules, `aegisperms-core` dans `core\`, `common`, `mc-26.1`, `mc-26.2`, `mc-26.3` |
| `build.gradle.kts` | la racine ne construit rien ; elle charge les plugins une fois, pour qu'un seul classloader les porte |
| `gradle.properties` | l'identité du mod, `mod_id`, `mod_version`, `maven_group`, `archives_base_name` ; les identifiants des projets Modrinth et CurseForge ; les clés `outfitter.*` qui valent sur chaque poste |
| `machine.properties` | jamais versionné : ce qui est propre au poste, `outfitter.username`, le lanceur et l'atelier de PackTool, les chemins d'un poste qui range le kit ailleurs ; celui d'un module de version surcharge clé par clé |
| `gradle\*.versions.toml` | les cinq catalogues : `libs`, le socle ; `minecraft`, la chaîne Fabric commune aux trois versions ; `mc-26.1`, `mc-26.2`, `mc-26.3`, ce qui suit une lignée, sous les mêmes clés |
| `build-logic\` | un build inclus : les deux plugins de convention, `aegisperms.loom-module` et `aegisperms.game-version`, et leurs deux tâches, sous `src\main\kotlin` |
| `core\` | le cœur : `build.gradle.kts`, `src\main\kotlin`, `src\test\kotlin` |
| `common\` | le code du mod : `src\main\java` pour les mixins, `src\main\kotlin`, `src\main\resources` ; `src\test`, `src\testMC` et `src\gametest`, les trois étages de tests |
| `mc-26.1\`, `mc-26.2\`, `mc-26.3\` | un `build.gradle.kts` par version, aucune source tant qu'aucun pont de version n'existe ; `run\` et `build\`, jamais versionnés |

**`aegisperms.loom-module`**, ce que partagent `common` et les modules de version : l'identité lue dans `gradle.properties` ; les dépôts de dépendances, le
seul endroit où ils se déclarent, Loom posant les siens et Repsy s'ajoutant, filtré sur `fr.moulou` ; Java 25 ; les compilateurs en UTF-8, avec un nom de
module Kotlin par module et par source set.

**`aegisperms.game-version`**, un module de version : la lignée lue dans le nom du module, la version compilée dans sa dépendance `minecraft`, d'où le jar
tire sa version, `<mod_version>+<version servie>`, et `fabric.mod.json` sa contrainte ; les dépendances communes, le cœur, Storify et l'API de lucko, chacune
embarquée dans le jar ; le mod en développement, `common` et le module réunis sous un même identifiant ; les ressources de `common`, `fabric.mod.json` rempli
pour la version ; le jar livrable, vérifié par `checkReleaseJar` ; les étages 1 et 2 des tests ; la vérification de compatibilité ; la publication.

Les versions des bibliothèques, et la source de chacune, sont dans le cahier des charges (7.2).

## 2. Construire

```powershell
.\gradlew.bat build --console=plain
```

Lancé à la racine, le build joue celui de chaque module : la compilation du cœur et du code commun, un jar par version du jeu dans `mc-<lignée>\build\libs\`,
`aegisperms-1.0.0+26.3.jar` par exemple, et les trois étages de tests. Sur le poste de l'auteur, un build complet depuis `clean` tient en une trentaine de
secondes, démon chaud, trois serveurs de test compris.

Ce que `check` vérifie, en plus des tests :

| Tâche | Ce qu'elle vérifie |
|---|---|
| `checkCommonCompatibility` | que le code compilé de `common`, tests des étages 1 et 2 compris, ne nomme que des classes, des méthodes et des champs qui existent dans le jeu de la lignée et dans ses bibliothèques ; ce qui manque fait échouer le build avec la liste des symboles, et le remède est un pont de version. Elle ne voit ni les cibles des mixins, écrites en chaînes, ni un changement de comportement sans changement de nom : l'étage 2 les éprouve |
| `checkReleaseJar` | que le jar livrable porte ses cinq jars imbriqués, le cœur, Storify, tomlkt, json5 et l'API de lucko, et ses deux licences |

Le cache de build du poste peut reprendre les tests des étages 0 et 1 sans les rejouer : une preuve se prend avec `--no-build-cache`, et avec `--rerun` après
le nom de la tâche quand rien n'a changé.

## 3. Les trois étages de tests

| Étage | Tâche | Ce qu'il charge |
|---|---|---|
| 0, la logique pure | `:aegisperms-core:test`, `:common:test`, une fois | rien du jeu : JUnit, Storify sur des dossiers temporaires, Brigadier seul pour `common` ; un import du jeu dans `common\src\test` ne compile pas |
| 1, le jeu amorcé | `:mc-<lignée>:testMC`, par version | les registres par `fabric-loader-junit`, le chargeur amorcé côté serveur, sans serveur ni mixin ; les classes de test viennent de `common\src\testMC` |
| 2, le serveur de test | `:mc-<lignée>:runGameTest`, par version | un serveur de test dans `run\game-test`, remis à neuf avant chaque run, avec le mod, ses mixins et le mod de test `aegisperms-gametest`, assemblé depuis `common\src\gametest` ; le rapport XML dans `build\test-results\gameTest\` |

Les tests de l'étage 2 se filtrent par `-Poutfitter.gametest_filter=<motif>`, un motif à jokers sur l'identifiant des tests,
`aegisperms-gametest:<classe>_<méthode>` en snake case, et `-Poutfitter.gametest_verify=true` rejoue cent fois chaque test du filtre pour débusquer un test
instable. Ce que ces étages ne montrent pas, l'arbre affiché par un client, la complétion, le message rendu, est au plan de tests en jeu,
`plan-de-tests-en-jeu.md`.

## 4. Les environnements de développement

Outfitter prépare et lance les environnements, dans `run\<nom>` sous chaque module de version, depuis l'entrepôt `C:\MC-Kit` de l'auteur, déclaré dans
`gradle.properties` ; sur un autre poste, `machine.properties` donne ses propres chemins.

| Environnement | Tâche | Ce que c'est |
|---|---|---|
| `client` | `:mc-<lignée>:runClient` | un client vanilla, profil `vanilla`, qui rejoint `localhost` ; le mod est `environment: server`, le client n'en charge rien |
| `server` | `:mc-<lignée>:runServer` | un serveur dédié avec le mod, profil `dev` |
| `serverModded` | `:mc-26.2:runServerModded`, `:mc-26.3:runServerModded` | le même, avec les mods du modpack de référence de la lignée, pour voir le mod au milieu d'autres ; pas en 26.1 |
| `gameTest` | `:mc-<lignée>:runGameTest` | le serveur de test de l'étage 2 |
| `serverPur` | `:mc-<lignée>:setupServerPur`, `deployToServerPur` | une cible de déploiement hors de Gradle : un serveur dédié installé à part, où le jar se pose pour l'éprouver tel qu'un administrateur le recevra |

`resetEnvironments` et `resetWorlds` remettent les environnements et leurs mondes à neuf ; `:mc-<lignée>:tasks --console=plain` liste le reste, groupe
par groupe.

## 5. Publier

La publication passe par `mod-publish-plugin` : `publishMods` envoie chaque jar sur Modrinth et sur CurseForge, six envois pour trois versions, avec la
version du jeu que le jar exige, le chargeur Fabric, le serveur dédié seul, la Fabric API et Fabric Language Kotlin requises, et le changelog.

```powershell
.\gradlew.bat publishMods --console=plain
.\gradlew.bat publishMods -Ppublish_live=true --console=plain
```

Sans `-Ppublish_live=true`, `publishMods` est un essai à blanc. `checkPublication` vérifie avant tout envoi le changelog, les deux identifiants de projet,
`modrinth_project_id` et `curseforge_project_id` dans `gradle.properties`, et la présence des jetons `MODRINTH_TOKEN` et `CURSEFORGE_TOKEN` dans
l'environnement du terminal ; en envoi réel, ce qui manque refuse tout. Les jetons ne vivent dans aucun fichier du dépôt.

Une version publiée est un tag `v<mod_version>`, sans la version du jeu : les trois jars viennent du même commit. Avant la première publication, Storify et
Outfitter passent de `-SNAPSHOT` à une version figée dans les catalogues, pour qu'un jar publié se reconstruise à l'identique.
