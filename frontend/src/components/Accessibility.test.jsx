import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { jsonResponse, librarian, page, renderApp, signedInAs } from '../test/utils';
import Field from './Field';

describe('Accessibility', () => {
  it('links a field to its error message and hint', () => {
    const { rerender } = render(<Field label="Title" name="title" value="" onChange={() => {}} hint="As printed on the cover." />);
    expect(screen.getByLabelText('Title')).toHaveAccessibleDescription('As printed on the cover.');
    expect(screen.getByLabelText('Title')).toHaveAttribute('aria-invalid', 'false');

    rerender(<Field label="Title" name="title" value="" onChange={() => {}} error="Title is required." />);
    expect(screen.getByLabelText('Title')).toHaveAccessibleDescription('Title is required.');
    expect(screen.getByLabelText('Title')).toHaveAttribute('aria-invalid', 'true');
  });

  it('offers a skip link to the main content', async () => {
    signedInAs(librarian);
    renderApp('/');

    const skip = await screen.findByRole('link', { name: 'Skip to main content' });
    expect(skip).toHaveAttribute('href', '#main');
    expect(document.getElementById('main').tagName).toBe('MAIN');
  });

  it('moves focus to the page content after navigating', async () => {
    signedInAs(librarian, { 'GET /api/books': page([]) });
    renderApp('/');

    await userEvent.click(await screen.findByRole('link', { name: 'Books' }));

    expect(document.activeElement).toBe(document.getElementById('main'));
  });

  it('leaves focus alone on the first page load, also under StrictMode', async () => {
    signedInAs(librarian, { 'GET /api/books': page([]) });
    renderApp('/', { strict: true });

    await screen.findByRole('heading', { name: /Welcome, Lina/ });
    expect(document.activeElement).not.toBe(document.getElementById('main'));

    await userEvent.click(screen.getByRole('link', { name: 'Books' }));
    expect(document.activeElement).toBe(document.getElementById('main'));
  });

  it('names the actions column of the users list', async () => {
    signedInAs(librarian, {
      'GET /api/users': page([{ id: 5, username: 'budi', displayName: 'Budi', email: null, role: 'CUSTOMER', enabled: true }]),
    });
    renderApp('/users');

    await screen.findByText('budi');
    expect(screen.getAllByRole('columnheader').at(-1)).toHaveAccessibleName('Actions');
  });

  it('moves focus to the page heading after adding a librarian closes the form', async () => {
    signedInAs(librarian, {
      'GET /api/users': page([]),
      'POST /api/users/librarians': () => jsonResponse({ id: 9, username: 'rina' }, 201),
    });
    renderApp('/users');

    await userEvent.click(await screen.findByRole('button', { name: 'Add librarian' }));
    const form = screen.getByRole('button', { name: 'Create librarian' }).closest('form');
    await userEvent.type(within(form).getByLabelText('Username'), 'rina');
    await userEvent.type(within(form).getByLabelText('Name'), 'Rina');
    await userEvent.type(within(form).getByLabelText('Initial password'), 'a-long-password');
    await userEvent.click(within(form).getByRole('button', { name: 'Create librarian' }));

    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Users' })));
  });
});
