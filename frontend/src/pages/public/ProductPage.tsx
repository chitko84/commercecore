import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Heart, ShoppingBag } from 'lucide-react';
import { cartApi, catalogApi, wishlistApi } from '../../lib/api';
import { formatMoney, formatDate, humanize } from '../../lib/format';
import { useAuth } from '../../lib/auth';
import { Button, ErrorState, Skeleton, Stars, useToasts, errorMessage } from '../../components/ui';
import { ProductImage, productImageUrl } from '../../components/ProductCard';

export default function ProductPage() {
  const { slug = '' } = useParams();
  const { status, profile } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toasts = useToasts();

  const product = useQuery({
    queryKey: ['product', slug],
    queryFn: () => catalogApi.product(slug),
  });
  const summary = useQuery({
    queryKey: ['rating', slug],
    queryFn: () => catalogApi.reviewSummary(slug),
  });
  const reviews = useQuery({
    queryKey: ['reviews', slug],
    queryFn: () => catalogApi.reviews(slug, 0, 8),
  });

  const [activeMedia, setActiveMedia] = useState(0);
  const [variantId, setVariantId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewTitle, setReviewTitle] = useState('');
  const [reviewBody, setReviewBody] = useState('');

  const data = product.data;
  const variants = useMemo(() => data?.variants ?? [], [data]);
  const activeVariant = variants.find((v) => v.id === variantId) ?? null;
  const price = activeVariant?.effectivePrice ?? data?.effectivePrice ?? '0';

  const addToCart = useMutation({
    mutationFn: () =>
      cartApi.add({
        productId: data!.id,
        variantId: activeVariant?.id ?? null,
        quantity,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      toasts.push('success', 'Added to cart');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const toggleWishlist = useMutation({
    mutationFn: async () => {
      const wl = await wishlistApi.get();
      const existing = wl.items.find((i) => i.productId === data!.id);
      if (existing) return wishlistApi.remove(existing.id);
      return wishlistApi.add({ productId: data!.id, variantId: activeVariant?.id ?? null });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['wishlist'] });
      toasts.push('success', 'Wishlist updated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const createReview = useMutation({
    mutationFn: () => catalogApi.createReview(slug, {
      rating: reviewRating,
      title: reviewTitle.trim() || undefined,
      body: reviewBody.trim() || undefined,
    }),
    onSuccess: () => {
      setReviewTitle('');
      setReviewBody('');
      void queryClient.invalidateQueries({ queryKey: ['reviews', slug] });
      void queryClient.invalidateQueries({ queryKey: ['rating', slug] });
      void queryClient.invalidateQueries({ queryKey: ['my-reviews'] });
      toasts.push('success', 'Review submitted for moderation');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const attrGroups = useMemo(() => {
    const groups = new Map<string, { name: string; value: string }[]>();
    for (const v of variants) {
      for (const attr of v.attributes) {
        if (!groups.has(attr.name)) groups.set(attr.name, []);
        if (!groups.get(attr.name)!.some((a) => a.value === attr.value)) {
          groups.get(attr.name)!.push({ name: attr.name, value: attr.value });
        }
      }
    }
    return Array.from(groups.entries());
  }, [variants]);

  if (product.isPending) return <div className="container"><Skeleton count={6} /></div>;
  if (product.isError) return <div className="container"><ErrorState error={product.error} title="Product not found" /></div>;
  if (!data) return <div className="container"><Skeleton count={6} /></div>;

  const media = data.media.length > 0 ? data.media : [];
  const availability = activeVariant?.availability ?? data.availability;
  const canOrder = availability !== 'OUT_OF_STOCK';

  return (
    <div className="container product-detail">
      <div className="product-gallery">
        {media.length > 0 ? (
          <>
            <div className="product-gallery__main">
              <img src={productImageUrl(media[activeMedia % media.length].mediaUrl)} alt={media[activeMedia % media.length].altText} />
            </div>
            {media.length > 1 && (
              <div className="product-gallery__thumbs">
                {media.map((m, i) => (
                  <button
                    key={m.id}
                    className={`product-gallery__thumb ${i === activeMedia % media.length ? 'is-active' : ''}`}
                    onClick={() => setActiveMedia(i)}
                    aria-label={`View image ${i + 1}`}
                  >
                    <img src={productImageUrl(m.mediaUrl)} alt="" />
                  </button>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="product-gallery__main">
            <ProductImage src={null} alt={data.name} className="product-image-fallback" />
          </div>
        )}
      </div>

      <div className="product-info">
        <span className="product-info__brand">
          {data.brand} · <Link to={`/catalog?category=${data.category.slug}`}>{data.category.name}</Link>
        </span>
        <h1 className="product-info__title">{data.name}</h1>
        {summary.data && (
          <div className="flex gap-2 items-center mb-4">
            <Stars rating={Number(summary.data.averageRating)} />
            <span className="text-sm text-muted">
              {summary.data.reviewCount === 0
                ? 'No reviews yet'
                : `${summary.data.averageRating} (${summary.data.reviewCount} review${summary.data.reviewCount === 1 ? '' : 's'})`}
            </span>
          </div>
        )}
        <div className="product-info__price">
          <span className="price">{formatMoney(price)}</span>
        </div>
        <span className="availability">
          <span className={`availability__dot ${availability === 'LOW_STOCK' ? 'availability__dot--low' : availability === 'OUT_OF_STOCK' ? 'availability__dot--out' : ''}`} />
          {humanize(availability)}
        </span>
        <p className="product-info__desc">{data.description}</p>

        {attrGroups.length > 0 &&
          attrGroups.map(([name, options]) => {
            const valueFor = (v: typeof variants[number], attrName: string) =>
              v.attributes.find((a) => a.name === attrName)?.value;
            return (
              <div className="variant-picker" key={name}>
                <span className="variant-picker__label">{name}</span>
                <div className="attr-chips" role="radiogroup" aria-label={name}>
                  {options.map((opt) => {
                    const matching = variants.find((v) => valueFor(v, name) === opt.value);
                    const selected =
                      activeVariant
                        ? valueFor(activeVariant, name) === opt.value
                        : variants.length === 1 && valueFor(variants[0], name) === opt.value;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        className={`attr-chip ${selected ? 'is-selected' : ''}`}
                        onClick={() => setVariantId(matching?.id ?? null)}
                      >
                        {opt.value}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}

        <div className="flex gap-3 flex-wrap" style={{ alignItems: 'center', marginBottom: '1rem' }}>
          <span className="qty">
            <button className="qty__btn" onClick={() => setQuantity((q) => Math.max(1, q - 1))} aria-label="Decrease quantity" disabled={!canOrder}>
              −
            </button>
            <span className="qty__value">{quantity}</span>
            <button className="qty__btn" onClick={() => setQuantity((q) => Math.min(99, q + 1))} aria-label="Increase quantity" disabled={!canOrder}>
              +
            </button>
          </span>
          <Button
            size="lg"
            onClick={() => {
              if (status !== 'authenticated') {
                navigate('/login', { state: { from: `/products/${data.slug}` } });
                return;
              }
              addToCart.mutate();
            }}
            disabled={!canOrder || addToCart.isPending}
          >
            <ShoppingBag size={18} /> {canOrder ? 'Add to cart' : 'Out of stock'}
          </Button>
          <Button variant="outline" size="lg" onClick={() => toggleWishlist.mutate()} disabled={toggleWishlist.isPending} aria-label="Toggle wishlist">
            <Heart size={18} />
          </Button>
        </div>
        {status !== 'authenticated' && (
          <p className="text-sm text-muted">
            <Link to="/login" style={{ textDecoration: 'underline' }}>
              Sign in
            </Link>{' '}
            to add to cart and check out.
          </p>
        )}
      </div>

      <div style={{ gridColumn: '1 / -1' }}>
        <div className="section-head" style={{ marginTop: '2rem' }}>
          <h2>Reviews</h2>
          {summary.data && summary.data.reviewCount > 0 && (
            <div className="rating-summary" style={{ margin: 0 }}>
              <span className="rating-summary__score">{Number(summary.data.averageRating).toFixed(1)}</span>
              <Stars rating={Number(summary.data.averageRating)} size={18} />
            </div>
          )}
        </div>
        {profile?.role === 'CUSTOMER' && (
          <form
            className="card mb-4"
            aria-label="Write a review"
            onSubmit={(event) => {
              event.preventDefault();
              createReview.mutate();
            }}
          >
            <h3>Write a review</h3>
            <p className="text-sm text-muted">Only delivered purchases are eligible. New reviews are published after moderation.</p>
            <label className="field">
              <span className="field__label">Rating</span>
              <select className="select" value={reviewRating} onChange={(event) => setReviewRating(Number(event.target.value))}>
                {[5, 4, 3, 2, 1].map((rating) => <option key={rating} value={rating}>{rating} star{rating === 1 ? '' : 's'}</option>)}
              </select>
            </label>
            <label className="field">
              <span className="field__label">Title</span>
              <input className="input" maxLength={160} value={reviewTitle} onChange={(event) => setReviewTitle(event.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">Review</span>
              <textarea className="textarea" maxLength={4000} value={reviewBody} onChange={(event) => setReviewBody(event.target.value)} />
            </label>
            <Button type="submit" loading={createReview.isPending} disabled={!reviewBody.trim()}>Submit review</Button>
          </form>
        )}
        {reviews.isPending ? (
          <Skeleton count={3} />
        ) : reviews.data && reviews.data.content.length > 0 ? (
          <div className="card">
            {reviews.data.content.map((r) => (
              <div className="review-card" key={r.id}>
                <div className="review-card__head">
                  <span className="review-card__author">{r.authorName}</span>
                  <span className="review-card__date">{formatDate(r.createdAt)}</span>
                </div>
                <div className="flex gap-2 items-center mb-2">
                  <Stars rating={r.rating} />
                </div>
                {r.title && <div className="review-card__title">{r.title}</div>}
                {r.body && <div className="review-card__body">{r.body}</div>}
              </div>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <h3>No reviews yet</h3>
            <p>Be the first to review this product after your order is delivered.</p>
          </div>
        )}
      </div>
      {toasts.node}
    </div>
  );
}
