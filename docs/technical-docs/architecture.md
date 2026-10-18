# L'architecture d'AegisPerms

> *Type : doc technique.*
> *Modèle : les règles générales de documents-markdown.md (The Human Readme).*

Pour qui reprend le code : les modules, les packages, ce qui vit en mémoire et sur quel fil, et les endroits du jeu où le mod s'accroche. Ce que le mod doit
faire, et pourquoi, est au cahier des charges, privé, hors du dépôt (`README.md`, « Le dépôt »). Le build est dans `build-et-publication.md`.

## 1. Les modules

| Module | Projet Gradle | Ce qu'il porte | Ce qu'il connaît |
|---|---|---|---|
| `core` | `aegisperms-core` | le modèle de permissions, la résolution, la validation, les fichiers : tout ce qui se décide et s'écrit sans le jeu | Kotlin, kotlinx.serialization, Storify ; ni Minecraft, ni Fabric, ni Brigadier |
| `common` | `common` | le code du mod : l'index de l'arbre, les accroches, les mixins, `/aegis`, les textes, les deux fournisseurs d'API ; les tests des trois étages | `core`, Minecraft 26.3, Brigadier, la Fabric API, l'API de lucko |
| `mc-26.1`, `mc-26.2`, `mc-26.3` | idem | rien au départ ; le jar livrable et les environnements de sa version du jeu, les étages 1 et 2 rejoués contre elle | `common`, le jeu de sa lignée |

`core` se compile sans le jeu et se teste seul : c'est aussi le module que l'application de bureau de la deuxième version consommera. `common` se compile une
fois, contre la dernière release servie, et chaque module de version en fait un jar pour son jeu ; une vérification de compatibilité contrôle que ce que
`common` nomme existe dans chaque version, et un pont de version, inexistant aujourd'hui, prendrait ce qui diffère.

## 2. Les packages

Sous `fr.moulou.aegisperms` :

| Module | Package | Rôle | Dépend de |
|---|---|---|---|
| `core` | `model` | les clés et leur grammaire (`Keys`, `Key`), les règles, les groupes, les joueurs, les réglages, les décisions, l'état en vigueur et sa génération (`EffectiveState`), ce qui change entre deux états (`PermissionsChanges`) | kotlinx.serialization pour ses annotations |
| `core` | `resolve` | la résolution d'une clé pour un sujet et sa trace (`Resolver`, `DecisionTrace`), l'ordre d'examen (`ExaminationOrder`), le jugement d'un nœud par les clés de son chemin (`NodeJudgment`), le cache par sujet et par clé | `model` |
| `core` | `validate` | la validation d'une configuration et ses constats (`ConfigurationValidator`, `Issue`, `IssueKind`), face à un catalogue de clés (`Catalog`) | `model`, `resolve` |
| `core` | `files` | les deux stores Storify (`Stores`, `ConfigurationFiles`), la lecture et l'inspection (`FileReader`, `FileInspection`), `schema-version`, les empreintes, `last-good`, les sauvegardes, les manuels écrits (`Manuals`), le catalogue et l'état écrits (`CatalogFile`, `StateFile`), la mise en page (`JsonLayout`) | `model`, `validate`, Storify |
| `common` | `tree` | l'index des nœuds d'un dispatcher (`CommandIndex`, `KeyIndexer`), le mod propriétaire (`OwnerResolver`, `LoaderMods`), les formes préfixées et les alias (`CommandForms`), le catalogue calculé (`IndexCatalog`), les constats qui confrontent les réglages à l'arbre (`TreeValidator`) | `core`, Brigadier, Fabric Loader |
| `common` | `hook` | l'enveloppe (`Envelope`), ce que les mixins appellent (`Hooks`), la publication (`Publisher`), le fil des fichiers et la surveillance (`FileThread`), le cycle de vie (`Lifecycle`), le renvoi des arbres (`TreeResend`), le journal (`ModLog`), le mode observation (`ObservationLog`), les messages aux ops (`Notices`), les niveaux d'op retenus (`OpLevels`), les clés d'API demandées (`ApiKeyRequests`) | tout, Minecraft, la Fabric API |
| `common` | `mixin` | les quatre mixins, en Java, minces : ils appellent `hook` | Minecraft, Brigadier |
| `common` | `text` | les clés de traduction (`TextKeys`), les deux fichiers de langue lus depuis le jar (`Texts`), la langue et la forme du destinataire (`Messages`, `Recipient`) | `core`, Minecraft |
| `common` | `command` | `/aegis` : l'arbre (`AegisCommand`), qui la lance (`Administrators`), la résolution du joueur (`PlayerTargets`), les consultations (`Queries`), `why` et `check` (`Explain`, `Explanations`), les modifications (`Modifications`, `PermissionsEdits`), le manuel (`Manuals`), les réponses (`Reply`), les complétions (`Suggestions`) | `core`, `hook`, `text`, `manual`, Minecraft |
| `common` | `manual` | le manuel des commandes : le modèle (`Manual`, `ManualMod`, `ManualCommand`, `ManualNode`), bâti depuis l'index et le catalogue (`ManualBuilder`), rendu en Markdown dans une langue (`ManualRenderer`), les descriptions du jar et de `descriptions.json` (`Descriptions`) | `core`, `tree`, `text`, Brigadier |
| `common` | `api` | les fournisseurs des deux API de permissions (`FabricPermissionProvider`, `LuckoPermissionProvider`), le sujet d'une demande (`RequestSubject`) | `core`, `hook`, la Fabric API, l'API de lucko |

