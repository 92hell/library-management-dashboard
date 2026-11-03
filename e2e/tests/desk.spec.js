import { expect, test } from '@playwright/test';
import { apiAs, createBook, isoDaysFromToday, LIBRARIAN, signIn, unique } from './support.js';

test('a late return by barcode charges a fee, which the member pays in two parts', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Cantik Itu Luka ${unique()}`;
  const book = await createBook(librarianApi, title);
  const member = await librarianApi.post('/api/members', {
    name: `Ani ${unique()}`, dateOfBirth: '1990-01-01', email: `ani-${unique()}@example.com`, phone: '081200000001',
  });
  await librarianApi.post('/api/borrowed-books', {
    bookId: book.id, memberId: member.id, borrowDate: isoDaysFromToday(-20), dueDate: isoDaysFromToday(-6),
  });
  const [copy] = await librarianApi.get(`/api/books/${book.id}/copies`);

  // The payment prompt is answered with 2,000 first, then with its default: everything still due.
  const answers = ['2000'];
  page.on('dialog', (dialog) => dialog.accept(answers.shift() ?? dialog.defaultValue()));
  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await page.getByRole('navigation').getByRole('link', { name: 'Loans' }).click();
  await page.getByLabel('Return by barcode').fill(copy.barcode);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('status').filter({ hasText: `Returned "${title}"` })).toBeVisible();

  await page.getByRole('navigation').getByRole('link', { name: 'Fines' }).click();
  const fine = page.locator('tr', { hasText: title });
  await expect(fine).toContainText('6 days late');
  await expect(fine).toContainText('IDR 6,000');
  await fine.getByRole('button', { name: 'Record payment' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Payment of IDR 2,000 recorded' }))
    .toContainText('IDR 4,000 still due.');
  await expect(fine).toContainText('IDR 2,000 paid · IDR 4,000 still due');

  await fine.getByRole('button', { name: 'Record payment' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Payment of IDR 4,000 recorded' }))
    .toContainText('The late fee is settled.');
  await expect(fine).toHaveCount(0);
  await page.getByLabel('Status').selectOption({ label: 'Paid' });
  await expect(page.locator('tr', { hasText: title })).toContainText(`Paid by ${LIBRARIAN.username}`);

  // Both payments are on the signed receipt.
  await page.getByRole('button', { name: 'Print receipt' }).click();
  await expect(page.getByRole('heading', { name: 'Loan receipt' })).toBeVisible();
  const payments = page.locator('.receipt-details div').filter({ has: page.locator('dt', { hasText: /^Paid$/ }) });
  await expect(payments).toHaveCount(2);
  await expect(payments.nth(0)).toContainText(`IDR 2,000`);
  await expect(payments.nth(1)).toContainText(`IDR 4,000`);
  await expect(payments.nth(1)).toContainText('Head Librarian'); // the name the bootstrap gives the first librarian

  await librarianApi.dispose();
});

test('reports show the period and download a CSV of loans', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Supernova ${unique()}`;
  const book = await createBook(librarianApi, title);
  const member = await librarianApi.post('/api/members', {
    name: `Bayu ${unique()}`, dateOfBirth: '1990-01-01', email: `bayu-${unique()}@example.com`, phone: '081200000002',
  });
  await librarianApi.post('/api/borrowed-books', { bookId: book.id, memberId: member.id });

  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await page.getByRole('navigation').getByRole('link', { name: 'Reports' }).click();
  await expect(page.getByRole('table', { name: 'Most borrowed' })).toContainText(title);
  // The weekly chart counts the loan just made; its numbers are also a table.
  const weekly = page.getByRole('figure', { name: 'Books lent per week' });
  await weekly.getByText('Show the numbers').click();
  await expect(weekly.getByRole('table').locator('tbody tr').last()).not.toContainText(/\b0$/);
  await expect(page.getByRole('figure', { name: 'Returns per month' }).getByRole('list', { name: 'Legend' }))
    .toContainText('Returned late');
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('link', { name: 'Download loans (CSV)' }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^loans-\d{4}-\d{2}-\d{2}-to-\d{4}-\d{2}-\d{2}\.csv$/);
  const csv = await (await import('node:fs/promises')).readFile(await download.path(), 'utf8');
  expect(csv).toContain('"Loan ID","Borrowed","Due"');
  expect(csv).toContain(title);

  await librarianApi.dispose();
});

test('a lost card is replaced and a duplicate record is merged on the member page', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const keep = await librarianApi.post('/api/members', {
    name: `Wati ${unique()}`, dateOfBirth: '1992-02-02', email: `wati-${unique()}@example.com`, phone: '081200000003',
  });
  const duplicate = await librarianApi.post('/api/members', {
    name: `Wati W ${unique()}`, dateOfBirth: '1992-02-02', email: `wati-w-${unique()}@example.com`, phone: '081200000004',
  });
  const title = `Gadis Kretek ${unique()}`;
  const book = await createBook(librarianApi, title);
  await librarianApi.post('/api/borrowed-books', { bookId: book.id, memberId: duplicate.id });

  page.on('dialog', (dialog) => dialog.accept(dialog.type() === 'prompt' ? 'Lost' : undefined));
  await signIn(page, LIBRARIAN.username, LIBRARIAN.password);
  await page.goto(`/members/${keep.id}`);
  const card = page.getByRole('region', { name: 'Library card' });
  await expect(card).toContainText(keep.libraryCardId);
  await expect(card).toContainText('Valid until');

  await card.getByRole('button', { name: 'Replace card' }).click();
  await expect(card.getByRole('status')).toContainText(`The old card ${keep.libraryCardId} no longer works.`);
  await expect(card.getByRole('list', { name: 'Earlier cards' })).toContainText(`${keep.libraryCardId} replaced: Lost`);

  await card.getByLabel('Duplicate record').selectOption({ label: `${duplicate.name} (${duplicate.libraryCardId})` });
  await card.getByRole('button', { name: 'Merge into this member' }).click();
  await expect(card.getByRole('status')).toContainText('was merged into this member.');
  await expect(card.getByRole('list', { name: 'Earlier cards' })).toContainText(`${duplicate.libraryCardId} merged from ${duplicate.name}`);
  await expect(page.getByText(title)).toBeVisible();

  // The old card IDs still find the member.
  await page.goto('/members');
  await page.getByLabel('Search members').fill(duplicate.libraryCardId);
  await expect(page.locator('tr', { hasText: keep.name })).toBeVisible();

  await librarianApi.dispose();
});
