import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountApi } from '../../lib/api';
import { Button, Card, EmptyState, ErrorState, Field, Input, LoadingState, Modal, useToasts, errorMessage } from '../../components/ui';
import type { AddressRequest } from '../../lib/types';

const EMPTY: AddressRequest = {
  label: '', recipientName: '', phone: '', addressLine1: '', addressLine2: '',
  city: '', stateRegion: '', postalCode: '', countryCode: '', defaultShipping: false, defaultBilling: false,
};

export default function AccountAddressesPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [editing, setEditing] = useState<{ id?: string; form: AddressRequest } | null>(null);

  const addresses = useQuery({ queryKey: ['addresses'], queryFn: accountApi.addresses });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['addresses'] });

  const save = useMutation({
    mutationFn: () => {
      if (!editing) return Promise.reject(new Error('no form'));
      return editing.id
        ? accountApi.updateAddress(editing.id, editing.form)
        : accountApi.createAddress(editing.form);
    },
    onSuccess: () => {
      invalidate();
      setEditing(null);
      toasts.push('success', 'Address saved');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => accountApi.deleteAddress(id),
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'Address removed');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (addresses.isPending) return <LoadingState label="Loading addresses…" />;
  if (addresses.isError) return <ErrorState error={addresses.error} />;

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 style={{ margin: 0 }}>Addresses</h2>
        <Button onClick={() => setEditing({ form: { ...EMPTY } })}>Add address</Button>
      </div>
      {addresses.data.length === 0 ? (
        <EmptyState title="No addresses yet" body="Add an address to make checkout faster." />
      ) : (
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }}>
          {addresses.data.map((addr) => (
            <Card key={addr.id} className="card--flat">
              <h3 style={{ fontSize: '1rem' }}>{addr.label}</h3>
              <p className="text-sm text-muted" style={{ whiteSpace: 'pre-line' }}>
                {addr.recipientName}
                {'\n'}
                {addr.addressLine1}
                {'\n'}
                {addr.city}, {addr.stateRegion} {addr.postalCode}
                {'\n'}
                {addr.countryCode}
              </p>
              <div className="flex gap-1 flex-wrap">
                {addr.defaultShipping && <span className="badge badge--accent">Default shipping</span>}
                {addr.defaultBilling && <span className="badge badge--accent">Default billing</span>}
              </div>
              <div className="flex gap-2 mt-4">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setEditing({
                      id: addr.id,
                      form: {
                        label: addr.label, recipientName: addr.recipientName, phone: addr.phone,
                        addressLine1: addr.addressLine1, addressLine2: addr.addressLine2 ?? '',
                        city: addr.city, stateRegion: addr.stateRegion, postalCode: addr.postalCode,
                        countryCode: addr.countryCode, defaultShipping: addr.defaultShipping,
                        defaultBilling: addr.defaultBilling,
                      },
                    })
                  }
                >
                  Edit
                </Button>
                <Button variant="danger" size="sm" onClick={() => remove.mutate(addr.id)}>
                  Delete
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={Boolean(editing)} title={editing?.id ? 'Edit address' : 'Add address'} onClose={() => setEditing(null)}>
        {editing && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Label">
                <Input value={editing.form.label} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, label: e.target.value } })} />
              </Field>
              <Field label="Recipient name">
                <Input value={editing.form.recipientName} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, recipientName: e.target.value } })} />
              </Field>
            </div>
            <Field label="Phone">
              <Input value={editing.form.phone} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, phone: e.target.value } })} />
            </Field>
            <Field label="Address line 1">
              <Input value={editing.form.addressLine1} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, addressLine1: e.target.value } })} />
            </Field>
            <Field label="Address line 2 (optional)">
              <Input value={editing.form.addressLine2 ?? ''} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, addressLine2: e.target.value } })} />
            </Field>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="City">
                <Input value={editing.form.city} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, city: e.target.value } })} />
              </Field>
              <Field label="State / region">
                <Input value={editing.form.stateRegion} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, stateRegion: e.target.value } })} />
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Postal code">
                <Input value={editing.form.postalCode} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, postalCode: e.target.value } })} />
              </Field>
              <Field label="Country code">
                <Input value={editing.form.countryCode} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, countryCode: e.target.value } })} />
              </Field>
            </div>
            <label className="filter-option">
              <input
                type="checkbox"
                checked={editing.form.defaultShipping}
                onChange={(e) => setEditing({ ...editing, form: { ...editing.form, defaultShipping: e.target.checked } })}
              />
              Default shipping address
            </label>
            <label className="filter-option">
              <input
                type="checkbox"
                checked={editing.form.defaultBilling}
                onChange={(e) => setEditing({ ...editing, form: { ...editing.form, defaultBilling: e.target.checked } })}
              />
              Default billing address
            </label>
            <div className="flex gap-3 mt-4">
              <Button type="submit" loading={save.isPending}>Save</Button>
              <Button variant="ghost" onClick={() => setEditing(null)}>Cancel</Button>
            </div>
          </form>
        )}
      </Modal>
      {toasts.node}
    </div>
  );
}