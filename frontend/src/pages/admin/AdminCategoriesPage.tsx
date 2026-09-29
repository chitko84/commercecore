import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { staffCatalogApi } from '../../lib/api';
import { Badge, Button, ErrorState, Field, Input, LoadingState, Modal, Textarea, useToasts, errorMessage } from '../../components/ui';

export default function AdminCategoriesPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [editing, setEditing] = useState<{ id?: string; form: { name: string; slug: string; description: string; active: boolean } } | null>(null);

  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: staffCatalogApi.categories });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] });

  const save = useMutation({
    mutationFn: () => {
      if (!editing) return Promise.reject(new Error('no form'));
      const body = { name: editing.form.name, slug: editing.form.slug, description: editing.form.description || undefined, parentCategoryId: null, active: editing.form.active };
      return editing.id ? staffCatalogApi.updateCategory(editing.id, body) : staffCatalogApi.createCategory(body);
    },
    onSuccess: () => {
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      setEditing(null);
      toasts.push('success', 'Category saved');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      active ? staffCatalogApi.deactivateCategory(id) : staffCatalogApi.activateCategory(id),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (categories.isPending) return <LoadingState label="Loading categories…" />;
  if (categories.isError) return <ErrorState error={categories.error} />;

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="admin-main__title">Categories</h2>
        <Button onClick={() => setEditing({ form: { name: '', slug: '', description: '', active: true } })}>
          <Plus size={18} /> New category
        </Button>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Slug</th>
              <th>Description</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {categories.data.map((c) => (
              <tr key={c.id}>
                <td><strong>{c.name}</strong></td>
                <td>{c.slug}</td>
                <td className="text-muted">{c.description ?? '—'}</td>
                <td><Badge tone={c.active ? 'success' : 'neutral'}>{c.active ? 'Active' : 'Inactive'}</Badge></td>
                <td>
                  <div className="actions justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setEditing({ id: c.id, form: { name: c.name, slug: c.slug, description: c.description ?? '', active: c.active } })}
                    >
                      Edit
                    </Button>
                    <Button variant={c.active ? 'outline' : 'dark'} size="sm" onClick={() => toggle.mutate({ id: c.id, active: c.active })}>
                      {c.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal open={Boolean(editing)} title={editing?.id ? 'Edit category' : 'New category'} onClose={() => setEditing(null)}>
        {editing && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <Field label="Name">
              <Input value={editing.form.name} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, name: e.target.value } })} />
            </Field>
            <Field label="Slug">
              <Input value={editing.form.slug} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, slug: e.target.value } })} />
            </Field>
            <Field label="Description">
              <Textarea value={editing.form.description} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, description: e.target.value } })} />
            </Field>
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