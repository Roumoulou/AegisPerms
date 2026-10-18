# La prise en main d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Pour l'administrateur d'un serveur Fabric dédié qui installe AegisPerms et veut, en une séance, ouvrir ses premières commandes. Le modèle (les clés, la
résolution, les refus, les ops) est dans `venir-de-luckperms.md`, la référence de `/aegis` dans `les-commandes.md`, celle des fichiers dans `les-fichiers.md`.

## 1. Installer

Le mod existe en un jar par version du jeu : `aegisperms-<version>+26.1.2.jar`, `aegisperms-<version>+26.2.jar` et `aegisperms-<version>+26.3.jar`. Posez
celui de votre version dans le dossier `mods/` du serveur, avec la Fabric API de cette version et Fabric Language Kotlin ; il faut Fabric Loader 0.19.5 ou
plus récent, et Java 25. Le mod embarque tout le reste.

Rien ne s'installe côté client : un joueur voit les commandes que le serveur lui envoie, les complète et les colore comme en vanilla.

AegisPerms ne cohabite pas avec un autre gestionnaire de permissions. Avec LuckPerms, Vanilla Permissions, Universal Perms ou Player Roles dans `mods/`, le
chargeur refuse de lancer le serveur et nomme les deux mods : retirez l'autre.

## 2. Les mots

- **Une clé** nomme ce qu'une règle ouvre ou ferme : `minecraft:command.teleport` pour `/teleport`, `minecraft:command.teleport.targets.location` pour sa
  branche `<targets> <location>`. Une clé couvre ses descendantes ; sa grammaire et les trois clés générales sont au chapitre 2 de `venir-de-luckperms.md`.
- **Une règle** pose `allow` ou `deny` sur une clé, dans un groupe ou chez un joueur.
- **Un groupe** a un poids, des parents dont il hérite, et ses règles ; l'ordre dans lequel les groupes d'un joueur répondent est au chapitre 3 de
  `venir-de-luckperms.md`.
- **La forme préfixée** d'une commande est son nom précédé du préfixe de son mod : `/mc:teleport` pour `/teleport`, `/homes:home` pour le `/home` d'un mod
  `homes`. Par défaut, un joueur ne voit et ne tape que les formes préfixées ; la console garde les deux, et `/mc:teleport` et `/teleport` ont la même clé.
  Un autre nom d'une même commande, `/tp` pour `/teleport`, `/tell` et `/w` pour `/msg`, n'a pas de forme préfixée ; un alias de `settings.json` le rend à
  qui le veut (`les-fichiers.md`, chapitre 3).

## 3. Le premier démarrage

Au premier démarrage, le mod écrit `config/aegisperms/settings.json`, les réglages, et `permissions.json`, les droits, avec deux groupes :

| Groupe | Poids | Membres | Règles |
|---|---|---|---|
| `default` | 0 | tout joueur, sans inscription | `command` à `deny` ; `minecraft:command.help` et `minecraft:command.msg` à `allow` |
| `admin` | 100 | les ops de niveau 4, d'office | `command` à `allow` |

Un joueur qui n'est pas op ne voit donc que `/mc:help` et `/mc:msg` ; un op de niveau 4 voit toutes les commandes, et `/aegis` ; un op de niveau 1 à 3 n'a
que les commandes de `default`. La console et le RCON ne sont jamais jugés et gardent tout.

## 4. Ouvrir une première commande

Depuis la console, ou en jeu en tant qu'op de niveau 4 :

```text
/aegis group vip create 80
/aegis group vip allow minecraft:command.teleport
/aegis player Joe group add vip
```

Trois lignes : un groupe `vip` de poids 80, une règle qui lui ouvre `/teleport`, et Joe qui le rejoint. Chaque modification s'écrit dans `permissions.json`
et s'applique tout de suite : Joe voit `/mc:teleport` apparaître dans sa complétion sans se reconnecter. Ce que la réponse dit est au chapitre 4 de
`les-commandes.md`.

Pour vérifier :

```text
/aegis why Joe mc:teleport Joe 10 64 10
```

`why` analyse la ligne sans l'exécuter et la raconte nœud par nœud, jusqu'à son verdict ; sa lecture est au chapitre 5 de `les-commandes.md`.

Deux pièges à connaître :

- `/teleport @p` demande en plus l'**atome des sélecteurs**, `minecraft:commands/entity_selectors`, que vanilla n'accorde qu'aux ops de niveau 2 ou plus ;
  une règle `allow` sur cette clé l'ouvre, dans toutes les commandes du joueur. Désigner un joueur par son pseudo ne demande rien.
- Ouvrir une commande ouvre toutes ses branches, même celles que vanilla réserve à un niveau d'op plus haut (`venir-de-luckperms.md`, chapitre 4) ; la
  réponse de la modification vous le dit, et une règle `deny` sur la branche la referme.

## 5. Fermer, masquer, laisser voir

```text
/aegis group vip deny minecraft:command.teleport.location
/aegis group vip deny-visible minecraft:command.kill
```

La première ligne referme une branche de `/teleport` aux membres de `vip` ; la seconde leur refuse `/kill` en le laissant visible. Ce qu'un refus masqué et
un refus visible montrent au joueur, et ce qu'un `deny` ferme sous lui, est au chapitre 4 de `venir-de-luckperms.md`. Le réglage `denied-commands` choisit la
visibilité d'un `deny` qui n'en dit rien, et `refusal-message` remplace le message livré (`les-fichiers.md`, chapitre 3).

## 6. Les ops et l'administration

Un op garde les pouvoirs que vanilla attache à son niveau d'op hors des commandes : fermer une commande à un op de niveau 2 ou plus n'est pas une frontière
de sécurité, et la configuration sûre laisse un modérateur non op (`venir-de-luckperms.md`, chapitre 5). `/aegis` reste aux ops d'au moins `admin-level`,
quoi que disent les règles (`les-commandes.md`, chapitre 1).

## 7. Les fichiers, à la main

Les deux fichiers de `config/aegisperms/` s'éditent à la main : un fichier changé s'applique par `/aegis reload`, ou tout seul par la surveillance, et un
fichier invalide ne s'applique jamais (`les-fichiers.md`, chapitre 2). `/aegis validate` relit les deux fichiers sans rien appliquer ; `/aegis backup` les
copie dans `backups/`, et `/aegis restore <dossier>` les remet (`les-fichiers.md`, chapitre 8).

## 8. Essayer avant d'appliquer

Sur un serveur déjà en service, posez avant le premier démarrage avec le mod un `config/aegisperms/settings.json` qui ne porte que `"schema-version": 1` et
`"mode": "observe"` : les autres réglages prennent leur valeur livrée. Sur un serveur où le mod tourne, `/aegis mode observe` passe en observation jusqu'au
prochain rechargement. En observation, le mod calcule ses décisions et les journalise sans les appliquer : les joueurs gardent ce que vanilla leur donne,
`why` et `check` disent ce qui se passerait, et le journal note chaque écart entre la décision et vanilla, une fois par joueur et par clé. Réglez les groupes,
puis écrivez `"mode": "enforce"` dans le fichier et lancez `/aegis reload`.

## 9. Les deux langues

Le mod parle français et anglais : chaque joueur reçoit la langue de son client, la console celle de la machine, et le réglage `language` impose l'une ou
l'autre à tous (`les-fichiers.md`, chapitre 3).

---

*Dernière vérification : 2026-10-18.*
