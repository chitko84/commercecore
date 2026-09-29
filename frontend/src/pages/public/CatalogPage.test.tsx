import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, stubRoutes } from '../../test/helpers';
import CatalogPage from './CatalogPage';

const PAGE = {
  content: [
    {
      id: 'p1', name: 'Aurora Headphones', slug: 'aurora-headphones', sku: 'AU-1', brand: 'Aurora',
      basePrice: '199.00', salePrice: null, effectivePrice: '199.00',
      category: { id: 'c1', name: 'Audio', slug: 'audio' },
      primaryMedia: { id: 'm1', mediaUrl: '/products/headphones.jpg', altText: 'Aurora Headphones', displayOrder: 0, primary: true, createdAt: 'x' },
      active: true, availability: 'IN_STOCK', createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0, size: 12, totalElements: 1, totalPages: 1, first: true, last: true,
};

describe('CatalogPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders products from the mocked API with real data', async () => {
    const restore = stubRoutes({ '/categories': [], '/products?': PAGE });
    renderWithProviders(<CatalogPage />, ['/catalog']);
    expect(await screen.findByText('Aurora Headphones')).toBeInTheDocument();
    // internal SKU is never exposed on the public card
    expect(screen.queryByText('AU-1')).not.toBeInTheDocument();
    expect(screen.getByText('$199.00')).toBeInTheDocument();
    restore();
  });

  it('shows an empty state when there are no products', async () => {
    const empty = { ...PAGE, content: [], totalElements: 0, totalPages: 0 };
    const restore = stubRoutes({ '/categories': [], '/products?': empty });
    renderWithProviders(<CatalogPage />, ['/catalog']);
    expect(await screen.findByText('No products found')).toBeInTheDocument();
    restore();
  });

  it('renders the count of products', async () => {
    const restore = stubRoutes({ '/categories': [], '/products?': PAGE });
    renderWithProviders(<CatalogPage />, ['/catalog']);
    expect(await screen.findByText('1 product')).toBeInTheDocument();
    restore();
  });
});