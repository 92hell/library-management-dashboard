// Renders frontend/public/favicon.svg into the PNG icons and favicon.ico (16/32/48 px PNGs in an ICO).
// Uses the e2e project's Playwright: cd e2e && npm ci && npx playwright install chromium, then
//   node scripts/icons/render.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(new URL('../../e2e/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const out = (name) => fileURLToPath(new URL(`../../frontend/public/${name}`, import.meta.url));
const svg = readFileSync(out('favicon.svg'), 'utf8');

const browser = await chromium.launch();
async function png(size, { padded = false } = {}) {
  const page = await browser.newPage({ viewport: { width: size, height: size } });
  // Touch icons get square corners and a little margin (iOS rounds and masks them itself).
  const body = padded
    ? `<div style="width:${size}px;height:${size}px;background:#1f3d34;display:grid;place-items:center">
         <div style="width:${size * 0.86}px;height:${size * 0.86}px">${svg.replace('rx="14"', 'rx="0"')}</div></div>`
    : svg;
  await page.setContent(`<style>html,body{margin:0;background:transparent}svg{width:100%;height:100%;display:block}</style>${body}`);
  const buffer = await page.screenshot({ type: 'png', omitBackground: true });
  await page.close();
  return buffer;
}

writeFileSync(out('apple-touch-icon.png'), await png(180, { padded: true }));
writeFileSync(out('icon-192.png'), await png(192));
writeFileSync(out('icon-512.png'), await png(512));

// ICO: header, one 16-byte directory entry per image, then the PNG images themselves.
const images = [16, 32, 48];
const pngs = [];
for (const size of images) pngs.push(await png(size));
const header = Buffer.alloc(6 + images.length * 16);
header.writeUInt16LE(0, 0);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(images.length, 4);
let offset = header.length;
images.forEach((size, i) => {
  const entry = 6 + i * 16;
  header.writeUInt8(size, entry);
  header.writeUInt8(size, entry + 1);
  header.writeUInt16LE(1, entry + 4); // colour planes
  header.writeUInt16LE(32, entry + 6); // bits per pixel
  header.writeUInt32LE(pngs[i].length, entry + 8);
  header.writeUInt32LE(offset, entry + 12);
  offset += pngs[i].length;
});
writeFileSync(out('favicon.ico'), Buffer.concat([header, ...pngs]));
await browser.close();
console.log('wrote favicon.ico, apple-touch-icon.png, icon-192.png, icon-512.png');
