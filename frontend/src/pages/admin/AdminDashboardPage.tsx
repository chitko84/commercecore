import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../../lib/api';
import { formatMoney, formatDateTime, humanize } from '../../lib/format';
import { Badge, ErrorState, LoadingState } from '../../components/ui';

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'var(--color-warning)',
  CONFIRMED: 'var(--color-info)',
  PROCESSING: 'var(--color-info)',
  SHIPPED: 'var(--color-info)',
  DELIVERED: 'var(--color-success)',
  CANCELLED: 'var(--color-error)',
};

export default function AdminDashboardPage() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.get });

  if (dash.isPending) return <LoadingState label="Loading dashboard…" />;
  if (dash.isError) return <ErrorState error={dash.error} title="Could not load dashboard" />;

  const d = dash.data;
  const maxStatus = Math.max(1, ...Object.values(d.ordersByStatus));

  return (
    <div>
      <h2 className="admin-main__title">Dashboard</h2>

      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-card__label">Total revenue</div>
          <div className="stat-card__value">{formatMoney(d.totalRevenue)}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Net revenue</div>
          <div className="stat-card__value stat-card__value--accent">{formatMoney(d.netRevenue)}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Customers</div>
          <div className="stat-card__value">{d.customerCount}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Active products</div>
          <div className="stat-card__value">{d.activeProductCount}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Low stock</div>
          <div className="stat-card__value" style={{ color: d.lowStockCount > 0 ? 'var(--color-warning)' : undefined }}>
            {d.lowStockCount}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Failed payments</div>
          <div className="stat-card__value" style={{ color: d.failedPaymentCount > 0 ? 'var(--color-error)' : undefined }}>
            {d.failedPaymentCount}
          </div>
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="card">
          <h3 style={{ fontSize: '1rem' }}>Orders by status</h3>
          <div className="chart-bar-row" aria-label="Orders by status chart">
            {Object.entries(d.ordersByStatus).map(([status, count]) => (
              <div key={status} className="chart-bar" style={{ height: `${Math.max(2, (count / maxStatus) * 100)}%`, background: STATUS_COLORS[status] }} title={`${humanize(status)}: ${count}`}>
                <span className="chart-bar__label">{status.slice(0, 3)}</span>
              </div>
            ))}
          </div>
          <div className="chart-legend">
            {Object.entries(d.ordersByStatus).map(([status, count]) => (
              <span key={status}>
                <span className="chart-legend__swatch" style={{ background: STATUS_COLORS[status] }} />
                {humanize(status)} · {count}
              </span>
            ))}
          </div>
        </div>

        <div className="card">
          <h3 style={{ fontSize: '1rem' }}>Recent orders</h3>
          {d.recentOrders.length === 0 ? (
            <p className="text-sm text-muted">No orders yet.</p>
          ) : (
            <ul style={{ listStyle: 'none' }}>
              {d.recentOrders.map((o) => (
                <li key={o.id} className="flex justify-between items-center py-2" style={{ borderBottom: '1px solid var(--color-line)' }}>
                  <div>
                    <Link to={`/admin/orders/${o.id}`} style={{ fontWeight: 600 }}>
                      {o.orderNumber}
                    </Link>
                    <div className="text-xs text-faint">{formatDateTime(o.createdAt)}</div>
                  </div>
                  <div className="flex gap-2 items-center">
                    <span className="text-sm">{formatMoney(o.grandTotal)}</span>
                    <Badge tone={o.status === 'CANCELLED' ? 'error' : o.status === 'DELIVERED' || o.status === 'CONFIRMED' ? 'success' : o.status === 'PENDING' ? 'warning' : 'info'}>
                      {humanize(o.status)}
                    </Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}