// Bots « figurants » pour tester à la main avec son propre client : ils restent connectés,
// se promènent un peu et réapparaissent automatiquement. Usage : node companions.mjs [nombre]
import { connectBot, sleep } from './lib.mjs';

const count = Number(process.argv[2] ?? 3);
const names = Array.from({ length: count }, (_, i) => `Bot${i + 1}`);

async function keepAlive(name) {
  for (;;) {
    try {
      const bot = await connectBot(name);
      console.log(`${name} connecté`);
      const wander = setInterval(() => {
        if (!bot.entity) return;
        bot.setControlState('forward', Math.random() < 0.5);
        bot.look(Math.random() * Math.PI * 2, 0, false).catch(() => {});
      }, 3000);
      await new Promise((res) => bot.once('end', res));
      clearInterval(wander);
      console.log(`${name} déconnecté, reconnexion dans 5 s`);
    } catch (e) {
      console.log(`${name} : ${e.message}`);
    }
    await sleep(5000);
  }
}

for (const n of names) { keepAlive(n); await sleep(1500); }
