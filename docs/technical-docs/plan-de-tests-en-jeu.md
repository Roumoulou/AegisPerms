# Plan de tests en jeu

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Vaut pour ce que le serveur de test ne montre pas et qu'un client vanilla montre (cahier des charges, 8.6) : les essais à la main sur l'environnement
`server` d'un module de version, avec le `runClient` vanilla du même module. Les numéros entre parenthèses sont des sections du cahier des charges, privé,
hors du dépôt (`README.md`, « Le dépôt »). Les lancements sont les gestes de l'auteur. Chaque essai dit son montage, ses gestes, son résultat attendu et son
dernier passage, avec la version du jeu et le commit du mod.

## 1. Le montage

- **Le serveur** : `.\gradlew.bat -p S:\16\_V\AegisPerms\main-project\AegisPerms :mc-26.3:runServer --console=plain`, l'environnement `mc-26.3\run\server`,
  profil `dev` de l'entrepôt, préparé par Outfitter avant le run ; `:mc-26.1:runServer` et `:mc-26.2:runServer` pour les deux autres lignées.
- **Le client** : `:mc-26.3:runClient`, profil `vanilla`, le pseudo d'`outfitter.username` dans `machine.properties`, qui rejoint `localhost`. Le mod est
  `environment: server` : le client n'en charge rien, et c'est ce qu'a un joueur.
- **Le premier démarrage** s'éprouve sur un `config\aegisperms\` absent : retirer `mc-26.3\run\server\config\aegisperms\` avant de lancer, ou remettre
  l'environnement à neuf par Outfitter (`docs\prise-en-main.md` d'Outfitter, 6.2).
- **Les droits** : `op <pseudo>` et `deop <pseudo>` depuis la console du serveur ; les règles par `/aegis` depuis la console ou depuis un op, ou par
  `permissions.json` et `settings.json` édités à la main. Une règle changée dans un fichier s'applique par `/aegis reload` depuis la console, ou par la
  surveillance, deux à trois secondes après l'enregistrement (4.8) ; un essai qui dit « redémarrer » éprouve aussi le démarrage et se joue tel qu'il est écrit.
- **Le journal** : les lignes `aegisperms.log.<événement>` et `aegisperms.issue.<nature>` de la console, en texte résolu dans la langue de la machine (5.7,
  5.9) ; `state.json` porte la dernière publication et ses constats.
- **Les passes** : le plan se joue en entier sur 26.3, la dernière release, et en passe courte sur 26.1.2 et sur 26.2 : les essais 1, 2, 3, 6 et 7.

## 2. Les essais

Un essai se joue tel qu'il est écrit ; ce qui s'écarte de l'attendu se note dans le passage, puis au journal du classeur.

