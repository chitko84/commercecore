import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthProvider } from '../lib/auth';

/** Stub fetch for a single request: returns the provided JSON for any call. */
export function stubFetch(json: unknown, status = 200): () => void {
  const original = globalThis.fetch;
  globalThis.fetch = async (input: RequestInfo | URL) => {
    const url = String(input);
    void url;
    return new Response(JSON.stringify(json), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  };
  return () => {
    globalThis.fetch = original;
  };
}

/** Route-based fetch stub: returns per-URL-path responses (longest path wins). */
export function stubRoutes(routes: Record<string, unknown>): () => void {
  const original = globalThis.fetch;
  const paths = Object.keys(routes).sort((a, b) => b.length - a.length);
  globalThis.fetch = async (input: RequestInfo | URL) => {
    const url = String(input);
    for (const path of paths) {
      if (url.includes(path)) {
        return new Response(JSON.stringify(routes[path]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
    }
    return new Response(JSON.stringify({ code: 'NOT_FOUND', message: 'not stubbed: ' + url }), { status: 404 });
  };
  return () => {
    globalThis.fetch = original;
  };
}

export function renderWithProviders(ui: ReactNode, initialEntries: string[] = ['/']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}