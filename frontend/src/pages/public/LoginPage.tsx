import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { Button, Field, Input, useToasts, errorMessage } from '../../components/ui';
import type { ApiClientError } from '../../lib/api';

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});
type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toasts = useToasts();
  const from = (location.state as { from?: string } | null)?.from ?? '/account';

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values);
      navigate(from, { replace: true });
    } catch (e) {
      const err = e as ApiClientError;
      if (err.code === 'ACCOUNT_DEACTIVATED' || err.code === 'ACCOUNT_LOCKED' || err.code === 'EMAIL_NOT_VERIFIED') {
        setError('root', { message: err.message });
      } else {
        setError('root', { message: errorMessage(e) });
      }
      toasts.push('error', errorMessage(e));
    }
  });

  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        <h1 style={{ marginBottom: '0.25rem' }}>Sign in</h1>
        <p className="text-muted mb-6">Welcome back to CommerceCore.</p>
        {errors.root && <div className="form-error">{errors.root.message}</div>}
        <form onSubmit={onSubmit} noValidate>
          <Field label="Email" error={errors.email?.message} htmlFor="email">
            <Input id="email" type="email" autoComplete="email" {...register('email')} />
          </Field>
          <Field label="Password" error={errors.password?.message} htmlFor="password">
            <Input id="password" type="password" autoComplete="current-password" {...register('password')} />
          </Field>
          <Button type="submit" block size="lg" loading={isSubmitting}>
            Sign in
          </Button>
        </form>
        <div className="flex justify-between mt-6 text-sm">
          <Link to="/forgot-password" className="text-muted" style={{ textDecoration: 'underline' }}>
            Forgot password?
          </Link>
          <span className="text-muted">
            New here?{' '}
            <Link to="/register" style={{ textDecoration: 'underline', fontWeight: 600 }}>
              Create an account
            </Link>
          </span>
        </div>
      </div>
      {toasts.node}
    </div>
  );
}