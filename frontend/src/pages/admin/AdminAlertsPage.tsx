import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import { adminAlertsApi } from '../../lib/api';
import { formatDateTime, humanize } from '../../lib/format';
import { Badge, Button, EmptyState, ErrorState, LoadingState, Pagination, Select, useToasts, errorMessage } from '../../components/ui';

export default function AdminAlertsPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [ack, setAck] = useState<'open' | 'all'>('open');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'alerts', ack, page],
    queryFn: () => adminAlertsApi.list({ acknowledged: ack === 'all' ? undefined : false, page, size: 20 }),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'alerts'] });
  };

  const acknowledge = useMutation({
    mutationFn: (id: string) => adminAlertsApi.acknowledge(id),
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'Alert acknowledged');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading alerts…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <h2 className="admin-main__title">Operational alerts</h2>
      <div className="filters-bar">
        <Select value={ack} onChange={(e) => { setAck(e.target.value as 'open' | 'all'); setPage(0); }} aria-label="Alert filter">
          <option value="open">Unacknowledged</option>
          <option value="all">All alerts</option>
        </Select>
      </div>
      {query.data.content.length === 0 ? (
        <EmptyState title="No alerts" body="Low-stock and failed-payment alerts will appear here." />
      ) : (
        <div>
          {query.data.content.map((a) => {
            const isLowStock = a.type === 'LOW_STOCK_ALERT';
            return (
              <div className="card mb-3" key={a.id}>
                <div className="flex justify-between items-start gap-3 flex-wrap">
                  <div className="flex gap-3">
                    <span style={{ color: isLowStock ? 'var(--color-warning)' : 'var(--color-error)', marginTop: 2 }}>
                      {isLowStock ? <AlertTriangle size={20} /> : <AlertTriangle size={20} />}
                    </span>
                    <div>
                      <div className="flex gap-2 items-center flex-wrap">
                        <strong>{a.title}</strong>
                        <Badge tone={isLowStock ? 'warning' : 'error'}>{humanize(a.type)}</Badge>
                        {a.acknowledged && <Badge tone="neutral">Acknowledged</Badge>}
                      </div>
                      <p className="text-sm text-muted mb-1" style={{ margin: 0 }}>{a.body}</p>
                      <span className="text-xs text-faint">{formatDateTime(a.createdAt)}</span>
                    </div>
                  </div>
                  {!a.acknowledged && (
                    <Button variant="outline" size="sm" onClick={() => acknowledge.mutate(a.id)} disabled={acknowledge.isPending}>
                      <CheckCircle2 size={15} /> Acknowledge
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
        </div>
      )}
      {toasts.node}
    </div>
  );
}