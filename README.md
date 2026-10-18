# AegisPerms

Le pare-feu des commandes d'un serveur Fabric dédié. Pour chaque joueur et pour chaque nœud de l'arbre des commandes, AegisPerms décide ce qui se voit et ce
qui s'utilise : les commandes de vanilla et celles des autres mods, sous-commandes et arguments compris, sans que ces mods aient à coopérer et sans rien
installer côté client.

- Des **groupes pondérés** qui héritent les uns des autres, des **règles propres** à un joueur, et un serveur **fermé par défaut** : un nouveau venu n'a que
  ce que le groupe `default` ouvre, et vanilla décide partout où aucune règle ne parle.
- Des **clés déduites de l'arbre** des commandes, `minecraft:command.teleport.targets.location`, et un **catalogue** qui les liste toutes, exigence vanilla
  comprise : rien à déclarer, rien à deviner.
- **Voir et utiliser** distingués : un refus masque la commande, ou la laisse visible avec un message, au choix.
- Les **formes préfixées** et les **alias** que le client vanilla comprend, complétion et couleurs comprises : `/mc:teleport`, `/gm` pour `/gamemode`.
- `/aegis why`, qui **explique une décision** nœud par nœud, et `/aegis check`, qui donne la décision d'une clé avec la règle qui l'emporte.
- Deux **fichiers JSON** édités à la main ou par `/aegis`, validés avec la ligne de l'erreur, rechargés sans commande, sauvegardés et restaurés.
- Un **mode observation**, qui calcule et journalise les décisions sans les appliquer, le temps d'ajuster les règles sur un serveur en service.
- Un **fournisseur des deux API de permissions**, celle de la Fabric API et celle de lucko, pour que les autres mods suivent les mêmes groupes.

Pour l'administrateur d'un serveur Fabric dédié, petit ou moyen, qui veut ouvrir une commande d'op à un joueur sans le rendre op, retirer une seule
sous-commande, tenir ses modérateurs à un niveau d'op bas, savoir pourquoi une commande est refusée, et garder ses règles dans des fichiers qu'il lit,
versionne et copie d'un serveur à l'autre.

## État

