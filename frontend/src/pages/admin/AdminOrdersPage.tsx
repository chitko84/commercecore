import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { staffOrdersApi } from '../../lib/api';
import type { StaffOrderQuery } from '../../lib/api';
import { formatDateTime, formatMoney, humanize } from '../../lib/format';
import { Badge, ErrorState, Field, Input, LoadingState, Pagination, Select } from '../../components/ui';
import type { OrderStatus } from '../../lib/types';

const STATUSES: (OrderStatus | '')[] = ['', 'PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminOrdersPage() {
  const [orderNumber, setOrderNumber] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);

  const query: StaffOrderQuery = {
    orderNumber: orderNumber || undefined,
    customerEmail: customerEmail || undefined,
    status: status || undefined,
    page,
    size: 15,
  };

  const data = useQuery({ queryKey: ['admin', 'orders', query], queryFn: () => staffOrdersApi.list(query) });

  if (data.isPending) return <LoadingState label="Loading orders…" />;
  if (data.isError) return <ErrorState error={data.error} />;

  return (
    <div>
      <h2 className="admin-main__title">Orders</h2>
      <div className="filters-bar">
        <Field label="Order number">
          <Input value={orderNumber} onChange={(e) => { setOrderNumber(e.target.value); setPage(0); }} placeholder="e.g. CC-…" />
        </Field>
        <Field label="Customer email">
          <Input value={customerEmail} onChange={(e) => { setCustomerEmail(e.target.value); setPage(0); }} />
        </Field>
        <Field label="Status">
          <Select value={status} onChange={(e) => { setStatus(e.target.value as OrderStatus | ''); setPage(0); }}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{s ? humanize(s) : 'All statuses'}</option>
            ))}
          </Select>
        </Field>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Status</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.data.content.map((o) => (
              <tr key={o.id}>
                <td><strong>{o.orderNumber}</strong></td>
                <td>{o.addresses.find((a) => a.type === 'SHIPPING')?.recipientName ?? '—'}</td>
                <td>{formatDateTime(o.createdAt)}</td>
                <td><Badge tone={o.status === 'CANCELLED' ? 'error' : o.status === 'DELIVERED' || o.status === 'CONFIRMED' ? 'success' : o.status === 'PENDING' ? 'warning' : 'info'}>{humanize(o.status)}</Badge></td>
                <td>{formatMoney(o.grandTotal)}</td>
                <td><Link className="btn btn--outline btn--sm" to={`/admin/orders/${o.id}`}>Manage</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} size={data.data.size} onChange={setPage} />
    </div>
  );
}