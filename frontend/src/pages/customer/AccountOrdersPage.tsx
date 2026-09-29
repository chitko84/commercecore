import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ordersApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize } from '../../lib/format';
import { Badge, EmptyState, ErrorState, LoadingState, Pagination } from '../../components/ui';

export default function AccountOrdersPage() {
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: ['orders', 'mine', page], queryFn: () => ordersApi.mine(page, 10) });

  if (query.isPending) return <LoadingState label="Loading orders…" />;
  if (query.isError) return <ErrorState error={query.error} title="Could not load orders" />;
  if (query.data.content.length === 0) {
    return (
      <EmptyState
        title="No orders yet"
        body="Your orders will appear here once you check out."
        action={
          <Link className="btn btn--dark" to="/catalog">
            Start shopping
          </Link>
        }
      />
    );
  }

  return (
    <div>
      <h2>Orders</h2>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Date</th>
              <th>Status</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {query.data.content.map((o) => (
              <tr key={o.id}>
                <td><strong>{o.orderNumber}</strong></td>
                <td>{formatDateTime(o.createdAt)}</td>
                <td><Badge tone={o.status === 'CANCELLED' ? 'error' : o.status === 'DELIVERED' || o.status === 'CONFIRMED' ? 'success' : o.status === 'PENDING' ? 'warning' : 'info'}>{humanize(o.status)}</Badge></td>
                <td>{formatMoney(o.grandTotal)}</td>
                <td><Link className="btn btn--outline btn--sm" to={`/account/orders/${o.id}`}>View</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
    </div>
  );
}