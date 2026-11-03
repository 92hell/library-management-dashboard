import { act, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { setLanguage } from './i18n';
import { customer, renderApp, signedInAs } from './test/utils';

const notification = (message) => ({
  content: [{ id: 1, type: 'HOLD_READY', message, link: '/my/loans', read: false, createdAt: '2026-11-03T08:00:00Z' }],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
});

describe('Server text in the chosen language', () => {
  it('asks the server for the chosen language and reloads what is on screen when it changes', async () => {
    const fetchMock = signedInAs(customer, {
      'GET /api/notifications': (url, init) =>
        new Response(JSON.stringify(notification(init.headers['Accept-Language'] === 'id'
          ? 'Eksemplar "Supernova" siap diambil.' : 'A copy of "Supernova" is ready for pickup.')),
        { status: 200, headers: { 'Content-Type': 'application/json' } }),
    });

    renderApp('/notifications');
    expect(await screen.findByText('A copy of "Supernova" is ready for pickup.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.at(-1)[1].headers['Accept-Language']).toBe('en');

    act(() => setLanguage('id'));

    expect(await screen.findByText('Eksemplar "Supernova" siap diambil.')).toBeInTheDocument();
  });
});
