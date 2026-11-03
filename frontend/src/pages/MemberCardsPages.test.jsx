import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { setLanguage } from '../i18n';
import { customer, jsonResponse, librarian, page, problem, renderApp, sentBody, signedInAs } from '../test/utils';

const siti = {
  id: 7, libraryCardId: 'LIB-7KQ2XM', name: 'Siti', dateOfBirth: '1990-05-17', email: 'siti@example.com',
  phone: '0812', active: true, cardExpiresOn: '2026-10-01', cardExpired: true, accountUsername: null,
};

/** The member page of Siti, whose record changes as the test goes (`member` is read on each load). */
function memberPage(routes = {}, history = []) {
  const state = { member: siti };
  const fetchMock = signedInAs(librarian, {
    'GET /api/members/7': () => jsonResponse(state.member),
    'GET /api/members/7/loans': [],
    'GET /api/members/7/cards': history,
    'GET /api/members/options': [{ id: 7, name: 'Siti (LIB-7KQ2XM)' }, { id: 9, name: 'Siti R (LIB-9ABCDE)' }],
    ...routes,
  });
  return { fetchMock, state };
}

function calls(fetchMock, method, path) {
  return fetchMock.mock.calls.filter(([url, init]) => url === path && (init?.method ?? 'GET') === method).length;
}

describe('Library card on the member page (librarian)', () => {
  it('shows an expired card and renews it', async () => {
    const renewed = { ...siti, cardExpiresOn: '2027-11-03', cardExpired: false };
    const { state } = memberPage({
      'POST /api/members/7/card/renew': () => {
        state.member = renewed;
        return jsonResponse(renewed);
      },
    });

    renderApp('/members/7');
    const card = await screen.findByRole('region', { name: 'Library card' });
    expect(await within(card).findByText('Expired on Oct 1, 2026')).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Renew card' }));

    expect(await within(card).findByRole('status')).toHaveTextContent('Card renewed. Valid until Nov 3, 2027.');
    expect(await within(card).findByText('Valid until Nov 3, 2027')).toBeInTheDocument();
  });

  it('replaces a lost card with the reason given, and names both card IDs', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue(' Lost ');
    const fetchMock = memberPage({
      'POST /api/members/7/card/replace': { ...siti, libraryCardId: 'LIB-NEW222' },
    }).fetchMock;

    renderApp('/members/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Replace card' }));

    expect(window.prompt).toHaveBeenCalledWith(expect.stringContaining('LIB-7KQ2XM'), '');
    expect(sentBody(fetchMock, 'POST', '/api/members/7/card/replace')).toEqual({ reason: 'Lost' });
    expect(await screen.findByRole('status')).toHaveTextContent('New card LIB-NEW222 issued. The old card LIB-7KQ2XM no longer works.');
  });

  it('asks for a reason before replacing a card', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('   ');
    const { fetchMock } = memberPage();

    renderApp('/members/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Replace card' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A reason is required to replace a card.');
    expect(sentBody(fetchMock, 'POST', '/api/members/7/card/replace')).toBeUndefined();
  });

  it('lists earlier card IDs with why they stopped being used', async () => {
    memberPage({}, [
      { libraryCardId: 'LIB-9ABCDE', reason: 'MERGED', note: 'Siti R', retiredAt: '2026-11-02T03:00:00Z', retiredBy: 'lina' },
      { libraryCardId: 'LIB-OLD111', reason: 'REPLACED', note: 'Lost', retiredAt: '2026-10-01T03:00:00Z', retiredBy: 'lina' },
    ]);

    renderApp('/members/7');

    const earlier = await screen.findByRole('list', { name: 'Earlier cards' });
    expect(within(earlier).getAllByRole('listitem')[0]).toHaveTextContent(/LIB-9ABCDE merged from Siti R.*by lina/);
    expect(within(earlier).getAllByRole('listitem')[1]).toHaveTextContent(/LIB-OLD111 replaced: Lost/);
  });

  it('merges a duplicate record after confirming, then reloads the borrowing history', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fetchMock } = memberPage({ 'POST /api/members/7/merge': siti });

    renderApp('/members/7');
    const select = await screen.findByLabelText('Duplicate record');
    expect(within(select).queryByRole('option', { name: 'Siti (LIB-7KQ2XM)' })).not.toBeInTheDocument();
    await userEvent.selectOptions(select, 'Siti R (LIB-9ABCDE)');
    await userEvent.click(screen.getByRole('button', { name: 'Merge into this member' }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('This cannot be undone.'));
    expect(sentBody(fetchMock, 'POST', '/api/members/7/merge')).toEqual({ duplicateId: 9 });
    expect(await screen.findByRole('status')).toHaveTextContent('Siti R (LIB-9ABCDE) was merged into this member.');
    expect(calls(fetchMock, 'GET', '/api/members/7/loans')).toBe(2);
  });

  it('does nothing when the merge is not confirmed, and shows a refusal from the server', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { fetchMock } = memberPage({
      'POST /api/members/7/merge': () => problem(409, 'Both members have an online account, so they cannot be merged.'),
    });

    renderApp('/members/7');
    await userEvent.selectOptions(await screen.findByLabelText('Duplicate record'), 'Siti R (LIB-9ABCDE)');
    await userEvent.click(screen.getByRole('button', { name: 'Merge into this member' }));
    expect(sentBody(fetchMock, 'POST', '/api/members/7/merge')).toBeUndefined();

    confirm.mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: 'Merge into this member' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Both members have an online account');
  });

  it('marks expired cards in the member list', async () => {
    signedInAs(librarian, { 'GET /api/members': page([siti, { ...siti, id: 8, name: 'Bayu', cardExpired: false }]) });

    renderApp('/members');

    expect(within((await screen.findByText('Siti')).closest('tr')).getByText('Card expired')).toBeInTheDocument();
    expect(within(screen.getByText('Bayu').closest('tr')).queryByText('Card expired')).not.toBeInTheDocument();
  });

  it('reads in Bahasa Indonesia', async () => {
    act(() => setLanguage('id'));
    memberPage();

    renderApp('/members/7');

    expect(await screen.findByRole('button', { name: 'Perpanjang kartu' })).toBeInTheDocument();
    expect(screen.getByText('Habis berlaku 1 Okt 2026')).toBeInTheDocument();
    act(() => setLanguage('en'));
  });
});

