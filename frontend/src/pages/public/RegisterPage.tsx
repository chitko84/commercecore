import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { Button, Field, Input, errorMessage } from '../../components/ui';

const schema = z.object({
  fullName: z.string().min(2, 'Enter your full name').max(200),
  email: z.string().email('Enter a valid email address'),
  password: z
    .string()
    .min(12, 'Password must be at least 12 characters')
    .max(128)
    .regex(/[a-z]/, 'Include a lowercase letter')
    .regex(/[A-Z]/, 'Include an uppercase letter')
    .regex(/[0-9]/, 'Include a number'),
  phone: z.string().max(32).optional().or(z.literal('')),
});
type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const { register: registerUser } = useAuth();
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await registerUser({ ...values, phone: values.phone || undefined });
      setRegisteredEmail(values.email);
    } catch (e) {
      setError('root', { message: errorMessage(e) });
    }
  });

  if (registeredEmail) {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
        <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
          <h1>Check your inbox</h1>
          <p className="text-muted">
            We sent a verification link to <strong>{registeredEmail}</strong>. Verify your email to
            activate your account, then sign in.
          </p>
          <Link className="btn btn--dark" to="/login">
            Go to sign in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        <h1 style={{ marginBottom: '0.25rem' }}>Create your account</h1>
        <p className="text-muted mb-6">Shop faster, track orders and write reviews.</p>
        {errors.root && <div className="form-error">{errors.root.message}</div>}
        <form onSubmit={onSubmit} noValidate>
          <Field label="Full name" error={errors.fullName?.message} htmlFor="fullName">
            <Input id="fullName" autoComplete="name" {...register('fullName')} />
          </Field>
          <Field label="Email" error={errors.email?.message} htmlFor="email">
            <Input id="email" type="email" autoComplete="email" {...register('email')} />
          </Field>
          <Field
            label="Password"
            error={errors.password?.message}
            hint="At least 12 characters with upper and lower case letters and a number."
            htmlFor="password"
          >
            <Input id="password" type="password" autoComplete="new-password" {...register('password')} />
          </Field>
          <Field label="Phone (optional)" error={errors.phone?.message} htmlFor="phone">
            <Input id="phone" type="tel" autoComplete="tel" {...register('phone')} />
          </Field>
          <Button type="submit" block size="lg" loading={isSubmitting}>
            Create account
          </Button>
        </form>
        <p className="text-sm text-muted mt-6 text-center">
          Already have an account?{' '}
          <Link to="/login" style={{ textDecoration: 'underline', fontWeight: 600 }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}