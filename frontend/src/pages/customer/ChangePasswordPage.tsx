import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { accountApi } from '../../lib/api';
import { Button, Field, Input, useToasts, errorMessage } from '../../components/ui';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z
      .string()
      .min(12, 'Password must be at least 12 characters')
      .max(128)
      .regex(/[a-z]/, 'Include a lowercase letter')
      .regex(/[A-Z]/, 'Include an uppercase letter')
      .regex(/[0-9]/, 'Include a number'),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { message: 'Passwords do not match', path: ['confirm'] });
type FormValues = z.infer<typeof schema>;

export default function ChangePasswordPage() {
  const toasts = useToasts();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const change = useMutation({
    mutationFn: (values: FormValues) =>
      accountApi.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: () => {
      reset();
      toasts.push('success', 'Password changed');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  return (
    <div className="card" style={{ maxWidth: 440 }}>
      <h2>Change password</h2>
      <p className="text-sm text-muted mb-6">Use at least 12 characters with mixed case and a number.</p>
      <form onSubmit={handleSubmit((v) => change.mutate(v))} noValidate>
        <Field label="Current password" error={errors.currentPassword?.message} htmlFor="current">
          <Input id="current" type="password" autoComplete="current-password" {...register('currentPassword')} />
        </Field>
        <Field label="New password" error={errors.newPassword?.message} htmlFor="new">
          <Input id="new" type="password" autoComplete="new-password" {...register('newPassword')} />
        </Field>
        <Field label="Confirm new password" error={errors.confirm?.message} htmlFor="confirm">
          <Input id="confirm" type="password" autoComplete="new-password" {...register('confirm')} />
        </Field>
        <Button type="submit" loading={isSubmitting || change.isPending}>
          Update password
        </Button>
      </form>
      {toasts.node}
    </div>
  );
}