# La mesure de performance d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Ce que le mod coûte au serveur, comment on le mesure, et les chiffres relevés. Le seuil et le protocole viennent du cahier des charges (7.6).

## 1. Ce qui est construit pour ne pas coûter

- **Le chemin chaud** est `canUse`, appelé à chaque envoi d'arbre, à chaque analyse et à chaque complétion, pour chaque nœud. La fiche du nœud est dans le
  nœud, par un champ qu'un mixin ajoute à `CommandNode` : aucun découpage de chaîne, aucune table à consulter. Le sujet est dans l'enveloppe, et la décision
  dans le cache du résolveur, sous l'UUID et le niveau d'op du sujet et sous la clé, clés du chemin comprises. Aucune allocation en régime établi, hors un
  prédicat de mod évalué sous `allow`, qui alloue une source à tous droits par `withPermission` ; le résultat d'un tel prédicat ne se met pas en cache, parce
  qu'il dépend de la source.
- **L'étape 0**, qui garde `/aegis` aux ops d'au moins `admin-level`, ne passe pas par le cache : elle lit le niveau de l'ensemble vanilla que porte
  l'enveloppe.
- **Le renvoi des arbres** est regroupé à la fin du tick, pour les seuls joueurs concernés ; une publication qui change les réglages ou la structure des
  groupes concerne tout le monde.
- **Les fichiers** ne se lisent jamais dans le chemin chaud ni sur le fil serveur en régime établi : la relecture, la validation et la compilation d'un état
  se font sur le fil des fichiers, et seule la publication, une référence, revient au fil serveur.
- **L'arbre du client**, sous la forme exigée, n'est pas plus gros qu'en vanilla : une seule forme par commande est visible. Forme exigée désactivée, chaque
  sous-arbre part deux fois.

## 2. Le protocole

À jouer sur l'environnement `server` d'un module de version, profil `dev`, avec le `runClient` vanilla du même module, sur l'exemple complet de
`docs\user-docs\les-fichiers.md` (chapitre 3) : cinq groupes, quatre joueurs.

| Mesure | Comment | Seuil |
|---|---|---|
| un envoi d'arbre complet à un op, avec et sans le mod | le temps de `Commands.sendCommands` pour un op, forme exigée activée puis désactivée ; le mod retiré de `mods\` pour la référence | le surcoût avec le mod sous 50 % |
| les allocations dans `canUse` | un profil JFR de connexions répétées, le mod en place ; filtrer les allocations sur `fr.moulou.aegisperms` et sur le moyen d'accroche | aucune allocation du mod dans `canUse`, hors la branche du prédicat de mod sous `allow` |
| la taille du paquet de l'arbre | la taille de `ClientboundCommandsPacket` envoyé à un op, forme exigée activée puis désactivée | activée, la taille de vanilla ; désactivée, à noter |

Si `@ModifyReturnValue` sur `canUse` coûte l'évaluation vanilla pour rien sur les `deny`, `@WrapMethod` de MixinExtras est la solution de repli.

## 3. Les chiffres

La mesure n'a pas encore été prise : elle demande un client et un serveur lancés à la main, le geste de l'auteur, et elle est l'essai 18 du plan de tests en
jeu (`plan-de-tests-en-jeu.md`). Les chiffres iront dans cette table, avec la machine, la version du jeu et le commit du mod :

| Date | Machine | Jeu, commit | Envoi d'arbre sans le mod | Avec le mod, forme exigée | Avec le mod, sans forme exigée | Allocations dans `canUse` | Taille du paquet |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

Ce que le build mesure déjà, sans client : le serveur de test de chaque version joue ses 49 GameTests, dont les envois d'arbre à des joueurs simulés, en
moins de dix secondes, serveur compris, et le build complet depuis `clean` tient en une trentaine de secondes sur le poste de l'auteur, démon chaud.
