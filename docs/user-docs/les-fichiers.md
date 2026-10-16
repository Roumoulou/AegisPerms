# Les fichiers d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Ce que le mod lit et écrit dans `config\aegisperms\`, pour l'administrateur qui édite ses fichiers à la main. Les commandes qui les modifient sont dans
`les-commandes.md`, les erreurs et leur remède dans `depannage.md`.

## 1. Le dossier

| Fichier | Contenu | Qui l'écrit |
|---|---|---|
| `settings.json` | les réglages (chapitre 2) | vous ; le mod quand il manque, et à `/aegis restore` |
| `permissions.json` | les groupes, les joueurs et leurs règles (chapitre 3) | vous, et le mod pour `/aegis` et le renommage d'un joueur |
| `catalog.json` | le catalogue des clés du serveur (chapitre 4) | le mod seul, à chaque démarrage et à chaque `/reload` réussi |
| `state.json` | la dernière publication, appliquée ou refusée, et ses constats (chapitre 5) | le mod seul, à chaque tentative |
| `last-good\` | la dernière configuration publiée, pour le démarrage suivant (chapitre 6) | le mod seul |
| `backups\<date>_<heure>[-nom]\` | une sauvegarde des deux fichiers (chapitre 7) | le mod, sur `/aegis backup` et avant `/aegis restore` |

Tous ces fichiers sont du JSON strict : ni commentaire, ni virgule finale, ni clé sans guillemets, et une clé inconnue est une erreur, ce qui attrape les fautes
de frappe. Un commentaire a sa place dans un champ `note`. Ils s'écrivent en UTF-8 sans BOM et en LF ; un BOM posé par un éditeur est toléré à la lecture.

Chaque fichier que le mod lit porte en tête `schema-version`, le numéro de la structure du fichier, `1` pour cette version ; un fichier sans cette clé, ou d'un
numéro plus récent, est refusé.

Le mod réécrit un fichier en entier, jamais en place : votre mise en forme ne survit pas à une écriture du mod, vos notes si. Il écrit une clé par ligne, une
liste de valeurs simples sur une ligne, deux espaces d'indentation, les clés triées, et omet ce qui vaut sa valeur par défaut. Les écritures sont atomiques :
un fichier est toujours une version entière, même après une coupure.

## 2. Les réglages, `settings.json`

Tel que le mod le crée au premier démarrage :

```json
{
  "schema-version": 1,
  "admin-level": "owners",
  "denied-commands": "hidden",
  "refusal-message": null,
  "language": "auto",
  "mode": "enforce",
  "namespaces": {
    "enabled": true,
    "require-prefix": true,
    "prefixes": {
      "minecraft": "mc"
    },
    "owners": {}
  },
  "aliases": {},
  "watch-files": true
}
```

| Réglage | Valeurs | Défaut | Ce qu'il fait |
|---|---|---|---|
| `admin-level` | `moderators`, `gamemasters`, `admins`, `owners` | `owners` | le niveau d'op à partir duquel un op garde `/aegis`, quoi que disent les règles |
| `denied-commands` | `hidden`, `visible` | `hidden` | la visibilité d'un refus dont la règle n'en dit rien : masqué, la commande disparaît ; visible, elle reste et un message la refuse |
| `refusal-message` | un texte, ou `null` | `null` | le message du refus visible, à la place du message livré ; affiché tel quel |
| `language` | `auto`, `fr`, `en` | `auto` | `auto` : la langue du client de chaque joueur, celle de la machine pour la console ; `fr` ou `en` l'impose à tous |
| `mode` | `enforce`, `observe` | `enforce` | en observation, le mod calcule et journalise ses décisions sans les appliquer |
| `namespaces.enabled` | `true`, `false` | `true` | les formes préfixées, `/mc:teleport` |
| `namespaces.require-prefix` | `true`, `false` | `true` | la forme exigée : un joueur ne voit et ne tape que les formes préfixées ; ignoré, avec un avertissement, sans les formes préfixées |
| `namespaces.prefixes` | un objet modid vers préfixe | `{"minecraft": "mc"}` | le préfixe d'un mod ; par défaut, son modid ; un préfixe qui est le modid d'un autre mod chargé lui cède la place |
| `namespaces.owners` | un objet commande racine vers modid | `{}` | donne à la main un mod propriétaire à une commande qui n'en a pas de connu, ou en a plusieurs |
| `aliases` | un objet nom vers cible | `{}` | un alias : `"gm": "minecraft:gamemode"`, `"wla": "minecraft:whitelist add"` |
| `watch-files` | `true`, `false` | `true` | la surveillance : un fichier écrit hors du mod se recharge tout seul, deux à trois secondes après |

Le mod ne réécrit jamais ce fichier quand il existe. Un changement se fait à la main et prend effet à `/aegis reload`, ou tout seul par la surveillance ; un
changement de `namespaces` ou d'`aliases` ne prend effet qu'à la construction suivante des commandes, au prochain `/reload` du serveur ou au redémarrage, et la
réponse de `/aegis reload` le rappelle.

**Les alias.** Le nom : tout caractère sauf les blancs et `/`. La cible : la commande racine, `<modid>:<nom>` ou le nom seul, suivie au besoin de littéraux,
`minecraft:whitelist add` ; jamais une valeur d'argument, et jamais le préfixe qu'on tape, `mc:gamemode`. Un alias dont le nom est déjà celui d'une commande
racine ne crée rien : s'il mène au même endroit, il rend la forme nue aux joueurs, `"msg": "minecraft:msg"` leur rend `/msg` ; sinon il est ignoré, avec un
avertissement. Un alias se juge sur la clé de sa cible.

## 3. Les droits, `permissions.json`

Tel que le mod le crée au premier démarrage :

```json
{
  "schema-version": 1,
  "groups": {
    "admin": {
      "include-ops": "owners",
      "rules": {
        "command": "allow"
      },
      "weight": 100
    },
    "default": {
      "rules": {
        "command": "deny",
        "minecraft:command.help": "allow",
        "minecraft:command.me": "allow",
        "minecraft:command.msg": "allow"
      }
    }
  },
  "players": {}
}
```

**Un groupe** a pour clé son nom, minuscules, chiffres, `_` et `-`, et pour champs, tous facultatifs :

| Champ | Contenu |
|---|---|
| `weight` | un entier, 0 quand il manque ; les groupes d'un joueur s'examinent par poids décroissant |
| `parents` | la liste ordonnée des groupes dont il hérite ; un cycle est une erreur |
| `include-ops` | `moderators`, `gamemasters`, `admins` ou `owners` : tout op d'au moins ce niveau en est membre d'office, sans inscription |
| `rules` | ses règles, sous leur clé |
| `note` | un texte libre |

Le groupe `default` existe toujours, s'applique à tout joueur sans être nommé, et ne se supprime pas.

**Un joueur** a pour clé sa forme lisible, `Pseudo#uuid`, et pour champs `groups`, la liste de ses groupes directs, `rules` et `note`. L'UUID est l'identité,
en minuscules avec ses tirets ; le pseudo n'est là que pour lire, et le mod le réécrit quand le joueur revient sous un autre. Un joueur ajouté par son UUID dont
aucun pseudo n'est connu s'écrit `?#<uuid>`.

