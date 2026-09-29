/* Formatting helpers. Backend money values are decimal strings. */

const moneyFmt = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
});

export function formatMoney(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return moneyFmt.format(0);
  const n = typeof value === 'number' ? value : Number(value);
  return moneyFmt.format(Number.isFinite(n) ? n : 0);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(d);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(d);
}

const TITLE_CASE: Record<string, string> = {
  CARD: 'Card',
  ONLINE_BANKING: 'Online banking',
  E_WALLET: 'E-wallet',
  PENDING: 'Pending',
  AUTHORIZED: 'Authorized',
  PAID: 'Paid',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
  PARTIALLY_REFUNDED: 'Partially refunded',
  PENDING_VERIFICATION: 'Pending verification',
  ACTIVE: 'Active',
  LOCKED: 'Locked',
  DEACTIVATED: 'Deactivated',
  CONFIRMED: 'Confirmed',
  PROCESSING: 'Processing',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  IN_STOCK: 'In stock',
  LOW_STOCK: 'Low stock',
  OUT_OF_STOCK: 'Out of stock',
  UNKNOWN: 'Unknown',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  STANDARD: 'Standard',
  EXPRESS: 'Express',
  PERCENTAGE: 'Percentage',
  FIXED_AMOUNT: 'Fixed amount',
  FREE_SHIPPING: 'Free shipping',
  CART: 'Cart-wide',
  PRODUCT: 'Product',
  CATEGORY: 'Category',
  COMPLETED: 'Completed',
  AVAILABLE: 'Available',
  INSUFFICIENT_STOCK: 'Insufficient stock',
  UNAVAILABLE: 'Unavailable',
  WELCOME: 'Welcome',
  PASSWORD_CHANGED: 'Password changed',
  PASSWORD_RESET: 'Password reset',
  ORDER_CREATED: 'Order created',
  ORDER_CONFIRMED: 'Order confirmed',
  ORDER_PROCESSING: 'Order processing',
  ORDER_SHIPPED: 'Order shipped',
  ORDER_DELIVERED: 'Order delivered',
  ORDER_CANCELLED: 'Order cancelled',
  PAYMENT_SUCCEEDED: 'Payment succeeded',
  PAYMENT_FAILED: 'Payment failed',
  REFUND_COMPLETED: 'Refund completed',
  LOW_STOCK_ALERT: 'Low stock alert',
  FAILED_PAYMENT_ALERT: 'Failed payment alert',
  CUSTOMER: 'Customer',
  STAFF: 'Staff',
  ADMIN: 'Admin',
};

export function humanize(value: string | null | undefined): string {
  if (!value) return '—';
  return TITLE_CASE[value] ?? value.replaceAll('_', ' ');
}

/** Converts a local date-time input value to a UTC ISO-8601 Instant. */
export function toInstant(dateTimeLocal: string): string {
  if (!dateTimeLocal) return '';
  const d = new Date(dateTimeLocal);
  return d.toISOString();
}