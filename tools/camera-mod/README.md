# Croquemitaine Camera — mod client de captures automatiques

Petit mod **Fabric** (client, Minecraft 26.3) qui transforme un vrai client Minecraft en **caméra pilotée par le serveur**. Il a servi à produire toutes les captures du [guide](../../docs/Guide-Croquemitaine.pdf) et du README, sans personne devant l'écran.

> ⚠️ C'est un **outil de tournage pour le serveur de test local**, pas un mod pour les joueurs. Il n'apporte rien en jeu, et il obéit à n'importe quel serveur qui lui envoie des ordres `cmcam:`. Ne l'installe pas sur ton client habituel.

## Pourquoi

Les bots de test (Mineflayer) ne voient rien : pas de rendu, pas d'interface. Pour montrer les titres, la roulette de pouvoirs, le chrono secret, les couleurs de pseudos ou la tab list, il faut un **vrai client**. Ce mod permet au serveur de dire au client quoi faire et quand prendre la photo.

## Ce qu'il fait

- **Se connecte tout seul** au serveur configuré (`localhost` par défaut) au démarrage, et **se reconnecte** 10 s après une déconnexion (par exemple un redémarrage du serveur).
- Passe l'écran d'accessibilité du premier lancement et **ne met pas le jeu en pause** quand la fenêtre perd le focus.
- **Obéit à des messages invisibles** envoyés par le serveur (`/tellraw <joueur> {"text":"cmcam:<ordre>"}`). Ces messages sont masqués : ils n'apparaissent jamais dans le chat ni sur les captures.

| Ordre | Effet |
|---|---|
| `shot:<nom>` | Capture d'écran (avec l'interface) → `run/screenshots/<nom>.png`. Les notifications sont effacées avant. |
| `tab:on` / `tab:off` | Affiche / masque la liste des joueurs (comme <kbd>Tab</kbd> maintenu) |
| `inv:open` / `close` | Ouvre l'inventaire / ferme l'écran ouvert |
| `cam:first` / `cam:back` / `cam:front` | Vue à la 1ʳᵉ personne / de dos / de face |
| `chat:clear` | Vide le chat |
| `hud:on` / `hud:off` | Affiche / masque l'interface |
| `select:<item>` | Sélectionne dans la barre rapide le premier item de ce type (ex. `select:spyglass`) |
| `use` | Clic droit avec l'item en main (pour utiliser un pouvoir) |
| `slot:<0-8>` | Sélectionne un emplacement de la barre rapide |
| `respawn` | Réapparaît après une mort |
| `spec:<n>` | Menu spectateur : `spec:0` l'ouvre, `spec:8` le ferme |
| `toasts` | Efface les notifications |
| `cmd:<commande>` | Envoie une commande (indisponible avec le client de développement, qui n'a pas de profil signé : utiliser plutôt `execute as <joueur> run …` côté serveur) |

## Utilisation

Prérequis : Java 25, et le serveur de test lancé (`./gradlew runServer` à la racine du dépôt).

```bash
cd tools/camera-mod
./gradlew runClient
```

- Lance un **client Minecraft 26.3 de développement** avec le mod, sous le pseudo **« Joueur »**, sans compte Microsoft (le serveur de test est en `online-mode=false`).
- Fenêtre 1600 × 900, interface à l'échelle 4, son coupé, en français (`run/options.txt`).
- Le serveur cible se change dans `run/config/croquemitaine-camera.properties` :
  ```properties
  autojoin=true
  server=localhost
  ```

Ensuite, le **réalisateur** ([`../guide/director.mjs`](../guide/director.mjs)) enchaîne les scènes : il passe par RCON, utilise les commandes `/croque` et 5 bots (Alice, Bruno, Chloé, David, Emma), et ordonne les captures au bon moment. Une séance complète prend environ 4 minutes :

```bash
cd tools/guide && npm install && node director.mjs
```

Voir [`../README.md`](../README.md) pour la suite : conversion des captures et génération du PDF.

## Technique

- Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19 ; Minecraft 26.x n'étant plus obfusqué, le code utilise directement les noms Mojang.
- Code : [`src/main/java/fr/croquemitaine/camera/CameraMod.java`](src/main/java/fr/croquemitaine/camera/CameraMod.java), un seul fichier. Il utilise `ClientReceiveMessageEvents` pour intercepter les ordres, `ClientTickEvents` pour les exécuter et `ScreenEvents` pour la connexion automatique.
- `./gradlew build` produit `build/libs/croquemitaine-camera-<version>.jar`, installable dans un profil Fabric 26.3 classique si besoin.
