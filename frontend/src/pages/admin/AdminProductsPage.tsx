import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { staffCatalogApi } from '../../lib/api';
import { formatMoney, humanize } from '../../lib/format';
import { Badge, Button, ErrorState, LoadingState, Pagination, useToasts, errorMessage } from '../../components/ui';
import { ProductImage, productImageUrl } from '../../components/ProductCard';

export default function AdminProductsPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'products', q, page],
    queryFn: () => staffCatalogApi.products(q, page, 15),
  });

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      active ? staffCatalogApi.deactivateProduct(id) : staffCatalogApi.activateProduct(id),
    onSuccess: () => {
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading products…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <div className="flex justify-between items-center mb-4 gap-3 flex-wrap">
        <input
          className="input"
          placeholder="Search by name or SKU…"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          style={{ maxWidth: 320 }}
          aria-label="Search products"
        />
        <Link className="btn btn--primary" to="/admin/products/new">
          <Plus size={18} /> New product
        </Link>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>SKU</th>
              <th>Price</th>
              <th>Availability</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {query.data.content.map((p) => (
              <tr key={p.id}>
                <td>
                  <div className="flex gap-2 items-center">
                    <div style={{ width: 40, height: 40, borderRadius: 8, overflow: 'hidden', background: 'var(--color-bg-alt)' }}>
                      <ProductImage src={productImageUrl(p.primaryMedia?.mediaUrl)} alt="" />
                    </div>
                    <div>
                      <Link to={`/admin/products/${p.id}/edit`} style={{ fontWeight: 600 }}>
                        {p.name}
                      </Link>
                      <div className="text-xs text-faint">{p.brand}</div>
                    </div>
                  </div>
                </td>
                <td>{p.sku}</td>
                <td>{formatMoney(p.effectivePrice)}</td>
                <td><Badge tone={p.availability === 'IN_STOCK' ? 'success' : p.availability === 'LOW_STOCK' ? 'warning' : 'error'}>{humanize(p.availability)}</Badge></td>
                <td><Badge tone={p.active ? 'success' : 'neutral'}>{p.active ? 'Active' : 'Inactive'}</Badge></td>
                <td>
                  <div className="actions justify-end">
                    <Link className="btn btn--outline btn--sm" to={`/admin/products/${p.id}/edit`}>
                      Edit
                    </Link>
                    <Button
                      variant={p.active ? 'outline' : 'dark'}
                      size="sm"
                      onClick={() => toggle.mutate({ id: p.id, active: p.active })}
                      loading={toggle.isPending}
                    >
                      {p.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
      {toasts.node}
    </div>
  );
}