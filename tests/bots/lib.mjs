// Outils communs des scénarios : contrôle du serveur local, RCON, bots Mineflayer, assertions.
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, copyFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mineflayer from 'mineflayer';
import { rcon as rconConnect, strip } from './rcon.mjs';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const RUN = path.join(ROOT, 'run');
export const LOG = path.join(RUN, 'logs/latest.log');
export const PLUGIN_DIR = path.join(RUN, 'plugins/Croquemitaine');
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Centre de carte des tests : le seed de test a un océan en (0,0), on joue sur la terre ferme.
export const CX = -180, CZ = 350;
/** Les plateformes de test sont décalées du centre pour ne jamais toucher la table d'enchantement. */
const PZ = 40;
/** Coordonnées relatives à la zone de test → chaîne « x y z » pour les commandes. */
export const at = (x, y, z) => `${CX + x} ${y} ${CZ + PZ + z}`;

// ------------------------------------------------------------------ résultats
export const results = [];
export function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail });
  console.log(`${ok ? '  ✅' : '  ❌'} ${name}${detail ? ` — ${detail}` : ''}`);
  return !!ok;
}
export function section(title) {
  console.log(`\n=== ${title} ===`);
}

// ------------------------------------------------------------------ serveur
let serverProc = null;

function javaHome() {
  return execFileSync('/usr/libexec/java_home', ['-v', '25']).toString().trim();
}

export async function startServer({ withVoice = false } = {}) {
  const args = ['runServer', '--console=plain', '-q'];
  if (withVoice) args.push('-PwithVoice');
  const startedAt = Date.now();
  serverProc = spawn('./gradlew', args, {
    cwd: ROOT, env: { ...process.env, JAVA_HOME: javaHome() }, stdio: ['pipe', 'ignore', 'ignore'],
  });
  for (let i = 0; i < 240; i++) {
    await sleep(1000);
    try {
      const st = (await import('node:fs')).statSync(LOG);
      if (st.mtimeMs >= startedAt && /Done \([\d.,]+s\)!/.test(readFileSync(LOG, 'utf8'))) {
        await sleep(1500);
        return;
      }
    } catch { /* log pas encore créé */ }
  }
  throw new Error('Le serveur ne démarre pas (timeout)');
}

/** Problèmes relevés dans les logs des serveurs précédents de ce run (le log tourne à chaque redémarrage). */
const earlierProblems = [];

export async function stopServer() {
  earlierProblems.push(...logProblems());
  await closeRcon();
  try {
    const r = await rconConnect();
    await r.send('stop');
    await r.end();
  } catch { /* déjà arrêté */ }
  if (serverProc) {
    await new Promise((res) => {
      const t = setTimeout(res, 90_000);
      serverProc.on('exit', () => { clearTimeout(t); res(); });
    });
    serverProc = null;
  }
}

/** Erreurs du log liées au plugin (ou exceptions en général). */
export function allLogProblems() {
  return [...earlierProblems, ...logProblems()];
}

export function logProblems() {
  if (!existsSync(LOG)) return [];
  return readFileSync(LOG, 'utf8').split('\n').filter((l) =>
    /ERROR|Exception|\tat [a-z]/.test(l) && !/Could not pass event .* to (ViaVersion|ViaBackwards)/.test(l));
}

// ------------------------------------------------------------------ RCON
let rc = null;
export async function cmd(c) {
  if (!rc) rc = await rconConnect();
  return strip(await rc.send(c));
}
export async function closeRcon() {
  if (rc) { try { await rc.end(); } catch { /* */ } rc = null; }
}

// ------------------------------------------------------------------ config de test
export function writeConfig(overrides) {
  const file = path.join(PLUGIN_DIR, 'config.yml');
  let text = readFileSync(path.join(ROOT, 'src/main/resources/config.yml'), 'utf8');
  for (const [key, value] of Object.entries(overrides)) {
    // « parent.enfant » : première occurrence de enfant après la ligne parent (clés homonymes).
    const [parent, leaf] = key.includes('.') ? key.split('.') : [null, key];
    const start = parent ? text.search(new RegExp(`^${parent}:`, 'm')) : 0;
    if (start < 0) throw new Error(`section de config inconnue: ${parent}`);
    const re = new RegExp(`^(\\s*${leaf}:)\\s*[^#\\n]*`, 'm');
    const tail = text.slice(start);
    if (!re.test(tail)) throw new Error(`clé de config inconnue: ${key}`);
    text = text.slice(0, start) + tail.replace(re, `$1 ${value} `);
  }
  writeFileSync(file, text);
}
export const BASE_CONFIG = {
  'initial-seconds-override': 600, 'decrement-seconds-override': 0, 'min-seconds-override': 600,
  'announce-suspense-seconds': 1, 'power-choice-seconds': 2, 'mob-egg-chance': 0.25,
  'shrink-delay-seconds': 60, 'mode': 'choice', 'inherit': 'true', x: CX, z: CZ,
  'roulette-delay-seconds': 0, 'intermission-seconds': 0, 'immediate-respawn': 'false',
  'elimination.mode': 'box', 'private-chat': 'true', 'enforce': 'false', 'recovery-compass': 'false', 'helmet-mode': 'poison', 'phantoms': 'false', 'mob-egg-owner': 'false', 'death-history-coordinates': 'false',
};
export async function applyConfig(over = {}) {
  writeConfig({ ...BASE_CONFIG, ...over });
  await cmd('croque reload');
}

export function gameJson() {
  return JSON.parse(readFileSync(path.join(PLUGIN_DIR, 'game.json'), 'utf8'));
}

