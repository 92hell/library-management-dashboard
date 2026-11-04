import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '../i18n';
import { currentLocation, jsonResponse, mockFetch, problem, renderApp, sentBody } from '../test/utils';

const signedOut = { 'GET /api/auth/me': () => problem(401, 'Not signed in') };
const resetOn = { ...signedOut, 'GET /api/auth/demo': { enabled: false, registrationOpen: true, passwordReset: true, accounts: [] } };
const resetOff = { ...signedOut, 'GET /api/auth/demo': { enabled: false, registrationOpen: true, passwordReset: false, accounts: [] } };
const SENT = 'If an account matches, we have sent a link to its email address. It works for 30 minutes.';

describe('Forgot password', () => {
  it('is offered on the sign-in page only when the library can send the email', async () => {
    mockFetch(resetOn);
    const { unmount } = renderApp('/login');
    await userEvent.click(await screen.findByRole('link', { name: 'Forgot your password?' }));
    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument();
    unmount();

    mockFetch(resetOff);
    renderApp('/login');
    await screen.findByRole('button', { name: 'Sign in' });
    expect(screen.queryByRole('link', { name: 'Forgot your password?' })).not.toBeInTheDocument();
  });

  it('asks for a link by username or email and shows the same answer whatever was entered', async () => {
    const fetchMock = mockFetch({ ...resetOn, 'POST /api/auth/password-reset': () => jsonResponse({ message: SENT }, 202) });

    renderApp('/forgot-password');
    await userEvent.type(await screen.findByLabelText('Username or email'), ' siti@example.com ');
    await userEvent.click(screen.getByRole('button', { name: 'Send me a link' }));

    expect(await screen.findByRole('status')).toHaveTextContent(SENT);
    expect(sentBody(fetchMock, 'POST', '/api/auth/password-reset')).toEqual({ identifier: 'siti@example.com' });
  });

  it('needs something to look for, and shows a refusal from the server', async () => {
    const fetchMock = mockFetch({
      ...resetOn,
      'POST /api/auth/password-reset': () => problem(429, 'Too many requests. Please try again later.'),
    });

    renderApp('/forgot-password');
    await userEvent.click(await screen.findByRole('button', { name: 'Send me a link' }));
    expect(await screen.findByText('Enter your username or email address.')).toBeInTheDocument();
    expect(sentBody(fetchMock, 'POST', '/api/auth/password-reset')).toBeUndefined();

    await userEvent.type(screen.getByLabelText('Username or email'), 'siti');
    await userEvent.click(screen.getByRole('button', { name: 'Send me a link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests');
  });

  it('says to ask a librarian where email is not set up', async () => {
    mockFetch(resetOff);

    renderApp('/forgot-password');

    expect(await screen.findByText(/Ask a librarian to set a new password for you/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Username or email')).not.toBeInTheDocument();
  });
});

describe('Choosing a new password from the emailed link', () => {
  it('takes the token out of the address, then sets the new password with it', async () => {
    const fetchMock = mockFetch({ ...signedOut, 'POST /api/auth/password-reset/confirm': () => new Response(null, { status: 204 }) });

    renderApp('/reset-password?token=abc_DEF-123', { withLocation: true });
    const password = await screen.findByLabelText('New password');
    expect(currentLocation()).toBe('/reset-password');
    await userEvent.type(password, 'a-brand-new-password');
    await userEvent.type(screen.getByLabelText('New password again'), 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Your password was changed, and you were signed out everywhere else.');
    expect(sentBody(fetchMock, 'POST', '/api/auth/password-reset/confirm'))
      .toEqual({ token: 'abc_DEF-123', newPassword: 'a-brand-new-password' });
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
  });

  it('checks the password length and that both entries match before sending', async () => {
    const fetchMock = mockFetch(signedOut);

    renderApp('/reset-password?token=t');
    await userEvent.type(await screen.findByLabelText('New password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));
    expect(await screen.findByText('Use at least 10 characters.')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('New password'), '-but-longer');
    await userEvent.type(screen.getByLabelText('New password again'), 'something-else');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));
    expect(await screen.findByText('The two passwords are not the same.')).toBeInTheDocument();
    expect(sentBody(fetchMock, 'POST', '/api/auth/password-reset/confirm')).toBeUndefined();
  });

  it('offers a new link when the link has expired or was already used', async () => {
    mockFetch({
      ...signedOut,
      'POST /api/auth/password-reset/confirm': () => problem(400, 'Validation failed',
        { token: 'This link is invalid or has expired. Ask for a new one.' }),
    });

    renderApp('/reset-password?token=old');
    await userEvent.type(await screen.findByLabelText('New password'), 'a-brand-new-password');
    await userEvent.type(screen.getByLabelText('New password again'), 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(await screen.findByText('This link is invalid or has expired. Ask for a new one.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ask for a new link' })).toHaveAttribute('href', '/forgot-password');
  });

  it('reads in Bahasa Indonesia', async () => {
    act(() => setLanguage('id'));
    mockFetch(signedOut);

    renderApp('/reset-password');

    expect(await screen.findByRole('heading', { name: 'Buat kata sandi baru' })).toBeInTheDocument();
    expect(screen.getByText('Tautan ini tidak valid atau sudah kedaluwarsa. Minta tautan baru.')).toBeInTheDocument();
    act(() => setLanguage('en'));
  });
});
