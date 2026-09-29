import { NavLink, Outlet } from 'react-router-dom';
import { Bell, Heart, KeyRound, MapPin, Package, Star, User } from 'lucide-react';

const links = [
  { to: '/account', label: 'Profile', icon: User, end: true },
  { to: '/account/orders', label: 'Orders', icon: Package },
  { to: '/account/addresses', label: 'Addresses', icon: MapPin },
  { to: '/account/reviews', label: 'Reviews', icon: Star },
  { to: '/account/notifications', label: 'Notifications', icon: Bell },
  { to: '/account/wishlist', label: 'Wishlist', icon: Heart },
  { to: '/account/password', label: 'Change password', icon: KeyRound },
];

export default function AccountLayout() {
  return (
    <div className="container account-shell">
      <nav className="account-nav" aria-label="Account">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => `account-nav__link ${isActive ? 'is-active' : ''}`}
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}
      </nav>
      <section>
        <Outlet />
      </section>
    </div>
  );
}