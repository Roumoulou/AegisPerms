# Les commandes `/aegis`

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

La référence de `/aegis`, pour l'administrateur. Les clés et les groupes sont expliqués dans `venir-de-luckperms.md`, les fichiers dans `les-fichiers.md`.

## 1. Qui lance `/aegis`

La console, le RCON, et les ops dont le niveau d'op atteint `admin-level` (`owners`, sa valeur livrée, dans `settings.json`) gardent `/aegis`, quoi que disent
les règles. Un joueur qui n'est pas op l'obtient par une règle `allow` sur `aegisperms:command.aegis` ; `command` à `allow` ne l'ouvre pas. Un bloc de commande,
une fonction et un `execute as <joueur> run aegis ...` lancé par la console sont refusés.

## 2. Les conventions

- `<joueur>` est un UUID, avec ou sans tirets, ou un pseudo : celui d'un joueur en ligne, du cache de profils du serveur, ou d'une forme lisible du fichier des
  droits, sans tenir compte de la casse. Quand ces sources donnent deux UUID différents, la commande refuse et les nomme : écrivez l'UUID. Un UUID écrit tel
  quel désigne aussi un joueur jamais venu.
- `<groupe>`, `<parent>` et `<nom>` sont des noms de groupe : minuscules, chiffres, `_` et `-`.
- `<clé>` est une clé de commande ou d'API, lue jusqu'au bout de la ligne.
- `<commande>` est une ligne telle que le joueur la taperait, sans le `/`, sous sa forme nue, préfixée ou par un alias, lue jusqu'au bout.
- `<niveau>` est `moderators`, `gamemasters`, `admins` ou `owners`.
- Un texte libre se met entre guillemets.

Le mod complète les arguments : les joueurs en ligne et ceux du fichier, les groupes, les clés du catalogue, les racines de commande, les dossiers de
`backups/`.

## 3. Consulter

| Commande | Ce qu'elle répond |
|---|---|
| `/aegis` | l'usage |
| `/aegis status` | la version du mod ; la génération en vigueur, sa source et sa date ; le mode ; la lecture seule ; la surveillance ; l'enveloppe en place ou remplacée ; les nombres de groupes, de joueurs et de règles ; le nombre de constats de la dernière publication |
| `/aegis why <joueur> <commande>` | la ligne analysée sans être exécutée, et la décision expliquée nœud par nœud (chapitre 5) |
| `/aegis check <joueur> <clé>` | la décision d'une clé pour ce joueur : la valeur, la règle qui répond et d'où elle vient, les ensembles écartés, l'étape 0 quand elle joue ; pour une clé de commande, si le nœud est permis compte tenu de son chemin ; pour la clé d'un alias de vanilla, la clé de la cible |
| `/aegis search <clé>` | les groupes et les joueurs qui portent une règle sur cette clé ou sur une clé qui la couvre, avec la valeur de chaque règle |
| `/aegis catalog [<début de clé>]` | les clés du catalogue qui commencent ainsi, trente au plus : l'exigence vanilla, la forme préfixée et les alias, les marques `sensitive`, `shared` et `more-demanding` ; puis le nombre de clés restantes |
| `/aegis manual [fr\|en]` | écrit le manuel des commandes du serveur dans `config/aegisperms/manuals/`, dans votre langue sans argument ; la réponse donne le fichier et les comptes ; ce que le manuel contient est au chapitre 9 de `les-fichiers.md` |
| `/aegis group list` | les groupes par poids décroissant puis nom, avec le poids, le nombre de règles, les parents et les membres d'office |
| `/aegis group <groupe> info` | le poids, les parents, les groupes qui en héritent, `include-ops`, la note, les règles triées, les joueurs du fichier qui l'ont pour groupe direct |
| `/aegis player <joueur> info` | l'UUID et le pseudo ; en ligne ou hors ligne, avec le niveau d'op ; les groupes directs et d'office ; l'ordre d'examen ; les règles propres, et celles que la validation dit sans effet |
| `/aegis validate` | les deux fichiers relus et validés sans rien appliquer : les constats, puis les règles qui changeraient |

Une consultation répond à qui l'a lancée, ligne par ligne, et ne touche à rien.

## 4. Modifier

