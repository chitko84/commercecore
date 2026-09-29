import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ordersApi, paymentsApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize } from '../../lib/format';
import { useIdempotencyKey } from '../../lib/idempotency';
import { Badge, Button, Card, ErrorState, LoadingState, useToasts, errorMessage } from '../../components/ui';
import { PaymentMethodIcon } from '../../components/PaymentMethodIcon';

export default function AccountOrderDetailPage() {
  const { orderId = '' } = useParams();
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const payKey = useIdempotencyKey('payment-retry');

  const order = useQuery({ queryKey: ['order', orderId], queryFn: () => ordersApi.get(orderId) });
  const payments = useQuery({
    queryKey: ['payments', orderId],
    queryFn: () => paymentsApi.listForOrder(orderId),
    enabled: Boolean(orderId),
  });

  const cancel = useMutation({
    mutationFn: () => ordersApi.cancel(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['order', orderId] });
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      toasts.push('success', 'Order cancelled');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const retryPayment = useMutation({
    mutationFn: () => paymentsApi.pay(orderId, payKey.key, { paymentMethod: 'CARD' }),
    onSuccess: (p) => {
      payKey.reset();
      void queryClient.invalidateQueries({ queryKey: ['order', orderId] });
      toasts.push('success', `Payment ${p.status.toLowerCase()}`);
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (order.isPending) return <LoadingState label="Loading order…" />;
  if (order.isError) return <ErrorState error={order.error} />;

  const data = order.data;
  const canCancel = data.status === 'PENDING';
  const canPay = data.status === 'PENDING';
  const timeline = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

  return (
    <div>
      <h2>Order {data.orderNumber}</h2>
      <div className="flex gap-2 mb-4 flex-wrap">
        <Badge tone={data.status === 'CANCELLED' ? 'error' : data.status === 'DELIVERED' || data.status === 'CONFIRMED' ? 'success' : data.status === 'PENDING' ? 'warning' : 'info'}>
          {humanize(data.status)}
        </Badge>
        <Badge tone="neutral">{humanize(data.shippingServiceLevel)} shipping</Badge>
      </div>

      {data.status !== 'CANCELLED' && (
        <div className="order-timeline mb-6" aria-label="Order progress">
          {timeline.map((step) => {
            const idx = timeline.indexOf(step);
            const currentIdx = timeline.indexOf(data.status);
            const done = idx <= currentIdx;
            return (
              <span key={step} className={`order-timeline__step ${done ? 'is-done' : ''} ${idx === currentIdx ? 'is-current' : ''}`}>
                {humanize(step)}
              </span>
            );
          })}
        </div>
      )}

      {data.shipment && (
        <Card className="mb-6">
          <h3 style={{ fontSize: '1rem' }}>Shipment tracking</h3>
          <p className="text-sm text-muted" style={{ marginBottom: '0.5rem' }}>
            {data.shipment.carrier} · {data.shipment.trackingNumber}
          </p>
          <ul className="status-timeline">
            {data.shipment.events.map((ev, i) => (
              <li key={i} className={i === data.shipment!.events.length - 1 ? 'is-active' : ''}>
                <span className="status-timeline__label">{humanize(ev.status)}</span>
                <span className="status-timeline__meta">
                  {formatDateTime(ev.occurredAt)}
                  {ev.location ? ` · ${ev.location}` : ''}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
        <Card>
          <h3 style={{ fontSize: '1rem' }}>Items</h3>
          {data.items.map((item) => (
            <div className="summary-row" key={item.id}>
              <span>{item.productName} × {item.quantity}</span>
              <span>{formatMoney(item.lineSubtotal)}</span>
            </div>
          ))}
          <hr className="divider" />
          <div className="summary-row"><span>Subtotal</span><span>{formatMoney(data.subtotal)}</span></div>
          {data.merchandiseDiscount !== '0.00' && (
            <div className="summary-row summary-row--discount"><span>Discount</span><span>−{formatMoney(data.merchandiseDiscount)}</span></div>
          )}
          <div className="summary-row"><span>Shipping</span><span>{formatMoney(data.shippingFee)}</span></div>
          <div className="summary-row"><span>Tax</span><span>{formatMoney(data.taxAmount)}</span></div>
          <div className="summary-row summary-row--total"><span>Total</span><span>{formatMoney(data.grandTotal)}</span></div>
        </Card>
        <Card>
          <h3 style={{ fontSize: '1rem' }}>Shipping address</h3>
          {data.addresses.find((a) => a.type === 'SHIPPING') && (
            <p className="text-sm" style={{ whiteSpace: 'pre-line' }}>
              {formatAddress(data.addresses.find((a) => a.type === 'SHIPPING')!)}
            </p>
          )}
          <h3 style={{ fontSize: '1rem', marginTop: '1rem' }}>Payment</h3>
          {payments.isPending ? (
            <p className="text-sm text-muted">Loading…</p>
          ) : payments.data && payments.data.length > 0 ? (
            <ul style={{ listStyle: 'none' }}>
              {payments.data.map((p) => (
                <li key={p.id} className="flex justify-between text-sm mb-2">
                  <span className="flex gap-1 items-center">
                    <PaymentMethodIcon method={p.paymentMethod} />
                  </span>
                  <span className="flex gap-2 items-center">
                    <Badge
                      tone={
                        p.status === 'PAID' || p.status === 'REFUNDED'
                          ? 'success'
                          : p.status === 'FAILED'
                            ? 'error'
                            : p.status === 'PARTIALLY_REFUNDED'
                              ? 'info'
                              : 'warning'
                      }
                    >
                      {humanize(p.status)}
                    </Badge>
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">No payment attempts yet.</p>
          )}
          {canPay && (
            <Button className="mt-4" onClick={() => retryPayment.mutate()} loading={retryPayment.isPending}>
              Retry sandbox payment
            </Button>
          )}
          {canCancel && (
            <Button variant="danger" className="mt-4" onClick={() => cancel.mutate()} loading={cancel.isPending}>
              Cancel order
            </Button>
          )}
        </Card>
      </div>
      {toasts.node}
    </div>
  );
}

function formatAddress(addr: { recipientName: string; phone: string; addressLine1: string; addressLine2: string | null; city: string; stateRegion: string; postalCode: string; countryCode: string }): string {
  return [
    addr.recipientName,
    addr.phone,
    addr.addressLine1,
    addr.addressLine2 ?? '',
    `${addr.city}, ${addr.stateRegion} ${addr.postalCode}`,
    addr.countryCode,
  ].filter(Boolean).join('\n');
}