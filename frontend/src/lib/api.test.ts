import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { request, ApiClientError, storeTokens, clearTokens, downloadCsv } from './api';
import type { AuthTokensResponse } from './types';

const TOKENS: AuthTokensResponse = {
  tokenType: 'Bearer',
  accessToken: 'access-123',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  refreshToken: 'refresh-123',
  refreshTokenExpiresAt: '2030-01-01T00:00:00Z',
};

describe('api client', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    clearTokens();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('injects the bearer token from storage', async () => {
    storeTokens(TOKENS);
    const seen: RequestInit[] = [];
    globalThis.fetch = async (_input, init) => {
      seen.push(init ?? {});
      return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };
    await request('/me');
    expect(seen[0].headers).toMatchObject({ Authorization: 'Bearer access-123' });
  });

  it('maps an ApiError body to a typed ApiClientError with traceId', async () => {
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({
          timestamp: '2026-01-01T00:00:00Z',
          status: 404,
          error: 'Not Found',
          code: 'PRODUCT_NOT_FOUND',
          message: 'The product was not found.',
          path: '/api/v1/products/x',
          traceId: 'corr-42',
        }),
        { status: 404, headers: { 'Content-Type': 'application/json' } },
      );
    try {
      await request('/products/x');
      expect.unreachable('should throw');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiClientError);
      const err = e as ApiClientError;
      expect(err.code).toBe('PRODUCT_NOT_FOUND');
      expect(err.status).toBe(404);
      expect(err.traceId).toBe('corr-42');
      expect(err.message).toBe('The product was not found.');
    }
  });

  it('maps a network failure to a safe actionable error', async () => {
    globalThis.fetch = async () => { throw new TypeError('socket details must not leak'); };
    await expect(request('/products')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Unable to reach CommerceCore. Check your connection and try again.',
    });
  });

  it('refreshes once on 401 and retries the original request', async () => {
    storeTokens(TOKENS);
    let calls = 0;
    globalThis.fetch = async (input, init) => {
      const url = String(input);
      calls += 1;
      if (url.includes('/auth/refresh')) {
        return new Response(JSON.stringify({ ...TOKENS, accessToken: 'access-456' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (calls === 1) {
        return new Response(JSON.stringify({ status: 401, code: 'UNAUTHORIZED', message: 'expired' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
      }
      expect((init?.headers as Record<string, string>)?.Authorization).toBe('Bearer access-456');
      return new Response(JSON.stringify({ id: 'p1' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };
    const result = await request<{ id: string }>('/me');
    expect(result.id).toBe('p1');
    // original 401 + refresh + retried request
    expect(calls).toBe(3);
  });

  it('clears tokens when refresh fails', async () => {
    storeTokens(TOKENS);
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ status: 401, code: 'UNAUTHORIZED', message: 'nope' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
    await expect(request('/me')).rejects.toThrow();
    expect(localStorage.getItem('commercecore.refresh_token')).toBeNull();
  });
});

describe('csv download', () => {
  const originalFetch = globalThis.fetch;
  const originalCreate = URL.createObjectURL;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    URL.createObjectURL = originalCreate;
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  it('uses the Content-Disposition filename for the download', async () => {
    const blob = new Blob(['a,b,c'], { type: 'text/csv' });
    URL.createObjectURL = vi.fn(() => 'blob:fake');
    URL.revokeObjectURL = vi.fn();
    globalThis.fetch = async () =>
      new Response(blob, {
        status: 200,
        headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="sales.csv"' },
      });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      (clickSpy as unknown as { clicked: HTMLAnchorElement | undefined }).clicked = this;
    });
    await downloadCsv('/admin/reports/sales.csv', 'fallback.csv');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    const clicked = (clickSpy as unknown as { clicked: HTMLAnchorElement | undefined }).clicked;
    expect(clicked?.download).toBe('sales.csv');
  });
});
