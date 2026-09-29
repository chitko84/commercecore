import { useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Bell, Heart, LayoutDashboard, LogOut, Menu, Package, ShoppingBag, User, X, Star,
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { cartApi, notificationsApi } from '../lib/api';
import { useQuery } from '@tanstack/react-query';

function useCartCount() {
  const { status } = useAuth();
  const { data } = useQuery({
    queryKey: ['cart'],
    queryFn: cartApi.get,
    enabled: status === 'authenticated',
    staleTime: 30_000,
  });
  return data?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
}

function useUnreadNotifications() {
  const { status, profile } = useAuth();
  const { data } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: notificationsApi.unreadCount,
    enabled: status === 'authenticated',
    refetchInterval: 60_000,
  });
  return profile ? data?.unreadCount ?? 0 : 0;
}

function UserMenu() {
  const { status, profile, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  if (status !== 'authenticated' || !profile) {
    return (
      <div className="header-actions desktop-auth-actions">
        <Link className="btn btn--ghost btn--sm" to="/login">
          Sign in
        </Link>
        <Link className="btn btn--dark btn--sm" to="/register">
          Create account
        </Link>
      </div>
    );
  }
  const adminLink = profile.role === 'ADMIN' || profile.role === 'STAFF' ? '/admin' : null;
  return (
    <div className="user-menu">
      <button className="user-menu__trigger" onClick={() => setOpen((v) => !v)} aria-haspopup="menu" aria-expanded={open}>
        <User size={16} />
        <span className="hidden-mobile">{profile.fullName.split(' ')[0]}</span>
      </button>
      {open && (
        <div className="user-menu__panel" role="menu">
          <div className="user-menu__label">{profile.email}</div>
          <Link className="user-menu__item" to="/account" onClick={() => setOpen(false)} role="menuitem">
            <User size={16} /> My account
          </Link>
          <Link className="user-menu__item" to="/account/orders" onClick={() => setOpen(false)} role="menuitem">
            <Package size={16} /> Orders
          </Link>
          <Link className="user-menu__item" to="/account/notifications" onClick={() => setOpen(false)} role="menuitem">
            <Bell size={16} /> Notifications
          </Link>
          {adminLink && (
            <Link className="user-menu__item" to={adminLink} onClick={() => setOpen(false)} role="menuitem">
              <LayoutDashboard size={16} /> Management
            </Link>
          )}
          <button
            className="user-menu__item"
            onClick={() => {
              setOpen(false);
              navigate('/', { replace: true });
              void logout();
            }}
            role="menuitem"
          >
            <LogOut size={16} /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

export default function PublicLayout() {
  const cartCount = useCartCount();
  const unread = useUnreadNotifications();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [search, setSearch] = useState('');
  const navigate = useNavigate();

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(search.trim() ? `/catalog?q=${encodeURIComponent(search.trim())}` : '/catalog');
    setSearch('');
    setMobileOpen(false);
  };

  const nav = useMemo(
    () => (
      <>
        <NavLink className={({ isActive }) => `site-nav__link ${isActive ? 'is-active' : ''}`} to="/catalog" end>
          Shop
        </NavLink>
        <NavLink className={({ isActive }) => `site-nav__link ${isActive ? 'is-active' : ''}`} to="/catalog?sort=price-asc">
          Best value
        </NavLink>
        <NavLink className={({ isActive }) => `site-nav__link ${isActive ? 'is-active' : ''}`} to="/account/orders">
          Track order
        </NavLink>
      </>
    ),
    [],
  );

  return (
    <>
      <header className="site-header">
        <div className="container site-header__inner">
          <Link to="/" className="logo" aria-label="CommerceCore home">
            <span className="logo__mark">
              <Star size={16} fill="currentColor" />
            </span>
            <span>CommerceCore</span>
          </Link>
          <nav className="site-nav hidden-mobile" aria-label="Primary">
            {nav}
          </nav>
          <form onSubmit={submitSearch} role="search" className="header-search" aria-label="Search products">
            <input
              className="input"
              type="search"
              placeholder="Search products…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search products"
            />
          </form>
          <div className="header-actions">
            <Link className="header-icon-btn hidden-mobile" to="/wishlist" aria-label="Wishlist">
              <Heart size={20} />
            </Link>
            <Link
              className="header-icon-btn hidden-mobile"
              to="/account/notifications"
              aria-label={`Notifications, ${unread} unread`}
            >
              <Bell size={20} />
              {unread > 0 && <span className="header-icon-btn__badge">{unread}</span>}
            </Link>
            <Link className="header-icon-btn" to="/cart" aria-label={`Cart, ${cartCount} items`}>
              <ShoppingBag size={20} />
              {cartCount > 0 && <span className="header-icon-btn__badge">{cartCount}</span>}
            </Link>
            <UserMenu />
            <button className="nav-toggle" onClick={() => setMobileOpen(true)} aria-label="Open menu">
              <Menu size={22} />
            </button>
          </div>
        </div>
        <style>{`.header-search{flex:1;max-width:320px}.header-search .input{border-radius:999px}.header-search .input:focus{box-shadow:var(--shadow-focus)}`}</style>
      </header>

      {mobileOpen && (
        <div className="mobile-nav" role="dialog" aria-modal="true">
          <div className="mobile-nav__head">
            <Link to="/" className="logo" onClick={() => setMobileOpen(false)}>
              <span className="logo__mark">
                <Star size={16} fill="currentColor" />
              </span>
              <span>CommerceCore</span>
            </Link>
            <button className="nav-toggle" onClick={() => setMobileOpen(false)} aria-label="Close menu">
              <X size={22} />
            </button>
          </div>
          <form onSubmit={submitSearch}>
            <input
              className="input"
              type="search"
              placeholder="Search products…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </form>
          <nav aria-label="Mobile">
            <Link className="mobile-nav__link" to="/catalog" onClick={() => setMobileOpen(false)}>
              Shop all
            </Link>
            <Link className="mobile-nav__link" to="/wishlist" onClick={() => setMobileOpen(false)}>
              Wishlist
            </Link>
            <Link className="mobile-nav__link" to="/account" onClick={() => setMobileOpen(false)}>
              My account
            </Link>
            <Link className="mobile-nav__link" to="/cart" onClick={() => setMobileOpen(false)}>
              Cart
            </Link>
          </nav>
        </div>
      )}

      <main id="main">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="container">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '2rem', marginBottom: '3rem' }}>
            <div className="footer-col">
              <h4>CommerceCore</h4>
              <p style={{ color: '#a8a8b0', fontSize: '0.875rem' }}>
                A complete e-commerce platform: catalog, secure checkout, orders, reviews and insights.
              </p>
            </div>
            <div className="footer-col">
              <h4>Shop</h4>
              <Link to="/catalog">All products</Link>
              <Link to="/catalog?sort=price-asc">Best value</Link>
            </div>
            <div className="footer-col">
              <h4>Account</h4>
              <Link to="/account/orders">Orders</Link>
              <Link to="/account/notifications">Notifications</Link>
              <Link to="/account/reviews">Reviews</Link>
            </div>
            <div className="footer-col">
              <h4>Support</h4>
              <Link to="/account/addresses">Addresses</Link>
              <Link to="/account">Profile</Link>
            </div>
          </div>
          <div className="text-sm text-faint" style={{ borderTop: '1px solid #26262a', paddingTop: '1.5rem' }}>
            © {new Date().getFullYear()} CommerceCore. Demo application — all payments are simulated sandbox transactions.
          </div>
        </div>
      </footer>
    </>
  );
}
