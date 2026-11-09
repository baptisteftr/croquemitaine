// Génère docs/Guide-Croquemitaine.pdf à partir de guide.html (captures dans shots/).
import puppeteer from 'puppeteer';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
const out = path.resolve('../../docs/Guide-Croquemitaine.pdf');
const browser = await puppeteer.launch({ headless: 'shell' });
const page = await browser.newPage();
await page.goto(pathToFileURL(path.resolve('guide.html')).href, { waitUntil: 'load' });
await page.pdf({
  path: out, format: 'A4', printBackground: true, preferCSSPageSize: true,
  displayHeaderFooter: true, headerTemplate: '<span></span>',
  footerTemplate: '<div style="width:100%;font:8px sans-serif;color:#999;text-align:center">Le Croquemitaine — Guide du joueur · <span class="pageNumber"></span>/<span class="totalPages"></span></div>',
});
await browser.close();
console.log(out);
