import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShoppingBag, Trash2 } from 'lucide-react';
import { wishlistApi } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { Button, EmptyState, ErrorState, LoadingState, useToasts, errorMessage } from '../../components/ui';

export default function WishlistPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();

  const wishlist = useQuery({ queryKey: ['wishlist'], queryFn: wishlistApi.get });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['wishlist'] });

  const remove = useMutation({
    mutationFn: (id: string) => wishlistApi.remove(id),
    onSuccess: invalidate,
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const moveToCart = useMutation({
    mutationFn: (id: string) => wishlistApi.moveToCart(id, 1),
    onSuccess: () => {
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      toasts.push('success', 'Moved to cart');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (wishlist.isPending) return <LoadingState label="Loading wishlist…" />;
  if (wishlist.isError) return <ErrorState error={wishlist.error} />;

  if (wishlist.data.items.length === 0) {
    return (
      <EmptyState
        title="Your wishlist is empty"
        body="Save products you are interested in and move them to your cart later."
        action={
          <Link className="btn btn--dark" to="/catalog">
            Browse products
          </Link>
        }
      />
    );
  }

  return (
    <div className="container" style={{ paddingBlock: '2rem' }}>
      <h1>Wishlist</h1>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Price</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {wishlist.data.items.map((item) => (
              <tr key={item.id}>
                <td>
                  <Link to={`/products/${item.productId}`} style={{ fontWeight: 600 }}>
                    {item.productName}
                  </Link>
                  <div className="text-xs text-faint">SKU {item.sku}</div>
                </td>
                <td>{formatMoney(item.currentPrice)}</td>
                <td>
                  <div className="actions justify-end">
                    <Button size="sm" onClick={() => moveToCart.mutate(item.id)} disabled={moveToCart.isPending}>
                      <ShoppingBag size={15} /> Move to cart
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => remove.mutate(item.id)} aria-label="Remove from wishlist">
                      <Trash2 size={15} />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {toasts.node}
    </div>
  );
}