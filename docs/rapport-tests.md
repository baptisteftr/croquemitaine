# Rapport des scénarios bots — 2026-11-09 23:26 — Paper 26.3 local

Rejouer : `cd tests/bots && npm install && node scenarios.mjs`

    
    === J0 — démarrage ===
      ✅ Plugin Croquemitaine listé — ℹ Server Plugins (3): Bukkit Plugins:  - Croquemitaine, ViaBackwards, ViaVersion 
      ✅ Aucune erreur au chargement
    
    === J1 — vies, couleurs, élimination ===
      ✅ Partie démarrée en manche 1
      ✅ 5 vies au départ, équipe cm_5
      ✅ Après mort → 4 vies, équipe cm_4
      ✅ Après mort → 3 vies, équipe cm_3
      ✅ Après mort → 2 vies, équipe cm_2
      ✅ Après mort → 1 vies, équipe cm_1
      ✅ Après mort → 0 vies, équipe cm_0
      ✅ Option spectator : éliminé → spectateur — spectator
      ✅ Chat d'un éliminé invisible pour les vivants
      ✅ Reconnexion d'un éliminé → toujours spectateur — spectator
      ✅ Ressuscité → retour en survie — survival
      ✅ Défaut (boîte) : éliminé téléporté dans la boîte — y=301.0
      ✅ Mode boîte : éliminé en mode aventure — adventure
      ✅ Mode boîte : reconnexion → retour boîte — y=301.0
      ✅ Tab list : vies affichées (objectif cm_lives) — CmB has 5 [Vies]
    
    === J2 — spawn, mort, gamerules ===
      ✅ keep_inventory = true — Game rule keep_inventory is currently set to true
      ✅ locator_bar = false — Game rule locator_bar is currently set to false
      ✅ Bordure 300 (3 joueurs → taille min) — The world border is currently 300 block(s) wide
      ✅ 20 respawns dans la bordure — 0 hors bordure
      ✅ 20 respawns sur bloc sûr — 0 douteux
      ✅ Pas de boussole de récupération par défaut — 0
      ✅ Option recovery-compass : une seule boussole après 2 morts — 1
      ✅ /death-history liste les 22 morts — 22 lignes ; ex: #1 09/11 23:20:31 — -123 72 357 — generic_kill
    
    === J3 — PvP : aucune restriction par défaut, matrice si pvp.enforce ===
      ✅ Défaut : passif → passif en mêlée passe (règle d'honneur)
      ✅ Défaut : potion de poison passif → passif appliquée (PvP indirect libre)
      ✅ Mêlée P→P refusée
      ✅ Mêlée P→H refusée
      ✅ Mêlée P→A autorisée
      ✅ Mêlée H→P autorisée
      ✅ Mêlée H→H refusée
      ✅ Mêlée H→A autorisée
      ✅ Mêlée A→P autorisée
      ✅ Mêlée A→H autorisée
      ✅ Mêlée A→A autorisée
      ✅ Coup réel du bot (5 vies → passif) bloqué
      ✅ Coup réel du bot (3 vies → passif) passe
      ✅ Flèche passif→assassin à 39 blocs touche
      ✅ Flèche passif→assassin à 41 blocs bloquée
      ✅ Potion de poison passif→passif sans effet
      ✅ Potion de poison hostile→passif appliquée (contrôle)
      ✅ Chute après un coup : tueur attribué
      ✅ Croque → assassin refusé
      ✅ Croque → passif autorisé
      ✅ Passif → Croque passif à 3 blocs autorisé
    
    === J4 — manches et Croquemitaine ===
      ✅ Manche 1 sans Croque
      ✅ Manche 2 : un Croque désigné — CmB
      ✅ Seul le Croque voit « Tu es le Croquemitaine » — CmB
      ✅ Les autres voient « Tu n'es pas… » — CmC,CmA,CmD
      ✅ Chrono (bossbar) visible par le Croque seul — CmB
      ✅ Durées décroissantes avec plancher — m3=360s m4=360s m5=360s m6=360s m7=360s
      ✅ Tirage : jamais l'assassin ni le déconnecté — CmB,CmC
      ✅ Réussite : la manche continue (pas de nouveau tirage)
      ✅ Réussite : plus de Croque jusqu'à la fin du chrono
      ✅ Réussite : victime -1, Croque intact
      ✅ Mort du Croque : nouvelle manche
      ✅ Mort du Croque : une seule vie perdue — 4
      ✅ Expiration : Croque -1 vie — 4
      ✅ Croque déconnecté : vie perdue à l'expiration — 4
    
    === J5 — pouvoirs (attribution / révocation, 4 fins de mission) ===
      ✅ [tracker] accordé
      ✅ [tracker] révoqué après success — items= effets= équipe=true
      ✅ [shadow] accordé
      ✅ [shadow] révoqué après death — items= effets= équipe=true
      ✅ [predator] accordé
      ✅ [predator] révoqué après skip — items= effets= équipe=true
      ✅ [brute] accordé
      ✅ [brute] révoqué après /croque stop — items= effets=
      ✅ [shell] accordé
      ✅ [shell] révoqué après success — items= effets= équipe=true
      ✅ [reprieve] accordé
      ✅ [reprieve] révoqué après death — items= effets= équipe=true
      ✅ [disguise] accordé
      ✅ [disguise] révoqué après skip — items= effets= équipe=true
      ✅ [second_wind] accordé
      ✅ [second_wind] révoqué après /croque stop — items= effets=
      ✅ [blind] accordé
      ✅ [blind] révoqué après success — items= effets= équipe=true
      ✅ [shadow_step] accordé
      ✅ [shadow_step] révoqué après death — items= effets= équipe=true
      ✅ [bite] accordé
      ✅ [bite] révoqué après skip — items= effets= équipe=true
      ✅ [reveal] accordé
      ✅ [reveal] révoqué après /croque stop — items= effets=
      ✅ Aveuglement utilisé au clic droit → cible aveuglée
      ✅ Mode random : pouvoir tiré sans roulette — shell
      ✅ Héritage : le Croque suivant a les pouvoirs du précédent + 1 nouveau — shell → shell, bite
    
    === J6 — règles spéciales ===
      ✅ Œufs : 10 zombies tués à 100 % → 10 œufs — donkey_spawn_eggx1 tropical_fish_spawn_eggx1 piglin_spawn_eggx1 pufferfish_spawn_eggx1 llama_spawn_eggx1 pig_spawn_eggx1 cod_spawn_eggx1 bogged_spawn_eggx1 shulker_spawn_eggx1 strider_spawn_eggx1
      ✅ Œufs : jamais celui du mob tué
      ✅ Œufs : aucun boss
      ✅ Creeper d'œuf marqué avec son propriétaire — Creeper has the following entity data: {"croquemitaine:egg_owner": "eaaafb03-b46a-3875-ad06-732ffe46d036"}
      ✅ Creeper du passif n'abîme pas l'hostile — 20→20
      ✅ Second creeper d'œuf apparu
      ✅ Creeper du passif blesse un assassin à ≤ 40 blocs (contrôle) — 20→7.1344633
      ✅ Recette TNT simplifiée enregistrée — Unlocked 1 recipe(s) for CmA
      ✅ Recette de table d'enchantement retirée — Unknown recipe: minecraft:enchanting_table
      ✅ Mode block — casque refusé via inventaire
      ✅ Mode block — balayage : citrouille retirée de la tête
      ✅ Mode block — balayage : item rendu dans l'inventaire
      ✅ Mode poison : le casque reste porté
      ✅ Mode poison : Poison II appliqué
      ✅ Mode poison : poison retiré avec le casque
      ✅ Gamerule spawn_phantoms = false
      ✅ Aucun phantom ne peut apparaître
      ✅ Table posée au centre — -180 62 350
      ✅ Table résiste à une TNT
      ✅ Table incassable par un joueur
    
    === J7 — fin de partie ===
      ✅ 1 survivant → partie terminée — ENDED
      ✅ Titre de victoire reçu
      ✅ 2 assassins → la bordure rétrécit — 300 → 259
      ✅ Pas de Croque possible entre assassins
    
    === J8 — commandes admin ===
      ✅ /croque pause & resume
      ✅ /croque lives add — [Croquemitaine] CmB a maintenant 7 vies.
    
      ✅ /croque border
      ✅ /croque reload
      ✅ /croque status
      ✅ /croque revive — [Croquemitaine] CmC est ressuscité avec 1 vie.
    
      ✅ /croque force
      ✅ /croque skip
      ✅ Non-admin : /croque status refusé — Unknown or incomplete command. See below for error
    croque status<--[HERE]
      ✅ Non-admin : /croque start refusé — Unknown or incomplete command. See below for error
    croque start<--[HERE]
      ✅ Non-admin : /croque stop refusé — Unknown or incomplete command. See below for error
    croque stop<--[HERE]
      ✅ Non-admin : /croque lives CmA set 1 refusé — Unknown or incomplete command. See below for error
    croque lives CmA set 1<--[HERE]
      ✅ Non-admin : /death-history d'un autre refusé — Incorrect argument for command
    ...h-history CmA<--[HERE] | [Croquemitaine] Pouvoir obtenu : Révélation
      ✅ /croque stop
      ✅ Hors partie : pas d'apparition sur le toit de la boîte — y=59.0
    
    === Persistance — redémarrage serveur en pleine partie ===
      ✅ Partie restaurée (phase + manche) — {"phase":"RUNNING","round":1,"seconds":596} → {"phase":"RUNNING","round":1,"seconds":588}
      ✅ Chrono restant conservé
      ✅ Vies conservées
      ✅ Croque et pouvoir conservés
      ✅ Chrono du Croque réaffiché
    
    === J9 — voice chat ===
      ✅ Sans voicechat : message d'absence, aucun crash
      ✅ Logs serveur sans ERROR / Exception
    
    Résultat : 133/133 OK
