// Scénarios de test du §9, joués sur le serveur Paper 26.3 local.
// Usage : node scenarios.mjs [j1 j2 ...]   (sans argument : tous)
import {
  check, section, results, startServer, stopServer, logProblems, allLogProblems, cmd, closeRcon, applyConfig, gameJson,
  bots, connectBot, disconnectBot, disconnectAll, waitFor, sleep, status, livesOf, croqueName, roundInfo,
  inTeam, hasEffect, uuidInts, freshGame, placeApart, heal, health, at, CX, CZ,
} from './lib.mjs';

const A = 'CmA', B = 'CmB', C = 'CmC', D = 'CmD';

async function hurts(victim, fn) {
  await heal(victim);
  const before = await health(victim);
  await fn();
  await sleep(250);
  return (await health(victim)) < before;
}

// ------------------------------------------------------------------ J0
async function j0() {
  section('J0 — démarrage');
  const pl = await cmd('plugins');
  check('Plugin Croquemitaine listé', /Croquemitaine/.test(pl), pl.replace(/\n/g, ' '));
  check('Aucune erreur au chargement', logProblems().length === 0, logProblems().slice(0, 3).join(' | '));
}

// ------------------------------------------------------------------ J1
async function j1() {
  section('J1 — vies, couleurs, élimination');
  await freshGame([A, B, C], { 'elimination.mode': 'spectator' });
  check('Partie démarrée en manche 1', (await roundInfo())?.round === 1);
  check('5 vies au départ, équipe cm_5', (await livesOf(A)) === 5 && await inTeam('cm_5', A));
  for (let expected = 4; expected >= 0; expected--) {
    await cmd(`kill ${A}`);
    await waitFor(async () => (await livesOf(A)) === expected, 5000);
    await sleep(1500);
    const ok = (await livesOf(A)) === expected && await inTeam(`cm_${expected}`, A);
    check(`Après mort → ${expected} vies, équipe cm_${expected}`, ok);
  }
  await sleep(1500);
  check('Option spectator : éliminé → spectateur', bots[A].game.gameMode === 'spectator', bots[A].game.gameMode);
  // Chat privé : le message d'un éliminé n'atteint pas les vivants.
  bots[B].messages.length = 0;
  bots[A].chat('je suis mort, le croque est derrière toi');
  await sleep(1000);
  check('Chat d\'un éliminé invisible pour les vivants', !bots[B].messages.some((m) => /croque est derrière/.test(m)), bots[B].messages.join(' | '));
  await disconnectBot(A);
  await connectBot(A);
  await sleep(1000);
  check('Reconnexion d\'un éliminé → toujours spectateur', bots[A].game.gameMode === 'spectator', bots[A].game.gameMode);
  await cmd(`croque revive ${A}`);
  await sleep(800);
  check('Ressuscité → retour en survie', bots[A].game.gameMode === 'survival', bots[A].game.gameMode);
  // Mode par défaut : la boîte.
  await freshGame([A, B, C]);
  await cmd(`croque lives ${A} set 0`);
  await sleep(1500);
  const pos = bots[A].entity.position;
  check('Défaut (boîte) : éliminé téléporté dans la boîte', pos.y >= 290, `y=${pos.y.toFixed(1)}`);
  check('Mode boîte : éliminé en mode aventure', bots[A].game.gameMode === 'adventure', bots[A].game.gameMode);
  await disconnectBot(A);
  await connectBot(A);
  await sleep(1000);
  check('Mode boîte : reconnexion → retour boîte', bots[A].entity.position.y >= 290, `y=${bots[A].entity.position.y.toFixed(1)}`);
  const tabCheck = await cmd(`scoreboard players get ${B} cm_lives`);
  check('Tab list : vies affichées (objectif cm_lives)', /has 5/.test(tabCheck), tabCheck);
}

