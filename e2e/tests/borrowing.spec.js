import { expect, test } from '@playwright/test';
import {
  apiAs, createBook, customerWithHistory, LIBRARIAN, myLoanTitles, PASSWORD, registerCustomer, signIn, unique,
} from './support.js';

test('My loans lists the book on loan and the request above the returned loans, newest return first', async ({ page, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const { customer, onLoan, requested, returned } = await customerWithHistory(baseURL, librarianApi);
  await librarianApi.dispose();

  await signIn(page, customer.username, PASSWORD);
  await page.goto('/my/loans');

  expect(await myLoanTitles(page)).toEqual([onLoan, requested, ...[...returned].reverse()]);
});

test('a customer requests a book, the librarian approves and hands it over, the customer renews it', async ({ browser, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Laskar Pelangi ${unique()}`;
  await createBook(librarianApi, title, { copies: 2 });
  const customer = await registerCustomer(baseURL);

  // Customer: find the book, open its page, request it.
  const customerPage = await (await browser.newContext()).newPage();
  await signIn(customerPage, customer.username, PASSWORD);
  await customerPage.getByRole('navigation').getByRole('link', { name: 'Catalogue' }).click();
  await customerPage.getByLabel('Search the catalogue').fill(title);
  await customerPage.getByRole('link', { name: title }).click();
  // Wait for the book page itself: until it is ready the catalogue stays on screen, and its other
  // books can carry the same "2 of 2 on shelf" stamp.
  await expect(customerPage.getByRole('heading', { level: 1, name: title })).toBeVisible();
  await expect(customerPage.getByText('2 of 2 on shelf')).toBeVisible();
  await customerPage.getByRole('button', { name: 'Request this book' }).click();
  await expect(customerPage.getByRole('status').filter({ hasText: 'Request sent' })).toBeVisible();

  // Librarian: approve (a copy is set aside), then hand it over at the desk.
  const desk = await (await browser.newContext()).newPage();
  await signIn(desk, LIBRARIAN.username, LIBRARIAN.password);
  await desk.goto('/borrowed-books?status=pending');
  const row = desk.locator('tr', { hasText: title });
  await row.getByRole('button', { name: 'Approve' }).click();
  await desk.goto('/borrowed-books?status=ready');
  await expect(row).toContainText('Ready for pickup');
  await row.getByRole('button', { name: 'Hand over' }).click();
  await expect(row).toHaveCount(0);

  // Customer: the book is on loan; renewing moves the due date.
  await customerPage.goto('/my/loans');
  const loan = customerPage.locator('tr', { hasText: title });
  await expect(loan).toContainText('On loan');
  const dueBefore = await loan.locator('td').nth(2).innerText();
  await loan.getByRole('button', { name: 'Renew' }).click();
  await expect(loan.locator('td').nth(2)).not.toHaveText(dueBefore);
  await expect(loan.getByRole('button', { name: 'Renew' })).toHaveCount(0);

  await librarianApi.dispose();
});

test('when every copy is out, an approved request waits in the queue and gets the returned copy', async ({ browser, baseURL }) => {
  const librarianApi = await apiAs(baseURL, LIBRARIAN.username, LIBRARIAN.password);
  const title = `Bumi Manusia ${unique()}`;
  const book = await createBook(librarianApi, title, { copies: 1 });
  const walkIn = await librarianApi.post('/api/members', {
    name: 'Budi Santoso', dateOfBirth: '1988-08-02', email: `budi-${unique()}@example.com`, phone: '081200000000',
  });
  const lent = await librarianApi.post('/api/borrowed-books', { bookId: book.id, memberId: walkIn.id });
  const customer = await registerCustomer(baseURL);
  const customerApi = await apiAs(baseURL, customer.username, PASSWORD);
  const request = await customerApi.post('/api/my/loans', { bookId: book.id });
  await librarianApi.post(`/api/borrowed-books/${request.id}/approve`, {});

  const page = await (await browser.newContext()).newPage();
  await signIn(page, customer.username, PASSWORD);
  await page.goto('/my/loans');
  await expect(page.locator('tr', { hasText: title })).toContainText('#1 in queue');

  await librarianApi.post(`/api/borrowed-books/${lent.id}/return`);

  await page.reload();
  await expect(page.locator('tr', { hasText: title })).toContainText('Ready for pickup');
  await page.goto('/');
  await expect(page.getByText(/Ready for pickup at the desk/)).toContainText(title);

  await customerApi.dispose();
  await librarianApi.dispose();
});
