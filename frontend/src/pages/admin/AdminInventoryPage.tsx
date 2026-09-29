import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { staffInventoryApi } from '../../lib/api';
import { formatDateTime, humanize } from '../../lib/format';
import { Badge, Button, EmptyState, ErrorState, Field, Input, LoadingState, Modal, useToasts, errorMessage } from '../../components/ui';

export default function AdminInventoryPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [action, setAction] = useState<{ id: string; kind: 'restock' | 'adjust'; quantity: string; reason: string } | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);

  const inventory = useQuery({ queryKey: ['admin', 'inventory'], queryFn: staffInventoryApi.list });
  const history = useQuery({
    queryKey: ['admin', 'inventory', 'history', historyId],
    queryFn: () => staffInventoryApi.history(historyId!),
    enabled: Boolean(historyId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    void queryClient.invalidateQueries({ queryKey: ['admin', 'alerts'] });
  };

  const runAction = useMutation({
    mutationFn: async () => {
      if (!action) return;
      if (action.kind === 'restock') {
        await staffInventoryApi.restock(action.id, { quantity: Number(action.quantity), reason: action.reason });
      } else {
        await staffInventoryApi.adjust(action.id, { delta: Number(action.quantity), reason: action.reason });
      }
    },
    onSuccess: () => {
      invalidate();
      setAction(null);
      toasts.push('success', 'Stock updated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (inventory.isPending) return <LoadingState label="Loading inventory…" />;
  if (inventory.isError) return <ErrorState error={inventory.error} />;
  if (inventory.data.length === 0) {
    return <EmptyState title="No inventory records" body="Initialize inventory when creating products." />;
  }

  const lowStock = inventory.data.filter((i) => i.stockStatus === 'LOW_STOCK' || i.stockStatus === 'OUT_OF_STOCK');

  return (
    <div>
      <h2 className="admin-main__title">Inventory</h2>
      {lowStock.length > 0 && (
        <div className="card mb-4" style={{ borderColor: 'var(--color-warning)', background: 'var(--color-warning-bg)' }}>
          <strong>{lowStock.length} item{lowStock.length > 1 ? 's' : ''} at or below the low-stock threshold.</strong>
          <span className="text-sm"> Restock to avoid sellouts.</span>
        </div>
      )}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Owner</th>
              <th>Total</th>
              <th>Reserved</th>
              <th>Sold</th>
              <th>Available</th>
              <th>Threshold</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {inventory.data.map((inv) => (
              <tr key={inv.id}>
                <td><strong>{inv.productId}</strong></td>
                <td>{inv.totalQuantity}</td>
                <td>{inv.reservedQuantity}</td>
                <td>{inv.soldQuantity}</td>
                <td><strong>{inv.availableQuantity}</strong></td>
                <td>{inv.lowStockThreshold}</td>
                <td>
                  <Badge tone={inv.stockStatus === 'IN_STOCK' ? 'success' : inv.stockStatus === 'LOW_STOCK' ? 'warning' : 'error'}>
                    {humanize(inv.stockStatus)}
                  </Badge>
                </td>
                <td>
                  <div className="actions justify-end">
                    <Button variant="outline" size="sm" onClick={() => setAction({ id: inv.id, kind: 'restock', quantity: '', reason: '' })}>
                      Restock
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setAction({ id: inv.id, kind: 'adjust', quantity: '', reason: '' })}>
                      Adjust
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setHistoryId(inv.id)}>
                      History
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal open={Boolean(action)} title={action?.kind === 'restock' ? 'Restock' : 'Adjust stock'} onClose={() => setAction(null)}>
        {action && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              runAction.mutate();
            }}
          >
            <Field label={action.kind === 'restock' ? 'Quantity to add' : 'Delta (positive increase, negative decrease)'}>
              <Input type="number" step="1" value={action.quantity} onChange={(e) => setAction({ ...action, quantity: e.target.value })} autoFocus />
            </Field>
            <Field label="Reason">
              <Input value={action.reason} onChange={(e) => setAction({ ...action, reason: e.target.value })} />
            </Field>
            <div className="flex gap-3">
              <Button type="submit" loading={runAction.isPending}>Apply</Button>
              <Button variant="ghost" onClick={() => setAction(null)}>Cancel</Button>
            </div>
          </form>
        )}
      </Modal>

      <Modal open={Boolean(historyId)} title="Stock movement history" onClose={() => setHistoryId(null)}>
        {history.isPending ? (
          <LoadingState />
        ) : history.data ? (
          <ul style={{ listStyle: 'none' }}>
            {history.data.map((h) => (
              <li key={h.id} className="py-2" style={{ borderBottom: '1px solid var(--color-line)' }}>
                <div className="flex justify-between">
                  <strong>{humanize(h.movementType)}</strong>
                  <span>{h.quantity > 0 ? '+' : ''}{h.quantity}</span>
                </div>
                <div className="text-xs text-faint">
                  {formatDateTime(h.occurredAt)} · total {h.afterTotal} · reserved {h.afterReserved} · sold {h.afterSold}
                </div>
                {h.reason && <div className="text-xs text-muted">{h.reason}</div>}
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState title="No history" />
        )}
      </Modal>
      {toasts.node}
    </div>
  );
}