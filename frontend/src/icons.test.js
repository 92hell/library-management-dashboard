import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const FRONTEND = join(import.meta.dirname, '..');
const PUBLIC = join(FRONTEND, 'public');
const html = readFileSync(join(FRONTEND, 'index.html'), 'utf8');
const pngSize = (file) => {
  const png = readFileSync(join(PUBLIC, file));
  expect(png.subarray(1, 4).toString()).toBe('PNG');
  return [png.readUInt32BE(16), png.readUInt32BE(20)];
};

/** The library icon in browser tabs, bookmarks and phone home screens (replacing the template's React logo). */
describe('Site icons', () => {
  it('are linked from the page, SVG first with ICO and touch-icon fallbacks', () => {
    expect(html).toContain('<link rel="icon" href="/favicon.svg" type="image/svg+xml" />');
    expect(html).toContain('<link rel="icon" href="/favicon.ico" sizes="16x16 32x32 48x48" />');
    expect(html).toContain('<link rel="apple-touch-icon" href="/apple-touch-icon.png" />');
    expect(html).toContain('<link rel="manifest" href="/site.webmanifest" />');
    expect(html).toContain('<meta name="theme-color" content="#1f3d34" />');
  });

  it('draws the open book of the app logo on the library green', () => {
    const svg = readFileSync(join(PUBLIC, 'favicon.svg'), 'utf8');
    expect(svg).toMatch(/^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg" viewBox="0 0 64 64"/);
    expect(svg).toContain('fill="#1f3d34"');
    expect(svg).toContain('fill="#f3e9d2"');
  });

  it('has an ICO with 16, 32 and 48 pixel images', () => {
    const ico = readFileSync(join(PUBLIC, 'favicon.ico'));
    expect(ico.readUInt16LE(2)).toBe(1); // icon
    const count = ico.readUInt16LE(4);
    const sizes = Array.from({ length: count }, (_, i) => ico.readUInt8(6 + i * 16));
    expect(sizes).toEqual([16, 32, 48]);
  });

  it('has touch and home-screen icons in the right sizes, listed in the web manifest', () => {
    expect(pngSize('apple-touch-icon.png')).toEqual([180, 180]);
    expect(pngSize('icon-192.png')).toEqual([192, 192]);
    expect(pngSize('icon-512.png')).toEqual([512, 512]);
    const manifest = JSON.parse(readFileSync(join(PUBLIC, 'site.webmanifest'), 'utf8'));
    expect(manifest.theme_color).toBe('#1f3d34');
    expect(manifest.icons.map((i) => i.sizes)).toEqual(['192x192', '512x512']);
  });
});