| # | Essai | Gestes | Attendu |
|---|---|---|---|
| 1 | Le premier démarrage (2.10) | lancer le serveur sur un `config\aegisperms\` absent ; rejoindre sans être op ; taper `/` | le dossier reçoit `settings.json`, `permissions.json`, `catalog.json`, `state.json` et `last-good\` ; le journal dit `files_created` puis `published 1 startup enforce false 2 0 4 0` ; le client ne propose que `mc:help` et `mc:msg`, ni `mc:me`, ni `mc:tell` ni `mc:w`, qui n'existent pas ; le tchat passe |
| 2 | La console garde tout (2.3) | depuis la console : `list`, `seed`, `mc:list`, `aegis`, `aegis status` | tout passe, formes nues et préfixées ; `aegis` répond l'usage de la commande, `aegis status` l'état en vigueur, dans la langue de la machine |
| 3 | Un op ne voit que les formes préfixées (3.5) | `op <pseudo>` ; taper `/`, puis `/teleport`, puis `/aegis` | le client propose les formes `mc:*` et `/aegis`, aucune forme nue ; `/teleport` est une commande inconnue ; `/aegis` répond l'usage, dans la langue du client |
| 4 | La complétion et la couleur (3.10) | op : taper `/mc:tele`, compléter, puis `/mc:teleport <pseudo> 10 64 10` | la complétion donne `/mc:teleport`, les arguments se complètent, rien n'est en rouge, le joueur est téléporté |
| 5 | `execute` préfixé (3.5, 3.10) | op : `/mc:execute as @s run mc:list`, puis `/mc:execute as @s run mc:seed` | les deux passent ; `mc:execute as` mène à ses sous-commandes et à la cible |
| 6 | Un `allow` qui ouvre (2.6) | ajouter `"minecraft:command.list": "allow"` aux règles de `default` dans `permissions.json`, redémarrer, rejoindre sans être op | `/mc:list` est proposé et s'exécute ; le reste de l'arbre ne bouge pas |
| 7 | Un `deny` qui ferme à un op (2.6, 2.7) | ajouter `"minecraft:command.seed": "deny"` aux règles d'`admin`, redémarrer, rejoindre op ; puis la même règle en `{"value": "deny", "visible": true}` | masqué : `/mc:seed` disparaît de la complétion et est une commande inconnue ; visible : `/mc:seed` reste proposé, s'analyse sans rouge et la porte répond le message du refus visible, dans la langue du client (5.6, 5.7) ; rien ne s'exécute |
| 8 | Le message signé (3.10) | deux joueurs : `/mc:msg <autre> salut` | le message est envoyé, signé et vérifié par le serveur, et affiché chez l'autre |
| 9 | L'aide sous la forme exigée (3.10) | joueur non op : `/mc:help`, puis `/mc:help mc:teleport` | l'aide ne cite aucune forme nue ; `/mc:help mc:teleport` décrit `teleport` sous sa forme préfixée |
| 10 | Les alias (3.4, 3.6, 3.10) | `"aliases": {"wla": "minecraft:whitelist add", "msg": "minecraft:msg", "tell": "minecraft:msg", "mc:tell": "minecraft:msg"}` dans `settings.json`, redémarrer ; op : `/wla <pseudo>` ; non op : `/msg <pseudo> salut`, `/tell <pseudo> salut`, `/mc:tell <pseudo> salut`, `/w <pseudo> salut` | `/wla` ajoute le pseudo à la liste blanche avec les arguments de `whitelist add` ; `/msg` et `/tell` sont rendus sous leur forme nue à qui a `minecraft:command.msg`, et `/mc:tell` existe ; `/w`, alias de vanilla sans forme préfixée, reste une commande inconnue |
| 11 | La taille de l'arbre (3.7, 3.10) | compter les commandes racines proposées à un op avec `require-prefix` à `true`, puis à `false` après redémarrage | à `true`, les seules formes préfixées et `/aegis` ; à `false`, les formes nues s'y ajoutent, soit à peu près le double |
| 12 | L'écran de confirmation (3.7) | op : cliquer un texte de tchat qui porte `/mc:seed` (un `tellraw` depuis la console) | le client montre l'écran de confirmation d'une commande restreinte, puis la commande passe ; c'est le drapeau `restricted` de vanilla, qu'AegisPerms ne change pas |
| 13 | Les sources cliquées et les sources de 5.2 (2.3) | une commande dans un livre et dans un dialogue, cliquée par un non-op privé de cette commande ; un panneau et un pupitre qui portent une commande ouverte à tous ; une fonction et un bloc de commande qui appellent `/aegis status` | le livre et le dialogue portent l'autorité du joueur : refusés ; le panneau et le pupitre ne sont pas jugés : la commande passe ; la fonction et le bloc de commande sont refusés par `/aegis` (5.2) |
| 14 | Le repli au démarrage et `restore` (4.9, 4.10) | une virgule finale ajoutée dans `permissions.json`, redémarrer ; rejoindre op ; `/aegis backup` avait été fait avant la virgule ; `/aegis restore <sauvegarde>` depuis la console | le serveur démarre, le journal dit `fallback permissions.json last_good` et `published ... true` (lecture seule), `state.json` porte le constat `malformed_file` avec sa ligne ; les règles de la copie `last-good` s'appliquent ; l'op qui se connecte lit le repli ; `/aegis restore` remplace le fichier, publie une génération de source `restore` et met de côté le fichier cassé |
| 15 | L'incompatibilité déclarée (6.6) | un jar vide dont le `fabric.mod.json` a l'identifiant `luckperms` dans `mods\`, lancer | le chargeur refuse de lancer et nomme les deux mods |
| 16 | Les trois jars hors de Gradle (7.3, 9.4) | `:mc-<lignée>:setupServerPur` puis `deployToServerPur`, le `launcher\` copié de `C:\MC-Kit\06-servers`, lancer `05-instances\server-pur-<lignée>` | le serveur démarre, charge le mod et ses cinq jars imbriqués, écrit `config\aegisperms\` |
| 17 | Le serveur moddé (7.3, phase 11) | `:mc-26.3:runServerModded`, le solo publié de MDTK pour instance de référence ; rejoindre sans être op, puis op | un non-op ne voit que les formes que `default` ouvre ; le catalogue porte les commandes des mods du modpack sous leur préfixe ; les constats `command_without_owner` et `shared_root` de `state.json`, s'il en est, se notent au journal |
| 18 | La mesure (7.6, phase 11) | un envoi d'arbre complet à un op, avec et sans le mod, sur l'exemple complet de `docs\user-docs\les-fichiers.md` (chapitre 3), forme exigée activée puis désactivée ; un profil JFR de connexions répétées | le surcoût sous 50 % ; aucune allocation d'AegisPerms dans `canUse` hors le prédicat de mod sous `allow` ; la taille du paquet notée |

| 19 | La surveillance vue d'un éditeur (4.8) | un op en ligne ; ajouter `"minecraft:command.list": "allow"` aux règles de `default` dans `permissions.json` et enregistrer, sans redémarrer ; puis casser le fichier et enregistrer | deux à trois secondes après, le journal dit `published <génération> watch`, l'op lit le message de la surveillance, et un non-op voit `/mc:list` sans se reconnecter ; le fichier cassé fait dire le refus à l'op et au journal, et l'état en vigueur reste |
| 20 | `/aegis` depuis le jeu (5.3, 5.6) | op : `/aegis group vip create 80`, `/aegis group vip allow minecraft:command.seed`, `/aegis player <pseudo d'un non-op> group add vip`, puis `/aegis why <ce pseudo> mc:seed` ; le non-op tape `/` | chaque modification se diffuse aux ops en ligne et s'inscrit au journal du serveur, dans la langue de l'op ; le non-op voit `/mc:seed` arriver sans se reconnecter ; `why` raconte le chemin nœud par nœud et conclut « permise » |
| 21 | Les deux langues (5.7) | un client en français puis en anglais (ses options), non op, privé de `/mc:seed` par un refus visible ; op : `/aegis status` dans chaque langue ; `language` à `fr` puis à `en` dans `settings.json`, `/aegis reload` | le message du refus et les réponses de `/aegis` suivent la langue du client sous `auto`, et la langue imposée sous `fr` ou `en` ; la console garde la langue de la machine |
| 22 | Ce que l'étage 2 ne prouve pas (2.3, 6.8) | Bob, le modérateur de l'exemple de `docs\user-docs\les-fichiers.md` (chapitre 3) : un non-op dans un groupe qui ouvre `minecraft:command.kick` et `minecraft:commands/entity_selectors` et ferme `minecraft:command.teleport`, un second joueur en ligne ; Bob : `/mc:teleport <l'autre>`, puis `/mc:kick @a` ; sur le serveur moddé de l'essai 17, une fonction de datapack qui appelle une commande d'un mod du modpack, lancée par `/mc:function` depuis la console | `/mc:teleport` est une commande inconnue pour Bob ; `/mc:kick @a` expulse les deux joueurs, Bob compris : l'atome des sélecteurs répond à l'exécution comme à l'analyse ; la fonction n'est pas jugée : la commande du mod passe ou échoue sur son seul prédicat, au niveau de la compilation |