/** "YYYY-MM-DD", `days` from today in the reader's time zone. */
function inDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function profileWithCard(card) {
  return {
    id: 2, username: 'siti', role: 'CUSTOMER', displayName: 'Siti', email: 'siti@example.com',
    member: { id: 7, libraryCardId: 'LIB-7KQ2XM', name: 'Siti', email: 'siti@example.com', phone: '0812',
      dateOfBirth: '1990-05-17', active: true, ...card },
  };
}

describe('Library card on the profile (member)', () => {
  it('tells the member their card has expired', async () => {
    signedInAs(customer, { 'GET /api/profile': profileWithCard({ cardExpiresOn: '2026-10-01', cardExpired: true }) });

    renderApp('/profile');

    expect(await screen.findByText('Your library card expired on Oct 1, 2026. Renew it at the desk to borrow again.'))
      .toBeInTheDocument();
  });

  it('warns a month ahead, and not before', async () => {
    const soon = inDays(10);
    signedInAs(customer, { 'GET /api/profile': profileWithCard({ cardExpiresOn: soon, cardExpired: false }) });
    renderApp('/profile');
    expect(await screen.findByText(/Your library card expires on .*Renew it at the desk to keep borrowing\./)).toBeInTheDocument();
  });

  it('shows only the expiry date when it is far away', async () => {
    signedInAs(customer, { 'GET /api/profile': profileWithCard({ cardExpiresOn: inDays(200), cardExpired: false }) });

    renderApp('/profile');

    expect(await screen.findByText(/^Valid until /)).toBeInTheDocument();
    expect(screen.queryByText(/Renew it at the desk/)).not.toBeInTheDocument();
  });
});
