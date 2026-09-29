import { Link } from 'react-router-dom';
import { ArrowRight, BadgeCheck, RefreshCcw, ShieldCheck, Truck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../../lib/api';
import { ProductCard } from '../../components/ProductCard';
import { Skeleton } from '../../components/ui';

const FEATURES = [
  { icon: Truck, title: 'Fast shipping', body: 'Standard and express delivery with live tracking.' },
  { icon: RefreshCcw, title: 'Easy returns', body: 'Hassle-free cancellations and refunds.' },
  { icon: ShieldCheck, title: 'Secure checkout', body: 'Sandbox payments with idempotent, verified transactions.' },
  { icon: BadgeCheck, title: 'Verified reviews', body: 'Ratings from verified purchases only.' },
];

export default function HomePage() {
  const featured = useQuery({
    queryKey: ['catalog', 'featured'],
    queryFn: () => catalogApi.products({ sort: 'newest', size: 8, page: 0 }),
  });
  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogApi.categories });

  return (
    <>
      <section className="hero">
        <div className="container hero__inner">
          <div>
            <span className="hero__eyebrow">The CommerceCore store</span>
            <h1 className="hero__title">
              Technology, <span className="accent">rethought.</span>
            </h1>
            <p className="hero__copy">
              A curated catalog of premium audio, wearables and everyday tech — with secure
              checkout, verified reviews and delivery tracking from order to doorstep.
            </p>
            <div className="flex gap-3 flex-wrap">
              <Link className="btn btn--primary btn--lg" to="/catalog">
                Shop the collection <ArrowRight size={18} />
              </Link>
              <Link className="btn btn--outline btn--lg" to="/register">
                Create an account
              </Link>
            </div>
          </div>
          <div className="hero__media">
            <img src="/products/hero-audio.jpg" alt="Premium over-ear headphones on a dark background" />
          </div>
        </div>
      </section>

      <section className="container feature-strip">
        {FEATURES.map(({ icon: Icon, title, body }) => (
          <div className="feature-strip__item" key={title}>
            <span className="feature-strip__icon">
              <Icon size={22} />
            </span>
            <div>
              <h3 style={{ fontSize: '1rem', marginBottom: '0.25rem' }}>{title}</h3>
              <p className="text-sm text-muted" style={{ margin: 0 }}>
                {body}
              </p>
            </div>
          </div>
        ))}
      </section>

      <section className="section section--alt">
        <div className="container">
          <div className="section-head">
            <h2>New arrivals</h2>
            <Link className="section-head__link" to="/catalog">
              View all <ArrowRight size={16} />
            </Link>
          </div>
          {featured.isPending ? (
            <div className="product-grid">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="card" style={{ padding: 0, overflow: 'hidden' }}>
                  <div className="skeleton" style={{ height: 220, borderRadius: 0 }} />
                  <div style={{ padding: 16 }}>
                    <Skeleton count={2} />
                  </div>
                </div>
              ))}
            </div>
          ) : featured.isError ? (
            <p className="text-muted">Products are temporarily unavailable. Please try again shortly.</p>
          ) : (
            <div className="product-grid">
              {featured.data.content.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <h2>Shop by category</h2>
            <Link className="section-head__link" to="/catalog">
              Browse everything <ArrowRight size={16} />
            </Link>
          </div>
          {categories.isPending ? (
            <Skeleton count={3} className="skeleton--card" />
          ) : (
            <div className="product-grid">
              {categories.data?.map((c) => (
                <Link key={c.id} to={`/catalog?category=${c.slug}`} className="card" style={{ display: 'block' }}>
                  <h3 style={{ marginBottom: '0.25rem' }}>{c.name}</h3>
                  <p className="text-sm text-muted" style={{ margin: 0 }}>
                    {c.description || `Browse the ${c.name} collection`}
                  </p>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="section section--dark">
        <div className="container" style={{ textAlign: 'center', maxWidth: 640 }}>
          <h2 style={{ fontSize: '2rem' }}>Checkout in minutes, track everything.</h2>
          <p style={{ color: '#b8b8c0' }}>
            Create an account to keep your cart, save addresses, follow orders and get notified at
            every step — from payment to delivery.
          </p>
          <Link className="btn btn--primary btn--lg" to="/register">
            Get started
          </Link>
        </div>
      </section>
    </>
  );
}