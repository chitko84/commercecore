import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../test/helpers';
import AccountNotificationsPage from './AccountNotificationsPage';
import { storeTokens, clearTokens } from '../../lib/api';
import type { AuthTokensResponse } from '../../lib/types';

const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer', accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'r', refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

const CUSTOMER = {
  id: 'u1', email: 'c@example.com', fullName: 'Cara', phone: null,
  role: 'CUSTOMER', emailVerified: true, accountStatus: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
};

const PAGE = {
  content: [
    { id: 'n1', type: 'ORDER_SHIPPED', title: 'Order shipped', body: 'CC-1001 is on its way', resourceType: 'ORDER', resourceId: 'o1', read: false, createdAt: '2026-09-01T00:00:00Z' },
    { id: 'n2', type: 'WELCOME', title: 'Welcome', body: 'Thanks for joining', resourceType: null, resourceId: null, read: true, createdAt: '2026-08-01T00:00:00Z' },
  ],
  page: 0, size: 15, totalElements: 2, totalPages: 1, first: true, last: true,
};

describe('AccountNotificationsPage', () => {
  afterEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it('shows unread and read notifications and marks one read on click', async () => {
    storeTokens(TOKENS);
    const restore = globalThis.fetch;
    const seen: string[] = [];
    globalThis.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/n1/read')) {
        seen.push(url);
        return new Response(JSON.stringify({ ...PAGE.content[0], read: true }), { status: 200 });
      }
      if (url.includes('/me/notifications')) return new Response(JSON.stringify(PAGE), { status: 200 });
      if (url.includes('/unread-count')) return new Response(JSON.stringify({ unreadCount: 1 }), { status: 200 });
      if (url.includes('/me')) return new Response(JSON.stringify(CUSTOMER), { status: 200 });
      return new Response(JSON.stringify(PAGE), { status: 200 });
    };
    renderWithProviders(<AccountNotificationsPage />, ['/account/notifications']);
    const titles = await screen.findAllByText('Order shipped');
    expect(titles.length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Welcome').length).toBeGreaterThanOrEqual(1);
    await userEvent.click(screen.getByLabelText('Order shipped — mark as read'));
    await waitFor(() => expect(seen.length).toBeGreaterThan(0));
    globalThis.fetch = restore;
  });

  it('shows an empty state when there are no notifications', async () => {
    storeTokens(TOKENS);
    const empty = { ...PAGE, content: [], totalElements: 0, totalPages: 0 };
    const restore = globalThis.fetch;
    globalThis.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/me/notifications')) return new Response(JSON.stringify(empty), { status: 200 });
      if (url.includes('/me')) return new Response(JSON.stringify(CUSTOMER), { status: 200 });
      return new Response(JSON.stringify(empty), { status: 200 });
    };
    renderWithProviders(<AccountNotificationsPage />, ['/account/notifications']);
    expect(await screen.findByText('No notifications')).toBeInTheDocument();
    globalThis.fetch = restore;
  });
});