| Commande | Ce qu'elle fait |
|---|---|
| `/aegis group <groupe> create [<poids>]` | crée un groupe vide, de poids 0 sans argument |
| `/aegis group <groupe> delete` | supprime un groupe que rien ne référence ; refuse `default`, un groupe parent d'un autre ou groupe direct d'un joueur, et nomme ce qui le retient |
| `/aegis group <groupe> rename <nom>` | renomme le groupe et chaque référence, dans les parents et chez les joueurs ; refuse `default` et un nom déjà pris |
| `/aegis group <groupe> clone <nom>` | crée `<nom>` avec le poids, les parents, les règles et la note du groupe ; ni `include-ops` ni les membres ne se copient |
| `/aegis group <groupe> weight <poids>` | le poids |
| `/aegis group <groupe> parent add <parent>` | l'héritage ; refuse un cycle, le groupe lui-même et `default`, qui s'applique à tous |
| `/aegis group <groupe> parent remove <parent>` | retire un parent |
| `/aegis group <groupe> include-ops <niveau>` | les membres d'office : tout op d'au moins ce niveau ; `none` les retire |
| `/aegis group <groupe> allow <clé>` | une règle `allow` du groupe |
| `/aegis group <groupe> deny <clé>` | une règle `deny`, dont la visibilité suit le réglage `denied-commands` |
| `/aegis group <groupe> deny-visible <clé>`, `deny-hidden <clé>` | une règle `deny` qui écrit sa visibilité |
| `/aegis group <groupe> unset <clé>` | retire la règle |
| `/aegis group <groupe> note "<texte>" [<clé>]` | la note du groupe, ou celle de l'une de ses règles ; `""` l'efface |
| `/aegis player <joueur> group add <groupe>`, `remove <groupe>` | ses groupes directs ; `default` se refuse |
| `/aegis player <joueur> allow <clé>`, `deny`, `deny-visible`, `deny-hidden`, `unset` | ses règles propres, comme pour un groupe |
| `/aegis player <joueur> note "<texte>" [<clé>]` | sa note, ou celle de l'une de ses règles |
| `/aegis reload` | relit les deux fichiers, les valide et publie ; la réponse dit la génération, les règles qui changent et les avertissements |
| `/aegis backup [<nom>]` | copie les deux fichiers dans `backups/<AAAA-MM-JJ>_<HH-mm-ss>[-<nom>]/` ; le nom : minuscules, chiffres, `_` et `-`, trente-deux caractères au plus |
| `/aegis restore <dossier>` | remplace les deux fichiers par ceux d'une sauvegarde de `backups/`, et publie (`les-fichiers.md`, chapitre 8) |
| `/aegis mode observe`, `enforce` | change le mode en mémoire, tout de suite, et renvoie les arbres aux joueurs ; le fichier des réglages ne change pas, et le prochain rechargement reprend son mode |

Un joueur entre dans le fichier des droits à sa première règle, à son premier groupe ou à sa première note, et en sort quand il n'a plus rien. Un groupe
vide y reste.

Une modification se calcule sur l'état en vigueur, se valide entière, s'écrit d'un bloc dans `permissions.json`, puis se publie. La
réponse se diffuse aux ops en ligne et s'inscrit au journal du serveur, comme `/op` ; elle dit la génération publiée et répète les avertissements nouveaux :
une clé absente du catalogue avec la plus proche, un `allow` sur une clé sensible, un `allow` sous un refus. Une modification refusée dit pourquoi et ne touche
à rien :

| Refus | Cause |
|---|---|
| « Lecture seule : les droits ne viennent pas de leur fichier » | le démarrage s'est replié sur la copie `last-good` ou sur les valeurs livrées, ou le dernier rechargement a été refusé ; `/aegis reload` ou `/aegis restore` d'abord |
| « Le fichier des droits a bougé depuis sa dernière lecture » | quelqu'un a écrit `permissions.json` pendant ce temps ; `/aegis reload` d'abord |
| « Modification refusée, la configuration serait invalide » | la validation refuse ce que la modification écrirait, et dit chaque erreur |
| « Rien à changer » | la configuration dit déjà cela |

## 5. Lire `why`

```text
/aegis why Jogger mc:teleport Jogger 10 64 10 90 0
mc:teleport est une forme ou un alias de teleport (mod minecraft)
  teleport : vu, allow, membre : minecraft:command.teleport
  targets : vu, allow, membre : minecraft:command.teleport, clé ancêtre
  location : refusé, masqué, deny, masqué, membre : minecraft:command.teleport.targets.location
  rotation : sans effet, allow, règle propre : minecraft:command.teleport.targets.location.rotation
Écarté : default, command = deny
Verdict : refusée, masquée ; Jogger ne voit pas la commande
```

Pour chaque nœud de la ligne, dans l'ordre : son nom, son statut, sa décision, et la règle qui l'emporte avec son ensemble, qui est un groupe (et par quel
héritage), la règle propre du joueur, ou l'étape 0 pour `/aegis` ; « clé ancêtre » quand une clé plus courte couvre le nœud. Les statuts :

| Statut | Sens |
|---|---|
| vu | le joueur voit le nœud |
| refusé, masqué | un `deny` masqué : le nœud disparaît de l'arbre et de l'analyse |
| refusé, visible | un `deny` visible : le nœud reste vu, et l'exécution est refusée |
| vu sous un refus visible | un descendant d'un refus visible, vu lui aussi |
| non vu, condition du mod | la décision vaut `allow`, mais une condition du mod qui ne porte pas sur les droits refuse |
| non vu, exigence vanilla | aucune règle, et l'exigence vanilla du nœud ne passe pas |
| hors catalogue | le nœud n'a pas de clé, et vanilla décide |
| non évalué | le joueur est hors ligne, et la condition demande une entité présente |
| sans effet | un `allow` sous un refus du chemin |
| forme nue | la forme nue d'une commande qui a une forme préfixée, masquée au joueur |

Puis les ensembles écartés après celui qui a répondu, les égalités de poids qui ont joué, et le verdict :

| Verdict | Quand |
|---|---|
| permise | aucun refus sur le chemin, et la ligne atteint un nœud qui s'exécute |
| refusée, masquée | le premier refus du chemin est masqué : un `deny` masqué, ou une forme nue sous la forme exigée |
| refusée, visible | le premier refus du chemin est visible |
| refusée par vanilla | l'exigence vanilla d'un nœud sans règle ne passe pas |
| incomplète | la ligne s'arrête avant un nœud qui s'exécute |

Pour un joueur hors ligne, la sonde porte son niveau d'op d'`ops.json`, et la dernière ligne le dit. En mode observation, une ligne rappelle que la décision est
calculée, pas appliquée.
