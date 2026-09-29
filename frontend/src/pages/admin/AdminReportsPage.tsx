import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { reportsApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize, toInstant } from '../../lib/format';
import { Badge, Button, ErrorState, Field, Input, LoadingState, Select, useToasts, errorMessage } from '../../components/ui';
import type { OrderStatus, PaymentStatus } from '../../lib/types';

function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - 90 * 86400_000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export default function AdminReportsPage() {
  const toasts = useToasts();
  const [range, setRange] = useState(defaultRange());
  const [tab, setTab] = useState<
    'sales' | 'top-products' | 'reviews' | 'orders' | 'customers' | 'inventory' | 'payments' | 'refunds'
  >('sales');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const r = { from: range.from, to: range.to };

  const sales = useQuery({ queryKey: ['report', 'sales', r], queryFn: () => reportsApi.sales(r) });
  const top = useQuery({ queryKey: ['report', 'top', r], queryFn: () => reportsApi.topProducts(r) });
  const reviews = useQuery({ queryKey: ['report', 'reviews', r], queryFn: () => reportsApi.reviews(r) });
  const orders = useQuery({
    queryKey: ['report', 'orders', r, status, page],
    queryFn: () => reportsApi.orders({ ...r, status: (status || undefined) as OrderStatus | undefined, page, size: 15 }),
  });
  const customers = useQuery({ queryKey: ['report', 'customers', r, page], queryFn: () => reportsApi.customers({ ...r, page, size: 15 }) });
  const inventory = useQuery({ queryKey: ['report', 'inventory', r, page], queryFn: () => reportsApi.inventory({ ...r, page, size: 15 }) });
  const payments = useQuery({
    queryKey: ['report', 'payments', r, status, page],
    queryFn: () => reportsApi.payments({ ...r, status: (status || undefined) as PaymentStatus | undefined, page, size: 15 }),
  });
  const refunds = useQuery({ queryKey: ['report', 'refunds', r, page], queryFn: () => reportsApi.refunds({ ...r, page, size: 15 }) });

  const download = (fn: () => Promise<void>, label: string) => {
    fn().catch((e) => toasts.push('error', errorMessage(e) + ` (${label})`));
  };

  const pending =
    sales.isPending || top.isPending || reviews.isPending || orders.isPending ||
    customers.isPending || inventory.isPending || payments.isPending || refunds.isPending;
  const errored = sales.isError || top.isError || reviews.isError || orders.isError ||
    customers.isError || inventory.isError || payments.isError || refunds.isError;

  if (pending) return <LoadingState label="Loading reports…" />;
  if (errored) return <ErrorState error={sales.error ?? orders.error ?? payments.error} />;

  const TABS = [
    ['sales', 'Sales'],
    ['top-products', 'Top products'],
    ['reviews', 'Reviews'],
    ['orders', 'Orders'],
    ['customers', 'Customers'],
    ['inventory', 'Inventory'],
    ['payments', 'Payments'],
    ['refunds', 'Refunds'],
  ] as const;

  return (
    <div>
      <h2 className="admin-main__title">Reports</h2>
      <div className="filters-bar">
        <Field label="From">
          <Input type="date" value={range.from.slice(0, 10)} onChange={(e) => setRange({ ...range, from: toInstant(`${e.target.value}T00:00:00`) })} />
        </Field>
        <Field label="To">
          <Input type="date" value={range.to.slice(0, 10)} onChange={(e) => setRange({ ...range, to: toInstant(`${e.target.value}T23:59:59`) })} />
        </Field>
        {(tab === 'orders' || tab === 'payments') && (
          <Field label="Status">
            <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
              <option value="">All</option>
              {(tab === 'orders' ? ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'] : ['PENDING', 'AUTHORIZED', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED']).map((s) => (
                <option key={s} value={s}>{humanize(s)}</option>
              ))}
            </Select>
          </Field>
        )}
      </div>

      <div className="flex gap-1 flex-wrap mb-6">
        {TABS.map(([key, label]) => (
          <button key={key} className={`btn btn--sm ${tab === key ? 'btn--dark' : 'btn--outline'}`} onClick={() => { setTab(key); setPage(0); }}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'sales' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">Orders</div><div className="stat-card__value">{sales.data!.totalOrders}</div></div>
            <div className="stat-card"><div className="stat-card__label">Gross sales</div><div className="stat-card__value">{formatMoney(sales.data!.grossSales)}</div></div>
            <div className="stat-card"><div className="stat-card__label">Refunds</div><div className="stat-card__value">{formatMoney(sales.data!.refunds)}</div></div>
            <div className="stat-card"><div className="stat-card__label">Net sales</div><div className="stat-card__value stat-card__value--accent">{formatMoney(sales.data!.netSales)}</div></div>
          </div>
          <div className="card">
            <h3 style={{ fontSize: '1rem' }}>Daily sales</h3>
            <div className="table-wrap" style={{ border: 'none' }}>
              <table className="table">
                <thead>
                  <tr><th>Date</th><th>Orders</th><th>Gross</th><th>Refunds</th><th>Net</th></tr>
                </thead>
                <tbody>
                  {sales.data!.days.map((d) => (
                    <tr key={d.date}>
                      <td>{d.date}</td>
                      <td>{d.orderCount}</td>
                      <td>{formatMoney(d.grossSales)}</td>
                      <td>{formatMoney(d.refunds)}</td>
                      <td>{formatMoney(d.netSales)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {tab === 'top-products' && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Product</th><th>SKU</th><th>Units</th><th>Revenue</th></tr></thead>
            <tbody>
              {top.data!.products.map((p) => (
                <tr key={p.sku}>
                  <td>{p.productName}</td><td>{p.sku}</td><td>{p.unitsSold}</td><td>{formatMoney(p.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'reviews' && (
        <div className="stat-grid">
          <div className="stat-card"><div className="stat-card__label">Reviews</div><div className="stat-card__value">{reviews.data!.reviewCount}</div></div>
          <div className="stat-card"><div className="stat-card__label">Average rating</div><div className="stat-card__value">{Number(reviews.data!.averageRating).toFixed(1)}</div></div>
        </div>
      )}

      {tab === 'orders' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">Orders</div><div className="stat-card__value">{orders.data!.totalOrders}</div></div>
            <div className="stat-card"><div className="stat-card__label">Total amount</div><div className="stat-card__value">{formatMoney(orders.data!.totalAmount)}</div></div>
          </div>
          <ReportTable
            headers={['Order', 'Status', 'Customer', 'Total', 'Created']}
            rows={orders.data!.orders.map((o) => [
              o.orderNumber,
              <Badge key={o.id} tone="neutral">{humanize(o.status)}</Badge>,
              o.customerEmail,
              formatMoney(o.grandTotal),
              formatDateTime(o.createdAt),
            ])}
          />
          <CsvBar onDownload={() => download(() => reportsApi.ordersCsv(r), 'orders')} label="Orders CSV" />
        </>
      )}

      {tab === 'customers' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">Total customers</div><div className="stat-card__value">{customers.data!.totalCustomers}</div></div>
            <div className="stat-card"><div className="stat-card__label">New in period</div><div className="stat-card__value">{customers.data!.newCustomers}</div></div>
            <div className="stat-card"><div className="stat-card__label">Total spent</div><div className="stat-card__value">{formatMoney(customers.data!.totalSpent)}</div></div>
          </div>
          <ReportTable
            headers={['Customer', 'Role', 'Status', 'Verified', 'Joined']}
            rows={customers.data!.customers.map((c) => [
              c.fullName || c.email,
              c.role,
              c.accountStatus,
              c.emailVerified ? 'Yes' : 'No',
              formatDateTime(c.createdAt),
            ])}
          />
          <CsvBar onDownload={() => download(() => reportsApi.customersCsv(r), 'customers')} label="Customers CSV" />
        </>
      )}

      {tab === 'inventory' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">In stock</div><div className="stat-card__value">{inventory.data!.inStock}</div></div>
            <div className="stat-card"><div className="stat-card__label">Low stock</div><div className="stat-card__value" style={{ color: 'var(--color-warning)' }}>{inventory.data!.lowStock}</div></div>
            <div className="stat-card"><div className="stat-card__label">Out of stock</div><div className="stat-card__value" style={{ color: 'var(--color-error)' }}>{inventory.data!.outOfStock}</div></div>
          </div>
          <ReportTable
            headers={['Owner', 'SKU', 'Available', 'Reserved', 'Sold', 'Status']}
            rows={inventory.data!.rows.map((row) => [
              row.ownerName,
              row.sku,
              row.availableQuantity,
              row.reservedQuantity,
              row.soldQuantity,
              <Badge key={row.id} tone={row.stockStatus === 'IN_STOCK' ? 'success' : row.stockStatus === 'LOW_STOCK' ? 'warning' : 'error'}>{humanize(row.stockStatus)}</Badge>,
            ])}
          />
          <CsvBar onDownload={() => download(() => reportsApi.inventoryCsv(r), 'inventory')} label="Inventory CSV" />
        </>
      )}

      {tab === 'payments' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">Payments</div><div className="stat-card__value">{payments.data!.totalPayments}</div></div>
            <div className="stat-card"><div className="stat-card__label">Total amount</div><div className="stat-card__value">{formatMoney(payments.data!.totalAmount)}</div></div>
          </div>
          <ReportTable
            headers={['Order', 'Method', 'Status', 'Amount', 'Created']}
            rows={payments.data!.payments.map((p) => [
              p.orderNumber,
              humanize(p.paymentMethod),
              <Badge key={p.id} tone={p.status === 'PAID' || p.status === 'REFUNDED' ? 'success' : p.status === 'FAILED' ? 'error' : p.status === 'PARTIALLY_REFUNDED' ? 'info' : 'warning'}>{humanize(p.status)}</Badge>,
              formatMoney(p.amount),
              formatDateTime(p.createdAt),
            ])}
          />
          <CsvBar onDownload={() => download(() => reportsApi.paymentsCsv(r), 'payments')} label="Payments CSV" />
        </>
      )}

      {tab === 'refunds' && (
        <>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-card__label">Refunds</div><div className="stat-card__value">{refunds.data!.totalRefunds}</div></div>
            <div className="stat-card"><div className="stat-card__label">Total refunded</div><div className="stat-card__value">{formatMoney(refunds.data!.totalAmount)}</div></div>
          </div>
          <ReportTable
            headers={['Order', 'Amount', 'Status', 'Created by', 'Created']}
            rows={refunds.data!.refunds.map((x) => [
              x.orderNumber,
              formatMoney(x.amount),
              <Badge key={x.id} tone={x.status === 'COMPLETED' ? 'success' : x.status === 'FAILED' ? 'error' : 'warning'}>{humanize(x.status)}</Badge>,
              x.createdByEmail,
              formatDateTime(x.createdAt),
            ])}
          />
          <CsvBar onDownload={() => download(() => reportsApi.refundsCsv(r), 'refunds')} label="Refunds CSV" />
        </>
      )}
      {toasts.node}
    </div>
  );
}

function ReportTable({ headers, rows }: { headers: string[]; rows: (string | number | JSX.Element)[][] }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>{headers.map((h) => <th key={h}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i}>{row.map((cell, j) => <td key={j}>{cell}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CsvBar({ onDownload, label }: { onDownload: () => void; label: string }) {
  return (
    <div className="flex mt-4">
      <Button variant="outline" onClick={onDownload}>
        <Download size={16} /> {label}
      </Button>
    </div>
  );
}