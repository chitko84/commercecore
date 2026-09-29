import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, stubRoutes } from '../../test/helpers';
import CheckoutPage from './CheckoutPage';
import { storeTokens, clearTokens } from '../../lib/api';
import { useIdempotencyKey } from '../../lib/idempotency';
import { renderHook } from '@testing-library/react';
import type { AuthTokensResponse } from '../../lib/types';

const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer', accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'r', refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

const CUSTOMER = {
  id: 'u1', email: 'c@example.com', fullName: 'Cara', phone: null,
  role: 'CUSTOMER', emailVerified: true, accountStatus: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
};

const CART = {
  id: 'cart1',
  items: [
    { id: 'i1', productId: 'p1', variantId: null, productName: 'Aurora Headphones', sku: 'AU-1', quantity: 1, unitPrice: '199.00', lineSubtotal: '199.00', status: 'AVAILABLE', availableQuantity: 10, updatedAt: 'x' },
  ],
  subtotal: '199.00', promotionDiscount: '0.00', discountedMerchandiseTotal: '199.00',
  freeShippingEligible: false, promotion: null, readyForCheckout: true, updatedAt: 'x',
};

const ADDRESSES = [
  { id: 'a1', label: 'Home', recipientName: 'Cara', phone: '+1', addressLine1: '1 Main St', addressLine2: null, city: 'Springfield', stateRegion: 'IL', postalCode: '62701', countryCode: 'US', defaultShipping: true, defaultBilling: true, createdAt: 'x', updatedAt: 'x' },
];

describe('CheckoutPage', () => {
  afterEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it('renders backend totals and a sandbox payment label', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': CUSTOMER, '/cart': CART, '/me/addresses': ADDRESSES });
    renderWithProviders(<CheckoutPage />, ['/checkout']);
    await screen.findByText('Checkout');
    await waitFor(() => expect(screen.getByText('Sandbox payments — no real charges')).toBeInTheDocument());
    expect(screen.getAllByText('$199.00').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('button', { name: /Place order/i })).toBeInTheDocument();
    restore();
  });

  it('keeps one idempotency key per logical action and regenerates after reset', () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey('checkout'));
    const first = result.current.key;
    rerender();
    expect(result.current.key).toBe(first);
    result.current.reset();
    rerender();
    expect(result.current.key).not.toBe(first);
  });
});