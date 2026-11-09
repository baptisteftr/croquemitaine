// Petit client RCON : node rcon.mjs "commande" ["commande2" ...]
// Le mot de passe est lu dans run/.rcon-password (jamais affiché).
import { Rcon } from 'rcon-client';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const password = () => readFileSync(path.join(root, 'run/.rcon-password'), 'utf8').trim();

export async function rcon() {
  return Rcon.connect({ host: '127.0.0.1', port: 25575, password: password() });
}

// Retire les codes couleur § pour des sorties lisibles.
export const strip = (s) => s.replace(/§./g, '');

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const r = await rcon();
  for (const cmd of process.argv.slice(2)) console.log(`> ${cmd}\n${strip(await r.send(cmd))}`);
  await r.end();
}
