import { Link, useLocation, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import { ordersApi } from '../../lib/api';
import { formatMoney, formatDateTime, humanize } from '../../lib/format';
import { Badge, LoadingState, ErrorState } from '../../components/ui';

export default function OrderConfirmationPage() {
  const { orderId = '' } = useParams();
  const location = useLocation();
  const state = (location.state as { paymentStatus?: string; uncertain?: boolean } | null) ?? {};
  const order = useQuery({ queryKey: ['order', orderId], queryFn: () => ordersApi.get(orderId) });

  if (order.isPending) return <div className="container"><LoadingState /></div>;
  if (order.isError) return <div className="container"><ErrorState error={order.error} /></div>;

  const data = order.data;
  const paid = state.paymentStatus === 'PAID' || state.paymentStatus === 'AUTHORIZED';
  const uncertain = Boolean(state.uncertain) || data.status === 'PENDING';

  return (
    <div className="container container--narrow" style={{ paddingBlock: '3rem' }}>
      <div className="card" style={{ padding: '2.5rem', textAlign: 'center' }}>
        {paid ? (
          <CheckCircle2 size={44} color="var(--color-success)" style={{ margin: '0 auto 1rem' }} />
        ) : uncertain ? (
          <Clock size={44} color="var(--color-warning)" style={{ margin: '0 auto 1rem' }} />
        ) : (
          <XCircle size={44} color="var(--color-error)" style={{ margin: '0 auto 1rem' }} />
        )}
        <h1>{paid ? 'Order confirmed' : uncertain ? 'Order pending confirmation' : 'Payment unsuccessful'}</h1>
        <p className="text-muted">
          Order <strong>{data.orderNumber}</strong> · {formatDateTime(data.createdAt)}
        </p>
        <div className="flex justify-center mt-4">
          <Badge tone={data.status === 'CONFIRMED' ? 'success' : data.status === 'PENDING' ? 'warning' : 'neutral'}>
            {humanize(data.status)}
          </Badge>
        </div>
        <div className="card card--flat" style={{ background: 'var(--color-bg-alt)', marginTop: '1.5rem', textAlign: 'left' }}>
          {data.items.map((item) => (
            <div className="summary-row" key={item.id}>
              <span>{item.productName} × {item.quantity}</span>
              <span>{formatMoney(item.lineSubtotal)}</span>
            </div>
          ))}
          <div className="summary-row"><span>Subtotal</span><span>{formatMoney(data.subtotal)}</span></div>
          {data.merchandiseDiscount !== '0.00' && (
            <div className="summary-row summary-row--discount"><span>Discount</span><span>−{formatMoney(data.merchandiseDiscount)}</span></div>
          )}
          <div className="summary-row"><span>Shipping ({humanize(data.shippingServiceLevel)})</span><span>{formatMoney(data.shippingFee)}</span></div>
          <div className="summary-row"><span>Tax</span><span>{formatMoney(data.taxAmount)}</span></div>
          <div className="summary-row summary-row--total"><span>Total</span><span>{formatMoney(data.grandTotal)}</span></div>
        </div>
        {uncertain && (
          <p className="text-sm text-muted mt-4">
            Your payment is being confirmed with the sandbox provider. Check your order for the
            latest status — you can retry payment there if needed.
          </p>
        )}
        <div className="flex gap-3 justify-center mt-6 flex-wrap">
          <Link className="btn btn--dark" to={`/account/orders/${data.id}`}>
            View order
          </Link>
          <Link className="btn btn--outline" to="/catalog">
            Continue shopping
          </Link>
        </div>
      </div>
    </div>
  );
}