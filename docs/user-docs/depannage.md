# Le dépannage d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Ce que le mod dit quand quelque chose ne va pas, et ce qu'on en fait. Les constats se lisent dans la console, dans la réponse de `/aegis reload` ou
`/aegis validate`, dans `state.json`, et les ops d'au moins `admin-level` en ligne les reçoivent. Chaque constat dit son fichier, son chemin JSON et sa
ligne, `permissions.json:groups.vip.rules:12`.

## 1. Le serveur ne démarre pas

| Ce que dit le chargeur | Cause et remède |
|---|---|
| « Incompatible mods found! », avec AegisPerms et `luckperms`, `vanilla-permissions`, `universal_perms` ou `player_roles` | AegisPerms est seul maître des droits ; retirez l'autre mod |
| une dépendance manque : `fabric-api`, `fabric-language-kotlin`, `java` | posez la Fabric API de votre version et Fabric Language Kotlin dans `mods\`, et lancez avec Java 25 |

Rien de ce qui vient d'un fichier d'AegisPerms ne fait tomber le serveur : un fichier fautif est dit, puis remplacé par le repli (chapitre 3).

## 2. Les erreurs de la validation

Une erreur refuse la configuration entière : l'état en vigueur reste, et rien ne s'applique tant qu'elle n'est pas corrigée.

| Constat | Sens et remède |
|---|---|
| « Fichier illisible » | le fichier manque pendant que le serveur tourne, est verrouillé, ou un dossier porte son nom ; rendez-le lisible, puis `/aegis reload` |
| « Fichier mal formé » | ce n'est pas du JSON strict : une virgule finale, un commentaire, une clé sans guillemets, une accolade manquante ; la ligne est donnée |
| « Clé écrite deux fois dans le même objet » | un groupe, un joueur ou une règle en double ; gardez-en un |
| « schema-version manque », « schema-version N est plus récent » | ajoutez `"schema-version": 1` en tête, ou prenez un mod plus récent |
| « Clé inconnue » | une faute de frappe le plus souvent : `weigth`, `rule` ; les clés admises sont dans `les-fichiers.md` |
| « Valeur hors de son domaine » | une valeur fermée inconnue, `"include-ops": "owner"`, ou d'un autre type, un nombre entre guillemets ; la valeur attendue est donnée |
| « Le groupe default manque » | recréez-le : `"default": {}` suffit |
| « Nom de groupe mal formé » | minuscules, chiffres, `_` et `-` seulement |
| « Le groupe X hérite d'un groupe inconnu » | corrigez `parents` |
| « Cycle d'héritage » | le cycle est écrit `a > b > a` ; retirez un parent |
| « Clé mal formée » | la clé et son défaut sont donnés : un espace de noms manquant, une majuscule, un nom de nœud vide, un `%` qui n'échappe rien |
| « Forme lisible mal formée » | un joueur s'écrit `Pseudo#uuid`, l'UUID en minuscules avec ses tirets |
| « UUID non canonique » | l'écriture attendue est donnée |
| « L'UUID est porté par deux joueurs » | gardez une entrée |
| « Groupe inconnu » | un joueur nomme un groupe qui n'existe pas |
| « Nom d'alias mal formé » | un nom d'alias n'a ni blanc ni `/` |
| « Préfixe mal formé », « Le préfixe est donné à deux mods » | un préfixe s'écrit comme un modid, et il est unique |
| « L'alias X vise Y, dont Z n'est pas un littéral » | la cible d'un alias est une commande suivie de littéraux, jamais une valeur d'argument |
| « L'alias X vise Y avec le préfixe tapé » | écrivez la cible avec son modid, `minecraft:gamemode`, pas `mc:gamemode` |
| « La clé X est l'ancienne clé d'une commande rattachée » | un rattachement de `namespaces.owners` a changé la clé ; l'écriture attendue est donnée |

## 3. Les avertissements

Un avertissement laisse la configuration s'appliquer. Il se dit à la publication, et la réponse d'une modification répète ceux qui la concernent.

| Constat | Sens |
|---|---|
| « Clé de commande absente du catalogue » | aucune commande de ce serveur n'a cette clé : une faute de frappe, un mod absent, ou un argument renommé par une mise à jour ; la plus proche est proposée |
| « Clé d'API ni connue de vanilla ni jamais demandée » | aucun mod n'a demandé cette clé pour l'instant ; la règle répondra le jour où l'un la demande |
| « X est la clé d'un alias de vanilla et ne couvre rien » | écrivez la clé de la cible, donnée : `minecraft:command.teleport` pour `/tp` |
| « Le allow de X est sans effet sous le refus de Y » | une clé du chemin est refusée dans le même ensemble ou chez un parent ; rouvrez Y, ou posez le `allow` dans un groupe qui passe avant |
| « Le allow de X ouvre des clés sensibles » | `/function`, `/setblock`, `/op`, `/execute as`... donnent plus que leur nom ne le dit ; c'est un choix à faire en connaissance |
| « Le allow de X couvre Y, plus exigeant » | la règle ouvre une branche que vanilla réservait à un niveau plus haut ; un `deny` sur Y la referme |
| « Égalité de poids » | deux groupes de départ de même poids d'un joueur décident autrement d'une clé ; le nom tranche, et un poids différent lève le doute |
| « Le groupe X nomme deux fois le parent Y », « Le groupe X est inscrit deux fois », « default est inscrit comme groupe direct » | sans effet, à nettoyer |
| « visible est ignoré sur le allow de X » | `visible` n'a de sens que sur un `deny` |
| « require-prefix est ignoré sans les formes préfixées » | `namespaces.enabled` vaut `false` |
| « L'alias X est ignoré : son nom est déjà pris » | une commande racine ou une forme préfixée porte ce nom ; choisissez-en un autre |
| « L'alias X est ignoré : sa cible n'est pas sur le serveur » | le mod de la cible n'est pas chargé |
| « Le préfixe de X cède devant un mod du même identifiant » | un mod chargé a pour modid ce préfixe ; le mod garde son modid pour préfixe |
| « La forme préfixée de X est refusée : son nom est déjà pris » | une commande porte déjà le nom `préfixe:commande` |
| « Le rattachement de X est ignoré » | la commande est de vanilla, le mod nommé n'est pas chargé, ou la commande n'est pas sur le serveur |
| « La commande X n'a pas de mod propriétaire connu » | ses clés prennent l'espace de noms `unknown`, et elle n'a pas de forme préfixée ; `namespaces.owners` lui donne un mod |
| « La commande X est partagée » | plusieurs mods ont enregistré la même racine ; `namespaces.owners` fixe le propriétaire pour que la clé ne dépende plus de l'ordre de chargement |

