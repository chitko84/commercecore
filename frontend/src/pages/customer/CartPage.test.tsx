import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, stubRoutes } from '../../test/helpers';
import CartPage from './CartPage';
import { storeTokens, clearTokens } from '../../lib/api';
import type { AuthTokensResponse, CartResponse } from '../../lib/types';

const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer', accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'r', refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

const CUSTOMER = {
  id: 'u1', email: 'c@example.com', fullName: 'Cara', phone: null,
  role: 'CUSTOMER', emailVerified: true, accountStatus: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
};

const CART: CartResponse = {
  id: 'cart1',
  items: [
    { id: 'i1', productId: 'p1', variantId: null, productName: 'Aurora Headphones', sku: 'AU-1', quantity: 2, unitPrice: '199.00', lineSubtotal: '398.00', status: 'AVAILABLE', availableQuantity: 10, updatedAt: 'x' },
  ],
  subtotal: '398.00', promotionDiscount: '0.00', discountedMerchandiseTotal: '398.00',
  freeShippingEligible: false, promotion: null, readyForCheckout: true, updatedAt: 'x',
};

const CART_WITH_PROMO: CartResponse = { ...CART, promotionDiscount: '39.80', discountedMerchandiseTotal: '358.20', promotion: { id: 'pr1', code: 'SAVE10', valid: true, errorCode: null, eligibleSubtotal: '398.00', discount: '39.80', freeShippingEligible: false } };

describe('CartPage', () => {
  afterEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it('renders cart lines with totals from the backend', async () => {
    storeTokens(TOKENS);
    const restore = stubRoutes({ '/me': CUSTOMER, '/cart': CART });
    renderWithProviders(<CartPage />, ['/cart']);
    expect(await screen.findByText('Aurora Headphones')).toBeInTheDocument();
    // line subtotal and order-summary total both render the backend figure
    expect(screen.getAllByText('$398.00').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('button', { name: /Proceed to checkout/ })).toBeInTheDocument();
    restore();
  });

  it('applies a promotion code through the API', async () => {
    storeTokens(TOKENS);
    let cartState = CART;
    const restore = stubRoutes({
      '/me': CUSTOMER,
      '/cart': () => cartState,
    } as unknown as Record<string, unknown>);
    // Override: apply coupon endpoint returns the discounted cart.
    globalThis.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/me')) return new Response(JSON.stringify(CUSTOMER), { status: 200 });
      if (url.includes('/cart/promotion')) {
        cartState = CART_WITH_PROMO;
        return new Response(JSON.stringify(CART_WITH_PROMO), { status: 200 });
      }
      return new Response(JSON.stringify(cartState), { status: 200 });
    };
    renderWithProviders(<CartPage />, ['/cart']);
    await screen.findByText('Aurora Headphones');
    await userEvent.type(screen.getByLabelText('Coupon code'), 'SAVE10');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(screen.getByText('SAVE10')).toBeInTheDocument());
    expect(screen.getByText('−$39.80')).toBeInTheDocument();
    restore();
  });
});