import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, BellRing } from 'lucide-react';
import { notificationsApi } from '../../lib/api';
import { formatDateTime, humanize } from '../../lib/format';
import { Button, EmptyState, ErrorState, LoadingState, Pagination, useToasts, errorMessage } from '../../components/ui';

export default function AccountNotificationsPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['notifications', page],
    queryFn: () => notificationsApi.list(page, 15),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const markRead = useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const markAllRead = useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'All notifications marked as read');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading notifications…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 style={{ margin: 0 }}>Notifications</h2>
        {query.data.content.some((n) => !n.read) && (
          <Button variant="outline" size="sm" onClick={() => markAllRead.mutate()} loading={markAllRead.isPending}>
            <BellRing size={16} /> Mark all read
          </Button>
        )}
      </div>
      {query.data.content.length === 0 ? (
        <EmptyState title="No notifications" body="Order, payment and account updates will appear here." />
      ) : (
        <div>
          {query.data.content.map((n) => (
            <button
              key={n.id}
              type="button"
              className="card card--flat mb-2"
              style={{ width: '100%', textAlign: 'left', cursor: n.read ? 'default' : 'pointer', opacity: n.read ? 0.72 : 1 }}
              onClick={() => {
                if (!n.read) markRead.mutate(n.id);
              }}
              aria-label={n.read ? `${n.title} (read)` : `${n.title} — mark as read`}
            >
              <div className="flex justify-between gap-3 items-start">
                <span className="flex gap-2 items-center">
                  {!n.read && <Bell size={15} color="var(--color-accent-strong)" />}
                  <strong>{n.title}</strong>
                </span>
                <span className="text-xs text-faint">{formatDateTime(n.createdAt)}</span>
              </div>
              <p className="text-sm text-muted mt-1" style={{ margin: 0 }}>{n.body}</p>
              <span className="text-xs text-faint mt-1" style={{ display: 'block' }}>
                {humanize(n.type)}
              </span>
            </button>
          ))}
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
        </div>
      )}
      {toasts.node}
    </div>
  );
}