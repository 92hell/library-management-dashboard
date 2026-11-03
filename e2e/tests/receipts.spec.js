import { expect, test } from '@playwright/test';
import { apiAs, createBook, LIBRARIAN, signIn, unique } from './support.js';

test('a librarian prints a signed receipt and anyone can check it with the QR link', async ({ page, browser, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Ronggeng ${unique()}`;
  const book = await createBook(librarianApi, title);
  const member = await librarianApi.post('/api/members', {
    name: 'Siti Rahma', dateOfBirth: '1990-01-01', email: `siti-${unique()}@example.com`, phone: '081200000003',
  });
  const loan = await librarianApi.post('/api/borrowed-books', { bookId: book.id, memberId: member.id });
  await librarianApi.post(`/api/borrowed-books/${loan.id}/return`);

  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await page.goto('/borrowed-books?status=returned');
  await page.locator('tr', { hasText: title }).getByRole('button', { name: 'Receipt' }).click();
  await expect(page.getByRole('heading', { name: 'Loan receipt' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'History' })).toContainText('Lent at the desk');
  await expect(page.getByRole('table', { name: 'History' })).toContainText('Book returned');
  await expect(page.getByRole('img', { name: /QR code/ })).toBeVisible();
  const number = (await page.locator('.receipt .font-monospace').first().innerText()).trim();
  expect(number).toMatch(/^R-\d{4}-\d{6}$/);

  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('link', { name: 'Download PDF' }).click()]);
  expect(download.suggestedFilename()).toBe(`receipt-${number}.pdf`);
  const pdf = await (await import('node:fs/promises')).readFile(await download.path());
  expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');

  // The QR link works for someone who is not signed in, and masks the member's name.
  const verifyUrl = (await librarianApi.post(`/api/borrowed-books/${loan.id}/receipt`)).verifyUrl;
  const stranger = await (await browser.newContext()).newPage();
  await stranger.goto(new URL(verifyUrl).pathname + new URL(verifyUrl).search);
  await expect(stranger.getByRole('status').filter({ hasText: 'Genuine receipt' })).toBeVisible();
  await expect(stranger.getByText('S**i R***a')).toBeVisible();
  await expect(stranger.getByText('Siti Rahma')).toHaveCount(0);

  await librarianApi.dispose();
});
