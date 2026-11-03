import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { apiAs, createBook, isoDaysFromToday, LIBRARIAN, PASSWORD, registerCustomer, signIn, unique } from './support.js';

/**
 * Runs in the "phone" project (Pixel 7 size). Every page must fit the screen: no sideways scrolling
 * of the page or of a table, and every button and link within reach.
 */
async function expectFitsOnPhone(page, name) {
  await page.waitForLoadState('networkidle');
  const problems = await page.evaluate(() => {
    const width = window.innerWidth;
    const out = [];
    if (document.documentElement.scrollWidth > width + 1) out.push(`page scrolls sideways (${document.documentElement.scrollWidth}px)`);
    for (const t of document.querySelectorAll('.table-responsive')) {
      if (t.scrollWidth > t.clientWidth + 1) out.push(`a table scrolls sideways (${t.scrollWidth}px in ${t.clientWidth}px)`);
    }
    for (const el of document.querySelectorAll('main button, main a, main input, main select')) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0 && (r.right > width + 1 || r.left < -1)) {
        out.push(`off screen: ${el.tagName.toLowerCase()} "${(el.textContent || el.getAttribute('aria-label') || el.name || '').trim().slice(0, 30)}"`);
      }
    }
    return out;
  });
  expect(problems, `${name} at phone width`).toEqual([]);
}

async function expectAccessible(page, name) {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), name).toEqual([]);
}

test('a member can use every page on a phone', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Cantik Itu Luka Edisi Panjang ${unique()}`;
  const book = await createBook(librarianApi, title, { copies: 2 });
  const customer = await registerCustomer(baseURL, 'Rina Wulandari Kusumawardhani');
  await librarianApi.post('/api/borrowed-books', {
    bookId: book.id, memberId: customer.memberId, borrowDate: isoDaysFromToday(-3), dueDate: isoDaysFromToday(11),
  });

  await signIn(page, customer.username, PASSWORD);
  await expectFitsOnPhone(page, 'home');
  for (const path of ['/catalogue', `/catalogue/${book.id}`, '/my/loans', '/notifications', '/profile']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expectFitsOnPhone(page, path);
  }
  // The loan's actions are reachable without scrolling sideways.
  await page.goto('/my/loans');
  const loan = page.locator('tr', { hasText: title });
  await expect(loan.getByRole('button', { name: 'Renew' })).toBeInViewport();
  await loan.getByRole('button', { name: 'Receipt' }).click();
  await expect(page.getByRole('heading', { name: 'Loan receipt' })).toBeVisible();
  await expectFitsOnPhone(page, 'receipt');
  await page.goto('/my/loans');
  await expectAccessible(page, 'my loans on a phone');

  await librarianApi.dispose();
});

test('a librarian can work the desk on a phone', async ({ page }) => {
  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await expectFitsOnPhone(page, 'librarian home');
  for (const path of ['/borrowed-books', '/borrowed-books/new', '/books', '/books/new', '/authors', '/members',
    '/members/new', '/fines', '/reports', '/users']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expectFitsOnPhone(page, path);
  }
  await page.goto('/borrowed-books');
  await expectAccessible(page, 'loans on a phone');
});