// ------------------------------------------------------------------ J2
async function j2() {
  section('J2 — spawn, mort, gamerules');
  await freshGame([A, B, C]);
  const kr = await cmd('gamerule keep_inventory');
  const lb = await cmd('gamerule locator_bar');
  check('keep_inventory = true', /true/.test(kr), kr);
  check('locator_bar = false', /false/.test(lb), lb);
  const wb = await cmd('worldborder get');
  check('Bordure 300 (3 joueurs → taille min)', /300/.test(wb), wb);
  let unsafe = 0, outside = 0;
  for (let i = 0; i < 20; i++) {
    await cmd(`croque lives ${A} set 5`);
    const before = bots[A].entity.position.clone();
    await cmd(`kill ${A}`);
    await waitFor(() => bots[A].entity && bots[A].entity.position.distanceTo(before) > 1 && bots[A].health > 0, 6000);
    await sleep(1500);
    const p = bots[A].entity.position;
    if (Math.abs(p.x - CX - 0.5) > 150 || Math.abs(p.z - CZ - 0.5) > 150) outside++;
    const below = bots[A].blockAt(p.offset(0, -1, 0));
    const feet = bots[A].blockAt(p), head = bots[A].blockAt(p.offset(0, 1, 0));
    const bad = !below || below.boundingBox !== 'block' || /leaves|water|lava/.test(below.name)
      || (feet && feet.boundingBox === 'block') || (head && head.boundingBox === 'block');
    if (bad) { unsafe++; console.log(`    position douteuse ${p} sol=${below?.name}`); }
  }
  check('20 respawns dans la bordure', outside === 0, `${outside} hors bordure`);
  check('20 respawns sur bloc sûr', unsafe === 0, `${unsafe} douteux`);
  const compasses = bots[A].inventory.items().filter((i) => i.name === 'recovery_compass').reduce((n, i) => n + i.count, 0);
  check('Pas de boussole de récupération par défaut', compasses === 0, `${compasses}`);
  await applyConfig({ 'recovery-compass': 'true' });
  for (let i = 0; i < 2; i++) {
    await cmd(`kill ${A}`);
    await sleep(2500);
  }
  const c2 = bots[A].inventory.items().filter((i) => i.name === 'recovery_compass').reduce((n, i) => n + i.count, 0);
  check('Option recovery-compass : une seule boussole après 2 morts', c2 === 1, `${c2}`);
  await applyConfig();
  bots[A].messages.length = 0;
  bots[A].chat('/death-history');
  await sleep(1200);
  const lines = bots[A].messages.filter((m) => /^#\d+ /.test(m));
  check('/death-history sans coordonnées par défaut', lines.length > 0 && !lines.some((l) => /— -?\d+ -?\d+ -?\d+ —/.test(l)), lines[0] ?? '');
  bots[B].messages.length = 0;
  await cmd(`kill ${A}`);
  await sleep(1200);
  check('Les morts s\'affichent dans le chat de tous', bots[B].messages.some((m) => m.includes(A)), bots[B].messages.join(' | '));
  await cmd(`croque lives ${A} set 5`);
  check('/death-history liste les 22 morts', lines.length >= 22, `${lines.length} lignes ; ex: ${lines[0] ?? ''}`);
}

// ------------------------------------------------------------------ J3
const LIVES = { P: 5, H: 3, A: 1 };
async function j3() {
  section('J3 — PvP : aucune restriction par défaut, matrice si pvp.enforce');
  await freshGame([A, B, C]);
  await cmd(`croque lives ${A} set 5`);
  await cmd(`croque lives ${B} set 5`);
  await placeApart(A, B, 3);
  check('Défaut : passif → passif en mêlée passe (règle d\'honneur)', await hurts(B, () => cmd(`damage ${B} 4 minecraft:player_attack by ${A}`)));
  {
    const uuid = bots[B].players[A].uuid;
    await placeApart(A, B, 4);
    await heal(B);
    await cmd(`summon splash_potion ${at(4.5, 202, 0.5)} {Motion:[0.0,-1.0,0.0],Owner:[I;${uuidInts(uuid).join(',')}],Item:{id:"minecraft:splash_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:poison"}}}}`);
    await sleep(1500);
    check('Défaut : potion de poison passif → passif appliquée (PvP indirect libre)', await hasEffect(B, 'poison'));
  }
  await freshGame([A, B, C], { enforce: 'true' });
  // Matrice hors Croque : attaquant × victime, à 3 blocs (mêlée).
  const expected = { PP: false, PH: false, PA: true, HP: true, HH: false, HA: true, AP: true, AH: true, AA: true };
  for (const [combo, exp] of Object.entries(expected)) {
    await cmd(`croque lives ${A} set ${LIVES[combo[0]]}`);
    await cmd(`croque lives ${B} set ${LIVES[combo[1]]}`);
    await placeApart(A, B, 3);
    const hit = await hurts(B, () => cmd(`damage ${B} 4 minecraft:player_attack by ${A}`));
    check(`Mêlée ${combo[0]}→${combo[1]} ${exp ? 'autorisée' : 'refusée'}`, hit === exp);
  }
  // Vraie attaque d'un bot (passif → passif refusée, hostile → passif autorisée).
  for (const [la, exp] of [[5, false], [3, true]]) {
    await cmd(`croque lives ${A} set ${la}`);
    await cmd(`croque lives ${B} set 5`);
    await placeApart(A, B, 2);
    const hit = await hurts(B, async () => {
      const target = bots[A].players[B]?.entity;
      if (target) { await bots[A].lookAt(target.position.offset(0, 1.5, 0), true); bots[A].attack(target); }
    });
    check(`Coup réel du bot (${la} vies → passif) ${exp ? 'passe' : 'bloqué'}`, hit === exp);
  }
  // Flèche à 39 / 41 blocs : passif → assassin.
  const uuidA = bots[B].players[A].uuid;
  for (const [dist, exp] of [[39, true], [41, false]]) {
    await cmd(`croque lives ${A} set 5`);
    await cmd(`croque lives ${B} set 1`);
    await placeApart(A, B, dist);
    const hit = await hurts(B, () => cmd(`summon arrow ${at(dist + 0.5, 203, 0.5)} {Motion:[0.0,-3.0,0.0],Owner:[I;${uuidInts(uuidA).join(',')}],damage:2.0d}`));
    check(`Flèche passif→assassin à ${dist} blocs ${exp ? 'touche' : 'bloquée'}`, hit === exp);
  }
  // Potion jetée par un passif sur un passif : sans effet.
  await cmd(`croque lives ${A} set 5`);
  await cmd(`croque lives ${B} set 5`);
  await placeApart(A, B, 4);
  await heal(B);
  await cmd(`summon splash_potion ${at(4.5, 202, 0.5)} {Motion:[0.0,-1.0,0.0],Owner:[I;${uuidInts(uuidA).join(',')}],Item:{id:"minecraft:splash_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:poison"}}}}`);
  await sleep(1500);
  check('Potion de poison passif→passif sans effet', !(await hasEffect(B, 'poison')));
  await cmd(`croque lives ${A} set 3`);
  await heal(B);
  await cmd(`summon splash_potion ${at(4.5, 202, 0.5)} {Motion:[0.0,-1.0,0.0],Owner:[I;${uuidInts(uuidA).join(',')}],Item:{id:"minecraft:splash_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:poison"}}}}`);
  await sleep(1500);
  check('Potion de poison hostile→passif appliquée (contrôle)', await hasEffect(B, 'poison'));
  // Mort par chute après un coup → bon tueur.
  await heal(B);
  await cmd(`damage ${B} 1 minecraft:player_attack by ${A}`);
  await sleep(300);
  await cmd(`tp ${B} ${at(4.5, 260, 0.5)}`);
  await waitFor(async () => (await livesOf(B)) === 4, 8000);
  const hist = await cmd(`death-history ${B}`);
  check('Chute après un coup : tueur attribué', new RegExp(`tué par ${A}`).test(hist), hist.split('\n').pop());
  // Le Croque : ne peut pas frapper un assassin ; un passif peut le frapper à ≤ 40.
  await cmd(`croque lives ${A} set 5`);
  await cmd(`croque lives ${B} set 1`);
  await cmd(`croque force ${A}`);
  await sleep(500);
  await placeApart(A, B, 3);
  check('Croque → assassin refusé', !(await hurts(B, () => cmd(`damage ${B} 4 minecraft:player_attack by ${A}`))));
  await cmd(`croque lives ${B} set 5`);
  check('Croque → passif autorisé', await hurts(B, () => cmd(`damage ${B} 4 minecraft:player_attack by ${A}`)));
  check('Passif → Croque passif à 3 blocs autorisé', await hurts(A, () => cmd(`damage ${A} 4 minecraft:player_attack by ${B}`)));
}

// ------------------------------------------------------------------ J4
async function j4() {
  section('J4 — manches et Croquemitaine');
  const short = { 'initial-seconds-override': 20, 'decrement-seconds-override': 5, 'min-seconds-override': 10 };
  await freshGame([A, B, C, D], short);
  await sleep(2500);
  const anyCroqueTitle = Object.values(bots).some((b) => b.titles.some((t) => /Tu es le Croquemitaine/.test(t)));
  check('Manche 1 sans Croque', (await croqueName()) === null && !anyCroqueTitle);
  // Attendre la manche 2.
  await waitFor(async () => (await roundInfo())?.round === 2, 25_000, 500);
  await sleep(3500);
  const cq = await croqueName();
  const youAre = Object.entries(bots).filter(([, b]) => b.titles.some((t) => /Tu es le Croquemitaine/.test(t))).map(([n]) => n);
  const notYou = Object.entries(bots).filter(([, b]) => b.titles.some((t) => /pas le Croquemitaine/.test(t))).map(([n]) => n);
  check('Manche 2 : un Croque désigné', cq !== null, cq);
  check('Seul le Croque voit « Tu es le Croquemitaine »', youAre.length === 1 && youAre[0] === cq, youAre.join(','));
  check('Les autres voient « Tu n\'es pas… »', notYou.length === 3, notYou.join(','));
  const withBar = Object.entries(bots).filter(([, b]) => b.barCount > 0).map(([n]) => n);
  check('Chrono (bossbar) visible par le Croque seul', withBar.length === 1 && withBar[0] === cq, withBar.join(','));

  // Durées décroissantes avec plancher (20, 15, 10, 10…).
  await applyConfig({ ...short, 'initial-seconds-override': 600, 'decrement-seconds-override': 120, 'min-seconds-override': 360, 'announce-suspense-seconds': 1 });
  const durations = [];
  for (let i = 0; i < 5; i++) {
    await cmd('croque skip');
    const ri = await roundInfo();
    durations.push([ri.round, ri.seconds]);
  }
  const okDur = durations.every(([n, s]) => Math.abs(s - Math.max(360, 600 - 120 * (n - 1))) <= 1);
  check('Durées décroissantes avec plancher', okDur, durations.map(([n, s]) => `m${n}=${s}s`).join(' '));

  // Tirage : jamais un assassin ni un déconnecté.
  await cmd(`croque lives ${A} set 1`);
  await disconnectBot(D);
  const picked = new Set();
  for (let i = 0; i < 15; i++) {
    await cmd('croque skip');
    picked.add(await croqueName());
  }
  check('Tirage : jamais l\'assassin ni le déconnecté', !picked.has(A) && !picked.has(D), [...picked].join(','));
  await connectBot(D);
  await cmd(`croque lives ${A} set 5`);

  // Réussite : le Croque tue un passif → manche suivante, seule la victime perd une vie.
  await cmd(`croque force ${B}`);
  await sleep(1500);
  let r0 = (await roundInfo()).round;
  await cmd(`damage ${A} 100 minecraft:player_attack by ${B}`);
  await sleep(2000);
  check('Réussite : la manche continue (pas de nouveau tirage)', (await roundInfo()).round === r0);
  check('Réussite : plus de Croque jusqu\'à la fin du chrono', (await croqueName()) === null);
  check('Réussite : victime -1, Croque intact', (await livesOf(A)) === 4 && (await livesOf(B)) === 5);

  // Mort du Croque : échec, une seule vie perdue, nouvelle manche.
  await cmd(`croque force ${C}`);
  await sleep(1500);
  r0 = (await roundInfo()).round;
  await cmd(`kill ${C}`);
  await waitFor(async () => (await roundInfo()).round === r0 + 1, 5000);
  check('Mort du Croque : nouvelle manche', (await roundInfo()).round === r0 + 1);
  check('Mort du Croque : une seule vie perdue', (await livesOf(C)) === 4, `${await livesOf(C)}`);

  // Expiration : -1 vie au Croque.
  await applyConfig({ 'initial-seconds-override': 8, 'decrement-seconds-override': 0, 'min-seconds-override': 8, reprieve: 0 });
  await cmd('croque skip');
  await cmd(`croque force ${D}`);
  r0 = (await roundInfo()).round;
  await waitFor(async () => (await roundInfo()).round === r0 + 1, 15_000, 500);
  check('Expiration : Croque -1 vie', (await livesOf(D)) === 4, `${await livesOf(D)}`);

  // Déconnexion + expiration : vie retirée hors ligne.
  await applyConfig({ 'initial-seconds-override': 10, 'decrement-seconds-override': 0, 'min-seconds-override': 10, reprieve: 0 });
  await cmd('croque skip');
  await cmd(`croque force ${B}`);
  await sleep(500);
  await disconnectBot(B);
  r0 = (await roundInfo()).round;
  await waitFor(async () => (await roundInfo()).round === r0 + 1, 15_000, 500);
  check('Croque déconnecté : vie perdue à l\'expiration', (await livesOf(B)) === 4, `${await livesOf(B)}`);
  await connectBot(B);
}

// ------------------------------------------------------------------ J5
const POWERS = ['tracker', 'shadow', 'predator', 'brute', 'shell', 'reprieve', 'disguise', 'second_wind', 'blind', 'shadow_step', 'bite', 'reveal'];
const POWER_ITEMS = { tracker: 'compass', shadow: 'echo_shard', blind: 'ink_sac', shadow_step: 'ender_eye', reveal: 'spyglass' };
const POWER_EFFECTS = { predator: 'speed', brute: 'strength', shell: 'resistance' };

async function powerPresent(id) {
  if (POWER_ITEMS[id]) return bots[A].inventory.items().some((i) => i.name === POWER_ITEMS[id]);
  if (POWER_EFFECTS[id]) return hasEffect(A, POWER_EFFECTS[id]);
  if (id === 'disguise') return inTeam('cm_5', A);
  if (id === 'bite') {
    await placeApart(A, B, 2);
    await cmd(`damage ${B} 1 minecraft:player_attack by ${A}`);
    await sleep(500);
    return hasEffect(B, 'slowness');
  }
  if (id === 'second_wind') {
    await cmd(`kill ${A}`);
    await sleep(2000);
    return (await croqueName()) === A;
  }
  return true; // reprieve : vérifié via le chrono
}

async function powerGone() {
  const items = bots[A].inventory.items().map((i) => i.name);
  const leftovers = Object.values(POWER_ITEMS).filter((n) => items.includes(n));
  const effects = [];
  for (const e of Object.values(POWER_EFFECTS)) if (await hasEffect(A, e)) effects.push(e);
  const lives = await livesOf(A);
  const teamOk = await inTeam(`cm_${lives}`, A);
  return { ok: leftovers.length === 0 && effects.length === 0 && teamOk && (await croqueName()) !== A, detail: `items=${leftovers} effets=${effects} équipe=${teamOk}` };
}

async function j5() {
  section('J5 — pouvoirs (attribution / révocation, 4 fins de mission)');
  const endings = ['success', 'death', 'skip', 'stop'];
  for (let i = 0; i < POWERS.length; i++) {
    const id = POWERS[i];
    const ending = endings[i % endings.length];
    await freshGame([A, B, C], { inherit: 'false', 'power-choice-seconds': 60 });
    await cmd(`croque lives ${A} set 3`);
    await cmd(`croque force ${A}`);
    await sleep(1800); // fin de l'annonce, roulette ouverte (60 s)
    const before = (await roundInfo()).seconds;
    await cmd(`croque givepower ${id}`);
    await sleep(1500);
    let present = await powerPresent(id);
    if (id === 'reprieve') present = (await roundInfo()).seconds >= before + 170;
    check(`[${id}] accordé`, present);
    // Fin de mission
    if (ending === 'success') {
      const it = POWER_ITEMS[id] && bots[A].inventory.items().find((x) => x.name === POWER_ITEMS[id]);
      if (it) await bots[A].equip(it, 'hand'); // tuer avec l'item de pouvoir en main
      bots[B].messages.length = 0;
      await cmd(`damage ${C} 100 minecraft:player_attack by ${A}`);
      await sleep(800);
      const dm = bots[B].messages.find((m) => m.includes(C)) ?? '';
      check(`[${id}] message de mort sans nom de pouvoir`, dm && !/\[/.test(dm), dm);
    }
    if (ending === 'death') { await cmd(`kill ${A}`); if (id === 'second_wind') { await sleep(1500); await cmd(`kill ${A}`); } }
    if (ending === 'skip') await cmd('croque skip');
    // La manche suivante tire un Croque au hasard, qui peut retomber sur A : on force B.
    if (ending !== 'stop') { await sleep(500); await cmd(`croque force ${B}`); }
    if (ending === 'stop') await cmd('croque stop');
    await sleep(2500);
    if (ending === 'stop') {
      const items = bots[A].inventory.items().map((it) => it.name).filter((n) => Object.values(POWER_ITEMS).includes(n));
      const eff = [];
      for (const e of Object.values(POWER_EFFECTS)) if (await hasEffect(A, e)) eff.push(e);
      check(`[${id}] révoqué après /croque stop`, items.length === 0 && eff.length === 0, `items=${items} effets=${eff}`);
    } else {
      const g = await powerGone();
      check(`[${id}] révoqué après ${ending}`, g.ok, g.detail);
    }
  }
  // Utilisation d'un pouvoir actif par clic droit : Aveuglement.
  await freshGame([A, B, C], { inherit: 'false', 'power-choice-seconds': 60 });
  await cmd(`croque force ${A}`);
  await sleep(1800);
  await cmd(`croque givepower blind`);
  await sleep(800);
  await placeApart(A, B, 3);
  const item = bots[A].inventory.items().find((it) => it.name === 'ink_sac');
  if (item) { await bots[A].equip(item, 'hand'); bots[A].activateItem(); }
  await sleep(800);
  check('Aveuglement utilisé au clic droit → cible aveuglée', await hasEffect(B, 'blindness'));

  // Héritage cumulatif + mode random.
  await freshGame([A, B, C], { inherit: 'true', mode: 'random' });
  await cmd(`croque force ${A}`);
  await sleep(2500);
  const s1 = (await status()).match(/pouvoir : ([^)]*)\)/)?.[1] ?? '';
  await cmd(`croque force ${B}`);
  await sleep(2500);
  const s2 = (await status()).match(/pouvoir : ([^)]*)\)/)?.[1] ?? '';
  const first = s1.split(',').map((x) => x.trim()).filter(Boolean);
  const second = s2.split(',').map((x) => x.trim()).filter(Boolean);
  check('Mode random : pouvoir tiré sans roulette', first.length === 1, s1);
  check('Héritage : le Croque suivant a les pouvoirs du précédent + 1 nouveau',
    second.length === 2 && first.every((p) => second.includes(p)), `${s1} → ${s2}`);
}

