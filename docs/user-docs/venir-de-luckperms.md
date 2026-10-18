# Le modèle d'AegisPerms, pour qui vient de LuckPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Pour l'administrateur qui connaît LuckPerms, Vanilla Permissions ou Player Roles, et qui veut savoir ce qu'AegisPerms fait de même, ce qu'il fait autrement,
et pourquoi : c'est aussi la référence du modèle, les clés, la résolution, les refus et les ops. La prise en main est dans `prise-en-main.md`.

## 1. Ce qui change d'abord

Sur Fabric, LuckPerms ne contrôle pas les commandes de vanilla : il laisse à vanilla toute exigence de niveau d'op, c'est-à-dire presque toutes les commandes,
et il lui faut un pont, Vanilla Permissions ou un autre, chacun avec sa convention de clés. AegisPerms est un pare-feu de commandes : il contrôle chaque nœud
de l'arbre des commandes, celles de vanilla et celles des mods, sous-commandes et arguments compris, avec une seule convention de clés, et il n'y a rien à
déclarer : les clés se déduisent de l'arbre, et un catalogue les liste toutes.

| | LuckPerms, avec un pont | AegisPerms |
|---|---|---|
| ce qu'il contrôle | les mods qui demandent une clé par une API ; les commandes de vanilla par le pont | toutes les commandes, nœud par nœud, et les demandes des deux API de permissions |
| la clé d'une commande | `minecraft.command.gamemode`, posée par le pont | `minecraft:command.gamemode`, déduite de l'arbre, avec le mod propriétaire devant |
| une sous-commande, un argument | selon le pont | `minecraft:command.gamemode.gamemode.target`, chaque nœud a sa clé |
| voir sans utiliser | non | un refus masque la commande ou la laisse visible, au choix |
| expliquer une décision | `/lp user <joueur> permission check <clé>` | `/aegis why <joueur> <ligne>`, nœud par nœud, et `/aegis check <joueur> <clé>` |
| le premier démarrage | tout comme en vanilla | fermé : `default` refuse toute commande sauf `/help` et `/msg` ; `admin` ouvre tout aux ops de niveau 4 |
| le stockage | dix formats, une base, un éditeur web | deux fichiers JSON, édités à la main ou par `/aegis`, versionnables |

Les deux ne cohabitent pas (`prise-en-main.md`, chapitre 1), et ce qu'AegisPerms ne fait pas est au chapitre 7.

## 2. Les clés

Une clé de commande s'écrit `<modid>:command.<chemin>` : le mod propriétaire de la commande, `minecraft` pour vanilla, puis `command`, puis les noms des nœuds
de la racine au nœud visé, séparés par des points. `/teleport <targets> <location>` donne `minecraft:command.teleport.targets.location`. Un nœud s'écrit par
son nom, celui que le code du jeu ou du mod lui donne, jamais par sa valeur : `creative` n'est pas une clé, `gamemode` l'est.

Une clé couvre ses descendantes, comme chez LuckPerms une permission couvre ses enfants quand le pont le prévoit. Il n'y a ni joker ni expression régulière :
`minecraft:command.teleport` couvre `minecraft:command.teleport.targets.location`. Trois clés générales couvrent des familles : `minecraft:command`, toutes
les commandes de vanilla ; `<modid>:command`, celles d'un mod ; `command`, toutes les commandes, `/aegis` excepté.

Les alias et les formes préfixées ne créent pas de clé : `/tp`, `/mc:teleport` et `/teleport` se jugent sur `minecraft:command.teleport`. Une règle écrite
sur `minecraft:command.tp` ne couvre rien, et la validation le dit. `/tp` n'a pas de forme préfixée : un joueur sous la forme exigée tape `/mc:teleport`, ou
l'alias que vous lui rendez.

**Les clés d'API** sont les identifiants que les mods demandent par les API de permissions : `homes:home/limit`. La chaîne à points de l'API de lucko,
`essentialcommands.home.set`, se lit comme l'identifiant `essentialcommands:home/set`, le premier segment pour espace de noms : c'est la convention des mods
qui demandent, l'inverse de celle de LuckPerms, qui écrit l'espace de noms, un point, puis le chemin avec ses `/` changés en `.`. Une règle sur
`essentialcommands:home/set` répond donc au mod qui demande `essentialcommands.home.set`.

## 3. La résolution : la priorité avant la précision

Chez LuckPerms, la précision d'une permission passe avant le poids du groupe : `admin` hérite du refus posé dans `moderateur` et doit le lever règle par règle.
AegisPerms fait l'inverse, et c'est le choix le plus important à connaître.

