# Changelog

Les versions publiées d'AegisPerms, de la plus récente à la plus ancienne. Une version est un tag `v<version>` du dépôt, une release GitHub, et un jar par
version de Minecraft servie sur Modrinth et CurseForge. Le changelog publié avec chaque version, en anglais puis en français, est le corps de sa release.

## 1.0.0-alpha.1, le 2026-10-18

La première version publique, en alpha : le code est complet et passe ses trois étages de tests sur 26.1.2, 26.2 et 26.3, mais le plan de tests en jeu n'a
pas encore été joué en entier, et le jar embarque Storify en `0.6.0-SNAPSHOT`. La `1.0.0` suivra les essais en jeu et les versions figées.

- Des groupes pondérés à héritage, des règles propres à un joueur, des membres d'office par niveau d'op, et un serveur fermé par défaut : `default` ouvre
  `/help` et `/msg`, et vanilla décide partout où aucune règle ne parle.
- Des clés déduites de l'arbre des commandes, et un catalogue qui les liste toutes avec leur exigence vanilla.
- Des refus masqués ou visibles, règle par règle ou par le réglage `denied-commands` ; `/aegis` reste aux administrateurs.
- Des formes préfixées et des alias : `/mc:teleport`, `/gm`, `/wla` ; une forme par commande : un alias de vanilla n'a pas de forme préfixée, et la table des
  alias le rend à qui le veut.
- `/aegis` : `status`, `why`, `check`, `search`, `catalog`, `manual`, les groupes et les joueurs, `reload`, `validate`, `backup`, `restore`, `mode`, en
  français et en anglais.
- `/aegis manual` : le manuel de toutes les commandes du serveur, mod par mod, chaque nœud avec sa clé et une description, dans `config/aegisperms/manuals/`.
- Deux fichiers JSON validés avec la ligne de chaque erreur, rechargés deux à trois secondes après une édition à la main, un repli `last-good`, des sauvegardes
  et des restaurations.
- Le mode observation, qui calcule et journalise les décisions sans les appliquer.
- Les fournisseurs des deux API de permissions, celle de la Fabric API et celle de lucko.
- L'incompatibilité déclarée avec LuckPerms, Vanilla Permissions, Universal Perms et Player Roles.
