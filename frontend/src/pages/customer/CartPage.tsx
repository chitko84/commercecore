import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Minus, Plus, ShoppingBag, Trash2, X } from 'lucide-react';
import { cartApi } from '../../lib/api';
import { formatMoney, humanize } from '../../lib/format';
import { useAuth } from '../../lib/auth';
import { Badge, Button, EmptyState, ErrorState, LoadingState, useToasts, errorMessage } from '../../components/ui';
import { productImageUrl } from '../../components/ProductCard';

export default function CartPage() {
  const { status } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [coupon, setCoupon] = useState('');

  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get, enabled: status === 'authenticated' });

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['cart'] });

  const updateQty = useMutation({
    mutationFn: ({ id, quantity }: { id: string; quantity: number }) => cartApi.update(id, quantity),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const removeLine = useMutation({
    mutationFn: (id: string) => cartApi.remove(id),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const applyCoupon = useMutation({
    mutationFn: (code: string) => cartApi.applyPromotion(code),
    onSuccess: () => {
      invalidate();
      setCoupon('');
      toasts.push('success', 'Coupon applied');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const removeCoupon = useMutation({
    mutationFn: () => cartApi.removePromotion(),
    onSuccess: invalidate,
  });

  if (status !== 'authenticated') {
    return (
      <div className="container container--narrow" style={{ paddingBlock: '4rem', textAlign: 'center' }}>
        <h1>Your cart</h1>
        <p className="text-muted mb-6">Sign in to view and manage your cart.</p>
        <Button onClick={() => navigate('/login', { state: { from: '/cart' } })}>Sign in</Button>
      </div>
    );
  }

  if (cart.isPending) return <div className="container"><LoadingState label="Loading your cart…" /></div>;
  if (cart.isError) return <div className="container"><ErrorState error={cart.error} title="Could not load cart" /></div>;

  const data = cart.data;
  const couponError = data.promotion && !data.promotion.valid ? data.promotion.errorCode : null;

  return (
    <div className="container cart-layout">
      <section>
        <h1>Your cart</h1>
        {data.items.length === 0 ? (
          <EmptyState
            title="Your cart is empty"
            body="Browse the catalog and add something you will love."
            action={
              <Link className="btn btn--dark" to="/catalog">
                Browse products
              </Link>
            }
          />
        ) : (
          <div>
            {data.items.map((item) => (
              <div className="cart-line" key={item.id}>
                <div className="cart-line__media">
                  <img src={productImageUrl(item.sku ? `/products/${item.sku.toLowerCase().split('-')[0]}.jpg` : '')} alt="" onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }} />
                </div>
                <div>
                  <Link to={`/products/${item.productId}`} className="cart-line__name">
                    {item.productName}
                  </Link>
                  <div className="cart-line__meta">SKU {item.sku}</div>
                  {item.status !== 'AVAILABLE' && (
                    <Badge tone={item.status === 'OUT_OF_STOCK' ? 'error' : 'warning'}>{humanize(item.status)}</Badge>
                  )}
                  <div className="text-sm mt-2">Unit {formatMoney(item.unitPrice)}</div>
                </div>
                <div className="cart-line__right">
                  <span className="qty">
                    <button
                      className="qty__btn"
                      aria-label="Decrease quantity"
                      disabled={updateQty.isPending}
                      onClick={() => updateQty.mutate({ id: item.id, quantity: Math.max(1, item.quantity - 1) })}
                    >
                      <Minus size={14} />
                    </button>
                    <span className="qty__value">{item.quantity}</span>
                    <button
                      className="qty__btn"
                      aria-label="Increase quantity"
                      disabled={updateQty.isPending}
                      onClick={() => updateQty.mutate({ id: item.id, quantity: Math.min(99, item.quantity + 1) })}
                    >
                      <Plus size={14} />
                    </button>
                  </span>
                  <span className="price">{formatMoney(item.lineSubtotal)}</span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => removeLine.mutate(item.id)}
                    aria-label={`Remove ${item.productName} from cart`}
                  >
                    <Trash2 size={16} /> Remove
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <aside className="order-summary">
        <h3 style={{ marginBottom: '1rem' }}>Order summary</h3>
        <div className="summary-row">
          <span>Subtotal</span>
          <span>{formatMoney(data.subtotal)}</span>
        </div>
        {data.promotionDiscount !== '0.00' && (
          <div className="summary-row summary-row--discount">
            <span>Discount ({data.promotion?.code})</span>
            <span>−{formatMoney(data.promotionDiscount)}</span>
          </div>
        )}
        <div className="summary-row">
          <span>Merchandise total</span>
          <span>{formatMoney(data.discountedMerchandiseTotal)}</span>
        </div>
        {data.freeShippingEligible && <div className="summary-row summary-row--discount">Free shipping eligible</div>}
        <div className="summary-row summary-row--total">
          <span>Total</span>
          <span>{formatMoney(data.discountedMerchandiseTotal)}</span>
        </div>

        <div className="mt-6">
          <h4 style={{ fontSize: '0.9rem' }}>Promotion code</h4>
          {data.promotion?.valid ? (
            <div className="flex justify-between items-center mt-2">
              <Badge tone="accent">{data.promotion.code}</Badge>
              <Button variant="ghost" size="sm" onClick={() => removeCoupon.mutate()} aria-label="Remove coupon">
                <X size={14} /> Remove
              </Button>
            </div>
          ) : (
            <>
              {couponError && <p className="text-xs text-error">{couponError}</p>}
              <form
                className="flex gap-1 mt-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (coupon.trim()) applyCoupon.mutate(coupon.trim());
                }}
              >
                <input
                  className="input"
                  placeholder="Coupon code"
                  value={coupon}
                  onChange={(e) => setCoupon(e.target.value)}
                  aria-label="Coupon code"
                />
                <Button variant="outline" type="submit" disabled={applyCoupon.isPending || !coupon.trim()}>
                  Apply
                </Button>
              </form>
            </>
          )}
        </div>

        <Button
          block
          size="lg"
          className="mt-6"
          disabled={data.items.length === 0 || !data.readyForCheckout}
          onClick={() => navigate('/checkout')}
        >
          <ShoppingBag size={18} /> Proceed to checkout <ArrowRight size={18} />
        </Button>
        {!data.readyForCheckout && data.items.length > 0 && (
          <p className="text-xs text-error mt-2">Resolve the item issues above to check out.</p>
        )}
      </aside>
      {toasts.node}
    </div>
  );
}