Pour décider si un joueur a une clé, le mod interroge des ensembles de règles dans un ordre total : ses règles propres d'abord, puis ses groupes. Dans chaque
ensemble, la règle la plus précise qui couvre la clé répond ; et **le premier ensemble qui répond l'emporte**, même par une clé générale. `command` à `allow`
sur `admin`, poids 100, l'emporte sur `minecraft:command.teleport` à `deny` sur `moderateur`, poids 50, dont `admin` hérite : un groupe plus haut qui parle a
le dernier mot.

L'ordre d'examen :

1. les groupes de départ sont les groupes directs du joueur, ceux dont il est membre d'office par son niveau d'op, et `default` ;
2. ils se rangent par poids décroissant, puis par nom, `default` en dernier à poids égal ;
3. chacun se parcourt en profondeur : le groupe, puis ses parents dans l'ordre déclaré, puis les parents de ceux-ci ;
4. un groupe atteint plusieurs fois ne s'examine qu'une fois, à sa dernière place.

Un groupe passe donc toujours après tous les groupes du joueur qui en héritent, et `/aegis player <joueur> info` montre l'ordre d'examen du joueur. Sans
règle, la décision est `unset` et vanilla décide : l'exigence de niveau d'op de la commande s'applique au joueur tel qu'il est.

## 4. Voir et utiliser

Un refus ferme la commande et tout ce qui descend de sa clé ; un `allow` posé sous un nœud refusé est sans effet, et la validation le signale. Pour rouvrir,
il faut une règle sur la clé refusée elle-même, ou sur l'une de ses ancêtres, dans un ensemble qui passe avant celui du refus.

Le refus est **masqué** par défaut : la commande disparaît de la complétion et de l'analyse, et le joueur qui la tape reçoit l'erreur d'une commande inconnue.
Le refus **visible** la laisse voir et compléter, et un message la refuse au moment d'exécuter, sans nommer la règle. Une règle dit son refus `deny-visible`
ou `deny-hidden`, ou laisse le réglage `denied-commands` décider.

`allow` lève les exigences de niveau d'op de la commande et de ses descendants, même ceux qui exigent plus : ouvrir `minecraft:command.datapack` ouvre aussi
`datapack create`, que vanilla réserve aux ops de niveau 4 ; le catalogue marque ces descendants, et la réponse d'une modification vous avertit. `allow`
garde les autres conditions qu'un mod pose, comme une commande réservée aux joueurs ou une option à activer.

## 5. Les ops

- Un joueur op garde les pouvoirs que vanilla attache à son niveau d'op hors des commandes (le sélecteur de mode de jeu, les règles de jeu), et un op de
  niveau 2 ou plus pose des blocs de commande qui exécutent sans être jugés : fermer une commande à un tel op n'est pas une frontière de sécurité. La
  configuration sûre tient les modérateurs non op, et leur ouvre par `allow` ce qu'on veut leur laisser.
- Les membres d'office remplacent les pistes et le groupe primaire : un groupe déclare `include-ops` à un niveau d'op, et tout op d'au moins ce niveau en est
  membre tant qu'il l'est, `/op` et `/deop` faisant entrer et sortir dès la commande suivante.
- `/aegis` reste aux ops d'au moins `admin-level`, `owners` par défaut, quoi que disent les règles ; `command` à `allow` ne l'ouvre pas.

## 6. Les API de permissions

AegisPerms répond aux deux API par lesquelles les mods demandent une clé : celle de la Fabric API, `fabric-permission-api-v1`, et celle de lucko,
`me.lucko:fabric-permissions-api`, qu'il embarque. Un mod qui demande `homes:home/limit` ou `essentialcommands.home.set` reçoit la décision des mêmes groupes
que les commandes : `true` pour `allow`, `false` pour `deny`, et rien pour `unset`, où le mod suit son propre repli, un niveau d'op le plus souvent. Les
valeurs typées et les options restent à une version suivante : un mod qui demande une méta reçoit une réponse vide.

Chaque clé demandée entre au catalogue à sa première demande, avec la voie par laquelle elle est venue : `/aegis catalog homes:` les montre.

## 7. Ce qu'AegisPerms ne fait pas

AegisPerms ne fait que les commandes et les clés d'API, côté serveur. Il laisse à d'autres :

- le chat et ses préfixes, que Styled Chat et Placeholder API gèrent, en lisant les groupes par l'API de permissions ;
- les contextes, les pistes de promotion, le groupe primaire, les rangs au temps de jeu ;
- la synchronisation entre serveurs, l'éditeur web, les bases de données ;
- ce qu'un joueur fait hors des commandes : casser un bloc, entrer dans une zone.

---

*Dernière vérification : 2026-10-18.*
