import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { renderWithProviders, stubRoutes } from '../test/helpers';
import { RequireAdmin, RequireAuth, AuthProvider } from './auth';
import { storeTokens, clearTokens } from './api';
import type { AuthTokensResponse } from './types';


const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer',
  accessToken: 'a',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'r',
  refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

const CUSTOMER = {
  id: 'u1', email: 'c@example.com', fullName: 'Cara Customer', phone: null,
  role: 'CUSTOMER', emailVerified: true, accountStatus: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
};

const ADMIN = { ...CUSTOMER, email: 'a@example.com', role: 'ADMIN' };

function LoginProbe() {
  const location = useLocation();
  const state = location.state as { from?: string } | null;
  return <div>Sign in from {state?.from ?? 'unknown'}</div>;
}

describe('route guards', () => {
  afterEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it('redirects anonymous users to login for protected routes', async () => {
    const restore = stubRoutes({});
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/account/orders?page=2#latest']}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginProbe />} />
              <Route
                path="/account/orders"
                element={
                  <RequireAuth>
                    <div>secret</div>
                  </RequireAuth>
                }
              />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('Sign in from /account/orders?page=2#latest')).toBeInTheDocument();
    expect(screen.queryByText('secret')).not.toBeInTheDocument();
    restore();
  });

  it('lets an authenticated customer through RequireAuth', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': CUSTOMER });
    renderWithProviders(<RequireAuth><div>secret-area</div></RequireAuth>, ['/account']);
    expect(await screen.findByText('secret-area')).toBeInTheDocument();
    restore();
  });

  it('blocks a customer from admin-only areas with a forbidden state', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': CUSTOMER });
    renderWithProviders(<RequireAdmin><div>admin-secret</div></RequireAdmin>, ['/admin']);
    expect(await screen.findByText('Access denied')).toBeInTheDocument();
    expect(screen.queryByText('admin-secret')).not.toBeInTheDocument();
    restore();
  });

  it('allows an admin through admin-only guards', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': ADMIN });
    renderWithProviders(<RequireAdmin><div>admin-secret</div></RequireAdmin>, ['/admin']);
    expect(await screen.findByText('admin-secret')).toBeInTheDocument();
    restore();
  });
});
