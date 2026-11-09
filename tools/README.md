# Outils

## `modpack/` — profil Modrinth pour tester
`Croquemitaine-test.mrpack` : Minecraft 26.3 + Fabric Loader 0.20.1 + Simple Voice Chat, avec le serveur
local déjà ajouté au multijoueur. À glisser dans Modrinth App.

## `camera-mod/` + `guide/` — captures automatiques et guide PDF
Chaîne entièrement automatique, sans personne devant le PC. Le mod est documenté en détail dans
[`camera-mod/README.md`](camera-mod/README.md).

1. **Serveur de test** : `./gradlew runServer` (à la racine).
2. **Client caméra** : `cd tools/camera-mod && ./gradlew runClient`. Lance un vrai client Minecraft 26.3
   de développement (pseudo « Joueur », pas de compte requis) avec le mod *Croquemitaine Camera*, qui :
   - rejoint tout seul `localhost` (et se reconnecte après une coupure) ;
   - ne met pas le jeu en pause quand la fenêtre perd le focus ;
   - obéit aux messages invisibles `cmcam:…` envoyés par le serveur (`tellraw`) :
     `shot:<nom>`, `tab:on|off`, `inv:open`, `close`, `cam:first|back|front`, `chat:clear`,
     `hud:on|off`, `select:<item>`, `use`, `respawn`, `spec:<n>`, `toasts`.
3. **Réalisateur** : `cd tools/guide && node director.mjs` met en scène chaque phase via RCON + bots
   (Alice, Bruno, Chloé, David, Emma) et ordonne les captures → `camera-mod/run/screenshots/`.
4. **PDF** : convertir les captures en JPEG dans `guide/shots/`, puis générer le PDF :
   ```bash
   cd tools/guide
   for f in ../camera-mod/run/screenshots/[0-9]*.png; do sips -s format jpeg -s formatOptions 82 --resampleWidth 1600 "$f" --out "shots/$(basename "${f%.png}").jpg"; done
   npx puppeteer browsers install chrome-headless-shell   # une seule fois
   node build-pdf.mjs   # → docs/Guide-Croquemitaine.pdf (rendu de guide.html par Chrome headless)
   ```
   `retake-table.mjs` refait une seule capture (exemple de reprise ponctuelle).

`node sheets.mjs` produit des planches contact pour relire les captures rapidement.