| 23 | Le manuel des commandes (5.10) | depuis la console : `aegis manual`, puis `aegis manual en`, puis `aegis manual fr` ; op en jeu, client en français : `/aegis manual` ; `/aegis manual de` | trois fichiers dans `config\aegisperms\manuals\`, `commands-<date>-fr.md`, `-en.md` et `-fr-2.md`, puis un quatrième pour l'op, dans sa langue ; chaque réponse donne le fichier et les comptes ; `de` est refusé en une ligne ; le manuel range `minecraft`, puis `aegisperms` et les mods par ordre alphabétique, une table par mod puis une section par commande avec son arbre et ses clés ; `/msg` porte `/tell` et `/w` en alias, sans section à eux ; `/teleport` porte `/tp` ; aucune ligne `mc:` n'a de section |
| 24 | Les progrès, nœud par nœud (2.7, 2.8) | un groupe `staff`, poids 50, qui ouvre `minecraft:command.advancement` et `minecraft:commands/entity_selectors` et ferme `minecraft:command.advancement.revoke` ; un non-op dans `staff` : `/mc:advancement grant @s only minecraft:story/mine_stone`, puis `/mc:advancement revoke @s only minecraft:story/mine_stone` ; puis `minecraft:command.advancement.grant.targets.everything` en `{"value": "deny", "visible": true}`, `/aegis reload` : `/mc:advancement grant @s everything` ; puis `minecraft:command.advancement.revoke.targets.only` à `allow`, `/aegis reload` : `/mc:advancement revoke @s only minecraft:story/mine_stone`, `/aegis why <pseudo> mc:advancement revoke @s only minecraft:story/mine_stone`, `/aegis player <pseudo> info` | `grant only` passe et le progrès « L'âge de pierre » s'affiche ; `revoke` est une commande inconnue, sa complétion absente ; `grant @s everything` reste proposé, s'analyse sans rouge et répond le message du refus visible, rien ne s'exécute ; l'`allow` sous `revoke` n'ouvre rien : la ligne reste inconnue, `why` dit `only` sans effet et conclut au refus masqué, `player info` ne liste pas la règle (elle est au groupe) et `validate` la dit sans effet |

Les essais 2, 3, 7, 13 et 14 sont écrits pour `/aegis`, les messages et la surveillance de la session 5, et les essais 19 à 21 sont nés avec elle ; l'essai 22
est né à la session 6, pour ce que l'étage 2 ne prouve pas ; les essais 1 et 10 sont réécrits pour la session 6 bis, une forme par commande et `default` sans
`me`, et les essais 23 et 24 sont nés avec elle. Aucun n'a encore été joué dans cet état.

## 3. Les passages

Du plus récent au plus ancien : la date, la version du jeu, le commit du mod, les essais joués et ce qui s'est écarté.

- **2026-10-07, 26.3, `12fd56a`** : les essais 1 à 6, dix étapes passées, aucun écart : au premier démarrage un non-op ne voit que les cinq formes que
  `default` ouvre, le tchat passe, la console garde formes nues et préfixées, un op ne voit que les formes préfixées et `/aegis`, `/mc:teleport` se complète
  sans rouge, `/mc:execute as @s run mc:list` passe, et un `allow` sur `minecraft:command.list` s'applique au redémarrage.
- **2026-10-05, 26.3, `006cd98`** : l'essai 15, passé : un jar vide d'identifiant `luckperms` fait refuser le lancement par le chargeur ; `:mc-26.3:runServer`
  charge le mod.

---

*Dernière vérification : 2026-10-18, les essais écrits d'après le cahier (2.3, 2.7, 2.8, 2.10, 3.4 à 3.7, 3.10, 4.8 à 4.10, 5.2, 5.3, 5.6, 5.7, 5.10, 6.6,
6.8, 7.3, 7.6, 8.6) ; aucun passage joué ce jour.*
