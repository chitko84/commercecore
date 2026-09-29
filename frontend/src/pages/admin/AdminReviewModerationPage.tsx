import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { moderationApi } from '../../lib/api';
import { formatDate, humanize } from '../../lib/format';
import { Badge, Button, EmptyState, ErrorState, Field, LoadingState, Pagination, Select, Stars, useToasts, errorMessage } from '../../components/ui';
import type { ReviewStatus } from '../../lib/types';

export default function AdminReviewModerationPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [status, setStatus] = useState<ReviewStatus | ''>('PENDING');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'reviews', status, page],
    queryFn: () => moderationApi.list(status, page, 15),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] });
    void queryClient.invalidateQueries({ queryKey: ['reviews'] });
    void queryClient.invalidateQueries({ queryKey: ['rating'] });
  };

  const decide = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'approve' | 'reject' }) =>
      action === 'approve' ? moderationApi.approve(id) : moderationApi.reject(id),
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'Review moderated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading reviews…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <h2 className="admin-main__title">Review moderation</h2>
      <div className="filters-bar">
        <Field label="Status">
          <Select value={status} onChange={(e) => { setStatus(e.target.value as ReviewStatus | ''); setPage(0); }}>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </Select>
        </Field>
      </div>
      {query.data.content.length === 0 ? (
        <EmptyState title="No reviews" body="Nothing to moderate in this view." />
      ) : (
        <div>
          {query.data.content.map((r) => (
            <div className="card mb-3" key={r.id}>
              <div className="flex justify-between items-start gap-3 flex-wrap">
                <div>
                  <div className="flex gap-2 items-center mb-1">
                    <Stars rating={r.rating} />
                    <strong>{r.productName}</strong>
                  </div>
                  <div className="text-xs text-faint mb-2">
                    Customer {r.customerId} · {formatDate(r.createdAt)}
                  </div>
                  {r.title && <div className="font-semibold">{r.title}</div>}
                  {r.body && <p className="text-sm text-muted" style={{ margin: 0 }}>{r.body}</p>}
                </div>
                <div className="flex gap-2">
                  <Badge tone={r.status === 'APPROVED' ? 'success' : r.status === 'REJECTED' ? 'error' : 'warning'}>
                    {humanize(r.status)}
                  </Badge>
                  {r.status === 'PENDING' && (
                    <>
                      <Button size="sm" onClick={() => decide.mutate({ id: r.id, action: 'approve' })} disabled={decide.isPending}>
                        Approve
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => decide.mutate({ id: r.id, action: 'reject' })} disabled={decide.isPending}>
                        Reject
                      </Button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
        </div>
      )}
      {toasts.node}
    </div>
  );
}