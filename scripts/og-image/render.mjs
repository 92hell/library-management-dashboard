// Renders og-image.html to frontend/public/og-image.png (1200 x 630), the link preview image.
// Uses the e2e project's Playwright: cd e2e && npm ci && npx playwright install chromium, then
//   node scripts/og-image/render.mjs
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(new URL('../../e2e/package.json', import.meta.url));
const { chromium } = require('@playwright/test');

const source = new URL('./og-image.html', import.meta.url).href;
const target = fileURLToPath(new URL('../../frontend/public/og-image.png', import.meta.url));

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 630 } });
await page.goto(source);
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: target, type: 'png' });
await browser.close();
console.log(`wrote ${target}`);