// ------------------------------------------------------------------ bots
export const bots = {};

export function connectBot(name, version = '26.1') {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version, auth: 'offline' });
    // Paper 26.3 exige un client_tick_end après chaque paquet de mouvement, que Mineflayer 4.39
    // n'envoie pas (PrismarineJS/mineflayer#4189) : sans ce correctif, kick « invalid_player_movement ».
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (packet, params) => {
      write(packet, params);
      if (packet === 'position' || packet === 'position_look' || packet === 'look' || packet === 'flying') write('tick_end', {});
    };
    bot.titles = [];
    bot.messages = [];
    bot.barCount = 0;
    bot.on('title', (t) => bot.titles.push(typeof t === 'string' ? t : JSON.stringify(t)));
    bot.on('messagestr', (m) => bot.messages.push(m));
    bot.on('bossBarCreated', () => { bot.barCount++; });
    bot.on('error', (e) => console.log(`[${name}] erreur: ${e.message}`));
    bot.on('kicked', (r) => console.log(`[${name}] kick: ${JSON.stringify(r)}`));
    const t = setTimeout(() => reject(new Error(`${name}: connexion timeout`)), 30_000);
    bot.once('spawn', () => { clearTimeout(t); bots[name] = bot; setTimeout(() => resolve(bot), 1500); });
  });
}

export async function disconnectBot(name) {
  const b = bots[name];
  if (!b) return;
  await new Promise((res) => { b.once('end', res); b.quit(); setTimeout(res, 3000); });
  delete bots[name];
  // Attendre que le SERVEUR ait traité la déconnexion (sinon les commandes RCON suivantes la devancent).
  await waitFor(async () => !new RegExp(`\\b${name}\\b`).test((await cmd('list')).split(':').slice(1).join(':')), 5000);
}

export async function disconnectAll() {
  for (const n of Object.keys(bots)) await disconnectBot(n);
}

/** Attend qu'une condition devienne vraie (polling). */
export async function waitFor(fn, timeoutMs = 10_000, step = 250) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    try { if (await fn()) return true; } catch { /* */ }
    await sleep(step);
  }
  return false;
}

// ------------------------------------------------------------------ helpers de jeu
export async function status() {
  return cmd('croque status');
}

/** Vies d'un joueur lues dans /croque status. */
export async function livesOf(name) {
  const s = await status();
  const m = s.match(new RegExp(`- ${name} : (\\d+) vies`));
  return m ? Number(m[1]) : null;
}

export async function croqueName() {
  const m = (await status()).match(/Croque : (\S+) \(pouvoir/);
  return m ? m[1] : null;
}

export async function roundInfo() {
  const m = (await status()).match(/Statut : (\w+), manche (\d+), chrono (\d+):(\d+)/);
  return m ? { phase: m[1], round: Number(m[2]), seconds: Number(m[3]) * 60 + Number(m[4]) } : null;
}

export async function inTeam(team, name) {
  const out = await cmd(`team list ${team}`);
  return new RegExp(`\\b${name}\\b`).test(out.split(':').slice(1).join(':'));
}

export async function hasEffect(name, effect) {
  const out = await cmd(`execute if entity @a[name=${name},nbt={active_effects:[{id:"minecraft:${effect}"}]}]`);
  return /passed/i.test(out);
}

/** UUID offline -> tableau d'entiers NBT. */
export function uuidInts(uuid) {
  const hex = uuid.replace(/-/g, '');
  return [0, 8, 16, 24].map((i) => (parseInt(hex.slice(i, i + 8), 16) | 0));
}

export async function freshGame(names, over = {}) {
  await cmd('croque stop');
  await applyConfig(over);
  for (const n of Object.keys(bots)) if (!names.includes(n)) await disconnectBot(n);
  for (const n of names) if (!bots[n]) await connectBot(n);
  // Facile plutôt que paisible : en paisible la vie remonte en < 1 s et fausse les mesures de dégâts.
  await cmd('difficulty easy');
  await cmd('gamerule spawn_mobs false'); // pas de mobs naturels parasites (serveur de test uniquement)
  await cmd('kill @e[type=!player]');
  await cmd('time set day');
  for (const n of names) bots[n].titles.length = 0, bots[n].messages.length = 0, bots[n].barCount = 0;
  const out = await cmd('croque start');
  await sleep(1500);
  return out;
}

/** Place deux joueurs à une distance donnée sur l'axe X, sur une plateforme dégagée en hauteur. */
export async function placeApart(a, b, distance, y = 200) {
  await cmd(`fill ${at(-5, y - 1, -5)} ${at(distance + 5, y - 1, 5)} minecraft:stone`);
  await cmd(`fill ${at(-5, y, -5)} ${at(distance + 5, y + 4, 5)} minecraft:air`);
  await cmd(`tp ${a} ${at(0.5, y, 0.5)} -90 0`);
  await cmd(`tp ${b} ${at(distance + 0.5, y, 0.5)} 90 0`);
  await sleep(600);
}

/** Vie lue côté serveur (plus fiable que celle reçue par le bot). */
export async function health(name) {
  const m = (await cmd(`data get entity ${name} Health`)).match(/(-?[\d.]+)f/);
  return m ? Number(m[1]) : NaN;
}

export async function heal(name) {
  await cmd(`effect clear ${name}`);
  await cmd(`effect give ${name} minecraft:instant_health 1 5`);
  await sleep(700); // laisse passer les ticks d'invulnérabilité éventuels
}
