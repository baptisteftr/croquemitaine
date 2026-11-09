# CLAUDE.md — Plugin « Le Croquemitaine » (Paper, Minecraft 26.3)

> Guide de développement complet destiné à l'agent de code (Claude Code) qui va écrire ce plugin.
> À relire en début de chaque session. Les règles de jeu ci-dessous sont **la référence** : elles intègrent le brief d'origine (dossier de présentation de @Anatole) **et** les arbitrages faits ensuite avec le porteur du projet.

---

## 0. Règles de travail de l'agent (À LIRE EN PREMIER)

1. **Tester sur un vrai serveur, en local, sur la machine de l'utilisateur — OBLIGATOIRE.**
   L'agent doit **self-host un serveur Paper 26.3 en local** (voir §8) et y faire tourner le plugin à chaque jalon. Un jalon n'est **pas terminé** tant que :
   - le plugin compile,
   - le serveur local démarre avec le plugin **sans erreur ni stacktrace** dans `logs/latest.log`,
   - les scénarios de test du jalon (§9) ont été joués sur ce serveur (bots + console/RCON) et leur résultat est rapporté.
   Les tests unitaires ne remplacent **jamais** ce test en conditions réelles.
2. **EULA Minecraft.** Avant de passer `eula=true` dans `eula.txt`, l'agent **demande explicitement à l'utilisateur** s'il accepte l'EULA de Minecraft (https://aka.ms/MinecraftEULA). Il ne l'accepte jamais à sa place.
3. **Vérifier les versions, ne pas se fier à la mémoire.** Minecraft est passé à une numérotation par année (26.x). Avant d'utiliser une API Paper, un nom de gamerule ou une coordonnée Maven, vérifier dans la doc officielle (https://docs.papermc.io, Javadoc Paper, Maven) que ça existe bien en 26.3.
4. **Ne jamais empiler sur une base cassée** : build vert + serveur qui démarre proprement avant de passer à la suite.
5. **Tout ce qui est réglable va dans la config**, jamais en dur (durées, rayons, chances, tailles, liste de pouvoirs…).
6. **Commentaires en français**, noms de symboles en anglais. Commenter le *pourquoi*, pas paraphraser le code.
7. **Les joueurs sont réputés réglos** : pas d'anti-cheat, pas de détection de mods clients. Ne pas perdre de temps là-dessus.
8. Hors périmètre → noter dans `docs/Backlog.md`, ne pas implémenter à la volée.

---

## 1. Le jeu — règles de référence (version arbitrée)

### 1.1 But
Être **le dernier joueur en vie**. Chaque joueur démarre avec **5 vies**.

### 1.2 Carte & règles générales
- Carte carrée bornée par la **world border**. Taille **adaptée au nombre de joueurs** au lancement (ex. 300×300 à 5 joueurs → 500×500 à 25), interpolation linéaire, bornes en config.
- Le jeu doit fonctionner **de 5 à 25 joueurs**.
- **Apparition aléatoire** (position sûre) au début et à **chaque réapparition**.
- **KeepInventory** activé.
- **Locator bar désactivée** (sinon la barre d'XP révèle la position des autres joueurs).
- **PvP** : la matrice §1.4 est une **règle d'honneur**, sans blocage technique par défaut (`pvp.enforce: false`, arbitrage du 2026-11-09 : sinon chacun frappe tout le monde « pour tester » qui est le Croque). Le **PvP indirect** (potions, pièges à TNT, trous, mobs d'œufs…) est **encouragé**.
- **Chat vocal** à portée très limitée via Simple Voice Chat (§6.10), intégration **optionnelle**.

### 1.3 Vies, couleurs, catégories
| Vies | Couleur du pseudo | Catégorie |
|---|---|---|
| 5 | Vert foncé (`DARK_GREEN`) | Passif |
| 4 | Vert clair (`GREEN`) | Passif |
| 3 | Jaune (`YELLOW`) | Hostile |
| 2 | Orange (`GOLD`) | Hostile |
| 1 | Rouge (`RED`) | Assassin |
| 0 | — | Éliminé |

- **Toute mort coûte une vie** (monstre, chute, meurtre, suicide…).
- La couleur s'applique au **nametag**, à la **tab list** et au **chat**. La tab list affiche aussi le **nombre de vies restantes**.
- **Élimination** (0 vie), deux modes (`elimination.mode`, arbitrage du 2026-11-09) :
  - `box` (**défaut**) : il est téléporté dans une **boîte en blocs de barrière au-dessus de la carte** et y reste (mode aventure, invulnérable). Retour joueurs : le mode spectateur a permis de tricher lors d'une édition précédente ;
  - `spectator` (option) : mode spectateur, téléportation de joueur en joueur (menu vanilla, touche 1).
  - Comme un spectateur voit tout, le **chat des éliminés n'est lu que par les éliminés** (`elimination.private-chat`), en plus du mute vocal.

### 1.4 Matrice PvP
**Règle d'honneur pour les coups directs** (non bloquée par défaut). Avec `pvp.enforce: true`, le plugin l'applique : un coup d'un joueur A sur un joueur B est alors **annulé** sauf si une ligne ci-dessous l'autorise.

| Attaquant ↓ / Cible → | Passif | Hostile | Assassin | Croquemitaine |
|---|---|---|---|---|
| **Passif** | ❌ | ❌ | ✅ si distance A↔B ≤ 40 | ✅ si distance A↔B ≤ 40 |
| **Hostile** | ✅ | ❌ | ✅ si distance A↔B ≤ 40 | ✅ si distance A↔B ≤ 40 |
| **Assassin** | ✅ | ✅ | ✅ | ✅ |
| **Croquemitaine** | ✅ | ✅ | ❌ | — |

Précisions :
- La règle des **40 blocs** (configurable, `pvp.defense-radius`) remplace la « défense en groupe » du brief (jugée abusable). Elle ne limite en pratique que le tir à distance (arc, arbalète, trident).
- Le Croquemitaine garde sa catégorie de base (passif ou hostile) pour **être attaqué** : un hostile peut toujours frapper un Croque passif. Les droits *d'attaque* du Croque sont ceux de la ligne « Croquemitaine ».
- Conséquence assumée : si ton coup passe sur quelqu'un, c'est peut-être le Croque → mécanique de démasquage.
- Un éliminé ne peut ni frapper ni être frappé.

### 1.5 Attribution des dégâts (« de sa propre main »)
Un dégât est attribué à un joueur s'il en est l'auteur **direct ou indirect identifiable** :
- coup au corps à corps ;
- projectile tiré par lui (flèche, trident, boule de neige, potion jetée, etc.) ;
- TNT **qu'il a lui-même allumée** ;
- **mob issu d'un œuf de spawn qu'il a utilisé** (mob marqué avec l'UUID du propriétaire, voir §6.8) ;
- loup apprivoisé par lui.
Les dégâts indirects attribués **respectent la matrice PvP** de leur auteur (un passif ne peut pas tuer un hostile via un creeper d'œuf). Un œuf posé par un dispenser n'a pas de propriétaire → aucune attribution.

Pour les morts « environnementales » (chute, lave, vide) consécutives à un coup, le **tueur** est le dernier joueur ayant infligé des dégâts attribués dans les **10 dernières secondes** (configurable) — c'est ce que fait déjà le *combat tracker* vanilla (`Player#getKiller()`), à vérifier puis compléter si besoin.

### 1.6 Manches & Croquemitaine
« Tour » et « manche » sont **la même chose**.

- **Manche 1** : pas de Croquemitaine.
- **Manche N ≥ 2** :
  1. **Désignation** : un joueur est tiré au sort parmi les **passifs et hostiles connectés** (assassins exclus). Aucun éligible → pas de Croque cette manche.
  2. **Annonce** : tous les joueurs voient un titre avec effet de suspense (« roulette » de quelques secondes), puis soit « **Tu es le Croquemitaine** », soit « Tu n'es pas le Croquemitaine ». Personne d'autre ne connaît son identité.
  3. **Pouvoir** : le Croque reçoit une **roulette de 2 pouvoirs tirés au hasard** (§1.7) et en choisit un (15 s, sinon choix aléatoire).
  4. **Mission** : il doit tuer **de sa propre main** (§1.5) un passif ou un hostile avant la fin du chrono.
- **Chrono secret** : visible **uniquement** par le Croque (bossbar ou actionbar personnelle).
- **Réussite** : le Croque est libéré et perd ses pouvoirs. **La manche continue** jusqu'au bout du chrono (caché), **sans nouveau Croque** : personne ne sait quand la mission a réussi, ce qui protège l'identité du Croque s'il y a plusieurs kills (arbitrage final du 2026-11-09, l'idée « manche suivante immédiate » est abandonnée).
- **Échec par expiration** : il perd **1 vie**, la manche se termine, la manche suivante démarre avec un nouveau tirage.
- **Mort pendant la mission** : échec, **pas de vie supplémentaire perdue** (il a déjà perdu celle de sa mort). La manche se termine, nouvelle manche immédiate.
- **Déconnexion du Croque** : **tant pis pour lui**, le chrono continue. S'il expire pendant qu'il est déco, il perd sa vie (appliquée côté serveur immédiatement) et la manche suivante démarre.
- **Durée de manche** : `max(min, initial − decrement × (N − 1))`. Défauts : initial 20 min, décrément 2 min, **plancher 6 min**.
- **Entracte** optionnel entre deux manches (défaut 0 s, configurable).

### 1.7 Pouvoirs
- **Héritage cumulatif** (arbitrage du 2026-11-09, remplace « remis à zéro ») : chaque nouveau Croque reçoit **tous les pouvoirs des Croques précédents de la partie** + **1 nouveau** pouvoir (jamais un déjà acquis). Le Croque devient de plus en plus fort tandis que les manches raccourcissent. Quand tous les pouvoirs sont acquis, plus de nouveau pouvoir. Configurable : `powers.inherit`.
- **Deux modes** (`powers.mode`) :
  - `choice` : roulette de 2 pouvoirs **distincts** tirés au hasard (pondération en config, parmi ceux pas encore acquis), le Croque en choisit 1 ;
  - `random` : le nouveau pouvoir est tiré au hasard, sans choix.
- Les états internes (recharges, usages uniques, Second souffle) repartent à zéro pour chaque Croque.
- Actif pendant toute la mission, retiré dès la fin (réussite, échec, mort).

| ID | Nom | Effet | Type | Indication d'implémentation |
|---|---|---|---|---|
| `tracker` | Traqueur | Boussole pointant vers la cible autorisée la plus proche, maj toutes les 5 s | Passif | `Player#setCompassTarget` ou lodestone compass dans l'inventaire |
| `shadow` | Ombre | Invisibilité 20 s, recharge 2 min | Actif | Effet + `Player#sendEquipmentChange` pour masquer l'armure aux autres |
| `predator` | Prédateur | Vitesse II | Passif | `PotionEffect` infini, retiré en fin de mission |
| `brute` | Brute | Force I | Passif | idem |
| `shell` | Carapace | Résistance I | Passif | idem |
| `reprieve` | Sursis | +3 min sur son chrono (uniquement quand obtenu comme nouveau pouvoir, pas en héritage) | Instantané | modifie le chrono de manche |
| `disguise` | Déguisement — **désactivé** (poids 0 : avec l'héritage, le Croque suivant reconnaîtrait le précédent) | Pseudo affiché vert foncé + « 5 vies » pour tous | Passif | équipe de scoreboard factice, restaurée ensuite |
| `second_wind` | Second souffle | La 1ʳᵉ mort ne fait pas échouer la mission | Passif | totem d'immortalité / annulation de la mort une fois |
| `blind` | Aveuglement | Cécité 4 s aux joueurs ≤ 8 blocs, recharge 90 s | Actif | `PotionEffectType.BLINDNESS` |
| `shadow_step` | Saut de l'ombre | 1 fois : TP derrière la cible autorisée la plus proche (≤ 30 blocs) | Actif | calcul position sûre derrière la cible |
| `bite` | Morsure | Ses coups infligent Lenteur II 3 s | Passif | sur dégât attribué au Croque |
| `reveal` | Révélation | 1 fois : particules au-dessus de la cible la plus proche pendant 10 s, visibles par lui seul | Actif | `Player#spawnParticle` (par joueur, pas besoin de paquets) |

Les pouvoirs **actifs** s'utilisent via un **item dédié** non jetable (clic droit), retiré à la fin de la mission. Ajouter un pouvoir = une classe qui implémente `Power` + une entrée en config.

### 1.8 Mort & réapparition
- Réapparition à une **position aléatoire sûre**.
- **Pas de boussole de récupération** par défaut (`rules.recovery-compass: false`) : avec le keepInventory, la conséquence d'une mort doit être de **se perdre** sur la carte. Option : boussole vanilla vers le dernier lieu de mort.
- Commande **`/death-history`** : liste de ses morts (date, coordonnées, cause, tueur éventuel).

### 1.9 Fin de partie
- **Victoire** : un seul joueur non éliminé → annonce, feu d'artifice, partie stoppée.
- **Ne restent que des assassins** (≥ 2) : plus de Croque possible ; la **world border rétrécit progressivement** (taille cible, vitesse, délai en config) pour forcer l'affrontement.

### 1.10 Règles spéciales
| Règle | Détail |
|---|---|
| Œufs de mobs | Tuer un mob hostile a une chance (config, défaut **25 %**, boosté pour encourager le PvP indirect) de faire tomber l'œuf d'un **autre** mob, passif ou hostile, tiré au hasard. **Boss exclus** (Ender Dragon, Wither, Warden, Elder Guardian — liste en config). |
| TNT simplifiée | Recette : 1 poudre à canon + 4 sable (shapeless). La recette vanilla reste valable. |
| Casque interdit | Par défaut (`rules.helmet-mode: poison`, arbitrage du 2026-11-09) : on **peut** porter quelque chose sur la tête, mais on subit **Poison II tant qu'on le porte**. Option `block` : impossible d'équiper quoi que ce soit dans l'emplacement tête. |
| Phantoms | **Désactivés** (`rules.phantoms: false`) : gamerule `spawn_phantoms` + tout autre spawn (œuf, commande) bloqué, œuf de phantom retiré du tirage. |
| Table d'enchantement | **Non craftable** (recette retirée). Une **unique table** est posée au centre de la carte, **incassable** et insensible aux explosions. |

---

## 2. Stack & versions

| Élément | Valeur | Remarque |
|---|---|---|
| Serveur | **Paper 26.3** | https://papermc.io/downloads |
| Java | **25** (minimum requis par MC 26.x) | `java -version` doit afficher 25+ |
| Build | **Gradle (Kotlin DSL)** + wrapper | |
| API Paper | `io.papermc.paper:paper-api:26.3.build.+` (`compileOnly`) | Nouveau format de version depuis 26.x (l'ancien `-R0.1-SNAPSHOT` ne vaut que pour ≤ 1.21.11). Épingler un build précis une fois stable. |
| Repo Maven Paper | `https://repo.papermc.io/repository/maven-public/` | |
| Serveur de test | Plugin Gradle **`xyz.jpenilla.run-paper`** | Télécharge et lance Paper automatiquement. Vérifier la dernière version sur le Gradle Plugin Portal. |
| Voice chat (optionnel) | `de.maxhenkel.voicechat:voicechat-api:2.6.27` (`compileOnly`) | Repo `https://maven.maxhenkel.de/repository/public`. Version 2.6.27 publiée pour 26.3 (plugin `bukkit-2.6.27` + mods clients `*-2.6.27+26.3`), vérifié le 2026-11-09. |
| Tests unitaires | JUnit 5 | Sur la logique pure (§3). |
| Texte | Adventure + **MiniMessage** | Natif dans Paper. |
| Manifest | `plugin.yml` | `paper-plugin.yml` est encore en dev côté Paper → rester sur `plugin.yml` sauf besoin précis. `softdepend: [voicechat]`. |

### Squelette `build.gradle.kts`
```kotlin
plugins {
    java
    id("xyz.jpenilla.run-paper") version "<dernière version>"
}

group = "fr.croquemitaine"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://maven.maxhenkel.de/repository/public") { name = "henkelmax" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.27")
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks {
    test { useJUnitPlatform() }
    runServer {
        minecraftVersion("26.3")
        // Plugins tiers pour les tests locaux (voir §8) : Simple Voice Chat, ViaVersion/ViaBackwards si besoin
        // downloadPlugins { modrinth("simple-voice-chat", "bukkit-2.6.27") }  // compatible 26.3 (vérifié le 2026-11-09)
    }
}
```

---

## 3. Architecture

Principe : **la logique de jeu ne dépend pas de Bukkit**. Elle vit dans un cœur Java pur, testable en JUnit. Les listeners Bukkit ne font que traduire les événements en appels au cœur, puis appliquer le résultat au monde.

```
src/main/java/fr/croquemitaine/
  CroquemitainePlugin.java      # onEnable/onDisable, câblage des services
  config/                       # chargement + validation de config.yml / messages.yml
  core/                         # LOGIQUE PURE (pas d'import org.bukkit)
    GameState.java              # état global : phase, manche, joueurs, croque
    PlayerRecord.java           # uuid, vies, catégorie, historique de morts
    Category.java               # PASSIVE, HOSTILE, ASSASSIN, ELIMINATED (dérivé des vies)
    PvpRules.java               # matrice §1.4 → canAttack(attacker, victim, distance)
    RoundEngine.java            # durées, tirage, transitions de manche
    WinCondition.java           # victoire / mode « assassins seuls »
  game/                         # orchestration avec Bukkit
    GameManager.java            # boucle (tick 1/s), persistance, transitions
    RoundTimer.java
    CroqueAnnouncer.java        # titre + effet roulette
    MapSizer.java               # taille de border selon nb de joueurs
    BorderShrinker.java
  players/
    LivesService.java
    NameColorService.java       # équipes scoreboard + tab list
    EliminationBox.java
    RespawnService.java         # spawn aléatoire sûr + recovery compass
  combat/
    DamageAttribution.java      # DamageSource → joueur responsable
    PvpListener.java            # applique PvpRules sur EntityDamageByEntityEvent etc.
    KillTracker.java            # dernier attaquant < 10 s
  powers/
    Power.java                  # interface : id, onGrant, onRevoke, onUse
    PowerRegistry.java
    PowerRoulette.java          # GUI de choix
    impl/…                      # une classe par pouvoir
  rules/
    MobEggDropListener.java
    TntRecipe.java
    HelmetBanListener.java
    EnchantingTableRules.java
  voice/
    VoiceChatBridge.java        # chargé seulement si le plugin voicechat est présent
  commands/
    CroqueCommand.java          # admin, via l'API Brigadier de Paper
    DeathHistoryCommand.java
  storage/
    GameStorage.java            # sauvegarde JSON/YAML de l'état
src/main/resources/
  plugin.yml
  config.yml
  messages.yml
src/test/java/…                 # JUnit sur core/
tests/bots/                     # scénarios Mineflayer (voir §8)
docs/Backlog.md
```

Règles :
- `core/` : **aucun** import `org.bukkit` / `io.papermc`. Entrées/sorties en types simples (UUID, int, double).
- Les services `game/`, `players/`… sont injectés par le constructeur depuis `CroquemitainePlugin`, pas de singletons statiques.
- Un seul **ticker** principal (scheduler Bukkit, 1 fois par seconde) pilote le chrono ; pas de multiples tâches concurrentes qui se marchent dessus.
- **Persistance** : l'état (`GameState`) est sauvegardé dans `plugins/Croquemitaine/game.json` à chaque transition importante et toutes les 30 s, et rechargé au démarrage → une partie survit à un redémarrage serveur (chrono restant inclus).

---

## 4. Configuration (`config.yml` de référence)

```yaml
lives:
  start: 5

map:
  size-min: 300          # taille à players-min joueurs
  size-max: 500          # taille à players-max joueurs
  players-min: 5
  players-max: 25
  center: { x: 0, z: 0 }

pvp:
  defense-radius: 40     # passif/hostile → croque/assassin
  kill-credit-seconds: 10

rounds:
  initial-minutes: 20
  decrement-minutes: 2
  min-minutes: 6
  intermission-seconds: 0
  announce-suspense-seconds: 5
  power-choice-seconds: 15

powers:                  # poids de tirage, 0 = désactivé
  mode: choice           # choice (roulette de 2) | random
  inherit: true          # le Croque hérite des pouvoirs des Croques précédents
  tracker: 10
  shadow: 10
  predator: 10
  brute: 10
  shell: 10
  reprieve: 10
  disguise: 10
  second_wind: 5
  blind: 10
  shadow_step: 5
  bite: 10
  reveal: 10
  settings:
    reprieve-minutes: 3
    shadow-duration-seconds: 20
    shadow-cooldown-seconds: 120
    blind-radius: 8
    blind-cooldown-seconds: 90
    shadow-step-range: 30

elimination-box:
  y: 300                 # vérifier la hauteur max du monde en 26.3

endgame:
  shrink-delay-seconds: 60
  shrink-target-size: 30
  shrink-duration-seconds: 600

rules:
  mob-egg-chance: 0.05
  mob-egg-excluded: [ENDER_DRAGON, WITHER, WARDEN, ELDER_GUARDIAN]
  tnt-simplified: true
  helmet-ban: true
  enchanting-table-at-center: true

voice:
  mute-eliminated: true  # ou groupe « Éliminés » isolé
```

Messages joueurs dans `messages.yml` (MiniMessage, en français), **aucune string en dur** dans le code.

---

## 5. Gamerules & réglages monde au lancement d'une partie

Appliqués par `/croque start`, via les **constantes de l'API** (`GameRules.*` / `GameRule.*` selon l'API 26.3) — les noms texte des gamerules ont changé en snake_case récemment, **ne pas les écrire en dur**, vérifier dans la Javadoc :
- keep inventory → `true`
- locator bar → `false`
- immediate respawn → au choix (défaut `false`)
- world border → centre + taille calculée par `MapSizer`
- spawn radius → 0 (on gère nous-mêmes le spawn aléatoire)

---

## 6. Spécifications détaillées par module

### 6.1 Vies & couleurs (`LivesService`, `NameColorService`)
- Source de vérité : `PlayerRecord.lives` dans `GameState`. La catégorie est **dérivée** des vies (jamais stockée séparément).
- Une équipe de scoreboard par palier (`cm_5` … `cm_1`) avec la couleur correspondante. Changement de palier = changement d'équipe.
- Tab list : objectif `DisplaySlot.PLAYER_LIST` affichant les vies (format entier).
- Événement interne `LivesChanged(uuid, old, new)` pour notifier les autres services (couleur, voice, win condition).

### 6.2 Mort (`PlayerDeathEvent`)
Ordre de traitement :
1. Récupérer le tueur attribué (§1.5) via `KillTracker`.
2. Enregistrer l'entrée dans l'historique (`/death-history`).
3. Si la victime est le Croque → échec de mission (sans vie supplémentaire) sauf pouvoir `second_wind` non consommé.
4. Si le tueur est le Croque et la victime passif/hostile → **réussite**.
5. Retirer 1 vie à la victime → si 0, élimination.
6. Vérifier la condition de victoire.

### 6.3 Élimination (`EliminationBox`) — mode `spectator` par défaut, `box` en option
- Boîte de barrières construite une fois (au centre, à `elimination-box.y`), intérieur assez grand pour 25 joueurs.
- Joueur éliminé : TP dans la boîte, `GameMode.ADVENTURE`, invulnérable, retiré des tirages.
- À la reconnexion d'un éliminé : renvoyé dans la boîte.

### 6.4 Réapparition (`RespawnService`)
- `PlayerRespawnEvent#setRespawnLocation` → position aléatoire dans la border, sur un bloc solide, ni liquide, ni feuillage, avec 2 blocs d'air au-dessus ; N essais max puis repli sur le centre.
- Donner une recovery compass si absente de l'inventaire.

### 6.5 PvP (`PvpListener`, `DamageAttribution`)
- Écouter `EntityDamageByEntityEvent` (priorité HIGH) quand la victime est un joueur.
- Utiliser l'API `DamageSource` (`getCausingEntity`, `getDirectEntity`) pour remonter à l'auteur ; compléter avec : `Projectile#getShooter`, `TNTPrimed#getSource`, tag PDC du mob issu d'œuf, propriétaire du loup.
- Consulter `PvpRules.canAttack(attackerCategory, victimCategory, isCroque…, distance)` ; si refusé → `setCancelled(true)`.
- Gérer aussi les **potions jetées** (`PotionSplashEvent`, `LingeringPotionSplashEvent`/`AreaEffectCloudApplyEvent`) : retirer les joueurs non attaquables des cibles affectées par des effets négatifs.
- Enregistrer chaque dégât autorisé dans `KillTracker` (attaquant, victime, timestamp).

### 6.6 Manches (`GameManager`, `RoundEngine`, `RoundTimer`, `CroqueAnnouncer`)
Machine à états :
```
LOBBY ──/croque start──▶ RUNNING(round=1, pas de croque)
RUNNING ──fin du chrono──▶ RUNNING(round+1, tirage)
         ├─ croque tue        → réussite, croque = null, le chrono continue (caché)
         ├─ croque meurt      → échec, round+1 immédiat
         └─ chrono expire     → si croque actif : -1 vie ; round+1
RUNNING ──1 survivant──▶ ENDED
RUNNING ──que des assassins──▶ RUNNING + BorderShrinker actif
PAUSED  ⇄ RUNNING via /croque pause|resume (chrono gelé)
```
- `CroqueAnnouncer` : séquence de titres « … » qui défilent (effet roulette, son de tick), puis le titre final personnalisé par joueur. **Même durée et même rythme pour tout le monde**, pour ne pas trahir le Croque.
- Chrono du Croque : `BossBar` visible **seulement** par lui (`player.showBossBar`). À la reconnexion du Croque, lui réafficher.

### 6.7 Pouvoirs (`powers/`)
```java
public interface Power {
    String id();
    void onGrant(Player croque, PowerContext ctx);   // appliquer effets / donner l'item
    void onRevoke(Player croque, PowerContext ctx);  // TOUT nettoyer (effets, items, équipe factice…)
    default void onUse(Player croque, PowerContext ctx) {}  // pouvoirs actifs
}
```
- `PowerRoulette` : inventaire de 9 cases avec 2 items cliquables (nom + description depuis `messages.yml`). Fermeture ou timeout → choix aléatoire.
- L'item de pouvoir actif est marqué en PDC, non jetable (`PlayerDropItemEvent`), non déplaçable dans un coffre, retiré à `onRevoke`.
- `onRevoke` doit être **idempotent** et appelé dans tous les cas : réussite, échec, mort, déconnexion + expiration, `/croque stop`, arrêt du serveur.

### 6.8 Œufs de mobs (`MobEggDropListener`)
- `EntityDeathEvent` : si le mob est hostile (`Enemy`), tué par un joueur, et non exclu → tirage `mob-egg-chance` → ajouter un œuf d'un **autre** type (pool : tous les types ayant un spawn egg, moins les boss exclus et le type du mob tué).
- Attribution : à l'utilisation d'un œuf par un joueur (`CreatureSpawnEvent` avec raison `SPAWNER_EGG` + interaction du joueur), écrire l'UUID du joueur dans le **PersistentDataContainer** du mob. Les dégâts de ce mob sur un joueur sont attribués au propriétaire **et** filtrés par la matrice PvP.

### 6.9 Règles spéciales restantes
- **TNT** : `ShapelessRecipe` (1 `GUNPOWDER` + 4 `SAND`), clé `croquemitaine:simple_tnt`.
- **Casque** : annuler toute pose dans l'emplacement tête (clic d'inventaire, shift-clic, clic droit avec l'item, dispenser, drag) **+ un balayage périodique** (1 fois/s) qui rend au joueur tout item présent dans l'emplacement tête. Vérifier dans la Javadoc 26.3 les événements d'équipement disponibles.
- **Table d'enchantement** : `Bukkit.removeRecipe` de la recette vanilla ; poser la table au centre au `/croque start` ; annuler `BlockBreakEvent`, `BlockExplodeEvent`/`EntityExplodeEvent` (la retirer de la liste), pistons. Les bibliothèques autour ne sont pas protégées (pas demandé).

### 6.10 Voice chat (`VoiceChatBridge`) — optionnel
- `softdepend: [voicechat]` dans `plugin.yml`. Charger le bridge **seulement si** le plugin `voicechat` est présent ; sinon le plugin fonctionne sans.
- Implémenter `VoicechatPlugin` (API Simple Voice Chat) et l'enregistrer via le service `BukkitVoicechatService`.
- Éliminés : les rendre muets pour les vivants (ou les mettre dans un groupe isolé « Éliminés »), selon `voice.mute-eliminated`.
- Côté config Simple Voice Chat (documenté pour l'admin, pas géré par le plugin) : `max_voice_distance` ≈ 15-20, **`enable_groups=false`**, port UDP 24454 (ou `-1` pour réutiliser le port du serveur).

### 6.11 Commandes
Enregistrées avec l'**API Brigadier de Paper** (événement de cycle de vie `COMMANDS`), pas via `plugin.yml`.

| Commande | Permission | Effet |
|---|---|---|
| `/croque start` | `croque.admin` | Calcule la taille de carte, applique gamerules, pose la table, spawn aléatoire de tous, lance la manche 1 |
| `/croque pause` / `resume` | `croque.admin` | Gèle / reprend le chrono |
| `/croque stop` | `croque.admin` | Termine la partie, nettoie pouvoirs/bossbars/équipes |
| `/croque force <joueur>` | `croque.admin` | Force le Croque de la manche en cours |
| `/croque skip` | `croque.admin` | Termine la manche sans pénalité |
| `/croque lives <joueur> set\|add\|remove <n>` | `croque.admin` | Gestion des vies |
| `/croque revive <joueur>` | `croque.admin` | Sort de la boîte avec 1 vie |
| `/croque border <taille> [secondes]` | `croque.admin` | Ajuste la border |
| `/croque status` | `croque.admin` | Croque actuel, chrono, vies de chacun (**admin seulement**) |
| `/croque reload` | `croque.admin` | Recharge config + messages |
| `/death-history [joueur]` | tous (`croque.admin` pour un autre joueur) | Historique des morts |

---

## 7. Ordre de développement (jalons)

Chaque jalon se termine par : build vert → tests unitaires verts → **serveur local démarré avec le plugin** → scénarios du jalon joués (§9) → compte rendu à l'utilisateur.

| # | Jalon | Contenu |
|---|---|---|
| J0 | Squelette | Projet Gradle, `plugin.yml`, config chargée, `runServer` qui démarre Paper 26.3 avec le plugin |
| J1 | Vies & élimination | `GameState`, vies, couleurs, tab list, mort = -1 vie, boîte d'élimination, persistance |
| J2 | Spawn & mort | Spawn aléatoire sûr, recovery compass, `/death-history`, gamerules, map sizer |
| J3 | PvP | Matrice, attribution des dégâts, kill tracker, potions |
| J4 | Manches & Croque | Machine à états, tirage, annonce, chrono secret, réussite/échec/déco |
| J5 | Pouvoirs | Interface, roulette, les 12 pouvoirs |
| J6 | Règles spéciales | Œufs (+ attribution), TNT, casque, table d'enchantement |
| J7 | Fin de partie | Victoire, rétrécissement de la border « assassins seuls » |
| J8 | Commandes admin | Toutes les commandes §6.11 |
| J9 | Voice chat | Bridge optionnel + doc de config SVC |
| J10 | Session de jeu réelle | Test de bout en bout avec l'utilisateur sur son client (§8.5) |

---

## 8. Test sur un vrai serveur local (OBLIGATOIRE)

L'agent **héberge lui-même un serveur Paper 26.3 sur la machine de l'utilisateur (macOS)** et y teste le plugin. Pas de serveur distant, pas d'hébergeur.

### 8.1 Prérequis
- JDK **25** installé (ex. Temurin 25). Vérifier `java -version`. S'il manque, **demander à l'utilisateur** avant de l'installer.
- Port `25565` libre en local (et `24454/udp` si Simple Voice Chat).

### 8.2 Lancer le serveur
Méthode recommandée : **run-paper**.
```bash
./gradlew runServer
```
- Le serveur tourne dans `run/`. Il télécharge Paper 26.3 et utilise le jar du plugin fraîchement buildé.
- Lancer la commande **en arrière-plan** (c'est un process long) et suivre `run/logs/latest.log`.
- Au premier lancement : `run/eula.txt` est créé → **demander à l'utilisateur s'il accepte l'EULA Minecraft** avant d'écrire `eula=true`, puis relancer.

Méthode alternative (si run-paper pose problème) : télécharger le jar Paper 26.3 depuis l'API officielle de PaperMC (https://papermc.io/downloads — vérifier l'URL de l'API de téléchargement dans la doc), le placer dans `run/`, copier `build/libs/*.jar` dans `run/plugins/`, puis `java -Xms2G -Xmx2G -jar paper.jar --nogui`.

### 8.3 `run/server.properties` pour les tests
```properties
server-ip=127.0.0.1        # écoute uniquement en local, jamais exposé
server-port=25565
online-mode=false          # LOCAL UNIQUEMENT : permet aux bots de se connecter sans compte
enable-rcon=true
rcon.port=25575
rcon.password=<mot de passe aléatoire généré par l'agent, stocké dans run/.rcon-password, jamais affiché dans le chat>
level-seed=croquemitaine-test   # seed fixe pour des tests reproductibles
spawn-protection=0
max-players=30
```
⚠️ `online-mode=false` et RCON ne sont acceptables **que** parce que le serveur écoute sur `127.0.0.1`. Ne jamais réutiliser ce fichier pour une vraie partie.

### 8.4 Piloter et observer le serveur
- **Console / RCON** : envoyer des commandes (`/croque start`, `/croque lives …`, `/kill <bot>`, `/tp`, `/effect`…) via un petit client RCON (ex. `mcrcon`, ou un script Node/Python) et vérifier les réponses.
- **Logs** : `run/logs/latest.log` doit être **exempt de `ERROR`, `Exception`, stacktrace** liés au plugin. À vérifier à chaque démarrage et après chaque scénario.
- **Bots joueurs** : utiliser **Mineflayer** (Node.js) dans `tests/bots/` pour simuler plusieurs joueurs (connexion, déplacement, attaque, tir à l'arc, mort, reconnexion, lecture des titres/bossbar/scoreboard reçus).
  - Si Mineflayer ne supporte pas encore le protocole 26.3 : installer **ViaVersion + ViaBackwards** sur le serveur de test (`run/plugins/`) pour que les bots se connectent avec la version la plus récente qu'ils supportent. À réserver au serveur de test.
  - Si même ça échoue : se rabattre sur console/RCON pour tout ce qui ne demande pas de vrai client, et lister précisément les scénarios restants pour la session manuelle (§8.5).
- Les scripts de bots sont **commités** dans `tests/bots/` avec un `README` pour les rejouer.

### 8.5 Session manuelle avec l'utilisateur
À J10 (et dès qu'un scénario ne peut pas être automatisé) :
- l'agent laisse le serveur tourner et demande à l'utilisateur de se connecter avec son client Minecraft 26.3 sur **`localhost`** ;
- il fournit une checklist courte de choses à vérifier visuellement (couleurs de pseudo, titres d'annonce, bossbar, roulette de pouvoirs, voice chat) ;
- il lit les logs pendant la session et corrige.

### 8.6 Arrêt propre
- Arrêter le serveur avec la commande `stop` (console/RCON), pas en tuant le process, pour que la persistance `game.json` soit écrite.
- Vérifier qu'aucun process Java du serveur ne reste en tâche de fond en fin de session.

---

## 9. Scénarios de test par jalon (minimum)

| Jalon | Scénarios |
|---|---|
| J0 | Le serveur démarre, le plugin est listé dans `/plugins` en vert, aucune erreur au chargement ni à l'arrêt |
| J1 | Bot meurt 5 fois → couleurs successives correctes → éliminé dans la boîte ; reconnexion d'un éliminé → retour boîte ; redémarrage serveur → vies conservées |
| J2 | 20 respawns → toujours sur un bloc sûr dans la border ; recovery compass présente une seule fois ; `/death-history` correct |
| J3 | Matrice §1.4 testée case par case avec 2 bots (mêlée **et** arc) ; tir à l'arc à 39 et 41 blocs sur un assassin ; potion jetée sur un passif par un passif sans effet ; mort par chute après coup → bon tueur |
| J4 | Manche 1 sans Croque ; tirage n'inclut jamais un assassin ni un déconnecté ; chrono visible par le Croque seul ; réussite / échec / mort / déco du Croque → bonnes conséquences ; durées décroissantes avec plancher à 6 min (raccourcir les durées en config pour tester) |
| J5 | Chaque pouvoir accordé puis révoqué proprement (aucun effet/item résiduel) dans les 4 cas de fin de mission |
| J6 | Taux de drop d'œufs plausible sur un grand nombre de kills (chance forcée à 100 % en test) ; creeper d'œuf d'un passif ne blesse pas un hostile ; recette TNT ; aucun moyen de mettre un casque ; table non craftable, incassable, survit à une TNT |
| J7 | 3 bots → 1 survivant → victoire ; 2 assassins restants → border qui rétrécit |
| J8 | Chaque commande admin, refusée pour un non-admin |
| J9 | Sans plugin voicechat → aucun crash ; avec → éliminé muet |

---

## 10. Definition of Done (V1)

- [ ] Toutes les règles de §1 implémentées et configurables.
- [ ] Tests unitaires verts sur `core/`.
- [ ] Tous les scénarios §9 joués sur le **serveur local Paper 26.3**, logs propres.
- [ ] Session manuelle (§8.5) faite avec l'utilisateur.
- [ ] Une partie survit à un redémarrage serveur.
- [ ] `README.md` : installation sur un vrai serveur (Paper 26.3 + Java 25), config Simple Voice Chat recommandée, liste des commandes, pack client conseillé (Fabric + Simple Voice Chat).

---

## 11. Points ouverts / valeurs à équilibrer
- Valeurs par défaut des durées, du taux d'œufs, des tailles de carte : à ajuster après la première vraie partie.
- Équilibrage des pouvoirs (poids, durées, recharges).
- Comportement exact des éliminés en vocal (muets vs groupe isolé).
