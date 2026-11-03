import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const FRONTEND = join(import.meta.dirname, '..');
const html = readFileSync(join(FRONTEND, 'index.html'), 'utf8');
const meta = (attr, name) => html.match(new RegExp(`<meta ${attr}="${name}" content="([^"]*)"`))?.[1];

/** What WhatsApp, Slack, LinkedIn etc. show when someone shares the demo link. */
describe('Link preview', () => {
  it('describes the live demo, not the old back-office text', () => {
    expect(html).toMatch(/<title>Library Dashboard · live demo<\/title>/);
    expect(meta('name', 'description')).toMatch(/try it as a librarian or a member, no sign-up needed/i);
    expect(meta('name', 'description')).not.toMatch(/back-office/i);
    expect(meta('property', 'og:title')).toBe('Library Dashboard · live demo');
    expect(meta('property', 'og:description')).toBe(meta('name', 'description'));
    expect(meta('property', 'og:type')).toBe('website');
    expect(meta('property', 'og:locale')).toBe('en_US');
    expect(meta('property', 'og:locale:alternate')).toBe('id_ID');
  });

  it('points to a large preview image at the public address (filled in at build time)', () => {
    expect(meta('property', 'og:url')).toBe('%VITE_PUBLIC_URL%/');
    expect(meta('property', 'og:image')).toBe('%VITE_PUBLIC_URL%/og-image.png');
    expect(meta('property', 'og:image:width')).toBe('1200');
    expect(meta('property', 'og:image:height')).toBe('630');
    expect(meta('property', 'og:image:alt')).toBeTruthy();
    expect(meta('name', 'twitter:card')).toBe('summary_large_image');
    expect(readFileSync(join(FRONTEND, '.env.production'), 'utf8')).toMatch(/^VITE_PUBLIC_URL=https:\/\/\S+[^/]$/m);
  });

  it('has a 1200 x 630 PNG image small enough for WhatsApp (under 300 KB)', () => {
    const file = join(FRONTEND, 'public', 'og-image.png');
    expect(existsSync(file)).toBe(true);
    const png = readFileSync(file);
    expect(png.subarray(1, 4).toString()).toBe('PNG');
    expect(png.readUInt32BE(16)).toBe(1200);
    expect(png.readUInt32BE(20)).toBe(630);
    expect(statSync(file).size).toBeLessThan(300 * 1024);
  });
});
