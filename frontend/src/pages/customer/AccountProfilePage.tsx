import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { accountApi } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { formatDateTime } from '../../lib/format';
import { Badge, Button, Field, Input, useToasts, errorMessage } from '../../components/ui';

const schema = z.object({
  fullName: z.string().min(2, 'Enter your full name').max(200),
  phone: z.string().max(32).optional().or(z.literal('')),
});
type FormValues = z.infer<typeof schema>;

export default function AccountProfilePage() {
  const { profile, refreshProfile } = useAuth();
  const queryClient = useQueryClient();
  const toasts = useToasts();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: profile?.fullName ?? '', phone: profile?.phone ?? '' },
  });

  const save = useMutation({
    mutationFn: (values: FormValues) =>
      accountApi.updateProfile({ fullName: values.fullName, phone: values.phone || undefined }),
    onSuccess: async () => {
      await refreshProfile();
      void queryClient.invalidateQueries({ queryKey: ['profile'] });
      toasts.push('success', 'Profile updated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (!profile) return null;

  return (
    <div className="card">
      <h2 style={{ marginBottom: 0 }}>Profile</h2>
      <p className="text-muted text-sm mb-6">Signed in as {profile.email}</p>
      <div className="flex gap-2 mb-6 flex-wrap">
        <Badge tone="dark">{profile.role}</Badge>
        <Badge tone={profile.accountStatus === 'ACTIVE' ? 'success' : 'warning'}>{profile.accountStatus}</Badge>
        <Badge tone={profile.emailVerified ? 'success' : 'warning'}>
          {profile.emailVerified ? 'Email verified' : 'Email not verified'}
        </Badge>
      </div>
      <form
        onSubmit={handleSubmit((values) => save.mutate(values))}
        noValidate
        style={{ maxWidth: 420 }}
      >
        <Field label="Full name" error={errors.fullName?.message} htmlFor="fullName">
          <Input id="fullName" {...register('fullName')} />
        </Field>
        <Field label="Phone" error={errors.phone?.message} htmlFor="phone">
          <Input id="phone" {...register('phone')} />
        </Field>
        <Button type="submit" loading={isSubmitting || save.isPending}>
          Save changes
        </Button>
      </form>
      <p className="text-xs text-faint mt-6">Member since {formatDateTime(profile.createdAt)}</p>
      {toasts.node}
    </div>
  );
}