// ------------------------------------------------------------------ J6
async function j6() {
  section('J6 — règles spéciales');
  await freshGame([A, B, C], { 'mob-egg-chance': 1.0, enforce: 'true', 'helmet-mode': 'block', 'mob-egg-owner': 'true' });
  await cmd('difficulty easy');
  await placeApart(A, B, 6);
  await cmd(`clear ${A}`);
  for (let i = 0; i < 10; i++) await cmd(`summon zombie ${at(1.5, 200, 0.5)} {NoAI:1b,PersistenceRequired:1b,Tags:["cmtest"]}`);
  await sleep(500);
  await cmd(`execute as @e[type=zombie,tag=cmtest] run damage @s 100 minecraft:player_attack by ${A}`);
  await sleep(1000);
  // Les œufs s'éparpillent : on les ramène sur le joueur pour qu'il les ramasse.
  await cmd(`tp @e[type=item] ${A}`);
  await sleep(2000);
  const eggs = bots[A].inventory.items().filter((i) => i.name.endsWith('_spawn_egg'));
  const count = eggs.reduce((n, i) => n + i.count, 0);
  check('Œufs : 10 zombies tués à 100 % → 10 œufs', count === 10, eggs.map((e) => `${e.name}x${e.count}`).join(' '));
  check('Œufs : jamais celui du mob tué', !eggs.some((e) => e.name === 'zombie_spawn_egg'));
  const bossEgg = eggs.some((e) => /^(wither|ender_dragon|warden|elder_guardian)_spawn_egg$/.test(e.name));
  check('Œufs : aucun boss', !bossEgg);

  // Creeper d'œuf d'un passif ne blesse pas un hostile ; blesse un assassin proche (contrôle).
  await cmd(`kill @e[type=item]`);
  await cmd(`croque lives ${A} set 5`);
  await cmd(`croque lives ${B} set 3`);
  await placeApart(A, B, 8);
  await cmd(`clear ${A}`);
  await cmd(`give ${A} creeper_spawn_egg 2`);
  await sleep(500);
  const egg = bots[A].inventory.items().find((i) => i.name === 'creeper_spawn_egg');
  await bots[A].equip(egg, 'hand');
  const ground = bots[A].blockAt(bots[A].entity.position.offset(3, -1, 0));
  await bots[A].lookAt(ground.position.offset(0.5, 1, 0.5), true);
  await bots[A].activateBlock(ground);
  await sleep(800);
  const tagged = await cmd('execute if entity @e[type=creeper,nbt={Paper.SpawnReason:"SPAWNER_EGG"}]');
  const pdc = await cmd('data get entity @e[type=creeper,limit=1,nbt={Paper.SpawnReason:"SPAWNER_EGG"}] BukkitValues');
  check('Option mob-egg-owner : creeper d\'œuf marqué avec son propriétaire', /croquemitaine:egg_owner/.test(pdc), pdc.slice(0, 120) || tagged);
  await cmd(`tp @e[type=creeper] ${at(7.5, 200, 0.5)}`);
  await heal(B);
  let before = await health(B);
  await cmd('execute as @e[type=creeper] run data merge entity @s {ignited:1b,Fuse:1s}');
  await sleep(600);
  let afterHp = await health(B);
  check('Creeper du passif n\'abîme pas l\'hostile', afterHp >= before, `${before}→${afterHp}`);
  await cmd(`croque lives ${B} set 1`);
  await cmd('kill @e[type=creeper]');
  await placeApart(A, B, 8); // l'explosion précédente a creusé la plateforme et repoussé les bots
  await activateEgg();
  check('Second creeper d\'œuf apparu', /passed/i.test(await cmd('execute if entity @e[type=creeper,nbt={Paper.SpawnReason:"SPAWNER_EGG"}]')));
  await cmd(`tp @e[type=creeper] ${at(7.5, 200, 0.5)}`);
  await heal(B);
  before = await health(B);
  await cmd('execute as @e[type=creeper] run data merge entity @s {ignited:1b,Fuse:1s}');
  await sleep(600);
  afterHp = await health(B);
  check('Creeper du passif blesse un assassin à ≤ 40 blocs (contrôle)', afterHp < before, `${before}→${afterHp}`);

  // Recettes.
  await cmd(`recipe take ${A} croquemitaine:simple_tnt`);
  const tnt = await cmd(`recipe give ${A} croquemitaine:simple_tnt`);
  check('Recette TNT simplifiée enregistrée', /Unlocked|débloqu|1 recipe/i.test(tnt), tnt);
  const ench = await cmd(`recipe give ${A} minecraft:enchanting_table`);
  check('Recette de table d\'enchantement retirée', !/Unlocked 1/i.test(ench), ench);

  // Casque.
  await cmd(`clear ${A}`);
  await cmd(`give ${A} iron_helmet`);
  await sleep(500);
  const helmet = bots[A].inventory.items().find((i) => i.name === 'iron_helmet');
  try { await bots[A].equip(helmet, 'head'); } catch { /* refus attendu */ }
  await sleep(1500);
  check('Mode block — casque refusé via inventaire', !bots[A].inventory.slots[5]);
  await cmd(`item replace entity ${A} armor.head with minecraft:carved_pumpkin`);
  await sleep(1600);
  check('Mode block — balayage : citrouille retirée de la tête', !bots[A].inventory.slots[5]);
  const hasPumpkin = bots[A].inventory.items().some((i) => i.name === 'carved_pumpkin');
  check('Mode block — balayage : item rendu dans l\'inventaire', hasPumpkin);

  // Mode par défaut « poison » : le casque reste, mais Poison II tant qu'il est porté.
  await applyConfig({ 'mob-egg-chance': 1.0, enforce: 'true', 'helmet-mode': 'poison' });
  await cmd(`effect clear ${A}`);
  await cmd(`item replace entity ${A} armor.head with minecraft:iron_helmet`);
  await sleep(1600);
  const keeps = /passed/i.test(await cmd(`execute if entity @a[name=${A},nbt={equipment:{head:{id:"minecraft:iron_helmet"}}}]`));
  check('Mode poison : le casque reste porté', keeps);
  check('Mode poison : Poison II appliqué', /passed/i.test(await cmd(`execute if entity @a[name=${A},nbt={active_effects:[{id:"minecraft:poison",amplifier:1b}]}]`)));
  await cmd(`item replace entity ${A} armor.head with minecraft:air`);
  await sleep(2000);
  check('Mode poison : poison retiré avec le casque', !(await hasEffect(A, 'poison')));

  // Par défaut, un mob d'œuf est un mob normal (aucun propriétaire).
  await applyConfig({ 'mob-egg-chance': 1.0, 'mob-egg-owner': 'false' });
  await cmd('kill @e[type=creeper]');
  await cmd(`gamemode survival ${A}`);
  await placeApart(A, B, 8);
  await activateEgg();
  const plain = await cmd('data get entity @e[type=creeper,limit=1,nbt={Paper.SpawnReason:"SPAWNER_EGG"}]');
  check('Défaut : mob d\'œuf sans propriétaire', /Creeper has/.test(plain) && !/egg_owner/.test(plain), plain.slice(0, 80));
  await cmd('kill @e[type=creeper]');

  // Phantoms désactivés.
  check('Gamerule spawn_phantoms = false', /false/.test(await cmd('gamerule spawn_phantoms')));
  await cmd(`execute at ${A} run summon phantom ~ ~5 ~`);
  await sleep(300);
  check('Aucun phantom ne peut apparaître', !/passed/i.test(await cmd('execute if entity @e[type=phantom]')));

  // Table d'enchantement.
  const [tx, ty, tz] = gameJson().enchantingTable;
  const isTable = async () => /passed/i.test(await cmd(`execute if block ${tx} ${ty} ${tz} minecraft:enchanting_table`));
  check('Table posée au centre', await isTable(), `${tx} ${ty} ${tz}`);
  await cmd(`summon tnt ${tx + 1.5} ${ty} ${tz + 0.5} {fuse:1s}`);
  await sleep(1500);
  check('Table résiste à une TNT', await isTable());
  await cmd(`tp ${A} ${tx + 1.5} ${ty} ${tz + 0.5}`);
  await cmd(`gamemode survival ${A}`);
  await sleep(800);
  try { await bots[A].dig(bots[A].blockAt(new (bots[A].entity.position.constructor)(tx, ty, tz)), true); } catch { /* */ }
  await sleep(500);
  check('Table incassable par un joueur', await isTable());

  async function activateEgg() {
    await cmd(`give ${A} creeper_spawn_egg 1`);
    await sleep(400);
    const e = bots[A].inventory.items().find((i) => i.name === 'creeper_spawn_egg');
    await bots[A].equip(e, 'hand');
    const g = bots[A].blockAt(bots[A].entity.position.offset(3, -1, 0));
    await bots[A].activateBlock(g);
    await sleep(800);
  }
}

