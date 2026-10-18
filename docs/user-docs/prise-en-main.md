# La prise en main d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Pour l'administrateur d'un serveur Fabric dédié qui installe AegisPerms et veut, en une séance, ouvrir ses premières commandes. Le détail des commandes est
dans `les-commandes.md`, celui des fichiers dans `les-fichiers.md`, et le modèle, pour qui connaît LuckPerms, dans `venir-de-luckperms.md`.

## 1. Installer

Le mod existe en un jar par version du jeu, `aegisperms-1.0.0+26.1.2.jar`, `aegisperms-1.0.0+26.2.jar` et `aegisperms-1.0.0+26.3.jar`. Posez celui de votre
version dans `mods\` du serveur, avec la Fabric API de cette version et Fabric Language Kotlin ; il faut Fabric Loader 0.19.5 ou plus récent, et Java 25. Le
mod embarque tout le reste.

Rien ne s'installe côté client : un joueur voit les commandes que le serveur lui envoie, les complète et les colore comme en vanilla.

AegisPerms ne cohabite pas avec un autre gestionnaire de permissions. Avec LuckPerms, Vanilla Permissions, Universal Perms ou Player Roles dans `mods\`, le
chargeur refuse de lancer le serveur et nomme les deux mods : retirez l'autre.

## 2. Le premier démarrage

Au premier démarrage, le mod écrit `config\aegisperms\settings.json`, les réglages, et `permissions.json`, les droits, avec deux groupes :

| Groupe | Poids | Membres | Règles |
|---|---|---|---|
| `default` | 0 | tout joueur, sans inscription | `command` à `deny` ; `minecraft:command.help` et `minecraft:command.msg` à `allow` |
| `admin` | 100 | les ops de niveau 4, d'office | `command` à `allow` |

Ce que cela donne : un joueur qui n'est pas op ne voit que `/mc:help` et `/mc:msg` ; un op de niveau 4 voit toutes les commandes, et `/aegis` ; un
op de niveau 1 à 3 n'a que les commandes de `default`. La console et le RCON ne sont jamais jugés et gardent tout.

Le serveur est donc fermé : rien ne s'ouvre à un joueur sans qu'une règle le dise. C'est voulu, et c'est ce qui suit qui ouvre.

## 3. Les mots

- **Une clé** nomme ce qu'une règle autorise ou refuse. Une clé de commande s'écrit `<modid>:command.<chemin>` : `minecraft:command.teleport` pour
  `/teleport`, `minecraft:command.teleport.targets.location` pour sa branche `<targets> <location>`, `homes:command.home` pour le `/home` d'un mod `homes`.
  Une clé couvre ses descendantes, et trois clés générales couvrent des familles : `minecraft:command`, toutes les commandes de vanilla ; `<modid>:command`,
  celles d'un mod ; `command`, toutes les commandes, `/aegis` excepté.
- **Une règle** pose `allow` ou `deny` sur une clé, dans un groupe ou chez un joueur. Un `deny` ferme la commande et tout ce qui descend de sa clé ; aucun
  `allow` posé plus bas ne la rouvre.
- **Un groupe** a un poids, des parents dont il hérite, et ses règles. Les groupes d'un joueur s'examinent par poids décroissant, et le premier qui porte une
  règle sur la clé l'emporte.
- **La forme préfixée** d'une commande est son nom précédé du préfixe de son mod : `/mc:teleport` pour `/teleport`, `/homes:home` pour `/home`. Par défaut,
  un joueur ne voit et ne tape que les formes préfixées ; la console garde les deux. Les clés ne changent pas : `/mc:teleport` et `/teleport` ont la même.
  Un autre nom d'une même commande, `/tp` pour `/teleport`, `/tell` et `/w` pour `/msg`, n'a pas de forme préfixée : une commande, une forme. Un alias de
  `settings.json` le rend à qui le veut (`les-fichiers.md`, chapitre 2).

## 4. Ouvrir une première commande

Depuis la console, ou en jeu en tant qu'op de niveau 4 :

```text
/aegis group vip create 80
/aegis group vip allow minecraft:command.teleport
/aegis player Joe group add vip
```

Trois lignes : un groupe `vip` de poids 80, une règle qui lui ouvre `/teleport`, et Joe qui le rejoint. Chaque modification s'écrit dans `permissions.json`,
s'applique tout de suite, et Joe voit `/mc:teleport` apparaître dans sa complétion sans se reconnecter. La réponse dit la génération publiée, le numéro qui
change à chaque application, et répète les avertissements qui concernent ce que vous venez d'écrire.

Pour vérifier :

```text
/aegis why Joe mc:teleport Joe 10 64 10
```

`why` analyse la ligne sans l'exécuter et la raconte nœud par nœud : pour chacun, son statut, sa décision, et la règle qui l'emporte avec le groupe d'où
elle vient. Puis le verdict : permise, refusée et masquée, refusée et visible, refusée par vanilla, ou incomplète quand la ligne s'arrête avant un nœud qui
s'exécute.

Deux pièges à connaître :

- `/teleport @p` demande en plus l'**atome des sélecteurs**, `minecraft:commands/entity_selectors`, que vanilla n'accorde qu'aux ops de niveau 2 ; une règle
  `allow` sur cette clé l'ouvre, dans toutes les commandes du joueur. Désigner un joueur par son pseudo ne demande rien.
- Ouvrir une commande ouvre toutes ses branches, même celles que vanilla réserve à un niveau plus haut ; la réponse de la modification vous le dit, et une
  règle `deny` sur la branche la referme.

## 5. Fermer, masquer, laisser voir

```text
/aegis group vip deny minecraft:command.teleport.location
/aegis group vip deny-visible minecraft:command.kill
```

Par défaut, un refus **masque** la commande : elle disparaît de la complétion, et le joueur qui la tape quand même reçoit l'erreur d'une commande inconnue. Un
refus **visible** la laisse dans l'arbre : le joueur la voit, la complète, et reçoit au moment d'exécuter le message du refus, sans que la règle, le groupe ou
la clé ne soient nommés. Le réglage `denied-commands` de `settings.json` choisit le défaut, `hidden` ou `visible`, et une règle écrite `deny-visible` ou
`deny-hidden` l'emporte sur lui ; `refusal-message` remplace le message livré.

## 6. Les ops et l'administration

- Un op garde les pouvoirs que vanilla attache à son niveau hors des commandes : le sélecteur de mode de jeu, les blocs de commande, les règles de jeu. Fermer
  une commande à un op de niveau 2 ou plus n'est pas une frontière de sécurité ; la configuration sûre laisse un modérateur non op, et lui ouvre par `allow`
  ce qu'on veut lui laisser.
- `/aegis` reste aux ops d'au moins `admin-level`, `owners` par défaut dans `settings.json`, quoi que disent les règles, et à la console. `command` à
  `allow` ne l'ouvre pas : il faut `aegisperms:command.aegis` à `allow` pour le confier à un joueur qui n'est pas op.
- Un groupe peut compter des membres d'office : `/aegis group moderateurs include-ops moderators` y fait entrer tout op de niveau 1 ou plus, tant qu'il
  l'est, sans rien écrire dans le fichier.

## 7. Les fichiers, à la main

Les deux fichiers de `config\aegisperms\` s'éditent à la main. Une règle changée s'applique par `/aegis reload` depuis la console, ou toute seule, deux à
trois secondes après l'enregistrement, tant que `watch-files` vaut `true`. Un fichier invalide ne s'applique jamais : l'état en vigueur reste, la console et
les ops en ligne lisent chaque erreur avec son fichier, son chemin et sa ligne, et `/aegis validate` les relit sans rien appliquer.

`/aegis backup` copie les deux fichiers dans `backups\`, `/aegis restore <dossier>` les remet. Avant toute édition large, une sauvegarde.

## 8. Essayer avant d'appliquer

Sur un serveur qui vit déjà, écrivez `"mode": "observe"` dans `settings.json` avant le premier démarrage avec le mod, ou passez `/aegis mode observe`. En
observation, le mod calcule ses décisions et les journalise sans les appliquer : les joueurs gardent ce que vanilla leur donne, `why` et `check` disent ce qui
se passerait, et le journal note chaque écart entre la décision et vanilla, une fois par joueur et par clé. Réglez les groupes, puis `enforce` dans le
fichier et `/aegis reload`.

## 9. Les deux langues

Le mod parle français et anglais. Par défaut, chaque joueur reçoit la langue de son client, et la console celle de la machine ; `language` à `fr` ou `en`
dans `settings.json` l'impose à tous.
