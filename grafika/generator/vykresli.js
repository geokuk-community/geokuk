// Vykreslí SVG do PNG v přesné velikosti (Chromium přes Playwright).
// node vykresli.js <zdroje> <cil>
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const [zdroje, cil] = process.argv.slice(2);
const ulohy = [
  ...[16, 24, 32, 48, 64, 128, 256].map(s => [`ikona-${s}.svg`, s, `ikona-${s}.png`]),
  ['logo-zelene.svg', 400, 'logo-zelene-400.png'],
  ['splash.svg', 400, 'splash-400.png'],
];
(async () => {
  fs.mkdirSync(cil, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const [svg, s, out] of ulohy) {
    await p.setViewportSize({ width: s, height: s });
    const d = 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(zdroje, svg)).toString('base64');
    await p.setContent(`<html><body style="margin:0;background:transparent"><img src="${d}" width="${s}" height="${s}" style="display:block"></body></html>`);
    await p.waitForFunction(() => document.images[0].complete);
    await p.screenshot({ path: path.join(cil, out), omitBackground: true, clip: { x: 0, y: 0, width: s, height: s } });
  }
  await b.close();
})();
