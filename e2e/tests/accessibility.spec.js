import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { apiAs, createBook, LIBRARIAN, PASSWORD, registerCustomer, signIn, unique } from './support.js';

/** Scans the page for WCAG 2.1 A/AA problems axe can detect automatically. */
async function expectAccessible(page, name) {
  // No transitions (Bootstrap honours reduced motion), so colours are measured at rest, not mid-fade.
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.mouse.move(0, 0);
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const summary = violations.map((v) => `${v.id} (${v.impact}): ${v.help} - ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(summary, `accessibility problems on ${name}`).toEqual([]);
}

test('the sign-in page is accessible in both languages', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await expectAccessible(page, 'login (English)');
  await page.getByRole('button', { name: /Bahasa Indonesia/ }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'id');
  await expect(page.getByRole('button', { name: 'Masuk' })).toBeVisible();
  await expectAccessible(page, 'login (Bahasa Indonesia)');
});

test('customer pages are accessible', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const book = await createBook(librarianApi, `Laskar Pelangi ${unique()}`, { copies: 2 });
  const customer = await registerCustomer(baseURL);
  const customerApi = await apiAs(baseURL, customer.username, PASSWORD);
  await customerApi.post('/api/my/loans', { bookId: book.id });

  await signIn(page, customer.username, PASSWORD);
  await expectAccessible(page, 'customer home');
  for (const [path, ready] of [
    ['/catalogue', page.locator('tbody tr').first()],
    [`/catalogue/${book.id}`, page.getByRole('heading', { name: book.title })],
    ['/my/loans', page.getByText('Requested')],
    ['/profile', page.getByRole('heading', { level: 1 })],
    ['/profile/card', page.getByRole('img', { name: /Barcode of card/ })],
  ]) {
    await page.goto(path);
    await expect(ready).toBeVisible();
    await expectAccessible(page, path);
  }
  await customerApi.dispose();
  await librarianApi.dispose();
});

test('librarian pages are accessible', async ({ page }) => {
  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await expectAccessible(page, 'librarian home');
  for (const path of ['/borrowed-books', '/books', '/books/new', '/members', '/fines', '/reports', '/borrowed-books/new']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expectAccessible(page, path);
  }
});
