import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { staffCatalogApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize } from '../../lib/format';
import { Badge, Button, ErrorState, Field, Input, LoadingState, Modal, Select, useToasts, errorMessage } from '../../components/ui';
import type { PromotionType, PromotionScope } from '../../lib/types';

interface PromoForm {
  code: string; name: string; description: string; type: PromotionType; scope: PromotionScope;
  discountValue: string; minimumSpend: string; maximumDiscount: string;
  startsAt: string; endsAt: string; totalUsageLimit: string; perUserUsageLimit: string; active: boolean;
}

const EMPTY: PromoForm = {
  code: '', name: '', description: '', type: 'PERCENTAGE', scope: 'CART', discountValue: '10',
  minimumSpend: '0', maximumDiscount: '', startsAt: '', endsAt: '', totalUsageLimit: '',
  perUserUsageLimit: '', active: true,
};

export default function AdminPromotionsPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [editing, setEditing] = useState<{ id?: string; form: PromoForm } | null>(null);

  const promos = useQuery({ queryKey: ['admin', 'promotions'], queryFn: staffCatalogApi.promotions });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['admin', 'promotions'] });

  const save = useMutation({
    mutationFn: () => {
      if (!editing) return Promise.reject(new Error('no form'));
      const f = editing.form;
      const body = {
        code: f.code, name: f.name, description: f.description || undefined, type: f.type, scope: f.scope,
        discountValue: f.discountValue, minimumSpend: f.minimumSpend,
        maximumDiscount: f.maximumDiscount || null,
        startsAt: new Date(f.startsAt || Date.now()).toISOString(),
        endsAt: new Date(f.endsAt || Date.now() + 7 * 86400_000).toISOString(),
        totalUsageLimit: f.totalUsageLimit ? Number(f.totalUsageLimit) : null,
        perUserUsageLimit: f.perUserUsageLimit ? Number(f.perUserUsageLimit) : null,
        active: f.active, targetProductId: null, targetCategoryId: null,
      };
      return editing.id ? staffCatalogApi.updatePromotion(editing.id, body) : staffCatalogApi.createPromotion(body);
    },
    onSuccess: () => {
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      setEditing(null);
      toasts.push('success', 'Promotion saved');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      active ? staffCatalogApi.deactivatePromotion(id) : staffCatalogApi.activatePromotion(id),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (promos.isPending) return <LoadingState label="Loading promotions…" />;
  if (promos.isError) return <ErrorState error={promos.error} />;

  const now = new Date();
  const defaultStarts = now.toISOString().slice(0, 16);
  const defaultEnds = new Date(now.getTime() + 7 * 86400_000).toISOString().slice(0, 16);

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="admin-main__title">Promotions</h2>
        <Button onClick={() => setEditing({ form: { ...EMPTY, startsAt: defaultStarts, endsAt: defaultEnds } })}>
          <Plus size={18} /> New promotion
        </Button>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Type</th>
              <th>Value</th>
              <th>Period</th>
              <th>Redemptions</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {promos.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.code}</strong>
                  <div className="text-xs text-faint">{p.name}</div>
                </td>
                <td>{humanize(p.type)} · {humanize(p.scope)}</td>
                <td>
                  {p.type === 'PERCENTAGE' ? `${p.discountValue}%` : p.type === 'FIXED_AMOUNT' ? formatMoney(p.discountValue) : 'Free shipping'}
                </td>
                <td className="text-xs text-muted">
                  {formatDateTime(p.startsAt)} → {formatDateTime(p.endsAt)}
                </td>
                <td>{p.totalRedemptions}{p.totalUsageLimit ? ` / ${p.totalUsageLimit}` : ''}</td>
                <td>
                  <Badge tone={p.active ? 'success' : 'neutral'}>{p.active ? 'Active' : 'Inactive'}</Badge>
                </td>
                <td>
                  <div className="actions justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        setEditing({
                          id: p.id,
                          form: {
                            code: p.code, name: p.name, description: p.description ?? '', type: p.type, scope: p.scope,
                            discountValue: p.discountValue, minimumSpend: p.minimumSpend,
                            maximumDiscount: p.maximumDiscount ?? '', startsAt: p.startsAt.slice(0, 16),
                            endsAt: p.endsAt.slice(0, 16), totalUsageLimit: p.totalUsageLimit ? String(p.totalUsageLimit) : '',
                            perUserUsageLimit: p.perUserUsageLimit ? String(p.perUserUsageLimit) : '', active: p.active,
                          },
                        })
                      }
                    >
                      Edit
                    </Button>
                    <Button variant={p.active ? 'outline' : 'dark'} size="sm" onClick={() => toggle.mutate({ id: p.id, active: p.active })}>
                      {p.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal open={Boolean(editing)} title={editing?.id ? 'Edit promotion' : 'New promotion'} onClose={() => setEditing(null)}>
        {editing && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Code">
                <Input value={editing.form.code} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, code: e.target.value } })} />
              </Field>
              <Field label="Name">
                <Input value={editing.form.name} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, name: e.target.value } })} />
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Type">
                <Select value={editing.form.type} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, type: e.target.value as PromotionType } })}>
                  <option value="PERCENTAGE">Percentage</option>
                  <option value="FIXED_AMOUNT">Fixed amount</option>
                  <option value="FREE_SHIPPING">Free shipping</option>
                </Select>
              </Field>
              <Field label="Scope">
                <Select value={editing.form.scope} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, scope: e.target.value as PromotionScope } })}>
                  <option value="CART">Cart-wide</option>
                  <option value="PRODUCT">Product</option>
                  <option value="CATEGORY">Category</option>
                </Select>
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Discount value">
                <Input type="number" step="0.01" value={editing.form.discountValue} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, discountValue: e.target.value } })} />
              </Field>
              <Field label="Minimum spend">
                <Input type="number" step="0.01" value={editing.form.minimumSpend} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, minimumSpend: e.target.value } })} />
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Starts">
                <Input type="datetime-local" value={editing.form.startsAt} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, startsAt: e.target.value } })} />
              </Field>
              <Field label="Ends">
                <Input type="datetime-local" value={editing.form.endsAt} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, endsAt: e.target.value } })} />
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Usage limit (optional)">
                <Input type="number" value={editing.form.totalUsageLimit} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, totalUsageLimit: e.target.value } })} />
              </Field>
              <Field label="Per-user limit (optional)">
                <Input type="number" value={editing.form.perUserUsageLimit} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, perUserUsageLimit: e.target.value } })} />
              </Field>
            </div>
            <label className="filter-option">
              <input type="checkbox" checked={editing.form.active} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, active: e.target.checked } })} />
              Active
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