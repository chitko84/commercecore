import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, stubRoutes } from '../../test/helpers';
import AdminDashboardPage from './AdminDashboardPage';
import { storeTokens, clearTokens } from '../../lib/api';
import type { AuthTokensResponse } from '../../lib/types';

const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer', accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'r', refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

const ADMIN = {
  id: 'u1', email: 'admin@example.com', fullName: 'Ada Admin', phone: null,
  role: 'ADMIN', emailVerified: true, accountStatus: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
};

const DASHBOARD = {
  totalRevenue: '1000.00', netRevenue: '920.00',
  ordersByStatus: { PENDING: 2, CONFIRMED: 3, DELIVERED: 5, CANCELLED: 1 },
  customerCount: 12, activeProductCount: 8, lowStockCount: 1, failedPaymentCount: 0,
  recentOrders: [
    { id: 'o1', orderNumber: 'CC-1001', status: 'DELIVERED', customerEmail: 'c@example.com', grandTotal: '199.00', createdAt: '2026-09-01T00:00:00Z' },
  ],
};

describe('AdminDashboardPage', () => {
  afterEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it('renders metrics and recent orders from the backend', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': ADMIN, '/admin/dashboard': DASHBOARD });
    renderWithProviders(<AdminDashboardPage />, ['/admin']);
    await waitFor(() => expect(screen.getByText('Total revenue')).toBeInTheDocument());
    expect(screen.getByText('$1,000.00')).toBeInTheDocument();
    expect(screen.getByText('$920.00')).toBeInTheDocument();
    expect(screen.getByText('CC-1001')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    restore();
  });
});