**Une règle** s'écrit en forme courte, `"allow"` ou `"deny"`, ou en forme longue, un objet qui porte `value` et, en option, `visible` pour un `deny` et `note` :

```json
"minecraft:command.teleport.targets.location": {
  "note": "Les VIP se téléportent aux coordonnées.",
  "value": "allow"
},
"minecraft:command.kill": {
  "value": "deny",
  "visible": true
}
```

Un exemple complet, cinq groupes et quatre joueurs :

```json
{
  "schema-version": 1,
  "groups": {
    "admin": {
      "include-ops": "owners",
      "parents": ["moderateur"],
      "rules": {
        "command": "allow"
      },
      "weight": 100
    },
    "default": {
      "rules": {
        "command": "deny",
        "minecraft:command.help": "allow",
        "minecraft:command.me": "allow",
        "minecraft:command.msg": "allow"
      }
    },
    "membre": {
      "note": "Les joueurs réguliers.",
      "rules": {
        "minecraft:command.teleport": "allow",
        "minecraft:command.teleport.location": "deny",
        "minecraft:command.teleport.targets.location": "deny"
      },
      "weight": 10
    },
    "moderateur": {
      "parents": ["membre"],
      "rules": {
        "minecraft:command.kick": "allow",
        "minecraft:command.teleport": "deny",
        "minecraft:commands/entity_selectors": "allow"
      },
      "weight": 50
    },
    "vip": {
      "rules": {
        "minecraft:command.teleport.targets.location": "allow"
      },
      "weight": 80
    }
  },
  "players": {
    "Alice#8f4d2c3a-5b6e-4f70-8a91-0b2c3d4e5f60": {
      "groups": ["admin"]
    },
    "Bob#1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d": {
      "groups": ["moderateur"]
    },
    "Joe#9c8b7a6f-5e4d-4c3b-8a19-0f1e2d3c4b5a": {
      "groups": ["membre", "vip"]
    },
    "Jogger#3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b": {
      "groups": ["membre"],
      "rules": {
        "minecraft:command.teleport.targets.location.rotation": "allow"
      }
    }
  }
}
```

Joe, dans `vip` et `membre`, se téléporte aux coordonnées : `vip`, poids 80, répond avant `membre`, poids 10. Bob, modérateur, ne se téléporte pas :
`moderateur` refuse `/teleport` avant `membre`, dont il hérite. La règle propre de Jogger est sans effet, parce que `membre` refuse déjà
`minecraft:command.teleport.targets.location`, qui est sur le chemin.

## 4. Le catalogue, `catalog.json`

Le mod l'écrit à chaque démarrage et à chaque `/reload` réussi ; il ne se lit pas comme une configuration, mais il dit toutes les clés de votre serveur.
`/aegis catalog` en montre une partie en jeu.