// ------------------------------------------------------------------ J7
async function j7() {
  section('J7 — fin de partie');
  await freshGame([A, B, C]);
  await cmd(`croque lives ${B} set 0`);
  await cmd(`croque lives ${C} set 0`);
  await sleep(1000);
  const ri = await roundInfo();
  check('1 survivant → partie terminée', ri.phase === 'ENDED', ri.phase);
  check('Titre de victoire reçu', Object.values(bots).some((b) => b.titles.some((t) => /remporte/.test(t))));
  await freshGame([A, B, C], { 'shrink-delay-seconds': 2, 'shrink-duration-seconds': 30 });
  await cmd(`croque lives ${C} set 0`);
  await cmd(`croque lives ${A} set 1`);
  await cmd(`croque lives ${B} set 1`);
  const w0 = Number((await cmd('worldborder get')).match(/([\d.]+)/)[1]);
  await sleep(6000);
  const w1 = Number((await cmd('worldborder get')).match(/([\d.]+)/)[1]);
  check('2 assassins → la bordure rétrécit', w1 < w0, `${w0} → ${w1}`);
  check('Pas de Croque possible entre assassins', (await croqueName()) === null);
}

// ------------------------------------------------------------------ J8
async function j8() {
  section('J8 — commandes admin');
  await freshGame([A, B, C]);
  const outs = {
    pause: await cmd('croque pause'), resume: await cmd('croque resume'),
    lives: await cmd(`croque lives ${B} add 2`), border: await cmd('croque border 250'),
    reload: await cmd('croque reload'), status: await cmd('croque status'),
  };
  check('/croque pause & resume', /pause/i.test(outs.pause) && /reprise/i.test(outs.resume));
  check('/croque lives add', /7 vies/.test(outs.lives), outs.lives);
  check('/croque border', /250/.test(await cmd('worldborder get')));
  check('/croque reload', /recharg/i.test(outs.reload));
  check('/croque status', /Statut/.test(outs.status));
  await cmd(`croque lives ${C} set 0`);
  const rv = await cmd(`croque revive ${C}`);
  await sleep(800);
  check('/croque revive', /ressuscité/.test(rv) && (await livesOf(C)) === 1 && bots[C].game.gameMode === 'survival', rv);
  await cmd(`croque force ${A}`);
  check('/croque force', (await croqueName()) === A);
  const r0 = (await roundInfo()).round;
  await cmd('croque skip');
  check('/croque skip', (await roundInfo()).round === r0 + 1);
  for (const sub of ['status', 'start', 'stop', 'lives CmA set 1']) {
    bots[B].messages.length = 0;
    bots[B].chat(`/croque ${sub}`);
    await sleep(700);
    const refused = bots[B].messages.some((m) => /Unknown|incomplete|inconnue|permission/i.test(m));
    check(`Non-admin : /croque ${sub} refusé`, refused, bots[B].messages.join(' | '));
  }
  bots[B].messages.length = 0;
  bots[B].chat(`/death-history ${A}`);
  await sleep(700);
  check('Non-admin : /death-history d\'un autre refusé', !bots[B].messages.some((m) => /Historique des morts de CmA/.test(m)), bots[B].messages.join(' | '));
  await cmd('croque stop');
  check('/croque stop', (await roundInfo()).phase === 'LOBBY');
  // Régression : hors partie, un joueur ne doit pas apparaître sur le toit de la boîte d'élimination.
  await cmd(`tp ${B} ${CX} 320 ${CZ}`);
  await disconnectBot(B);
  await connectBot(B);
  await sleep(1000);
  check('Hors partie : pas d\'apparition sur le toit de la boîte', bots[B].entity.position.y < 290, `y=${bots[B].entity.position.y.toFixed(1)}`);
}

