import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CreditCard, Landmark, Wallet } from 'lucide-react';
import { accountApi, cartApi, ordersApi, paymentsApi } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { useIdempotencyKey } from '../../lib/idempotency';
import { Badge, Button, ErrorState, Field, Input, LoadingState, useToasts, errorMessage } from '../../components/ui';
import type { PaymentMethod } from '../../lib/types';

const PAYMENT_METHODS: { value: PaymentMethod; label: string; icon: typeof CreditCard }[] = [
  { value: 'CARD', label: 'Card', icon: CreditCard },
  { value: 'ONLINE_BANKING', label: 'Online banking', icon: Landmark },
  { value: 'E_WALLET', label: 'E-wallet', icon: Wallet },
];

export default function CheckoutPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const checkoutKey = useIdempotencyKey('checkout');
  const paymentKey = useIdempotencyKey('payment');

  const [shippingAddressId, setShippingAddressId] = useState<string>('');
  const [shippingLevel, setShippingLevel] = useState<'STANDARD' | 'EXPRESS'>('STANDARD');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CARD');
  const [showNewAddress, setShowNewAddress] = useState(false);
  const [newAddress, setNewAddress] = useState({
    label: 'Home', recipientName: '', phone: '', addressLine1: '', addressLine2: '',
    city: '', stateRegion: '', postalCode: '', countryCode: '', defaultShipping: false, defaultBilling: false,
  });

  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get });
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: accountApi.addresses });

  const defaultAddress = addresses.data?.find((a) => a.defaultShipping) ?? addresses.data?.[0];
  const effectiveShippingId = shippingAddressId || defaultAddress?.id || '';

  const shippingFee = useMemo(() => {
    const base = shippingLevel === 'EXPRESS' ? 25 : 10;
    return cart.data?.freeShippingEligible ? 0 : base;
  }, [shippingLevel, cart.data?.freeShippingEligible]);

  const tax = useMemo(() => {
    const base = Number(cart.data?.discountedMerchandiseTotal ?? 0) + shippingFee;
    return Math.round(base * 0.08 * 100) / 100;
  }, [cart.data?.discountedMerchandiseTotal, shippingFee]);

  const grandTotal = useMemo(
    () => Number(cart.data?.discountedMerchandiseTotal ?? 0) + shippingFee + tax,
    [cart.data?.discountedMerchandiseTotal, shippingFee, tax],
  );

  const createAddress = useMutation({
    mutationFn: () => accountApi.createAddress({ ...newAddress, defaultShipping: newAddress.defaultShipping, defaultBilling: newAddress.defaultBilling }),
    onSuccess: (addr) => {
      setShippingAddressId(addr.id);
      setShowNewAddress(false);
      void queryClient.invalidateQueries({ queryKey: ['addresses'] });
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const submit = useMutation({
    mutationFn: async () => {
      const order = await ordersApi.checkout(checkoutKey.key, {
        shippingAddressId: effectiveShippingId,
        shippingServiceLevel: shippingLevel,
      });
      const payment = await paymentsApi.pay(order.id, paymentKey.key, { paymentMethod });
      return { order, payment };
    },
    onSuccess: ({ order, payment }) => {
      checkoutKey.reset();
      paymentKey.reset();
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
      if (payment.status === 'PAID' || payment.status === 'AUTHORIZED') {
        navigate(`/orders/${order.id}/confirmation`, { state: { orderId: order.id, paymentStatus: payment.status } });
      } else if (payment.status === 'PENDING' || payment.reconciliationRequired) {
        // Uncertain outcome — the backend reconciles asynchronously.
        navigate(`/orders/${order.id}/confirmation`, { state: { orderId: order.id, paymentStatus: payment.status, uncertain: true } });
      } else {
        toasts.push('warning', `Payment ${payment.status.toLowerCase()}. You can retry from your order.`);
        navigate(`/orders/${order.id}/confirmation`, { state: { orderId: order.id, paymentStatus: payment.status } });
      }
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (cart.isPending || addresses.isPending) return <div className="container"><LoadingState label="Preparing checkout…" /></div>;
  if (cart.isError) return <div className="container"><ErrorState error={cart.error} /></div>;

  if (cart.data.items.length === 0) {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem', textAlign: 'center' }}>
        <h1>Your cart is empty</h1>
        <Link className="btn btn--dark" to="/catalog">Browse products</Link>
      </div>
    );
  }

  const canSubmit = Boolean(effectiveShippingId) && !submit.isPending && !createAddress.isPending;

  return (
    <div className="container" style={{ paddingBlock: '2rem' }}>
      <h1>Checkout</h1>
      <div className="checkout-steps" aria-label="Checkout progress">
        <span className="checkout-step is-done"><span className="checkout-step__num">1</span> Cart</span>
        <span className="checkout-step is-active"><span className="checkout-step__num">2</span> Details</span>
        <span className="checkout-step"><span className="checkout-step__num">3</span> Payment</span>
      </div>

      <div className="cart-layout">
        <section>
          <h2 style={{ fontSize: '1.25rem' }}>Shipping address</h2>
          {addresses.data && addresses.data.length > 0 && (
            <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
              {addresses.data.map((addr) => (
                <button
                  key={addr.id}
                  type="button"
                  className={`address-option ${effectiveShippingId === addr.id ? 'is-selected' : ''}`}
                  onClick={() => setShippingAddressId(addr.id)}
                  aria-pressed={effectiveShippingId === addr.id}
                >
                  <span className="address-option__label">
                    {addr.label}
                    {addr.defaultShipping && <Badge tone="accent" >Default</Badge>}
                  </span>
                  <span className="address-option__body">
                    {addr.recipientName}
                    {'\n'}
                    {addr.addressLine1}
                    {'\n'}
                    {addr.city}, {addr.stateRegion} {addr.postalCode}
                    {'\n'}
                    {addr.countryCode}
                  </span>
                </button>
              ))}
            </div>
          )}
          {showNewAddress ? (
            <div className="card mt-4">
              <h3 style={{ fontSize: '1rem' }}>New address</h3>
              <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
                <Field label="Recipient name">
                  <Input value={newAddress.recipientName} onChange={(e) => setNewAddress({ ...newAddress, recipientName: e.target.value })} />
                </Field>
                <Field label="Phone">
                  <Input value={newAddress.phone} onChange={(e) => setNewAddress({ ...newAddress, phone: e.target.value })} />
                </Field>
              </div>
              <Field label="Address line 1">
                <Input value={newAddress.addressLine1} onChange={(e) => setNewAddress({ ...newAddress, addressLine1: e.target.value })} />
              </Field>
              <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
                <Field label="City">
                  <Input value={newAddress.city} onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })} />
                </Field>
                <Field label="State / region">
                  <Input value={newAddress.stateRegion} onChange={(e) => setNewAddress({ ...newAddress, stateRegion: e.target.value })} />
                </Field>
              </div>
              <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
                <Field label="Postal code">
                  <Input value={newAddress.postalCode} onChange={(e) => setNewAddress({ ...newAddress, postalCode: e.target.value })} />
                </Field>
                <Field label="Country code (e.g. MY)">
                  <Input value={newAddress.countryCode} onChange={(e) => setNewAddress({ ...newAddress, countryCode: e.target.value })} />
                </Field>
              </div>
              <div className="flex gap-3">
                <Button onClick={() => createAddress.mutate()} loading={createAddress.isPending} disabled={!newAddress.recipientName || !newAddress.addressLine1 || !newAddress.city}>
                  Save address
                </Button>
                <Button variant="ghost" onClick={() => setShowNewAddress(false)}>Cancel</Button>
              </div>
            </div>
          ) : (
            <Button variant="outline" className="mt-4" onClick={() => setShowNewAddress(true)}>
              + Add a new address
            </Button>
          )}

          <h2 style={{ fontSize: '1.25rem', marginTop: '2rem' }}>Shipping method</h2>
          <button type="button" className={`shipping-option ${shippingLevel === 'STANDARD' ? 'is-selected' : ''}`} onClick={() => setShippingLevel('STANDARD')} aria-pressed={shippingLevel === 'STANDARD'}>
            <span><strong>Standard</strong> <span className="text-sm text-muted">· 5–7 business days</span></span>
            <span className="price">{cart.data.freeShippingEligible ? 'Free' : formatMoney(10)}</span>
          </button>
          <button type="button" className={`shipping-option ${shippingLevel === 'EXPRESS' ? 'is-selected' : ''}`} onClick={() => setShippingLevel('EXPRESS')} aria-pressed={shippingLevel === 'EXPRESS'}>
            <span><strong>Express</strong> <span className="text-sm text-muted">· 2–3 business days</span></span>
            <span className="price">{cart.data.freeShippingEligible ? 'Free' : formatMoney(25)}</span>
          </button>

          <h2 style={{ fontSize: '1.25rem', marginTop: '2rem' }}>Payment method</h2>
          <div className="flex gap-2 mb-2">
            <span className="sandbox-note">Sandbox payments — no real charges</span>
          </div>
          {PAYMENT_METHODS.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              type="button"
              className={`payment-method-option ${paymentMethod === value ? 'is-selected' : ''}`}
              onClick={() => setPaymentMethod(value)}
              aria-pressed={paymentMethod === value}
            >
              <span className="flex gap-2 items-center">
                <Icon size={18} /> {label}
              </span>
            </button>
          ))}
        </section>

        <aside className="order-summary">
          <h3 style={{ marginBottom: '1rem' }}>Summary</h3>
          {cart.data.items.map((item) => (
            <div className="summary-row" key={item.id}>
              <span>{item.productName} × {item.quantity}</span>
              <span>{formatMoney(item.lineSubtotal)}</span>
            </div>
          ))}
          <div className="summary-row"><span>Subtotal</span><span>{formatMoney(cart.data.subtotal)}</span></div>
          {cart.data.promotionDiscount !== '0.00' && (
            <div className="summary-row summary-row--discount"><span>Discount</span><span>−{formatMoney(cart.data.promotionDiscount)}</span></div>
          )}
          <div className="summary-row"><span>Shipping</span><span>{shippingFee === 0 ? 'Free' : formatMoney(shippingFee)}</span></div>
          <div className="summary-row"><span>Estimated tax</span><span>{formatMoney(tax)}</span></div>
          <div className="summary-row summary-row--total"><span>Total</span><span>{formatMoney(grandTotal)}</span></div>
          <Button block size="lg" className="mt-6" onClick={() => submit.mutate()} disabled={!canSubmit} loading={submit.isPending}>
            Place order &amp; pay
          </Button>
          {!effectiveShippingId && <p className="text-xs text-error mt-2">Choose or add a shipping address.</p>}
        </aside>
      </div>
      {toasts.node}
    </div>
  );
}