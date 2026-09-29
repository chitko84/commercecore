import { NavLink, Outlet, Link, useLocation } from 'react-router-dom';
import {
  AlertTriangle, BarChart3, Boxes, ClipboardList, CreditCard, FolderTree, LayoutDashboard,
  Package, ScrollText, ShieldCheck, Star, Tags, Users, ChevronLeft, Store,
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { Badge } from '../components/ui';

export default function AdminLayout() {
  const { profile } = useAuth();
  const location = useLocation();
  const isAdmin = profile?.role === 'ADMIN';

  const catalogLinks = [
    { to: '/admin/products', label: 'Products', icon: Package },
    { to: '/admin/categories', label: 'Categories', icon: FolderTree },
    { to: '/admin/inventory', label: 'Inventory', icon: Boxes },
    { to: '/admin/promotions', label: 'Promotions', icon: Tags },
  ];

  const operationsLinks = [
    { to: '/admin/orders', label: 'Orders', icon: ClipboardList },
    { to: '/admin/payments', label: 'Payments & refunds', icon: CreditCard },
    { to: '/admin/reviews', label: 'Review moderation', icon: Star },
  ];

  const adminLinks = [
    { to: '/admin/users', label: 'Users', icon: Users },
    { to: '/admin/alerts', label: 'Alerts', icon: AlertTriangle },
    { to: '/admin/audit', label: 'Audit log', icon: ScrollText },
    { to: '/admin/reports', label: 'Reports', icon: BarChart3 },
  ];

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <Link to="/" className="logo" style={{ color: '#fff', marginBottom: '1rem' }}>
          <span className="logo__mark">
            <Store size={16} />
          </span>
          <span>CommerceCore</span>
        </Link>
        <Link
          to="/admin"
          className={`admin-sidebar__link ${location.pathname === '/admin' ? 'is-active' : ''}`}
        >
          <LayoutDashboard size={16} /> Dashboard
        </Link>
        <div className="admin-sidebar__section">Catalog</div>
        {catalogLinks.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={({ isActive }) => `admin-sidebar__link ${isActive ? 'is-active' : ''}`}>
            <Icon size={16} /> {label}
          </NavLink>
        ))}
        <div className="admin-sidebar__section">Operations</div>
        {operationsLinks.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={({ isActive }) => `admin-sidebar__link ${isActive ? 'is-active' : ''}`}>
            <Icon size={16} /> {label}
          </NavLink>
        ))}
        {isAdmin && (
          <>
            <div className="admin-sidebar__section">Administration</div>
            {adminLinks.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} className={({ isActive }) => `admin-sidebar__link ${isActive ? 'is-active' : ''}`}>
                <Icon size={16} /> {label}
              </NavLink>
            ))}
          </>
        )}
      </aside>
      <main className="admin-main">
        <div className="admin-main__head">
          <div>
            <div className="breadcrumbs">
              <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                <ChevronLeft size={12} /> Storefront
              </Link>
            </div>
            <h1 className="admin-main__title">Management</h1>
          </div>
          <Badge tone={profile?.role === 'ADMIN' ? 'accent' : 'info'}>
            <ShieldCheck size={12} /> {profile?.role}
          </Badge>
        </div>
        <Outlet />
      </main>
    </div>
  );
}