import { useDeferredValue, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../../lib/api';
import { ProductCard } from '../../components/ProductCard';
import { ErrorState, Pagination, Skeleton } from '../../components/ui';

const SORTS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
  { value: 'name', label: 'Name A–Z' },
  { value: 'oldest', label: 'Oldest' },
];

export default function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const [searchInput, setSearchInput] = useState(params.get('q') ?? '');
  const deferredQ = useDeferredValue(searchInput);

  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const brand = params.get('brand') ?? '';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const sort = params.get('sort') ?? 'newest';
  const page = Number(params.get('page') ?? '0');

  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogApi.categories });
  const query = useQuery({
    queryKey: ['catalog', { q, category, brand, minPrice, maxPrice, sort, page }],
    queryFn: () =>
      catalogApi.products({
        q: q || undefined,
        category: category || undefined,
        brand: brand || undefined,
        minPrice: minPrice || undefined,
        maxPrice: maxPrice || undefined,
        sort,
        page,
        size: 12,
      }),
    placeholderData: (prev) => prev,
  });

  const brands = useMemo(() => {
    if (!query.data) return [];
    return Array.from(new Set(query.data.content.map((p) => p.brand))).sort();
  }, [query.data]);

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(patch)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    next.delete('page');
    setParams(next);
  };

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    update({ q: deferredQ });
  };

  return (
    <div className="container catalog-layout">
      <aside className="catalog-filters" id="categories">
        <h3>Filters</h3>
        <form onSubmit={submitSearch}>
          <div className="filter-group">
            <label className="filter-group__label" htmlFor="catalog-search">
              Search
            </label>
            <input
              id="catalog-search"
              className="input"
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search products…"
            />
          </div>
        </form>
        <div className="filter-group">
          <span className="filter-group__label">Category</span>
          {categories.data?.map((c) => (
            <label key={c.id} className="filter-option">
              <input
                type="radio"
                name="category"
                checked={category === c.slug}
                onChange={() => update({ category: category === c.slug ? '' : c.slug })}
              />
              {c.name}
            </label>
          ))}
        </div>
        {brands.length > 0 && (
          <div className="filter-group">
            <span className="filter-group__label">Brand</span>
            {brands.map((b) => (
              <label key={b} className="filter-option">
                <input
                  type="radio"
                  name="brand"
                  checked={brand === b}
                  onChange={() => update({ brand: brand === b ? '' : b })}
                />
                {b}
              </label>
            ))}
          </div>
        )}
        <div className="filter-group">
          <span className="filter-group__label">Price</span>
          <div className="flex gap-1">
            <input
              className="input"
              type="number"
              min="0"
              step="0.01"
              placeholder="Min"
              value={minPrice}
              onChange={(e) => update({ minPrice: e.target.value })}
              aria-label="Minimum price"
            />
            <input
              className="input"
              type="number"
              min="0"
              step="0.01"
              placeholder="Max"
              value={maxPrice}
              onChange={(e) => update({ maxPrice: e.target.value })}
              aria-label="Maximum price"
            />
          </div>
        </div>
      </aside>

      <section>
        <div className="catalog-topbar">
          <span className="catalog-count">
            {query.data ? `${query.data.totalElements} product${query.data.totalElements === 1 ? '' : 's'}` : '…'}
          </span>
          <label className="flex gap-1 items-center">
            <span className="text-sm text-muted">Sort</span>
            <select
              className="select"
              value={sort}
              onChange={(e) => update({ sort: e.target.value })}
              aria-label="Sort products"
            >
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        {query.isPending ? (
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
        ) : query.isError ? (
          <ErrorState error={query.error} title="Could not load products" />
        ) : query.data.content.length === 0 ? (
          <div className="empty-state">
            <h3>No products found</h3>
            <p>Try adjusting your filters or search terms.</p>
          </div>
        ) : (
          <>
            <div className="product-grid">
              {query.data.content.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
            <Pagination
              page={query.data.page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              size={query.data.size}
              onChange={(p) => update({ page: String(p) })}
            />
          </>
        )}
      </section>
    </div>
  );
}
