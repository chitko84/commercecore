import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ImageOff } from 'lucide-react';
import type { ProductSummary } from '../lib/types';
import { formatMoney, humanize } from '../lib/format';
import { Badge } from './ui';

export function productImageUrl(mediaUrl: string | null | undefined): string {
  if (!mediaUrl) return '';
  if (mediaUrl.startsWith('http')) return mediaUrl;
  return mediaUrl;
}

export function ProductImage({
  src,
  alt,
  className,
}: {
  src?: string | null;
  alt: string;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) {
    return (
      <div className={`product-image-fallback ${className ?? ''}`} role="img" aria-label={alt}>
        <ImageOff size={32} />
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={alt}
      loading="lazy"
      onError={() => setFailed(true)}
      className={className}
    />
  );
}

export function ProductCard({ product }: { product: ProductSummary }) {
  const sale = product.salePrice !== null && product.salePrice !== product.effectivePrice;
  const lowStock = product.availability === 'LOW_STOCK' || product.availability === 'OUT_OF_STOCK';
  return (
    <Link to={`/products/${product.slug}`} className="product-card" aria-label={product.name}>
      <div className="product-card__media">
        <ProductImage src={productImageUrl(product.primaryMedia?.mediaUrl)} alt={product.name} />
      </div>
      <div className="product-card__body">
        <span className="product-card__brand">{product.brand}</span>
        <h3 className="product-card__name">{product.name}</h3>
        <div className="product-card__foot">
          <span className="price">
            {formatMoney(product.effectivePrice)}
            {sale && <span className="price--strike">{formatMoney(product.basePrice)}</span>}
          </span>
          {lowStock && <Badge tone={product.availability === 'OUT_OF_STOCK' ? 'error' : 'warning'}>{humanize(product.availability)}</Badge>}
        </div>
      </div>
    </Link>
  );
}