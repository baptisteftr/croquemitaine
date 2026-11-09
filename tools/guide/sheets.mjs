// Planches contact pour relire les captures rapidement.
import puppeteer from 'puppeteer';
import { readdirSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
const dir = path.resolve('shots');
const files = readdirSync(dir).filter((f) => f.endsWith('.jpg')).sort();
const browser = await puppeteer.launch({ headless: 'shell' });
const page = await browser.newPage();
await page.setViewport({ width: 1600, height: 1000 });
for (let i = 0; i < files.length; i += 6) {
  const cells = files.slice(i, i + 6).map((f) => `<figure><img src="shots/${f}"><figcaption>${f}</figcaption></figure>`).join('');
  writeFileSync('sheet.html', `<style>body{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:4px;background:#222;font:16px sans-serif;color:#fff}figure{margin:0}img{width:100%;display:block}figcaption{padding:2px 6px}</style>${cells}`);
  await page.goto(pathToFileURL(path.resolve('sheet.html')).href, { waitUntil: 'load' });
  await page.screenshot({ path: `sheet_${i / 6 + 1}.png`, fullPage: true });
}
await browser.close();