Version `1.0.0-alpha.1`, la première publication, en alpha : le mod est complet et éprouvé par ses trois étages de tests sur les trois versions servies ;
le plan de tests en jeu n'est pas encore joué en entier, et le jar embarque Storify en `0.6.0-SNAPSHOT`. La `1.0.0` suivra les essais en jeu et les versions
figées. Les versions sont sur [Modrinth](https://modrinth.com/mod/KNCR3gkY), sur CurseForge et dans les
[releases](https://github.com/Roumoulou/AegisPerms/releases) du dépôt ; le détail de chacune est dans [CHANGELOG.md](CHANGELOG.md).

## Installation

| Prérequis | Version |
|---|---|
| Minecraft, serveur dédié | 26.1.2, 26.2 ou 26.3, un jar par version : `aegisperms-<version>+26.1.2.jar`, `aegisperms-<version>+26.2.jar`, `aegisperms-<version>+26.3.jar` |
| Fabric Loader | 0.19.5 ou plus récent |
| Fabric API | celle de votre version du jeu, au plus bas 0.155.3 pour 26.1.2, 0.161.0 pour 26.2, 0.163.0 pour 26.3 |
| Fabric Language Kotlin | 1.14.1 ou plus récent |
| Java | 25 |

Posez le jar de votre version dans le dossier `mods/` du serveur, avec la Fabric API et Fabric Language Kotlin. Le mod embarque le reste : son cœur, Storify,
l'API de permissions de lucko. Il est `environment: server` : un client qui le trouve dans ses mods ne le charge pas, et un joueur n'a rien à installer.

AegisPerms est seul maître des droits sur son serveur : il refuse de démarrer avec LuckPerms, Vanilla Permissions, Universal Perms ou Player Roles, et le
chargeur nomme les deux mods avant le chargement du jeu.

## Premier démarrage

Au premier démarrage, le mod crée `config/aegisperms/settings.json` et `permissions.json` avec deux groupes : `default`, qui refuse toute commande sauf
`/help` et `/msg`, et `admin`, qui ouvre tout aux ops de niveau 4. Un joueur ne voit alors que `/mc:help` et `/mc:msg` ; un op de niveau 4 voit tout, et
`/aegis`. La console et le RCON ne sont jamais jugés.

```text
/aegis group vip create 80
/aegis group vip allow minecraft:command.teleport
/aegis player Joe group add vip
/aegis why Joe mc:teleport Joe 10 64 10
```

Joe se téléporte, et `why` raconte pourquoi, nœud par nœud. `/aegis manual` écrit toutes les commandes du serveur, avec leurs clés et leur description,
dans `config/aegisperms/manuals/`. La suite est dans la doc.

## Documentation

Pour l'administrateur, dans `docs/user-docs/` :

- [La prise en main](docs/user-docs/prise-en-main.md) : installer, démarrer, ouvrir une première commande, comprendre ce qu'un joueur voit.
- [Le modèle, pour qui vient de LuckPerms](docs/user-docs/venir-de-luckperms.md) : les clés, les groupes, la résolution, et ce qui change par rapport à
  LuckPerms et aux ponts.
- [Les commandes](docs/user-docs/les-commandes.md) : la référence de `/aegis`, sous-commande par sous-commande.
- [Les fichiers](docs/user-docs/les-fichiers.md) : `settings.json`, `permissions.json`, le catalogue, l'état, `last-good` et les sauvegardes.
- [Le dépannage](docs/user-docs/depannage.md) : les constats de la validation et leur remède, le repli, le journal.

Pour qui reprend le code, dans `docs/technical-docs/` :

- [L'architecture](docs/technical-docs/architecture.md) : les modules, les packages, les fils, les accroches dans le jeu.
- [Le build et la publication](docs/technical-docs/build-et-publication.md) : le projet Gradle, les trois étages de tests, les environnements, la
  publication.
- [La mesure de performance](docs/technical-docs/mesure-de-performance.md) : le protocole et les chiffres.
- [Le plan de tests en jeu](docs/technical-docs/plan-de-tests-en-jeu.md) : ce qu'un client vanilla montre et que le serveur de test ne montre pas.

## Construire

```bash
./gradlew build
```

Il faut Java 25 ; Gradle vient par le wrapper. Le build compile le cœur et le code commun une fois, assemble un jar par version du jeu dans
`mc-<lignée>/build/libs/`, et joue les trois étages de tests : les tests JUnit du cœur et du code commun, puis, pour chaque version, les tests qui amorcent
le jeu et un serveur de test avec de vrais joueurs simulés. Les bibliothèques de l'auteur, Storify et Outfitter, viennent de son dépôt Maven, déclaré dans
`settings.gradle.kts`. Le détail, les environnements de développement et la publication sont dans [le build et la
publication](docs/technical-docs/build-et-publication.md).

## Licence

`LGPL-3.0-only`, texte dans [`LICENSE`](LICENSE) et [`LICENSE.GPL`](LICENSE.GPL). Les bibliothèques embarquées voyagent avec leur propre licence dans le
jar.

## Le dépôt

Ce dépôt est la racine Gradle du mod, et rien d'autre : le code, ses tests, sa doc dans `docs/`, ses tags et ses releases. Le classeur de l'auteur reste
hors du dépôt : ses notes de travail, ses relevés et le cahier des charges, privé, qui fait foi sur ce que le mod doit faire ; les mentions
`(cahier des charges, <section>)` du code et de la doc renvoient à ses sections. Un clone se construit et passe ses tests sans lui ; seuls les
environnements de développement (`runServer`, `runClient`, `serverModded`) lisent l'entrepôt de l'auteur, `C:\MC-Kit`, ou les chemins qu'un
`machine.properties` donne.
