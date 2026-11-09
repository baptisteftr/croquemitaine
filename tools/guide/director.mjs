// Réalisateur des captures du guide : met en scène chaque phase via RCON + bots, et ordonne au
// mod « Croquemitaine Camera » (client « Joueur ») de prendre les captures.
// Prérequis : serveur de test lancé, client caméra connecté (tools/camera-mod : ./gradlew runClient).
import { existsSync, mkdirSync, statSync } from 'node:fs';
import path from 'node:path';
import {
  ROOT, CX, CZ, cmd, sleep, waitFor, bots, connectBot, disconnectBot, applyConfig, gameJson, closeRcon,
} from '../../tests/bots/lib.mjs';

const CAM = 'Joueur';
const SHOTS = path.join(ROOT, 'tools/camera-mod/run/screenshots');
const NAMES = ['Alice', 'Bruno', 'Chloe', 'David', 'Emma'];
const done = [];

// ------------------------------------------------------------------ ordres au mod
const cam = (order) => cmd(`tellraw ${CAM} {"text":"cmcam:${order}"}`);
const clearChat = () => cam('chat:clear');

async function shot(name, caption) {
  const file = path.join(SHOTS, `${name}.png`);
  const before = existsSync(file) ? statSync(file).mtimeMs : 0;
  await cam(`shot:${name}`);
  const ok = await waitFor(() => existsSync(file) && statSync(file).mtimeMs > before, 8000, 150);
  console.log(`${ok ? '📸' : '❌'} ${name} — ${caption}`);
  if (ok) done.push(name);
  await sleep(300);
}

// ------------------------------------------------------------------ mise en scène
let stage; // coin de la scène (x, y, z) : à l'écart du centre pour ne pas toucher la table

async function posOf(name) {
  const m = (await cmd(`data get entity ${name} Pos`)).match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  return m ? m.slice(1).map(Number) : null;
}

async function buildStage() {
  const sx = CX + 24, sz = CZ + 24;
  await cmd(`spreadplayers ${sx} ${sz} 0 1 false ${CAM}`);
  await sleep(800);
  const [, y] = await posOf(CAM);
  const gy = Math.floor(y) - 1;
  await cmd(`fill ${sx - 12} ${gy} ${sz - 12} ${sx + 12} ${gy} ${sz + 12} minecraft:grass_block`);
  await cmd(`fill ${sx - 12} ${gy + 1} ${sz - 12} ${sx + 12} ${gy + 12} ${sz + 12} minecraft:air`);
  stage = { x: sx, y: gy + 1, z: sz };
}

/** Caméra sur la scène : regarde vers +Z (yaw 0) depuis le bord sud de la scène. */
async function camAt(dx = 0, dz = -6, yaw = 0, pitch = 5) {
  await ensureAlive();
  const want = [stage.x + dx + 0.5, stage.y, stage.z + dz + 0.5];
  await cmd(`tp ${CAM} ${want.join(' ')} ${yaw} ${pitch}`);
  const got = await posOf(CAM);
  if (!got || Math.hypot(got[0] - want[0], got[2] - want[2]) > 1) console.log(`   ⚠️ caméra mal placée : ${got}`);
}

/** Aligne les bots face à la caméra. */
async function lineUp(names, dz = 0) {
  const n = names.length;
  for (let i = 0; i < n; i++) {
    const dx = (i - (n - 1) / 2) * 1.8;
    await cmd(`tp ${names[i]} ${stage.x + dx + 0.5} ${stage.y} ${stage.z + dz + 0.5} facing entity ${CAM} eyes`);
  }
}

async function setLives(map) {
  for (const [n, l] of Object.entries(map)) await cmd(`croque lives ${n} set ${l}`);
}

async function slotOf(itemId) {
  const out = await cmd(`data get entity ${CAM} Inventory`);
  const re = new RegExp(`\\{Slot: (\\d+)b, id: "minecraft:${itemId}"`);
  const m = out.match(re);
  return m ? Number(m[1]) : -1;
}

// ------------------------------------------------------------------ séance
/** Config de tournage : durées longues, entracte réglable (évite qu'une nouvelle annonce masque une scène). */
function setup(over = {}) {
  return applyConfig({
    'initial-seconds-override': 900, 'min-seconds-override': 900, 'announce-suspense-seconds': 4,
    'power-choice-seconds': 120, 'roulette-delay-seconds': 3, 'mob-egg-chance': 1.0,
    'shrink-delay-seconds': 2, 'shrink-duration-seconds': 25, 'intermission-seconds': 0,
    'immediate-respawn': 'true', ...over,
  });
}