Deux règles tiennent ces frontières : `core` ne connaît pas le jeu, et le compilateur le garantit ; dans `common`, `tree`, `command` et `text` ont une partie
pure, éprouvée à l'étage 0 sur des arbres Brigadier synthétiques ou sans arbre du tout (`KeyIndexer`, `Explanations`, `PermissionsEdits`, `Texts`), et une
partie qui touche au jeu, éprouvée aux étages 1 et 2.

## 3. Ce qui vit en mémoire, et sur quel fil

**L'état en vigueur** (`EffectiveState`) : la configuration validée et compilée, immuable, avec sa génération, le numéro qui change à chaque publication. Son
résolveur (`Resolver`) porte le cache des décisions, par sujet, UUID et niveau d'op, et par clé. `Publisher` le publie d'un bloc par une référence
`@Volatile` : une génération nouvelle arrive avec un résolveur neuf, donc un cache vide, et un calcul fait sur l'ancienne ne peut rien y écrire. L'enveloppe,
`canUse`, la porte d'exécution et les fournisseurs d'API ne lisent que lui, depuis n'importe quel fil. `Publisher` retient aussi la dernière publication
appliquée (`LastPublication` : la génération, la source, la date, la lecture seule, les fichiers venus du repli, les constats), que `/aegis status` dit et
que la connexion d'un op rappelle, et le mode posé en mémoire par `/aegis mode`, jusqu'à la publication suivante.

**L'index** (`CommandIndex`) : attaché à l'instance de `Commands` qui l'a produit, par un champ que le mixin ajoute à `Commands` ; chaque nœud porte sa fiche
(`NodeEntry` : la clé, le mod propriétaire, les marques du catalogue) dans un champ que le mixin ajoute à `CommandNode`. La clé d'un nœud se retrouve par
identité, sans table ni découpage de chaîne. L'index se construit à la fin du constructeur de `Commands`, sur le fil qui le construit, celui de fond pendant
un `/reload` ; il n'est adopté, c'est-à-dire confié au fil des fichiers pour le catalogue et la validation, qu'à `SERVER_STARTING` et à la fin d'un `/reload`
réussi. Un `/reload` raté garde l'ancien `Commands` et son index.

**L'enveloppe** (`Envelope`) : le `PermissionSet` qu'un mixin substitue à celui de chaque joueur au retour de `ServerPlayer.permissions()`. Elle porte l'UUID
du joueur et l'ensemble vanilla qu'elle remplace, répond aux atomes par la décision du sujet et au reste par l'ensemble vanilla, et son `union` rend l'ensemble
vanilla, pour que `withMaximumPermission` sorte de l'enveloppe. Une source est jugée si et seulement si son `PermissionSet` est une enveloppe : toute accroche
commence par ce test, sans rien lire de l'état.

