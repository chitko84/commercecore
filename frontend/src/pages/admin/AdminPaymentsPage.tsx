import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { reportsApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize, toInstant } from '../../lib/format';
import { Badge, Button, ErrorState, Field, Input, LoadingState, Pagination, Select } from '../../components/ui';
import type { PaymentStatus } from '../../lib/types';

function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - 90 * 86400_000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export default function AdminPaymentsPage() {
  const [range, setRange] = useState(defaultRange());
  const [status, setStatus] = useState<PaymentStatus | ''>('');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'payments-report', range, status, page],
    queryFn: () =>
      reportsApi.payments({ from: range.from, to: range.to, status: status || undefined, page, size: 15 }),
  });

  if (query.isPending) return <LoadingState label="Loading payments…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <h2 className="admin-main__title">Payments &amp; refunds</h2>
      <div className="filters-bar">
        <Field label="From">
          <Input type="date" value={range.from.slice(0, 10)} onChange={(e) => setRange({ ...range, from: toInstant(`${e.target.value}T00:00:00`) })} />
        </Field>
        <Field label="To">
          <Input type="date" value={range.to.slice(0, 10)} onChange={(e) => setRange({ ...range, to: toInstant(`${e.target.value}T23:59:59`) })} />
        </Field>
        <Field label="Status">
          <Select value={status} onChange={(e) => { setStatus(e.target.value as PaymentStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {['PENDING', 'AUTHORIZED', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED'].map((s) => (
              <option key={s} value={s}>{humanize(s)}</option>
            ))}
          </Select>
        </Field>
        <Button
          variant="outline"
          onClick={() => reportsApi.paymentsCsv({ from: range.from, to: range.to })}
        >
          <Download size={16} /> CSV
        </Button>
      </div>

      <div className="stat-grid">
        <div className="stat-card"><div className="stat-card__label">Total payments</div><div className="stat-card__value">{query.data.totalPayments}</div></div>
        <div className="stat-card"><div className="stat-card__label">Total amount</div><div className="stat-card__value">{formatMoney(query.data.totalAmount)}</div></div>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Customer</th>
              <th>Method</th>
              <th>Status</th>
              <th>Amount</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {query.data.payments.map((p) => (
              <tr key={p.id}>
                <td>{p.orderNumber}</td>
                <td>{p.customerEmail}</td>
                <td>{humanize(p.paymentMethod)}</td>
                <td>
                  <Badge tone={p.status === 'PAID' || p.status === 'REFUNDED' ? 'success' : p.status === 'FAILED' ? 'error' : p.status === 'PARTIALLY_REFUNDED' ? 'info' : 'warning'}>
                    {humanize(p.status)}
                  </Badge>
                </td>
                <td>{formatMoney(p.amount)}</td>
                <td>{formatDateTime(p.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
    </div>
  );
}