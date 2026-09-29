import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../../lib/api';
import { Button, Field, Input, errorMessage } from '../../components/ui';

const schema = z.object({
  password: z
    .string()
    .min(12, 'Password must be at least 12 characters')
    .max(128)
    .regex(/[a-z]/, 'Include a lowercase letter')
    .regex(/[A-Z]/, 'Include an uppercase letter')
    .regex(/[0-9]/, 'Include a number'),
});
type FormValues = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await authApi.resetPassword({ token, newPassword: values.password });
      setDone(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  if (!token) {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
        <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
          <h1>Invalid reset link</h1>
          <p className="text-muted">Use the full link from your reset email.</p>
        </div>
      </div>
    );
  }

  if (done) {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
        <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
          <h1>Password updated</h1>
          <p className="text-muted">You can now sign in with your new password.</p>
          <Link className="btn btn--dark" to="/login">
            Sign in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        <h1>Choose a new password</h1>
        {error && <div className="form-error">{error}</div>}
        <form onSubmit={onSubmit} noValidate>
          <Field
            label="New password"
            error={errors.password?.message}
            hint="At least 12 characters with upper and lower case letters and a number."
            htmlFor="password"
          >
            <Input id="password" type="password" autoComplete="new-password" {...register('password')} />
          </Field>
          <Button type="submit" block size="lg" loading={isSubmitting}>
            Update password
          </Button>
        </form>
      </div>
    </div>
  );
}