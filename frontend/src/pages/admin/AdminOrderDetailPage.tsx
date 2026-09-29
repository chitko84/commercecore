import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { staffOrdersApi, staffPaymentsApi } from '../../lib/api';
import { formatDateTime, formatMoney, humanize } from '../../lib/format';
import { useIdempotencyKey } from '../../lib/idempotency';
import { Badge, Button, Card, ErrorState, Field, Input, LoadingState, Modal, useToasts, errorMessage } from '../../components/ui';
import { PaymentMethodIcon } from '../../components/PaymentMethodIcon';

export default function AdminOrderDetailPage() {
  const { orderId = '' } = useParams();
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const cancelPaidKey = useIdempotencyKey('cancel-paid');
  const refundKey = useIdempotencyKey('refund');
  const [shipmentForm, setShipmentForm] = useState({ carrier: '', trackingNumber: '' });
  const [showShipment, setShowShipment] = useState(false);
  const [refundFor, setRefundFor] = useState<{ paymentId: string; amount: string } | null>(null);
  const [showCancelPaid, setShowCancelPaid] = useState(false);

  const order = useQuery({ queryKey: ['admin', 'order', orderId], queryFn: () => staffOrdersApi.get(orderId) });
  const payments = useQuery({
    queryKey: ['admin', 'payments', orderId],
    queryFn: () => staffPaymentsApi.paymentsByOrder(orderId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'order', orderId] });
    void queryClient.invalidateQueries({ queryKey: ['admin', 'payments', orderId] });
    void queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const action = useMutation({
    mutationFn: (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => {
      invalidate();
      setShowShipment(false);
      toasts.push('success', 'Order updated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const issueRefund = useMutation({
    mutationFn: () => {
      if (!refundFor) return Promise.reject(new Error('no refund'));
      return staffPaymentsApi.refund(refundFor.paymentId, refundKey.key, refundFor.amount);
    },
    onSuccess: () => {
      refundKey.reset();
      setRefundFor(null);
      invalidate();
      toasts.push('success', 'Refund issued');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const cancelPaid = useMutation({
    mutationFn: () => staffOrdersApi.cancelPaid(orderId, cancelPaidKey.key),
    onSuccess: () => {
      cancelPaidKey.reset();
      setShowCancelPaid(false);
      invalidate();
      toasts.push('success', 'Paid order cancelled with full refund');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (order.isPending) return <LoadingState label="Loading order…" />;
  if (order.isError) return <ErrorState error={order.error} />;

  const data = order.data;

  return (
    <div>
      <h2 className="admin-main__title">Order {data.orderNumber}</h2>
      <div className="flex gap-2 mb-4 flex-wrap">
        <Badge tone={data.status === 'CANCELLED' ? 'error' : data.status === 'DELIVERED' || data.status === 'CONFIRMED' ? 'success' : data.status === 'PENDING' ? 'warning' : 'info'}>
          {humanize(data.status)}
        </Badge>
        <Badge tone="neutral">{humanize(data.shippingServiceLevel)} shipping</Badge>
        <span className="text-sm text-muted">Placed {formatDateTime(data.createdAt)}</span>
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>
        <Card>
          <h3 style={{ fontSize: '1rem' }}>Customer</h3>
          <p className="text-sm" style={{ whiteSpace: 'pre-line' }}>
            {data.addresses.find((a) => a.type === 'SHIPPING')
              ? formatAddress(data.addresses.find((a) => a.type === 'SHIPPING')!)
              : '—'}
          </p>
        </Card>
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
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>
        <Card>
          <h3 style={{ fontSize: '1rem' }}>Lifecycle</h3>
          <div className="flex gap-2 flex-wrap">
            {data.status === 'PENDING' && (
              <>
                <Button size="sm" onClick={() => action.mutate(() => staffOrdersApi.cancel(orderId))}>
                  Cancel order
                </Button>
                <Button variant="outline" size="sm" onClick={() => setShowCancelPaid(true)}>
                  Cancel paid (full refund)
                </Button>
              </>
            )}
            {data.status === 'CONFIRMED' && (
              <Button size="sm" onClick={() => action.mutate(() => staffOrdersApi.processing(orderId))}>
                Start processing
              </Button>
            )}
            {(data.status === 'CONFIRMED' || data.status === 'PROCESSING') && (
              <Button variant="outline" size="sm" onClick={() => setShowShipment(true)}>
                Add shipment
              </Button>
            )}
            {data.status === 'PROCESSING' && data.shipment && (
              <Button size="sm" onClick={() => action.mutate(() => staffOrdersApi.ship(orderId))}>
                Mark shipped
              </Button>
            )}
            {data.status === 'SHIPPED' && (
              <Button size="sm" onClick={() => action.mutate(() => staffOrdersApi.deliver(orderId))}>
                Mark delivered
              </Button>
            )}
          </div>
          {data.shipment && (
            <div className="mt-4 text-sm">
              <strong>Shipment:</strong> {data.shipment.carrier} · {data.shipment.trackingNumber}
            </div>
          )}
        </Card>

        <Card>
          <h3 style={{ fontSize: '1rem' }}>Payments & refunds</h3>
          {payments.isPending ? (
            <LoadingState label="Loading…" />
          ) : payments.data && payments.data.length > 0 ? (
            <ul style={{ listStyle: 'none' }}>
              {payments.data.map((p) => (
                <li key={p.id} className="py-2" style={{ borderBottom: '1px solid var(--color-line)' }}>
                  <div className="flex justify-between items-center">
                    <PaymentMethodIcon method={p.paymentMethod} />
                    <span className="flex gap-2 items-center">
                      <Badge tone={p.status === 'PAID' || p.status === 'REFUNDED' ? 'success' : p.status === 'FAILED' ? 'error' : p.status === 'PARTIALLY_REFUNDED' ? 'info' : 'warning'}>
                        {humanize(p.status)}
                      </Badge>
                    </span>
                  </div>
                  <div className="flex justify-between text-sm mt-1">
                    <span className="text-faint">{formatMoney(p.amount)} · {formatDateTime(p.createdAt)}</span>
                    <span className="flex gap-2">
                      {p.status === 'PAID' || p.status === 'PARTIALLY_REFUNDED' ? (
                        <Button variant="outline" size="sm" onClick={() => setRefundFor({ paymentId: p.id, amount: '' })}>
                          Refund
                        </Button>
                      ) : null}
                      {p.reconciliationRequired && (
                        <Button variant="outline" size="sm" onClick={() => action.mutate(() => staffPaymentsApi.reconcilePayment(p.id))}>
                          Reconcile
                        </Button>
                      )}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">No payments yet.</p>
          )}
          {payments.data && (
            <p className="text-xs text-faint mt-2">
              Refunds and their statuses are visible on the payment record after issue.
            </p>
          )}
        </Card>
      </div>

      <Modal open={showShipment} title="Add shipment" onClose={() => setShowShipment(false)}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            action.mutate(() =>
              staffOrdersApi.shipment(orderId, {
                carrier: shipmentForm.carrier,
                trackingNumber: shipmentForm.trackingNumber,
              }),
            );
          }}
        >
          <Field label="Carrier">
            <Input value={shipmentForm.carrier} onChange={(e) => setShipmentForm({ ...shipmentForm, carrier: e.target.value })} autoFocus />
          </Field>
          <Field label="Tracking number">
            <Input value={shipmentForm.trackingNumber} onChange={(e) => setShipmentForm({ ...shipmentForm, trackingNumber: e.target.value })} />
          </Field>
          <div className="flex gap-3">
            <Button type="submit" disabled={!shipmentForm.carrier || !shipmentForm.trackingNumber}>Save shipment</Button>
            <Button variant="ghost" onClick={() => setShowShipment(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      <Modal open={Boolean(refundFor)} title="Issue refund" onClose={() => setRefundFor(null)}>
        {refundFor && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              issueRefund.mutate();
            }}
          >
            <p className="text-sm text-muted mb-4">
              Amount is capped at the remaining unrefunded balance by the backend. A full refund of
              the remaining balance will set the payment to REFUNDED.
            </p>
            <Field label="Amount">
              <Input type="number" step="0.01" min="0.01" value={refundFor.amount} onChange={(e) => setRefundFor({ ...refundFor, amount: e.target.value })} autoFocus />
            </Field>
            <div className="flex gap-3">
              <Button type="submit" loading={issueRefund.isPending} disabled={!refundFor.amount}>
                Issue refund
              </Button>
              <Button variant="ghost" onClick={() => setRefundFor(null)}>Cancel</Button>
            </div>
          </form>
        )}
      </Modal>

      <Modal open={showCancelPaid} title="Cancel paid order" onClose={() => setShowCancelPaid(false)}>
        <div>
          <p className="text-sm mb-4">
            This will cancel the order, reverse the sale and issue a full refund for the paid
            amount. This action is idempotent and cannot be partially undone.
          </p>
          <div className="flex gap-3">
            <Button variant="danger" onClick={() => cancelPaid.mutate()} loading={cancelPaid.isPending}>
              Cancel and refund
            </Button>
            <Button variant="ghost" onClick={() => setShowCancelPaid(false)}>Close</Button>
          </div>
        </div>
      </Modal>
      {toasts.node}
    </div>
  );
}

function formatAddress(addr: { recipientName: string; phone: string; addressLine1: string; addressLine2: string | null; city: string; stateRegion: string; postalCode: string; countryCode: string }): string {
  return [addr.recipientName, addr.addressLine1, addr.addressLine2 ?? '', `${addr.city}, ${addr.stateRegion} ${addr.postalCode}`, addr.countryCode]
    .filter(Boolean)
    .join('\n');
}