**L'avertissement renforcé**, « Le deny de X ne ferme plus rien : sa clé n'existe plus », se répète à chaque démarrage et à chaque connexion d'un op
d'au moins `admin-level`, jusqu'à correction : une mise à jour qui renomme un argument ou change le modid d'un mod rendrait sinon l'accès que le refus fermait.
Les clés apparues au même endroit du catalogue sont données ; réécrivez le refus sur la nouvelle clé.

## 4. Le repli au démarrage et la lecture seule

Au démarrage, un fichier illisible ou invalide est remplacé par la copie `last-good\`, ou, sans copie valide, par les valeurs livrées. Le journal le dit en
erreur, « settings.json remplacé par last_good : corrigez le fichier, puis /aegis reload », et chaque op d'au moins `admin-level` qui se connecte le lit tant
que le repli dure.

Quand ce sont les droits qui se replient, le mod passe en **lecture seule** : les règles s'appliquent, et toute modification par commande répond
« Lecture seule : les droits ne viennent pas de leur fichier ». Même chose après un rechargement refusé. Corrigez le fichier, puis `/aegis reload` ; ou
`/aegis restore`, qui le remplace par une sauvegarde. `/aegis status` dit la lecture seule et le repli en cours.

Sans copie valide, le serveur reste fermé pour les non-ops, comme au premier démarrage : jamais ouvert à vanilla par accident.

## 5. Un joueur ne voit pas une commande

1. `/aegis why <joueur> <ligne>` dit, nœud par nœud, ce qui refuse et d'où vient la règle.
2. Sous la forme exigée, le défaut, un joueur ne tape que `/mc:teleport`, pas `/teleport` : la forme nue vaut « commande inconnue » pour lui.
3. `/teleport @p` demande aussi l'atome des sélecteurs, `minecraft:commands/entity_selectors`, que vanilla réserve au niveau 2.
4. Un `allow` sous un refus du chemin est sans effet : la règle qui refuse se lit dans `why`, et la validation le signale.
5. Une clé absente du catalogue ne couvre rien : `/aegis catalog <début>` donne les clés du serveur, et `/aegis search <clé>` les règles qui la touchent.
6. Un `/reload` du serveur reconstruit les commandes : les règles ne bougent pas, les formes et les alias se reconstruisent, et les arbres repartent.

## 6. Le journal

Le mod écrit dans le journal du serveur, sous le nom `aegisperms`, dans la langue de la machine :

| Ligne | Sens |
|---|---|
| « AegisPerms est chargé » | le mod a démarré |
| « Fichiers créés avec les valeurs livrées : ... » | le premier démarrage |
| « Génération N appliquée (source), mode ..., lecture seule ... : X groupes, Y joueurs, Z règles, W constats », puis un constat par ligne | chaque publication |
| « Publication refusée (source) : la génération N reste, W constats » | un rechargement, une commande ou une restauration refusés |
| « X remplacé par last_good », « ... par delivered » | le repli au démarrage |
| « N clés apparues au catalogue », « N clés disparues du catalogue » | ce qui change dans les commandes du serveur depuis le dernier démarrage |
| « Le catalogue précédent ne se lisait pas : il est régénéré, sans ses clés d'API » | `catalog.json` était corrompu |
| « X n'a pas pu s'écrire : ce qui était en place reste » | une panne d'écriture d'un fichier généré |
| « L'autorité de X n'est pas l'enveloppe d'AegisPerms, mais ... : le mod ne juge plus ce joueur » | un autre mod a remplacé l'autorité des joueurs ; `/aegis status` le dit aussi ; retirez ce mod |
| « X revient sous le pseudo de Y : sa forme lisible se réécrit » | un joueur du fichier a changé de pseudo |
| « Observation : pour X, Y vaudrait Z, et vanilla répond W » | en mode observation, un écart entre la décision et vanilla, une fois par joueur et par clé |
| « Observation : N écarts de plus dans la minute écoulée, non détaillés » | au-delà de soixante écarts dans la minute, le mod compte |
| « Erreur interne dans ... : refus » | une erreur du mod lui-même, dite une fois par clé et par génération avec sa trace ; pour un joueur, elle vaut un refus masqué ; signalez-la avec la trace |
| « Le fil des fichiers ne s'est pas arrêté en N s » | à l'arrêt du serveur, une écriture n'a pas fini à temps |

Le mod n'écrit ni les commandes que les joueurs exécutent, ni les refus qu'ils essuient. Les modifications par `/aegis` arrivent au journal par la diffusion
de vanilla, sous le nom de qui les a faites.
