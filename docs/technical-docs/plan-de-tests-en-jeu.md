# Plan de tests en jeu

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Vaut pour ce que le serveur de test ne montre pas et qu'un client vanilla montre (cahier des charges, 8.6) : les essais à la main sur l'environnement
`server` d'un module de version, avec le `runClient` vanilla du même module. Les lancements sont les gestes de l'auteur. Chaque essai dit son montage, ses
gestes, son résultat attendu et son dernier passage, avec la version du jeu et le commit du mod.

## 1. Le montage

- **Le serveur** : `.\gradlew.bat -p S:\16\_V\AegisPerms\main-project\AegisPerms :mc-26.3:runServer --console=plain`, l'environnement `mc-26.3\run\server`,
  profil `dev` de l'entrepôt, préparé par Outfitter avant le run ; `:mc-26.1:runServer` et `:mc-26.2:runServer` pour les deux autres lignées.
- **Le client** : `:mc-26.3:runClient`, profil `vanilla`, le pseudo d'`outfitter.username` dans `machine.properties`, qui rejoint `localhost`. Le mod est
  `environment: server` : le client n'en charge rien, et c'est ce qu'a un joueur.
- **Le premier démarrage** s'éprouve sur un `config\aegisperms\` absent : retirer `mc-26.3\run\server\config\aegisperms\` avant de lancer, ou remettre
  l'environnement à neuf par Outfitter (`docs\prise-en-main.md` d'Outfitter, 6.2).
- **Les droits** : `op <pseudo>` et `deop <pseudo>` depuis la console du serveur ; les règles par `permissions.json` et `settings.json` édités à la main.
  Sans `/aegis reload` ni surveillance, qui viennent à la session 5, une règle changée dans un fichier ne s'applique qu'au redémarrage du serveur : chaque
  essai qui change un fichier redémarre.
- **Le journal** : les lignes `aegisperms.log.<événement>` et `aegisperms.issue.<nature>` de la console, la clé suivie de ses arguments jusqu'à la session 5
  (D19 de la carte de la reprise) ; `state.json` porte la dernière publication et ses constats.
- **Les passes** : le plan se joue en entier sur 26.3, la dernière release, et en passe courte sur 26.1.2 et sur 26.2 : les essais 1, 2, 3, 6 et 7.

## 2. Les essais

Un essai se joue tel qu'il est écrit ; ce qui s'écarte de l'attendu se note dans le passage, puis au journal du classeur.

