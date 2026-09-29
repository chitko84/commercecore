import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { authApi } from '../../lib/api';
import { Button, Field, Input, errorMessage } from '../../components/ui';

const schema = z.object({ email: z.string().email('Enter a valid email address') });
type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await authApi.forgotPassword(values.email);
      setSent(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  if (sent) {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
        <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
          <h1>Check your inbox</h1>
          <p className="text-muted">
            If an account exists for that address, a password reset link is on its way.
          </p>
          <Link className="btn btn--dark" to="/login">
            Back to sign in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        <h1>Reset your password</h1>
        <p className="text-muted mb-6">Enter your account email and we will send you a reset link.</p>
        {error && <div className="form-error">{error}</div>}
        <form onSubmit={onSubmit} noValidate>
          <Field label="Email" error={errors.email?.message} htmlFor="email">
            <Input id="email" type="email" autoComplete="email" {...register('email')} />
          </Field>
          <Button type="submit" block size="lg" loading={isSubmitting}>
            Send reset link
          </Button>
        </form>
        <p className="text-sm text-muted mt-6 text-center">
          <Link to="/login" style={{ textDecoration: 'underline' }}>
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}