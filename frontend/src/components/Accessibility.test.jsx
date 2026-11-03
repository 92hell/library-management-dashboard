import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { librarian, page, renderApp, signedInAs } from '../test/utils';
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
});