// ------------------------------------------------------------------ persistance (J1)
async function persist() {
  section('Persistance — redémarrage serveur en pleine partie');
  await freshGame([A, B, C], { 'power-choice-seconds': 60 });
  await cmd(`croque lives ${B} set 2`);
  await cmd(`croque force ${A}`);
  await sleep(1800);
  await cmd('croque givepower predator');
  await sleep(500);
  const before = await roundInfo();
  await disconnectAll();
  await stopServer();
  await startServer();
  await connectBot(A); await connectBot(B); await connectBot(C);
  await sleep(1500);
  const after = await roundInfo();
  check('Partie restaurée (phase + manche)', after.phase === 'RUNNING' && after.round === before.round, `${JSON.stringify(before)} → ${JSON.stringify(after)}`);
  check('Chrono restant conservé', Math.abs(after.seconds - before.seconds) < 20);
  check('Vies conservées', (await livesOf(B)) === 2 && await inTeam('cm_2', B));
  check('Croque et pouvoir conservés', (await croqueName()) === A && await hasEffect(A, 'speed'));
  check('Chrono du Croque réaffiché', bots[A].barCount > 0 && bots[B].barCount === 0);
}

// ------------------------------------------------------------------ J9
async function j9() {
  section('J9 — voice chat');
  const log = (await import('node:fs')).readFileSync((await import('./lib.mjs')).LOG, 'utf8');
  check('Sans voicechat : message d\'absence, aucun crash', /Simple Voice Chat absent/.test(log) && logProblems().length === 0);
}

// ------------------------------------------------------------------ main
const ALL = { j0, j1, j2, j3, j4, j5, j6, j7, j8, persist, j9 };
const wanted = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(ALL);
let external = false;
try { await cmd('list'); external = true; } catch { /* pas de serveur : on le lance */ }
if (!external) await startServer();
try {
  for (const k of wanted) {
    try { await ALL[k](); } catch (e) { check(`${k} : exception`, false, e.stack?.split('\n').slice(0, 3).join(' ')); }
  }
} finally {
  await disconnectAll();
  const problems = allLogProblems();
  check('Logs serveur sans ERROR / Exception', problems.length === 0, problems.slice(0, 5).join('\n'));
  if (!external) await stopServer(); else await closeRcon();
  const ko = results.filter((r) => !r.ok);
  console.log(`\nRésultat : ${results.length - ko.length}/${results.length} OK`);
  if (ko.length) console.log('Échecs :\n' + ko.map((r) => ` - ${r.name}: ${r.detail}`).join('\n'));
  process.exit(ko.length ? 1 : 0);
}
