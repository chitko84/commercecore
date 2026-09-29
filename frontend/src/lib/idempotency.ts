import { useRef, useCallback } from 'react';

/**
 * Backend-protected mutations (checkout, payment, refund, paid-cancellation)
 * require an Idempotency-Key that is stable across retries of the same
 * logical action but fresh for each new action. React re-renders must never
 * generate a new key, so the key is memoized per mount and only reset after
 * a successful submission.
 */
export function useIdempotencyKey(prefix: string): { key: string; reset: () => void } {
  const ref = useRef<string | null>(null);
  if (ref.current === null) {
    ref.current = `${prefix}-${crypto.randomUUID()}`;
  }
  const reset = useCallback(() => {
    ref.current = `${prefix}-${crypto.randomUUID()}`;
  }, [prefix]);
  return { key: ref.current, reset };
}