const isAlive = async () => /passed/i.test(await cmd(`execute if entity @e[type=player,name=${CAM}]`));

/** La caméra peut se reconnecter morte (sauvegarde) : on force la réapparition avant toute scène. */
async function ensureAlive() {
  if (await isAlive()) return;
  await cmd('gamerule immediate_respawn true');
  await cam('close');
  await cam('respawn');
  const ok = await waitFor(isAlive, 15000, 300);
  console.log(ok ? '   (caméra réapparue)' : '   ⚠️ la caméra ne réapparaît pas');
  await sleep(2000);
}

async function main() {
  mkdirSync(SHOTS, { recursive: true });
  if (!/Joueur/.test(await cmd('list'))) throw new Error('Le client caméra « Joueur » n\'est pas connecté.');
  await ensureAlive();
  await cmd('croque stop');
  await setup();
  for (const n of NAMES) if (!bots[n]) await connectBot(n);
  for (const c of ['difficulty peaceful', 'time set 6000', 'weather clear', 'gamerule advance_time false',
    'gamerule advance_weather false', 'gamerule spawn_mobs false', 'gamerule show_death_messages true',
    `effect clear ${CAM}`, `clear ${CAM}`]) await cmd(c);
  await cam('cam:first'); await cam('tab:off'); await cam('close'); await cam('hud:on');
  // Anciennes tables empilées par les tests précédents.
  await cmd(`fill ${CX} 60 ${CZ} ${CX} 299 ${CZ} minecraft:air replace minecraft:enchanting_table`);

  // ---------------------------------------------------------------- 1. Début, couleurs, tab
  await clearChat();
  await cmd('croque start');
  await sleep(2500);
  await buildStage();
  await setLives({ [CAM]: 5, Alice: 5, Bruno: 4, Chloe: 3, David: 2, Emma: 1 });
  await camAt(0, -6, 0, 8);
  await lineUp(NAMES);
  await sleep(1500);
  await shot('01_debut_partie', 'Lancement : taille de carte, manche 1 sans Croquemitaine');
  await clearChat();
  await camAt(0, -4.5, 0, 12);
  await sleep(800);
  await shot('02_pseudos_couleurs', 'Couleur des pseudos selon les vies (5 → 1)');
  await cam('tab:on'); await sleep(700);
  await shot('03_tab_vies', 'Tab list : couleurs + nombre de vies');
  await cam('tab:off');

  // ---------------------------------------------------------------- 2. Mort, réapparition, catégories
  await clearChat();
  await cmd('gamerule immediate_respawn false');
  await cmd(`kill ${CAM}`);
  await sleep(1800);
  await shot('04_ecran_mort', 'Mort : toute mort coûte une vie');
  await cmd('gamerule immediate_respawn true');
  await cam('respawn');
  await waitFor(isAlive, 10000, 300);
  await sleep(3000);
  await sleep(800);
  await shot('05_reapparition', 'Réapparition à un endroit aléatoire de la carte');
  await camAt(0, -4.5, 0, 12); await lineUp(NAMES);
  await clearChat(); await setLives({ [CAM]: 3 }); await sleep(800);
  await shot('06_devenu_hostile', 'Passage en hostile (3 vies)');
  await clearChat(); await setLives({ [CAM]: 1 }); await sleep(800);
  await cam('tab:on'); await sleep(600);
  await shot('07_devenu_assassin', 'Passage en assassin (1 vie) — pseudo rouge');
  await cam('tab:off');
  await clearChat();
  await setLives({ Emma: 0, David: 0 }); await sleep(400);
  await setLives({ [CAM]: 0 }); await sleep(1500);
  // Éliminé (mode par défaut) : boîte de barrières au-dessus de la carte.
  const bp = await posOf(CAM);
  await cmd(`tp ${CAM} ${bp[0]} ${bp[1]} ${bp[2] - 3} 0 15`);
  await cmd(`tp Emma ${bp[0] - 1.5} ${bp[1]} ${bp[2] + 1} facing entity ${CAM} eyes`);
  await cmd(`tp David ${bp[0] + 1.5} ${bp[1]} ${bp[2] + 1} facing entity ${CAM} eyes`);
  await sleep(1200);
  await shot('08_boite_elimination', 'Éliminé : boîte au-dessus de la carte');
  await clearChat();
  await cmd(`execute as ${CAM} run death-history`); await sleep(1200);
  await shot('09_death_history', '/death-history : historique de ses morts');
  for (const n of [CAM, 'Emma', 'David']) await cmd(`croque revive ${n}`);
  await setLives({ [CAM]: 5, Emma: 1, David: 2 });
  await sleep(800);

  await ensureAlive();
  // ---------------------------------------------------------------- 3. Annonce vue par un non-Croque
  await camAt(0, -4.5, 0, 12); await lineUp(NAMES);
  await clearChat();
  await cmd('croque force Bruno');
  await sleep(1500);
  await shot('10_annonce_suspense', 'Annonce : suspense identique pour tout le monde');
  await sleep(3200);
  await shot('11_pas_le_croque', '« Tu n\'es pas le Croquemitaine »');

  await ensureAlive();
  // ---------------------------------------------------------------- 4. Vue du Croquemitaine
  await sleep(2500);
  await clearChat();
  await cmd(`croque force ${CAM}`);
  await sleep(5000);
  await shot('12_tu_es_le_croque', '« Tu es le Croquemitaine »');
  await sleep(3500);
  await shot('13_roulette_pouvoirs', 'Roulette : choisir 1 pouvoir parmi 2');
  for (const p of ['reveal', 'blind', 'tracker']) { await cmd(`croque givepower ${p}`); await sleep(300); }
  await sleep(1200);
  await shot('14_chrono_et_pouvoirs', 'Chrono secret (bossbar) + items de pouvoirs');
  await cam('inv:open'); await sleep(800);
  await shot('15_inventaire_pouvoirs', 'Items de pouvoir : non jetables, retirés en fin de mission');
  await cam('close');
  // Révélation : particules au-dessus de la cible la plus proche, visibles par le Croque seul.
  await cmd(`tp Alice ${stage.x + 0.5} ${stage.y} ${stage.z + 5.5} facing entity ${CAM} eyes`);
  for (const n of ['Bruno', 'Chloe', 'David', 'Emma']) await cmd(`tp ${n} ${stage.x + 9} ${stage.y} ${stage.z - 9}`);
  await camAt(0, -2, 0, 0);
  await cam('select:spyglass'); await sleep(300); await cam('use');
  await sleep(2000);
  await shot('16_revelation', 'Pouvoir Révélation : la cible est signalée au Croque seul');
  await setup({ 'intermission-seconds': 6 });
  await clearChat();
  await cmd(`tp Alice ${stage.x + 0.5} ${stage.y} ${stage.z + 1.5} facing entity ${CAM} eyes`);
  await sleep(500);
  await cmd(`damage Alice 100 minecraft:player_attack by ${CAM}`);
  await sleep(1500);
  await shot('17_mission_reussie', 'Kill réussi : mission accomplie, la manche se termine');
  await setup();

  await ensureAlive();
  // ---------------------------------------------------------------- 5. Victime d'un Croque
  await sleep(14000); // fin de l'entracte et de l'annonce de la nouvelle manche
  await camAt(0, -4.5, 0, 5); await lineUp(['Bruno']);
  await cmd('croque force Bruno');
  await sleep(9500);
  await cmd('croque givepower blind');
  await sleep(800);
  const b = bots.Bruno;
  const sac = b.inventory.items().find((i) => i.name === 'ink_sac');
  if (sac) { await b.equip(sac, 'hand'); b.activateItem(); }
  await sleep(1200);
  await shot('18_aveugle_par_le_croque', 'Victime du pouvoir Aveuglement');
  await cmd(`effect clear ${CAM}`);

  await ensureAlive();
  // ---------------------------------------------------------------- 6. Échec au chrono + héritage
  await setup({ 'initial-seconds-override': 20, 'min-seconds-override': 20, 'announce-suspense-seconds': 2 });
  await cmd('croque skip');
  await sleep(500);
  await cmd(`croque force ${CAM}`);
  // Entracte après l'expiration : aucune nouvelle annonce ne vient masquer le message d'échec.
  await setup({ 'initial-seconds-override': 20, 'min-seconds-override': 20, 'announce-suspense-seconds': 2, 'intermission-seconds': 15 });
  await sleep(6000);
  await cmd('croque givepower shell');
  await clearChat();
  await waitFor(async () => !(await cmd('croque status')).includes(`Croque : ${CAM}`), 30000, 300);
  await sleep(800);
  await shot('19_temps_ecoule', 'Chrono écoulé : le Croque perd une vie');
  await setup();
  await cmd('croque skip');
  await sleep(500);
  await cmd(`croque force ${CAM}`);
  await sleep(10000); // titre terminé, roulette ouverte
  await cmd('croque givepower predator');
  await sleep(800);
  await clearChat();
  await cmd(`execute as ${CAM} run croque status`); await sleep(1200);
  await shot('20_heritage_status', 'Admin /croque status : pouvoirs hérités + nouveau');
  // Plus de Croque pour la suite : longue entracte.
  await setup({ 'intermission-seconds': 900 });
  await cmd('croque skip');
  await sleep(1500);

  await ensureAlive();
  // ---------------------------------------------------------------- 7. Règles spéciales
  await clearChat();
  const [tx, ty, tz] = gameJson().enchantingTable;
  await cmd(`setblock ${tx} ${ty - 1} ${tz - 3} minecraft:grass_block`);
  await cmd(`fill ${tx} ${ty} ${tz - 3} ${tx} ${ty + 1} ${tz - 2} minecraft:air`);
  await cmd(`tp ${CAM} ${tx + 0.5} ${ty} ${tz - 2.5} facing ${tx + 0.5} ${ty + 0.4} ${tz + 0.5}`);
  await sleep(1500);
  await shot('21_table_enchantement', 'Unique table d\'enchantement, au centre, incassable');
  await camAt(0, -4.5, 0, 10);
  await clearChat();
  await cmd(`item replace entity ${CAM} armor.head with minecraft:iron_helmet`);
  await sleep(3000);
  await shot('22_casque_interdit', 'Casque porté : Poison II tant qu\'il est sur la tête');
  await cmd(`item replace entity ${CAM} armor.head with minecraft:air`);
  await cmd(`effect clear ${CAM}`);
  await clearChat();
  await cmd('difficulty easy'); // en paisible, un zombie invoqué disparaît aussitôt
  await cmd(`tp ${CAM} ${stage.x + 0.5} ${stage.y} ${stage.z - 6.5} 0 35`);
  await cmd(`summon zombie ${stage.x + 0.5} ${stage.y} ${stage.z - 4} {NoAI:1b,Tags:["guide"]}`);
  await sleep(300);
  await cmd(`execute as @e[type=zombie,tag=guide] run damage @s 100 minecraft:player_attack by ${CAM}`);
  await sleep(1500);
  await shot('23_oeuf_de_mob', 'Mob hostile tué : chance de lâcher l\'œuf d\'un autre mob');
  await cmd('kill @e[type=item]');
  await cmd('difficulty peaceful');

  await ensureAlive();
  // ---------------------------------------------------------------- 8. Fin de partie
  await clearChat();
  await cmd('croque border 50');
  await camAt(0, -4.5, 0, 5); await lineUp(['Alice']);
  await setLives({ Bruno: 0, Chloe: 0, David: 0, Emma: 0, Alice: 1, [CAM]: 1 });
  await sleep(9000);
  await cmd(`spreadplayers ${CX + 5} ${CZ} 0 1 false ${CAM}`);
  await cmd(`tp ${CAM} ~ ~ ~ -90 5`);
  await sleep(1500);
  await shot('24_assassins_seuls', 'Plus que des assassins : la bordure se referme');
  await clearChat();
  await setLives({ Alice: 0 });
  await sleep(2000);
  await shot('25_victoire', 'Dernier survivant : victoire');

  await cmd('croque stop');
  await setup();
  for (const c of ['gamerule immediate_respawn false', 'gamerule advance_time true', 'gamerule advance_weather true', 'gamerule spawn_mobs true']) await cmd(c);
  console.log(`\n${done.length} captures dans ${SHOTS}`);
}

try {
  await main();
} catch (e) {
  console.error('Échec :', e);
  process.exitCode = 1;
} finally {
  for (const n of Object.keys(bots)) await disconnectBot(n);
  await closeRcon();
  process.exit(process.exitCode ?? 0);
}
