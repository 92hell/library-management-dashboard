import { expect, test } from '@playwright/test';
import { apiAs, createBook, LIBRARIAN, PASSWORD, registerCustomer, signIn, unique } from './support.js';

test('the catalogue filters by author and category, and the address keeps the filters', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const authorName = `Dee ${unique()}`;
  const author = await librarianApi.post('/api/authors', { name: authorName });
  await createBook(librarianApi, `Poems ${unique()}`, { author, categories: ['Poetry'] });
  const novel = `Novel ${unique()}`;
  await createBook(librarianApi, novel, { author, categories: ['Science Fiction'] });
  const customer = await registerCustomer(baseURL);

  await signIn(page, customer.username, PASSWORD);
  await page.goto('/catalogue');
  await page.getByLabel('Author').selectOption({ label: authorName });
  await expect(page.locator('tbody tr')).toHaveCount(2);
  await page.getByLabel('Category').selectOption('Science Fiction');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.locator('tbody')).toContainText(novel);
  expect(new URL(page.url()).searchParams.get('category')).toBe('Science Fiction');

  await page.reload();
  await expect(page.getByLabel('Category')).toHaveValue('Science Fiction');
  await expect(page.locator('tbody tr')).toHaveCount(1);

  await librarianApi.dispose();
});

test('visitors must sign in, and customers cannot open staff pages', async ({ page, baseURL }) => {
  await page.goto('/books');
  await expect(page).toHaveURL(/\/login$/);

  const customer = await registerCustomer(baseURL);
  await signIn(page, customer.username, PASSWORD);
  await page.goto('/fines');
  await expect(page).not.toHaveURL(/\/fines$/);
  await expect(page.getByRole('link', { name: 'Fines' })).toHaveCount(0);
  expect((await page.request.get('/api/fines')).status()).toBe(403);
});

test('a shared link shows a preview: description and image for anonymous visitors', async ({ request }) => {
  const page = await (await request.get('/')).text();
  expect(page).toContain('<meta property="og:title" content="Library Dashboard · live demo"');
  expect(page).toMatch(/<meta property="og:image" content="https:\/\/[^"%]+\/og-image\.png"/);

  const image = await request.get('/og-image.png');
  expect(image.status()).toBe(200);
  expect(image.headers()['content-type']).toBe('image/png');
});

test('the library icon is served for tabs, bookmarks and phone home screens', async ({ request }) => {
  for (const [path, type] of [['/favicon.svg', 'image/svg+xml'], ['/favicon.ico', 'image/x-icon'],
    ['/apple-touch-icon.png', 'image/png'], ['/icon-512.png', 'image/png']]) {
    const response = await request.get(path);
    expect(response.status(), path).toBe(200);
    expect(response.headers()['content-type'], path).toContain(type);
  }
  expect((await request.get('/site.webmanifest')).status()).toBe(200);
});