**Le fil des fichiers** (`FileThread`) : un `ScheduledThreadPoolExecutor` à un fil, démon, seul à appeler `ConfigurationFiles`, et seul à compter les
générations. Il sérialise lectures, validations, écritures, sauvegardes, catalogue, état et `last-good`. Le démarrage se fait en deux temps que le fil serveur
attend : `start` à l'initialisation du mod, qui ouvre les stores et publie la génération 0, provisoire, pour que le premier `Commands` trouve ses réglages ;
`startup` à `SERVER_STARTING`, l'index adopté, qui écrit le catalogue, relit avec lui, publie la génération 1 et lance la surveillance. Chaque travail qu'une
commande lui confie, `reload`, `write`, `check`, `backup`, `restore`, rend dans une `CompletableFuture` un `Outcome` : son résultat, ce qu'il a publié, et la
dernière publication d'avant lui ; `Reply.later` dépose la réponse sur le fil serveur quand il a fini. La surveillance (`watch`) relève toutes les deux secondes
ce que `ConfigurationFiles.changedOutside` dit, et recharge une seconde plus tard, de source `watch` ; un rechargement différé s'annule à l'arrêt.

**Le renvoi des arbres** (`TreeResend`) : les demandes s'accumulent, tout le monde après un changement des réglages ou de la structure des groupes, après un
`/reload` ou un `/aegis mode`, sinon les seuls joueurs que les règles changées touchent, et partent à la fin du tick, par `Commands.sendCommands`.

**Les niveaux d'op retenus** (`OpLevels`) : une table nourrie sur le fil serveur, par la liste des ops à `SERVER_STARTED` et par chaque enveloppe créée, pour
juger un sujet désigné par UUID, hors ligne ; un `/op` donné hors ligne n'est vu qu'à la connexion suivante.

**Les fils**, en résumé : le fil serveur lit l'état, juge, répond aux commandes et renvoie les arbres ; le fil des fichiers lit, valide, écrit et publie ; les
fils de fond d'un `/reload` construisent un `Commands` et son index sans rien publier ; les fournisseurs d'API et l'enveloppe peuvent être interrogés depuis
n'importe quel fil, et ne lisent que la référence publiée. Rien ne se partage d'autre que cette référence, les tables `ConcurrentHashMap` d'`OpLevels`,
d'`ApiKeyRequests` et d'`ObservationLog`, et les files de `TreeResend`.

## 4. Les accroches

