import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { staffCatalogApi, staffInventoryApi } from '../../lib/api';
import { Button, Card, ErrorState, Field, Input, LoadingState, Textarea, useToasts, errorMessage } from '../../components/ui';

interface VariantDraft {
  sku: string;
  priceOverride: string;
  active: boolean;
  attributes: { name: string; value: string; displayOrder: number }[];
}

interface MediaDraft {
  mediaUrl: string;
  altText: string;
  displayOrder: number;
  primary: boolean;
}

export default function AdminProductFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const editing = Boolean(id);

  const categories = useQuery({ queryKey: ['categories'], queryFn: staffCatalogApi.categories });
  const existing = useQuery({
    queryKey: ['admin', 'product', id],
    queryFn: () => staffCatalogApi.product(id!),
    enabled: editing,
  });

  const [form, setForm] = useState({
    name: '', slug: '', description: '', sku: '', brand: '', basePrice: '',
    salePrice: '', categoryId: '', active: true,
  });
  const [variants, setVariants] = useState<VariantDraft[]>([]);
  const [media, setMedia] = useState<MediaDraft[]>([]);
  const [inventory, setInventory] = useState({ initialQuantity: 10, lowStockThreshold: 3 });

  useEffect(() => {
    if (existing.data) {
      setForm({
        name: existing.data.name, slug: existing.data.slug, description: existing.data.description,
        sku: existing.data.sku, brand: existing.data.brand, basePrice: existing.data.basePrice,
        salePrice: existing.data.salePrice ?? '', categoryId: existing.data.category.id,
        active: existing.data.active,
      });
      setVariants(
        existing.data.variants.map((v) => ({
          sku: v.sku, priceOverride: v.priceOverride ?? '', active: v.active,
          attributes: v.attributes.map((a) => ({ ...a })),
        })),
      );
      setMedia(
        existing.data.media.map((m) => ({
          mediaUrl: m.mediaUrl, altText: m.altText, displayOrder: m.displayOrder, primary: m.primary,
        })),
      );
    }
  }, [existing.data]);

  const save = useMutation({
    mutationFn: async () => {
      const base = {
        name: form.name, slug: form.slug, description: form.description, sku: form.sku,
        brand: form.brand, basePrice: form.basePrice,
        salePrice: form.salePrice || null,
        categoryId: form.categoryId, active: form.active,
      };
      let saved = editing
        ? await staffCatalogApi.updateProduct(id!, base)
        : await staffCatalogApi.createProduct(base);
      // Apply media (replace if editing to keep ordering simple).
      if (media.length > 0) {
        for (const m of media) {
          if (editing) {
            const existingMedia = existing.data?.media ?? [];
            if (existingMedia.length > 0) {
              await staffCatalogApi.updateMedia(saved.id, existingMedia[0].id, m);
            }
          } else {
            saved = await staffCatalogApi.addMedia(saved.id, m);
          }
        }
      }
      // Variants: create only (editing keeps variant management minimal here).
      for (const v of variants) {
        if (!editing || !existing.data?.variants.some((ev) => ev.sku === v.sku)) {
          await staffCatalogApi.createVariant(saved.id, {
            sku: v.sku,
            priceOverride: v.priceOverride || null,
            active: v.active,
            attributes: v.attributes,
          });
        }
      }
      // Inventory: initialize only for new products.
      if (!editing) {
        await staffInventoryApi.initialize({
          productId: saved.id,
          variantId: null,
          initialQuantity: inventory.initialQuantity,
          lowStockThreshold: inventory.lowStockThreshold,
        });
      }
      return saved;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      toasts.push('success', editing ? 'Product updated' : 'Product created');
      navigate('/admin/products');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const variantsSummary = useMemo(() => variants, [variants]);

  if (editing && existing.isPending) return <LoadingState label="Loading product…" />;
  if (editing && existing.isError) return <ErrorState error={existing.error} />;

  const setAttr = (vi: number, name: string, value: string) => {
    setVariants((prev) => {
      const next = prev.map((v, i) => (i === vi ? { ...v, attributes: [{ name, value, displayOrder: 0 }] } : v));
      return next;
    });
  };

  return (
    <div style={{ maxWidth: 860 }}>
      <h2 className="admin-main__title">{editing ? 'Edit product' : 'New product'}</h2>
      <Card>
        <h3 style={{ fontSize: '1rem' }}>Details</h3>
        <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <Field label="Name">
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="Slug">
            <Input value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} />
          </Field>
        </div>
        <Field label="Description">
          <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <Field label="SKU">
            <Input value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })} />
          </Field>
          <Field label="Brand">
            <Input value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} />
          </Field>
        </div>
        <div className="grid" style={{ gridTemplateColumns: '1fr 1fr 1fr' }}>
          <Field label="Base price">
            <Input type="number" step="0.01" min="0" value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} />
          </Field>
          <Field label="Sale price (optional)">
            <Input type="number" step="0.01" min="0" value={form.salePrice} onChange={(e) => setForm({ ...form, salePrice: e.target.value })} />
          </Field>
          <Field label="Category">
            <select className="select" value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
              <option value="">Select…</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </Field>
        </div>
        <label className="filter-option">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          Active (visible in storefront)
        </label>
      </Card>

      {!editing && (
        <Card className="mt-4">
          <h3 style={{ fontSize: '1rem' }}>Initial inventory</h3>
          <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <Field label="Quantity">
              <Input type="number" min="0" value={inventory.initialQuantity} onChange={(e) => setInventory({ ...inventory, initialQuantity: Number(e.target.value) })} />
            </Field>
            <Field label="Low-stock threshold">
              <Input type="number" min="0" value={inventory.lowStockThreshold} onChange={(e) => setInventory({ ...inventory, lowStockThreshold: Number(e.target.value) })} />
            </Field>
          </div>
        </Card>
      )}

      <Card className="mt-4">
        <h3 style={{ fontSize: '1rem' }}>Media</h3>
        {media.map((m, i) => (
          <div className="flex gap-2 mb-2 items-center" key={i}>
            <Input
              placeholder="Media URL (e.g. /products/headphones.jpg)"
              value={m.mediaUrl}
              onChange={(e) => setMedia((prev) => prev.map((mm, j) => (j === i ? { ...mm, mediaUrl: e.target.value } : mm)))}
            />
            <Input
              placeholder="Alt text"
              value={m.altText}
              onChange={(e) => setMedia((prev) => prev.map((mm, j) => (j === i ? { ...mm, altText: e.target.value } : mm)))}
            />
            <Button variant="ghost" size="sm" onClick={() => setMedia((prev) => prev.filter((_, j) => j !== i))}>
              Remove
            </Button>
          </div>
        ))}
        <Button variant="outline" size="sm" onClick={() => setMedia((prev) => [...prev, { mediaUrl: '', altText: '', displayOrder: prev.length, primary: prev.length === 0 }])}>
          + Add media
        </Button>
      </Card>

      <Card className="mt-4">
        <h3 style={{ fontSize: '1rem' }}>Variants</h3>
        {variantsSummary.map((v, i) => (
          <div className="card card--flat mb-2" key={i}>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr 1fr' }}>
              <Field label="SKU">
                <Input value={v.sku} onChange={(e) => setVariants((prev) => prev.map((vv, j) => (j === i ? { ...vv, sku: e.target.value } : vv)))} />
              </Field>
              <Field label="Price override (optional)">
                <Input value={v.priceOverride} onChange={(e) => setVariants((prev) => prev.map((vv, j) => (j === i ? { ...vv, priceOverride: e.target.value } : vv)))} />
              </Field>
              <Field label="Attribute (e.g. Color)">
                <Input value={v.attributes[0]?.name ?? ''} onChange={(e) => setAttr(i, e.target.value, v.attributes[0]?.value ?? '')} />
              </Field>
            </div>
            <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              <Field label="Attribute value (e.g. Midnight)">
                <Input value={v.attributes[0]?.value ?? ''} onChange={(e) => setVariants((prev) => prev.map((vv, j) => (j === i ? { ...vv, attributes: [{ name: vv.attributes[0]?.name ?? 'Color', value: e.target.value, displayOrder: 0 }] } : vv)))} />
              </Field>
              <label className="filter-option" style={{ alignSelf: 'end', marginBottom: '1rem' }}>
                <input type="checkbox" checked={v.active} onChange={(e) => setVariants((prev) => prev.map((vv, j) => (j === i ? { ...vv, active: e.target.checked } : vv)))} />
                Active
              </label>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setVariants((prev) => prev.filter((_, j) => j !== i))}>
              Remove variant
            </Button>
          </div>
        ))}
        <Button variant="outline" size="sm" onClick={() => setVariants((prev) => [...prev, { sku: '', priceOverride: '', active: true, attributes: [{ name: 'Color', value: '', displayOrder: 0 }] }])}>
          + Add variant
        </Button>
      </Card>

      <div className="flex gap-3 mt-6">
        <Button
          size="lg"
          onClick={() => save.mutate()}
          loading={save.isPending}
          disabled={!form.name || !form.slug || !form.sku || !form.brand || !form.basePrice || !form.categoryId}
        >
          {editing ? 'Save changes' : 'Create product'}
        </Button>
        <Button variant="ghost" size="lg" onClick={() => navigate('/admin/products')}>
          Cancel
        </Button>
      </div>
      {toasts.node}
    </div>
  );
}