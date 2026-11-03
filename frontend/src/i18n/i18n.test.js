import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import ID from './id';
import { setLanguage, translate } from '.';

const SRC = join(import.meta.dirname, '..');

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'test' || name === 'i18n' ? [] : sourceFiles(path);
    return /\.jsx?$/.test(name) && !/\.test\.jsx?$/.test(name) ? [path] : [];
  });
}

/** Every text passed to t('...'), translate('...') or msg('...') in the app's source. */
function usedTexts() {
  const texts = new Set();
  for (const file of sourceFiles(SRC)) {
    const code = readFileSync(file, 'utf8');
    for (const m of code.matchAll(/\b(?:t|translate|msg)\(\s*'((?:[^'\\]|\\.)*)'/g)) texts.add(m[1].replace(/\\'/g, "'"));
  }
  return texts;
}

const placeholders = (s) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe('Translations (English / Bahasa Indonesia)', () => {
  const used = usedTexts();

  it('has an Indonesian translation for every text the app shows', () => {
    expect([...used].filter((text) => !(text in ID))).toEqual([]);
  });

  it('has no leftover translations for texts the app no longer shows', () => {
    expect(Object.keys(ID).filter((text) => !used.has(text))).toEqual([]);
  });

  it('keeps the same {placeholders} in both languages', () => {
    expect(Object.entries(ID).filter(([en, id]) => placeholders(en).join() !== placeholders(id).join())).toEqual([]);
  });

  it('fills placeholders and falls back to English', () => {
    expect(translate('Notifications ({count} unread)', { count: 3 }, 'id')).toBe('Notifikasi (3 belum dibaca)');
    expect(translate('Notifications ({count} unread)', { count: 3 }, 'en')).toBe('Notifications (3 unread)');
    expect(translate('Not translated', undefined, 'id')).toBe('Not translated');
  });

  it('uses the agreed Indonesian wording', () => {
    const wording = {
      'Collect by {date}': 'Ambil paling lambat {date}',
      'collect by {date}': 'ambil paling lambat {date}',
      Receipt: 'Bukti pinjam',
      'This receipt does not match its signature. Do not accept it as evidence; ask the library.':
        'Bukti ini tidak cocok dengan tanda tangannya. Jangan diterima sebagai alat bukti; hubungi perpustakaan.',
      Request: 'Ajukan pinjaman',
      Requested: 'Diajukan',
      'Requested online': 'Diajukan secara daring',
      'Pending requests': 'Menunggu persetujuan',
      'Cancel the copy set aside for you for "{title}"?': 'Batalkan reservasi "{title}" yang sudah disiapkan untuk Anda?',
      Withdraw: 'Keluarkan',
      'Withdraw copy {barcode} from the collection (lost, damaged or discarded)?':
        'Keluarkan eksemplar {barcode} dari koleksi (hilang, rusak, atau dibuang)?',
      'Each copy gets its own barcode; you can add or withdraw copies later.':
        'Setiap eksemplar mendapat barkode sendiri; Anda dapat menambah atau mengeluarkan eksemplar nanti.',
      'Newest first': 'Terbitan terbaru',
      'Oldest first': 'Terbitan terlama',
      'Recently added': 'Terakhir ditambahkan',
      Borrowed: 'Tanggal pinjam',
      'Returned on': 'Tanggal kembali',
      'Due date (optional, defaults to the loan period)': 'Tanggal jatuh tempo (opsional; jika kosong, mengikuti masa pinjam standar)',
      'Register a walk-in member': 'Daftarkan anggota di meja layanan',
      'Online account': 'Akun daring',
      'My loans': 'Peminjaman saya',
      Actions: 'Tindakan',
      '{n} days late': 'terlambat {n} hari',
      '{n} day late': 'terlambat {n} hari',
      'Books with you': 'Buku yang sedang Anda pinjam',
      'Returned "{title}" from {name} ({barcode}).': '"{title}" ({barcode}) dari {name} telah dikembalikan.',
    };
    expect(Object.fromEntries(Object.keys(wording).map((en) => [en, ID[en]]))).toEqual(wording);
    // Holds are "reservasi" everywhere, and "online" is "daring".
    expect(Object.values(ID).filter((id) => /\bonline\b|disisihkan/i.test(id))).toEqual([]);
  });

  it('marks the page with the chosen language', () => {
    setLanguage('id');
    expect(document.documentElement.lang).toBe('id');
    setLanguage('en');
    expect(document.documentElement.lang).toBe('en');
  });
});