```json
{
  "schema-version": 1,
  "commands": {
    "minecraft:command.teleport": {
      "kind": "literal",
      "requirement": "gamemasters",
      "owner": "minecraft",
      "prefixed-form": "mc:teleport",
      "aliases": ["tp"]
    },
    "minecraft:command.tp": {
      "kind": "literal",
      "requirement": "gamemasters",
      "alias-of": "minecraft:command.teleport",
      "owner": "minecraft",
      "prefixed-form": "mc:tp"
    }
  },
  "api-keys": {
    "minecraft:commands/entity_selectors": {
      "type": "atom",
      "first-requested": "2026-10-16T12:00:00Z"
    }
  }
}
```

| Champ d'une clé de commande | Ce qu'il porte |
|---|---|
| `kind` | `literal` ou `argument` |
| `executable` | `true` pour un nœud qui s'exécute |
| `requirement` | `none`, un niveau d'op par son nom, ou `mod-predicate`, une condition écrite par un mod |
| `alias-of` | la clé de la cible : une règle écrite sur la clé d'un alias ne couvre rien |
| `shared`, `more-demanding` | `true` pour un nœud rattaché à plusieurs endroits de l'arbre, pour un nœud qui exige plus que son ancêtre |
| `sensitive` | ce que la commande donne en plus de son nom : `functions`, `command_blocks`, `command_minecart`, `lectern_book`, `data_writing`, `impersonation` ou `op_change` |
| `owner`, `co-owners` | le mod propriétaire, et les autres mods qui ont greffé une branche sur la même racine |
| `prefixed-form`, `aliases` | la forme préfixée et les alias, tels qu'on les tape |

Une clé d'API porte `type`, la voie par laquelle elle est venue, `atom`, `fabric` ou `lucko`, et `first-requested`, l'instant de sa première demande ; une
chaîne de l'API de lucko qui ne fait pas une clé entre avec `refused`, et une clé dont un segment porte un point avec `dotted-path`. Les clés d'API se gardent
d'un démarrage à l'autre.

## 5. L'état, `state.json`

La dernière tentative de publication, pour qui édite les fichiers sans voir la console : `generation`, la génération en vigueur après elle ; `date` ;
`source`, `startup`, `reload`, `command`, `watch` ou `restore` ; `result`, `applied` ou `refused` ; `issues`, les constats, chacun avec son niveau, sa nature,
son fichier, son chemin, sa ligne et ses arguments.

```json
{
  "schema-version": 1,
  "generation": 3,
  "date": "2026-10-16T12:00:00Z",
  "source": "watch",
  "result": "refused",
  "issues": [
    {
      "severity": "error",
      "kind": "unknown_group",
      "file": "permissions.json",
      "path": ["players", "Joe#9c8b7a6f-5e4d-4c3b-8a19-0f1e2d3c4b5a", "groups", "0"],
      "line": 42,
      "arguments": ["vips"]
    }
  ]
}
```

## 6. Le repli, `last-good\`

Après chaque publication réussie, le mod copie la configuration publiée dans `last-good\` : `settings.json`, `permissions.json` et un `manifest.json` qui
porte la génération et l'instant. Au démarrage, quand un fichier est illisible ou invalide, le mod prend celui de cette copie, ou, sans copie valide, les
valeurs livrées :

- les réglages se replient sans rien réécrire ;
- les droits se replient en **lecture seule** : les règles s'appliquent, mais les modifications par commande sont refusées, sauf `/aegis reload` et
  `/aegis restore`, jusqu'à ce qu'une configuration valide soit relue.

Le journal le dit en erreur, et chaque op d'au moins `admin-level` qui se connecte le lit tant que le repli dure. Corrigez le fichier, puis `/aegis reload`,
ou `/aegis restore` pour le remplacer par une sauvegarde.

## 7. Les sauvegardes, `backups\`

`/aegis backup [nom]` copie les deux fichiers, à l'octet, dans `backups\<AAAA-MM-JJ>_<HH-mm-ss>[-nom]\`, à l'heure du serveur, avec un `manifest.json` ; un
dossier existant n'est jamais écrasé. Rien ne s'y valide : on sauvegarde aussi des fichiers fautifs.

`/aegis restore <dossier>` n'admet que le nom d'une sauvegarde de `backups\`. Il la valide comme une configuration entière, met de côté les fichiers en place
dans une sauvegarde `-before-restore`, les remplace par ceux de la sauvegarde, et publie. Le mod ne supprime jamais une sauvegarde.

## 8. Le rechargement et la surveillance

Un fichier changé s'applique par `/aegis reload`, ou tout seul par la surveillance : toutes les deux secondes, le mod relève l'empreinte des deux fichiers,
et un changement qu'il n'a pas fait lui-même déclenche un rechargement une seconde plus tard, le temps que l'éditeur finisse d'écrire. Le résultat va au
journal, à `state.json` et aux ops d'au moins `admin-level` en ligne.

Une configuration invalide ne s'applique jamais : l'état en vigueur reste, chaque erreur se dit avec son fichier, son chemin et sa ligne, et les modifications
par commande attendent une configuration valide. Le mod n'écrit pas non plus sur un fichier qui a bougé depuis sa dernière lecture : la commande refuse, et
`/aegis reload` relit d'abord.
