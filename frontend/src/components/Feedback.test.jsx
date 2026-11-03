import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../api';
import { setLanguage } from '../i18n';
import { mockFetch, problem, renderApp } from '../test/utils';
import { ErrorAlert } from './Feedback';

describe('Error messages', () => {
  it('translates the client\'s own message when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const error = await api.get('/api/books').catch((e) => e);

    render(<ErrorAlert error={error} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not reach the server. Please check your connection and try again.');

    act(() => setLanguage('id'));
    expect(screen.getByRole('alert')).toHaveTextContent('Tidak dapat terhubung ke server. Periksa koneksi Anda, lalu coba lagi.');
  });

  it('translates "request failed" with its status code', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>Bad gateway</html>', { status: 502 })));
    const error = await api.get('/api/books').catch((e) => e);
    expect(error.message).toBe('Request failed with status 502');

    setLanguage('id');
    render(<ErrorAlert error={error} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Permintaan gagal dengan status 502');
  });

  it('shows the server\'s own messages as they are', () => {
    setLanguage('id');
    render(<ErrorAlert error={new ApiError(409, 'No copy is on the shelf.')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('No copy is on the shelf.');
  });

  it('translates the network error on the sign-in page too', async () => {
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
      'POST /api/auth/login': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    setLanguage('id');

    renderApp('/login');
    await userEvent.type(await screen.findByLabelText('Nama pengguna'), 'siti');
    await userEvent.type(screen.getByLabelText('Kata sandi'), 'a-long-password');
    await userEvent.click(screen.getByRole('button', { name: 'Masuk' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Tidak dapat terhubung ke server.');
  });
});
