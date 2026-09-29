import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../../lib/api';
import { LoadingState } from '../../components/ui';
import { CheckCircle2, XCircle } from 'lucide-react';

export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [state, setState] = useState<'loading' | 'ok' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setState('error');
      setMessage('Missing verification token. Use the link from your email.');
      return;
    }
    let cancelled = false;
    authApi
      .verifyEmail(token)
      .then((res) => {
        if (!cancelled) {
          setState('ok');
          setMessage(res.message);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setState('error');
          setMessage(e instanceof Error ? e.message : 'Verification failed.');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
      <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
        {state === 'loading' && <LoadingState label="Verifying your email…" />}
        {state === 'ok' && (
          <>
            <CheckCircle2 size={40} color="var(--color-success)" style={{ marginBottom: '1rem' }} />
            <h1>Email verified</h1>
            <p className="text-muted">{message}</p>
            <Link className="btn btn--dark" to="/login">
              Sign in
            </Link>
          </>
        )}
        {state === 'error' && (
          <>
            <XCircle size={40} color="var(--color-error)" style={{ marginBottom: '1rem' }} />
            <h1>Verification failed</h1>
            <p className="text-muted">{message}</p>
            <Link className="btn btn--dark" to="/login">
              Back to sign in
            </Link>
          </>
        )}
      </div>
    </div>
  );
}