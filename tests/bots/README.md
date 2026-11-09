# Tests sur serveur local (bots Mineflayer + RCON)

Scénarios du §9 de CLAUDE.md, joués sur le serveur Paper 26.3 local (`run/`).

```bash
cd tests/bots
npm install
node scenarios.mjs            # tous les jalons ; lance et arrête le serveur lui-même
node scenarios.mjs j3 j5      # seulement certains jalons
node rcon.mjs "croque status" # commande RCON ponctuelle
```

- Si un serveur tourne déjà (`./gradlew runServer`), les scénarios l'utilisent sans l'arrêter.
- Le mot de passe RCON est lu dans `run/.rcon-password` (généré localement, jamais commité).
- Prérequis `run/server.properties` : `server-ip=127.0.0.1`, `online-mode=false`, `white-list=false`, RCON activé.
- Les bots parlent le protocole 26.1 (dernier supporté par Mineflayer) ; ViaVersion + ViaBackwards
  (ajoutés par `runServer`) traduisent vers 26.3.
- **Correctif 26.3** : `lib.mjs` envoie un paquet `tick_end` après chaque paquet de mouvement, sinon Paper 26.3
  expulse les bots (`invalid_player_movement`, cf. PrismarineJS/mineflayer#4189).
- Chaque scénario écrit `run/plugins/Croquemitaine/config.yml` (durées raccourcies, chances forcées) puis fait
  `/croque reload`.