Les mixins vivent dans `common\src\main\java\fr\moulou\aegisperms\mixin\`, déclarés dans `aegisperms.mixins.json` avec `defaultRequire: 1` : une cible qui
disparaît fait échouer le chargement, le bon réglage pour un mod de sûreté. Chacun appelle une fonction de `Hooks` ou de `hook`, où vit la logique et où les
tests la trouvent.

| Accroche | Cible | Moyen | Ce qu'elle fait |
|---|---|---|---|
| la décision par nœud | `CommandNode.canUse(S)` | `CommandNodeMixin`, `@ModifyReturnValue` | pour une source jugée, applique la décision à la valeur vanilla : `deny` ferme, `allow` passe et évalue un prédicat de mod avec la source à tous droits, `unset` garde vanilla ; la forme exigée retire la forme nue ; en observation, la valeur vanilla |
| les nœuds tardifs | `CommandNode.addChild` | `CommandNodeMixin`, `@Inject` au `TAIL` | un nœud ajouté après l'index reçoit sa fiche à la demande ; une racine tardive reçoit sa forme préfixée |
| la porte d'exécution | `Commands.performCommand(ParseResults, String)` et `CommandDispatcher.execute(ParseResults)` | `CommandsMixin` et `CommandDispatcherMixin`, `@Inject` au `HEAD`, annulable | lit les clés du chemin dans les contextes de l'analyse, l'enfant d'une redirection compris, et refuse tout refus, masqué ou visible, avec le message du refus visible ; en observation, laisse passer et note l'écart |
| l'autorité | `ServerPlayer.permissions()` | `ServerPlayerMixin`, `@ModifyReturnValue` | l'enveloppe, réutilisée tant que l'ensemble vanilla ne change pas |
| l'index, les formes et les alias | la fin du constructeur `Commands(CommandSelection, CommandBuildContext)` | `CommandsMixin`, `@Inject` au `TAIL` | ajoute les formes préfixées et les alias, puis construit l'index de ce `Commands` |
| les complétions | l'appel à `CommandNode.listSuggestions` dans `CommandDispatcher.getCompletionSuggestions` | `CommandDispatcherMixin`, `@WrapOperation` | ne fait tourner que les suggestions des enfants vus par le sujet |
| l'arbre du client | l'appel à `Map.get` dans `Commands.fillUsableCommands` | `CommandsMixin`, `@WrapOperation` | remplace une redirection vers une forme nue masquée par la forme préfixée de la même commande |

Et par les événements de la Fabric API, dans `Lifecycle`, `AegisPerms` et `api` : `SERVER_STARTING` pour l'adoption de l'index et le second temps du
démarrage ; `SERVER_STARTED` pour la liste des ops ; `END_DATA_PACK_RELOAD`, en cas de succès, pour l'adoption du nouvel index ; `END_SERVER_TICK` pour le
renvoi des arbres ; `JOIN` et `DISCONNECT` pour le contrôle de l'enveloppe, les messages aux ops, le renommage de la forme lisible et l'oubli du cache ;
`SERVER_STOPPING` pour l'arrêt du fil des fichiers ; `CommandRegistrationCallback` pour `/aegis` ; `PermissionEvents.ON_REQUEST` et les trois événements de
l'API de lucko pour les fournisseurs.

Une erreur interne dans une accroche, pour une source jugée, vaut un refus masqué, et se journalise une fois par accroche, par clé et par génération, avec sa
trace ; les fournisseurs d'API répondent alors `null` et `DEFAULT`. Un serveur fermé plutôt qu'ouvert par accident, et la console, jamais jugée, garde
`/aegis reload` pour réparer.

## 5. Les textes

Chaque texte que le mod envoie a une clé, `aegisperms.<chemin>`, et deux gabarits, dans `assets\aegisperms\lang\fr_fr.json` et `en_us.json`, lus depuis le
jar par `Texts`. Un joueur reçoit un composant `translatableWithFallback`, avec la clé et, en repli, le gabarit de sa langue ; la console, le RCON et le journal
reçoivent le texte résolu, parce que la Fabric API charge l'`en_us.json` des mods côté serveur et que le serveur rendrait l'anglais à la place du repli. La
langue suit le réglage `language`, celle du client d'un joueur ou celle de la machine en `auto`. Un test de l'étage 0 vérifie que les deux fichiers portent
les mêmes clés aux mêmes arités, et que chaque clé que le code nomme existe.

## 6. Les tests

| Étage | Où | Ce qu'il charge | Ce qu'il prouve |
|---|---|---|---|
| 0 | `core\src\test`, `common\src\test` | rien du jeu : JUnit, Storify sur des dossiers temporaires, Brigadier seul pour `common` | le modèle, la résolution, la validation, les fichiers, les clés des arbres, les textes, les transformations de `/aegis`, l'analyse de `why` |
| 1 | `common\src\testMC` | les registres par `fabric-loader-junit`, le chargeur amorcé côté serveur, sans serveur ni mixin | l'enveloppe, les fournisseurs, le fil des fichiers et la surveillance, les messages, le mode observation face aux classes du jeu |
| 2 | `common\src\gametest` | un serveur de test, le mod et ses mixins, un mod de test et de vrais joueurs simulés | les invariants de bout en bout : les sources, voir et utiliser, l'arbre du client, les clés et le mod propriétaire, le modèle sur serveur, les API, les fichiers et le cycle, `/aegis` |

Les tests de l'étage 0 se jouent une fois ; ceux des étages 1 et 2 s'écrivent une fois, dans `common`, et chaque module de version les rejoue contre son jeu.
Le mod de test, `aegisperms-gametest`, remet l'état en vigueur avant chaque test, et les droits livrés sur le disque quand le test touche les fichiers.