| # | Essai | Gestes | Attendu |
|---|---|---|---|
| 1 | Le premier démarrage (2.10) | lancer le serveur sur un `config\aegisperms\` absent ; rejoindre sans être op ; taper `/` | le dossier reçoit `settings.json`, `permissions.json`, `catalog.json`, `state.json` et `last-good\` ; le journal dit `files_created` puis `published 1 startup enforce false 2 0 5 0` ; le client ne propose que `mc:help`, `mc:me`, `mc:msg`, `mc:tell` et `mc:w` ; le tchat passe |
| 2 | La console garde tout (2.3) | depuis la console : `list`, `seed`, `mc:list`, `aegis` | tout passe, formes nues et préfixées ; `aegis` répond sans erreur et ne fait rien (la grammaire vient à la session 5) |
| 3 | Un op ne voit que les formes préfixées (3.5) | `op <pseudo>` ; taper `/`, puis `/teleport`, puis `/aegis` | le client propose les formes `mc:*` et `/aegis`, aucune forme nue ; `/teleport` est une commande inconnue ; `/aegis` s'exécute sans rien dire |
| 4 | La complétion et la couleur (3.10) | op : taper `/mc:tele`, compléter, puis `/mc:teleport <pseudo> 10 64 10` | la complétion donne `/mc:teleport`, les arguments se complètent, rien n'est en rouge, le joueur est téléporté |
| 5 | `execute` préfixé (3.5, 3.10) | op : `/mc:execute as @s run mc:list`, puis `/mc:execute as @s run mc:seed` | les deux passent ; `mc:execute as` mène à ses sous-commandes et à la cible |
| 6 | Un `allow` qui ouvre (2.6) | ajouter `"minecraft:command.list": "allow"` aux règles de `default` dans `permissions.json`, redémarrer, rejoindre sans être op | `/mc:list` est proposé et s'exécute ; le reste de l'arbre ne bouge pas |
| 7 | Un `deny` qui ferme à un op (2.6, 2.7) | ajouter `"minecraft:command.seed": "deny"` aux règles d'`admin`, redémarrer, rejoindre op ; puis la même règle en `{"value": "deny", "visible": true}` | masqué : `/mc:seed` disparaît de la complétion et est une commande inconnue ; visible : `/mc:seed` reste proposé, s'analyse sans rouge et la porte répond `aegisperms.refusal` (la clé brute, D18) ; rien ne s'exécute |
| 8 | Le message signé (3.10) | deux joueurs : `/mc:msg <autre> salut` | le message est envoyé, signé et vérifié par le serveur, et affiché chez l'autre |
| 9 | L'aide sous la forme exigée (3.10) | joueur non op : `/mc:help`, puis `/mc:help mc:teleport` | l'aide ne cite aucune forme nue ; `/mc:help mc:teleport` décrit `teleport` sous sa forme préfixée |
| 10 | Les alias (3.6, 3.10) | `"aliases": {"wla": "minecraft:whitelist add", "msg": "minecraft:msg"}` dans `settings.json`, redémarrer ; op : `/wla <pseudo>` ; non op : `/msg <pseudo> salut` | `/wla` ajoute le pseudo à la liste blanche avec les arguments de `whitelist add` ; `/msg` est rendu sous sa forme nue à qui a `minecraft:command.msg` |
| 11 | La taille de l'arbre (3.7, 3.10) | compter les commandes racines proposées à un op avec `require-prefix` à `true`, puis à `false` après redémarrage | à `true`, les seules formes préfixées et `/aegis` ; à `false`, les formes nues s'y ajoutent, soit à peu près le double |
| 12 | L'écran de confirmation (3.7) | op : cliquer un texte de tchat qui porte `/mc:seed` (un `tellraw` depuis la console) | le client montre l'écran de confirmation d'une commande restreinte, puis la commande passe ; c'est le drapeau `restricted` de vanilla, qu'AegisPerms ne change pas |
| 13 | Les sources cliquées (2.3) | une commande dans un livre et dans un dialogue, cliquée par un non-op privé de cette commande ; un panneau et un pupitre qui portent une commande ouverte à tous | le livre et le dialogue portent l'autorité du joueur : refusés ; le panneau et le pupitre ne sont pas jugés : la commande passe |
| 14 | Le repli au démarrage (4.9) | une virgule finale ajoutée dans `permissions.json`, redémarrer | le serveur démarre, le journal dit `fallback permissions.json last_good` et `published ... true` (lecture seule), `state.json` porte le constat `malformed_file` avec sa ligne ; les règles de la copie `last-good` s'appliquent ; `/aegis restore` vient à la session 5 |
| 15 | L'incompatibilité déclarée (6.6) | un jar vide dont le `fabric.mod.json` a l'identifiant `luckperms` dans `mods\`, lancer | le chargeur refuse de lancer et nomme les deux mods |
| 16 | Les trois jars hors de Gradle (7.3, 9.4) | `:mc-<lignée>:setupServerPur` puis `deployToServerPur`, le `launcher\` copié de `C:\MC-Kit\06-servers`, lancer `05-instances\server-pur-<lignée>` | le serveur démarre, charge le mod et ses cinq jars imbriqués, écrit `config\aegisperms\` |
| 17 | Le serveur moddé (7.3, phase 11) | `:mc-26.3:runServerModded`, le solo publié de MDTK pour instance de référence ; rejoindre sans être op, puis op | un non-op ne voit que les formes que `default` ouvre ; le catalogue porte les commandes des mods du modpack sous leur préfixe ; les constats `command_without_owner` et `shared_root` de `state.json`, s'il en est, se notent au journal |
| 18 | La mesure (7.6, phase 11) | un envoi d'arbre complet à un op, avec et sans le mod, sur les personnages de 2.11, forme exigée activée puis désactivée ; un profil JFR de connexions répétées | le surcoût sous 50 % ; aucune allocation d'AegisPerms dans `canUse` hors le prédicat de mod sous `allow` ; la taille du paquet notée |

Les essais 2 (le `/aegis` qui ne fait rien), 7 (le texte du refus), 10 (le renommage par `/aegis`), 13 (la fonction et le bloc de commande qui appellent
`/aegis`) et 14 (`/aegis restore`) se rejouent à la session 5, quand `/aegis`, les messages et la surveillance existent.

## 3. Les passages

Du plus récent au plus ancien : la date, la version du jeu, le commit du mod, les essais joués et ce qui s'est écarté.

- **2026-10-07, 26.3, `12fd56a`** : les essais 1 à 6, dix étapes passées, aucun écart : au premier démarrage un non-op ne voit que les cinq formes que
  `default` ouvre, le tchat passe, la console garde formes nues et préfixées, un op ne voit que les formes préfixées et `/aegis`, `/mc:teleport` se complète
  sans rouge, `/mc:execute as @s run mc:list` passe, et un `allow` sur `minecraft:command.list` s'applique au redémarrage.
- **2026-10-05, 26.3, `006cd98`** : l'essai 15, passé : un jar vide d'identifiant `luckperms` fait refuser le lancement par le chargeur ; `:mc-26.3:runServer`
  charge le mod.

---

*Dernière vérification : 2026-10-15, les essais écrits d'après le cahier (2.3, 2.10, 3.5 à 3.7, 3.10, 4.9, 6.6, 7.3, 7.6, 8.6) ; aucun passage joué ce
jour.*
