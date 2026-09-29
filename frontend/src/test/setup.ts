import '@testing-library/jest-dom/vitest';

// jsdom does not implement crypto.randomUUID; provide a small polyfill so
// idempotency-key generation works in unit tests.
if (typeof globalThis.crypto?.randomUUID !== 'function') {
  Object.defineProperty(globalThis.crypto ?? {}, 'randomUUID', {
    value: () => {
      const bytes = new Uint8Array(16);
      crypto.getRandomValues(bytes);
      bytes[6] = (bytes[6] & 0x0f) | 0x40;
      bytes[8] = (bytes[8] & 0x3f) | 0x80;
      const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    },
  });
}

// Default fetch stub so components never hit a real network in unit tests.
// Individual tests may override this with fetchMock.
if (!globalThis.fetch) {
  globalThis.fetch = async () => {
    throw new Error('fetch not mocked in this test');
  };
}