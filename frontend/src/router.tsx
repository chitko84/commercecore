import { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import PublicLayout from './layouts/PublicLayout';
import AccountLayout from './layouts/AccountLayout';
import AdminLayout from './layouts/AdminLayout';
import { RequireAdmin, RequireAuth, RequireCustomer, RequireStaff } from './lib/auth';
import { LoadingState } from './components/ui';

function lazyPage(factory: () => Promise<{ default: React.ComponentType }>) {
  const Component = lazy(factory);
  return (
    <Suspense fallback={<LoadingState label="Loading page…" />}>
      <Component />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <PublicLayout />,
    children: [
      { index: true, element: lazyPage(() => import('./pages/public/HomePage')) },
      { path: 'catalog', element: lazyPage(() => import('./pages/public/CatalogPage')) },
      { path: 'products/:slug', element: lazyPage(() => import('./pages/public/ProductPage')) },
      { path: 'login', element: lazyPage(() => import('./pages/public/LoginPage')) },
      { path: 'register', element: lazyPage(() => import('./pages/public/RegisterPage')) },
      { path: 'verify-email', element: lazyPage(() => import('./pages/public/VerifyEmailPage')) },
      { path: 'forgot-password', element: lazyPage(() => import('./pages/public/ForgotPasswordPage')) },
      { path: 'reset-password', element: lazyPage(() => import('./pages/public/ResetPasswordPage')) },
      {
        path: 'cart',
        element: (
          <RequireCustomer>
            {lazyPage(() => import('./pages/customer/CartPage'))}
          </RequireCustomer>
        ),
      },
      {
        path: 'checkout',
        element: (
          <RequireCustomer>
            {lazyPage(() => import('./pages/customer/CheckoutPage'))}
          </RequireCustomer>
        ),
      },
      {
        path: 'orders/:orderId/confirmation',
        element: (
          <RequireCustomer>
            {lazyPage(() => import('./pages/customer/OrderConfirmationPage'))}
          </RequireCustomer>
        ),
      },
      {
        path: 'wishlist',
        element: (
          <RequireCustomer>
            {lazyPage(() => import('./pages/customer/WishlistPage'))}
          </RequireCustomer>
        ),
      },
      {
        path: 'account',
        element: (
          <RequireAuth>
            <AccountLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: lazyPage(() => import('./pages/customer/AccountProfilePage')) },
          { path: 'orders', element: lazyPage(() => import('./pages/customer/AccountOrdersPage')) },
          { path: 'orders/:orderId', element: lazyPage(() => import('./pages/customer/AccountOrderDetailPage')) },
          { path: 'addresses', element: lazyPage(() => import('./pages/customer/AccountAddressesPage')) },
          { path: 'reviews', element: lazyPage(() => import('./pages/customer/AccountReviewsPage')) },
          { path: 'notifications', element: lazyPage(() => import('./pages/customer/AccountNotificationsPage')) },
          {
            path: 'wishlist',
            element: (
              <RequireCustomer>
                {lazyPage(() => import('./pages/customer/WishlistPage'))}
              </RequireCustomer>
            ),
          },
          { path: 'password', element: lazyPage(() => import('./pages/customer/ChangePasswordPage')) },
        ],
      },
    ],
  },
  {
    path: '/admin',
    element: (
      <RequireStaff>
        <AdminLayout />
      </RequireStaff>
    ),
    children: [
      { index: true, element: lazyPage(() => import('./pages/admin/AdminDashboardPage')) },
      { path: 'products', element: lazyPage(() => import('./pages/admin/AdminProductsPage')) },
      { path: 'products/new', element: lazyPage(() => import('./pages/admin/AdminProductFormPage')) },
      { path: 'products/:id/edit', element: lazyPage(() => import('./pages/admin/AdminProductFormPage')) },
      { path: 'categories', element: lazyPage(() => import('./pages/admin/AdminCategoriesPage')) },
      { path: 'inventory', element: lazyPage(() => import('./pages/admin/AdminInventoryPage')) },
      { path: 'promotions', element: lazyPage(() => import('./pages/admin/AdminPromotionsPage')) },
      { path: 'orders', element: lazyPage(() => import('./pages/admin/AdminOrdersPage')) },
      { path: 'orders/:orderId', element: lazyPage(() => import('./pages/admin/AdminOrderDetailPage')) },
      { path: 'payments', element: lazyPage(() => import('./pages/admin/AdminPaymentsPage')) },
      { path: 'reviews', element: lazyPage(() => import('./pages/admin/AdminReviewModerationPage')) },
      {
        path: 'users',
        element: (
          <RequireAdmin>
            {lazyPage(() => import('./pages/admin/AdminUsersPage'))}
          </RequireAdmin>
        ),
      },
      {
        path: 'alerts',
        element: (
          <RequireAdmin>
            {lazyPage(() => import('./pages/admin/AdminAlertsPage'))}
          </RequireAdmin>
        ),
      },
      {
        path: 'audit',
        element: (
          <RequireAdmin>
            {lazyPage(() => import('./pages/admin/AdminAuditPage'))}
          </RequireAdmin>
        ),
      },
      {
        path: 'reports',
        element: (
          <RequireAdmin>
            {lazyPage(() => import('./pages/admin/AdminReportsPage'))}
          </RequireAdmin>
        ),
      },
    ],
  },
  { path: '/unauthorized', element: lazyPage(() => import('./pages/public/ForbiddenPage')) },
  { path: '*', element: lazyPage(() => import('./pages/public/NotFoundPage')) },
]);
