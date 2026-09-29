import type {
  AddressRequest, AddressResponse, AdminAlert, AdminReview, AdminUserResponse, AuditLog,
  AuthTokensResponse, CartResponse, CategoryResponse, CustomersReport, Dashboard, InventoryHistory,
  InventoryReport, LoginRequest, MessageResponse, MyReview, Notification, OrderResponse, OrdersReport,
  Page, PaymentResponse, PaymentStatus, PaymentsReport, ProductResponse, ProductSummary,
  ProfileResponse, PromotionResponse, PublicReview, RefundResponse, RefundsReport, RegisterRequest,
  RegistrationResponse, ResetPasswordRequest, ReviewRequest, ReviewSummary, ReviewsReport,
  SalesReport, ShipmentResponse, TopProductsReport, UnacknowledgedCount, UnreadCount,
  UserRole, WishlistResponse,
} from './types';
import type { AdminUserPage } from './typesAdmin';

/* ------------------------------------------------------------------ */
/* Configuration                                                      */
/* ------------------------------------------------------------------ */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1';
const parsedTimeout = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 15_000);
export const API_TIMEOUT_MS = Number.isFinite(parsedTimeout) && parsedTimeout > 0 ? parsedTimeout : 15_000;

export class ApiClientError extends Error {
  status: number;
  code: string;
  traceId?: string;
  fieldErrors?: { field: string; message: string }[];

  constructor(body: {
    status: number; code: string; message: string; traceId?: string;
    fieldErrors?: { field: string; message: string }[];
  }) {
    super(body.message);
    this.name = 'ApiClientError';
    this.status = body.status;
    this.code = body.code;
    this.traceId = body.traceId;
    this.fieldErrors = body.fieldErrors;
  }
}

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } catch (error) {
    const timedOut = error instanceof DOMException && error.name === 'AbortError';
    throw new ApiClientError({
      status: 0,
      code: timedOut ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      message: timedOut
        ? 'The request timed out. Please try again.'
        : 'Unable to reach CommerceCore. Check your connection and try again.',
    });
  } finally {
    window.clearTimeout(timeout);
  }
}

/* ------------------------------------------------------------------ */
/* Token storage                                                      */
/* ------------------------------------------------------------------ */
const ACCESS_KEY = 'commercecore.access_token';
const REFRESH_KEY = 'commercecore.refresh_token';

let accessToken: string | null = typeof window !== 'undefined' ? sessionStorage.getItem(ACCESS_KEY) : null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function hasTokens(): boolean {
  return Boolean(accessToken || localStorage.getItem(REFRESH_KEY));
}

export function storeTokens(tokens: AuthTokensResponse): void {
  accessToken = tokens.accessToken;
  try { sessionStorage.setItem(ACCESS_KEY, tokens.accessToken); } catch { /* storage unavailable */ }
  try { localStorage.setItem(REFRESH_KEY, tokens.refreshToken); } catch { /* storage unavailable */ }
}

export function clearTokens(): void {
  accessToken = null;
  try { sessionStorage.removeItem(ACCESS_KEY); } catch { /* noop */ }
  try { localStorage.removeItem(REFRESH_KEY); } catch { /* noop */ }
}

/* ------------------------------------------------------------------ */
/* Low-level fetch                                                     */
/* ------------------------------------------------------------------ */
interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  retryOnAuth?: boolean;
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshTokensOnce(): Promise<boolean> {
  if (!refreshPromise) {
    const attempt = (async () => {
      const refreshToken = localStorage.getItem(REFRESH_KEY);
      if (!refreshToken) return false;
      try {
        const res = await fetchWithTimeout(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return false;
        const tokens = (await res.json()) as AuthTokensResponse;
        storeTokens(tokens);
        return true;
      } catch {
        return false;
      }
    })();
    refreshPromise = attempt.then(
      (ok) => {
        refreshPromise = null;
        return ok;
      },
      (err: unknown) => {
        refreshPromise = null;
        throw err;
      },
    );
  }
  return refreshPromise;
}

async function parseError(res: Response): Promise<ApiClientError> {
  let status = res.status;
  let code = 'REQUEST_FAILED';
  let message = `Request failed with status ${res.status}`;
  let traceId: string | undefined;
  let fieldErrors: { field: string; message: string }[] | undefined;
  try {
    const body = await res.json();
    if (body && typeof body === 'object') {
      status = body.status ?? status;
      code = body.code ?? code;
      message = body.message ?? message;
      traceId = body.traceId ?? undefined;
      fieldErrors = body.fieldErrors ?? undefined;
    }
  } catch { /* non-JSON error body */ }
  return new ApiClientError({ status, code, message, traceId, fieldErrors });
}

/** Central request helper: auth header, JSON handling, 401 refresh+retry. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, retryOnAuth = true } = options;

  const doFetch = (token: string | null): Promise<Response> => {
    const finalHeaders: Record<string, string> = { ...headers };
    if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
    if (token) finalHeaders['Authorization'] = `Bearer ${token}`;
    return fetchWithTimeout(`${API_BASE}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let res = await doFetch(accessToken);
  if (res.status === 401 && retryOnAuth) {
    const ok = await refreshTokensOnce();
    if (ok) {
      res = await doFetch(accessToken);
    } else if (path !== '/auth/login') {
      // Session genuinely expired — clear and let the UI redirect.
      clearTokens();
      window.dispatchEvent(new CustomEvent('commercecore:session-expired'));
    }
  }
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** For CSV blob downloads — preserves the raw bytes and Content-Disposition filename. */
export async function downloadCsv(path: string, filename: string): Promise<void> {
  const token = accessToken;
  const res = await doRaw(path, token);
  if (res.status === 401) {
    const ok = await refreshTokensOnce();
    if (!ok) throw new ApiClientError({ status: 401, code: 'UNAUTHORIZED', message: 'Session expired. Please sign in again.' });
    return downloadCsv(path, filename);
  }
  if (!res.ok) throw await parseError(res);
  const blob = await res.blob();
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = disposition.match(/filename="?([^";]+)"?/i);
  const name = match ? match[1] : filename;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function doRaw(path: string, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetchWithTimeout(`${API_BASE}${path}`, { headers });
}

/* ------------------------------------------------------------------ */
/* Auth                                                                */
/* ------------------------------------------------------------------ */
export const authApi = {
  register: (body: RegisterRequest) => request<RegistrationResponse>('/auth/register', { method: 'POST', body }),
  verifyEmail: (token: string) => request<MessageResponse>('/auth/verify-email', { method: 'POST', body: { token } }),
  resendVerification: (email: string) => request<MessageResponse>('/auth/resend-verification', { method: 'POST', body: { email } }),
  login: (body: LoginRequest) => request<AuthTokensResponse>('/auth/login', { method: 'POST', body, retryOnAuth: false }),
  refresh: () => refreshTokensOnce(),
  logout: async () => {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    clearTokens();
    if (refreshToken) {
      try { await request<MessageResponse>('/auth/logout', { method: 'POST', body: { refreshToken }, retryOnAuth: false }); } catch { /* best effort */ }
    }
  },
  forgotPassword: (email: string) => request<MessageResponse>('/auth/forgot-password', { method: 'POST', body: { email } }),
  resetPassword: (body: ResetPasswordRequest) => request<MessageResponse>('/auth/reset-password', { method: 'POST', body }),
};

/* ------------------------------------------------------------------ */
/* Account / addresses                                                 */
/* ------------------------------------------------------------------ */
export const accountApi = {
  profile: () => request<ProfileResponse>('/me'),
  updateProfile: (body: { fullName?: string; phone?: string }) => request<ProfileResponse>('/me', { method: 'PATCH', body }),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    request<{ message: string }>('/me/password', { method: 'PUT', body }),
  addresses: () => request<AddressResponse[]>('/me/addresses'),
  createAddress: (body: AddressRequest) => request<AddressResponse>('/me/addresses', { method: 'POST', body }),
  updateAddress: (id: string, body: AddressRequest) => request<AddressResponse>(`/me/addresses/${id}`, { method: 'PUT', body }),
  deleteAddress: (id: string) => request<void>(`/me/addresses/${id}`, { method: 'DELETE' }),
  setDefaultShipping: (id: string) => request<AddressResponse>(`/me/addresses/${id}/default-shipping`, { method: 'PUT' }),
  setDefaultBilling: (id: string) => request<AddressResponse>(`/me/addresses/${id}/default-billing`, { method: 'PUT' }),
};

/* ------------------------------------------------------------------ */
/* Catalog                                                             */
/* ------------------------------------------------------------------ */
export interface CatalogQuery {
  q?: string; category?: string; brand?: string; minPrice?: string; maxPrice?: string;
  page?: number; size?: number; sort?: string;
}
export const catalogApi = {
  products: (q: CatalogQuery = {}) => {
    const params = new URLSearchParams();
    if (q.q) params.set('q', q.q);
    if (q.category) params.set('category', q.category);
    if (q.brand) params.set('brand', q.brand);
    if (q.minPrice) params.set('minPrice', q.minPrice);
    if (q.maxPrice) params.set('maxPrice', q.maxPrice);
    params.set('page', String(q.page ?? 0));
    params.set('size', String(q.size ?? 20));
    params.set('sort', q.sort ?? 'newest');
    return request<Page<ProductSummary>>(`/products?${params.toString()}`);
  },
  product: (slug: string) => request<ProductResponse>(`/products/${encodeURIComponent(slug)}`),
  categories: () => request<CategoryResponse[]>('/categories'),
  reviews: (slug: string, page = 0, size = 10) =>
    request<Page<PublicReview>>(`/products/${encodeURIComponent(slug)}/reviews?page=${page}&size=${size}`),
  reviewSummary: (slug: string) => request<ReviewSummary>(`/products/${encodeURIComponent(slug)}/reviews/summary`),
  createReview: (slug: string, body: ReviewRequest) =>
    request<PublicReview>(`/products/${encodeURIComponent(slug)}/reviews`, { method: 'POST', body }),
};

/* ------------------------------------------------------------------ */
/* Cart / wishlist                                                     */
/* ------------------------------------------------------------------ */
export const cartApi = {
  get: () => request<CartResponse>('/cart'),
  add: (body: { productId: string; variantId?: string | null; quantity: number }) =>
    request<CartResponse>('/cart/items', { method: 'POST', body }),
  update: (itemId: string, quantity: number) =>
    request<CartResponse>(`/cart/items/${itemId}`, { method: 'PUT', body: { quantity } }),
  remove: (itemId: string) => request<CartResponse>(`/cart/items/${itemId}`, { method: 'DELETE' }),
  clear: () => request<CartResponse>('/cart', { method: 'DELETE' }),
  applyPromotion: (code: string) => request<CartResponse>('/cart/promotion', { method: 'POST', body: { code } }),
  removePromotion: () => request<CartResponse>('/cart/promotion', { method: 'DELETE' }),
};

export const wishlistApi = {
  get: () => request<WishlistResponse>('/wishlist'),
  add: (body: { productId: string; variantId?: string | null }) =>
    request<WishlistResponse>('/wishlist/items', { method: 'POST', body }),
  remove: (itemId: string) => request<WishlistResponse>(`/wishlist/items/${itemId}`, { method: 'DELETE' }),
  moveToCart: (itemId: string, quantity: number) =>
    request<CartResponse>(`/wishlist/items/${itemId}/move-to-cart`, { method: 'POST', body: { quantity } }),
};

/* ------------------------------------------------------------------ */
/* Checkout / orders                                                   */
/* ------------------------------------------------------------------ */
export const ordersApi = {
  checkout: (key: string, body: { shippingAddressId: string; billingAddressId?: string; shippingServiceLevel: 'STANDARD' | 'EXPRESS' }) =>
    request<OrderResponse>('/checkout', { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  mine: (page = 0, size = 10) => request<Page<OrderResponse>>(`/orders?page=${page}&size=${size}`),
  get: (id: string) => request<OrderResponse>(`/orders/${id}`),
  cancel: (id: string) => request<OrderResponse>(`/orders/${id}/cancel`, { method: 'POST' }),
  tracking: (id: string) => request<ShipmentResponse>(`/orders/${id}/tracking`),
};

export const paymentsApi = {
  pay: (orderId: string, key: string, body: { paymentMethod: 'CARD' | 'ONLINE_BANKING' | 'E_WALLET'; paymentMethodToken?: string }) =>
    request<PaymentResponse>(`/orders/${orderId}/payments`, { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  listForOrder: (orderId: string) => request<PaymentResponse[]>(`/orders/${orderId}/payments`),
  get: (orderId: string, paymentId: string) => request<PaymentResponse>(`/orders/${orderId}/payments/${paymentId}`),
};

/* ------------------------------------------------------------------ */
/* Reviews (customer)                                                  */
/* ------------------------------------------------------------------ */
export const reviewsApi = {
  mine: (page = 0, size = 10) => request<Page<MyReview>>(`/me/reviews?page=${page}&size=${size}`),
  get: (id: string) => request<MyReview>(`/me/reviews/${id}`),
  update: (id: string, body: ReviewRequest) => request<MyReview>(`/me/reviews/${id}`, { method: 'PUT', body }),
  remove: (id: string) => request<void>(`/me/reviews/${id}`, { method: 'DELETE' }),
};

/* ------------------------------------------------------------------ */
/* Notifications                                                       */
/* ------------------------------------------------------------------ */
export const notificationsApi = {
  list: (page = 0, size = 20) => request<Page<Notification>>(`/me/notifications?page=${page}&size=${size}`),
  unreadCount: () => request<UnreadCount>('/me/notifications/unread-count'),
  markRead: (id: string) => request<Notification>(`/me/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => request<{ message: string }>('/me/notifications/read-all', { method: 'POST' }),
};

/* ------------------------------------------------------------------ */
/* Staff/Admin: catalog management                                     */
/* ------------------------------------------------------------------ */
export const staffCatalogApi = {
  products: (q: string, page = 0, size = 20) =>
    request<Page<ProductSummary>>(`/staff/products?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  product: (id: string) => request<ProductResponse>(`/staff/products/${id}`),
  createProduct: (body: { name: string; slug: string; description: string; sku: string; brand: string; basePrice: string; salePrice?: string | null; categoryId: string; active: boolean }) =>
    request<ProductResponse>('/staff/products', { method: 'POST', body }),
  updateProduct: (id: string, body: { name: string; slug: string; description: string; sku: string; brand: string; basePrice: string; salePrice?: string | null; categoryId: string; active: boolean }) =>
    request<ProductResponse>(`/staff/products/${id}`, { method: 'PUT', body }),
  activateProduct: (id: string) => request<ProductResponse>(`/staff/products/${id}/activate`, { method: 'POST' }),
  deactivateProduct: (id: string) => request<ProductResponse>(`/staff/products/${id}/deactivate`, { method: 'POST' }),
  addMedia: (productId: string, body: { mediaUrl: string; altText: string; displayOrder: number; primary: boolean }) =>
    request<ProductResponse>(`/staff/products/${productId}/media`, { method: 'POST', body }),
  updateMedia: (productId: string, mediaId: string, body: { mediaUrl: string; altText: string; displayOrder: number; primary: boolean }) =>
    request<ProductResponse>(`/staff/products/${productId}/media/${mediaId}`, { method: 'PUT', body }),
  deleteMedia: (productId: string, mediaId: string) =>
    request<ProductResponse>(`/staff/products/${productId}/media/${mediaId}`, { method: 'DELETE' }),

  categories: () => request<CategoryResponse[]>('/staff/categories'),
  createCategory: (body: { name: string; slug: string; description?: string; parentCategoryId?: string | null; active: boolean }) =>
    request<CategoryResponse>('/staff/categories', { method: 'POST', body }),
  updateCategory: (id: string, body: { name: string; slug: string; description?: string; parentCategoryId?: string | null; active: boolean }) =>
    request<CategoryResponse>(`/staff/categories/${id}`, { method: 'PUT', body }),
  activateCategory: (id: string) => request<CategoryResponse>(`/staff/categories/${id}/activate`, { method: 'POST' }),
  deactivateCategory: (id: string) => request<CategoryResponse>(`/staff/categories/${id}/deactivate`, { method: 'POST' }),

  variants: (productId: string) => request<ProductResponse['variants']>(`/staff/products/${productId}/variants`),
  createVariant: (productId: string, body: { sku: string; priceOverride?: string | null; active: boolean; attributes: { name: string; value: string; displayOrder: number }[] }) =>
    request<ProductResponse['variants'][number]>(`/staff/products/${productId}/variants`, { method: 'POST', body }),
  updateVariant: (id: string, body: { sku: string; priceOverride?: string | null; active: boolean; attributes: { name: string; value: string; displayOrder: number }[] }) =>
    request<ProductResponse['variants'][number]>(`/staff/variants/${id}`, { method: 'PUT', body }),
  activateVariant: (id: string) => request<ProductResponse['variants'][number]>(`/staff/variants/${id}/activate`, { method: 'POST' }),
  deactivateVariant: (id: string) => request<ProductResponse['variants'][number]>(`/staff/variants/${id}/deactivate`, { method: 'POST' }),

  promotions: () => request<PromotionResponse[]>('/staff/promotions'),
  promotion: (id: string) => request<PromotionResponse>(`/staff/promotions/${id}`),
  createPromotion: (body: object) => request<PromotionResponse>('/staff/promotions', { method: 'POST', body }),
  updatePromotion: (id: string, body: object) => request<PromotionResponse>(`/staff/promotions/${id}`, { method: 'PUT', body }),
  activatePromotion: (id: string) => request<PromotionResponse>(`/staff/promotions/${id}/activate`, { method: 'POST' }),
  deactivatePromotion: (id: string) => request<PromotionResponse>(`/staff/promotions/${id}/deactivate`, { method: 'POST' }),
};

export const staffInventoryApi = {
  list: () => request<{ id: string; productId: string; variantId: string | null; totalQuantity: number; reservedQuantity: number; soldQuantity: number; availableQuantity: number; lowStockThreshold: number; stockStatus: string; createdAt: string; updatedAt: string }[]>('/staff/inventory'),
  get: (id: string) => request<{ id: string; productId: string; variantId: string | null; totalQuantity: number; reservedQuantity: number; soldQuantity: number; availableQuantity: number; lowStockThreshold: number; stockStatus: string; createdAt: string; updatedAt: string }>(`/staff/inventory/${id}`),
  initialize: (body: { productId: string; variantId?: string | null; initialQuantity: number; lowStockThreshold: number }) =>
    request<{ id: string }>('/staff/inventory', { method: 'POST', body }),
  restock: (id: string, body: { quantity: number; reference?: string; reason?: string }) =>
    request<{ id: string }>(`/staff/inventory/${id}/restock`, { method: 'POST', body }),
  adjust: (id: string, body: { delta: number; reason: string; reference?: string }) =>
    request<{ id: string }>(`/staff/inventory/${id}/adjust`, { method: 'POST', body }),
  history: (id: string) => request<InventoryHistory[]>(`/staff/inventory/${id}/history`),
};

/* ------------------------------------------------------------------ */
/* Staff/Admin: orders, payments, refunds                              */
/* ------------------------------------------------------------------ */
export interface StaffOrderQuery {
  orderNumber?: string; status?: string; customerId?: string; customerEmail?: string;
  createdFrom?: string; createdTo?: string; page?: number; size?: number;
}
export const staffOrdersApi = {
  list: (q: StaffOrderQuery = {}) => {
    const params = new URLSearchParams();
    if (q.orderNumber) params.set('orderNumber', q.orderNumber);
    if (q.status) params.set('status', q.status);
    if (q.customerId) params.set('customerId', q.customerId);
    if (q.customerEmail) params.set('customerEmail', q.customerEmail);
    if (q.createdFrom) params.set('createdFrom', q.createdFrom);
    if (q.createdTo) params.set('createdTo', q.createdTo);
    params.set('page', String(q.page ?? 0));
    params.set('size', String(q.size ?? 20));
    return request<Page<OrderResponse>>(`/staff/orders?${params.toString()}`);
  },
  get: (id: string) => request<OrderResponse>(`/staff/orders/${id}`),
  cancel: (id: string) => request<OrderResponse>(`/staff/orders/${id}/cancel`, { method: 'POST' }),
  cancelPaid: (id: string, key: string) =>
    request<OrderResponse>(`/staff/orders/${id}/cancel-paid`, { method: 'POST', headers: { 'Idempotency-Key': key } }),
  processing: (id: string) => request<OrderResponse>(`/staff/orders/${id}/processing`, { method: 'POST' }),
  shipment: (id: string, body: { carrier: string; trackingNumber: string; estimatedDeliveryAt?: string | null }) =>
    request<OrderResponse>(`/staff/orders/${id}/shipment`, { method: 'POST', body }),
  ship: (id: string) => request<OrderResponse>(`/staff/orders/${id}/ship`, { method: 'POST' }),
  deliver: (id: string) => request<OrderResponse>(`/staff/orders/${id}/deliver`, { method: 'POST' }),
  addTracking: (id: string, body: { status: string; location?: string; note?: string; occurredAt: string }) =>
    request<OrderResponse>(`/staff/orders/${id}/tracking-events`, { method: 'POST', body }),
};

export const staffPaymentsApi = {
  paymentsByOrder: (orderId: string) => request<PaymentResponse[]>(`/staff/payments/order/${orderId}`),
  refunds: (paymentId: string) => request<RefundResponse[]>(`/staff/payments/${paymentId}/refunds`),
  refund: (paymentId: string, key: string, amount: string) =>
    request<RefundResponse>(`/staff/payments/${paymentId}/refunds`, {
      method: 'POST', body: { amount }, headers: { 'Idempotency-Key': key },
    }),
  reconcilePayment: (paymentId: string) => request<PaymentResponse>(`/staff/payments/${paymentId}/reconcile`, { method: 'POST' }),
  reconcileRefund: (refundId: string) => request<RefundResponse>(`/staff/refunds/${refundId}/reconcile`, { method: 'POST' }),
};

/* ------------------------------------------------------------------ */
/* Reviews moderation                                                   */
/* ------------------------------------------------------------------ */
export const moderationApi = {
  list: (status: string, page = 0, size = 20) =>
    request<Page<AdminReview>>(`/admin/reviews?status=${status}&page=${page}&size=${size}`),
  approve: (id: string) => request<AdminReview>(`/admin/reviews/${id}/approve`, { method: 'POST' }),
  reject: (id: string) => request<AdminReview>(`/admin/reviews/${id}/reject`, { method: 'POST' }),
};

/* ------------------------------------------------------------------ */
/* Admin: users, alerts, audit                                         */
/* ------------------------------------------------------------------ */
export const adminUsersApi = {
  list: (q: { email?: string; role?: string; status?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (q.email) params.set('email', q.email);
    if (q.role) params.set('role', q.role);
    if (q.status) params.set('status', q.status);
    params.set('page', String(q.page ?? 0));
    params.set('size', String(q.size ?? 20));
    return request<AdminUserPage>(`/admin/users?${params.toString()}`);
  },
  get: (id: string) => request<AdminUserResponse>(`/admin/users/${id}`),
  lock: (id: string) => request<AdminUserResponse>(`/admin/users/${id}/lock`, { method: 'POST' }),
  unlock: (id: string) => request<AdminUserResponse>(`/admin/users/${id}/unlock`, { method: 'POST' }),
  activate: (id: string) => request<AdminUserResponse>(`/admin/users/${id}/activate`, { method: 'POST' }),
  deactivate: (id: string) => request<AdminUserResponse>(`/admin/users/${id}/deactivate`, { method: 'POST' }),
  changeRole: (id: string, role: UserRole) => request<AdminUserResponse>(`/admin/users/${id}/role`, { method: 'PUT', body: { role } }),
};

export const adminAlertsApi = {
  list: (q: { acknowledged?: boolean; type?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (q.acknowledged !== undefined) params.set('acknowledged', String(q.acknowledged));
    if (q.type) params.set('type', q.type);
    params.set('page', String(q.page ?? 0));
    params.set('size', String(q.size ?? 20));
    return request<Page<AdminAlert>>(`/admin/alerts?${params.toString()}`);
  },
  unacknowledgedCount: () => request<UnacknowledgedCount>('/admin/alerts/unacknowledged-count'),
  acknowledge: (id: string) => request<AdminAlert>(`/admin/alerts/${id}/acknowledge`, { method: 'POST' }),
};

export const auditApi = {
  list: (q: { actorUserId?: string; action?: string; category?: string; resourceType?: string; resourceId?: string; from?: string; to?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (q.actorUserId) params.set('actorUserId', q.actorUserId);
    if (q.action) params.set('action', q.action);
    if (q.category) params.set('category', q.category);
    if (q.resourceType) params.set('resourceType', q.resourceType);
    if (q.resourceId) params.set('resourceId', q.resourceId);
    if (q.from) params.set('from', q.from);
    if (q.to) params.set('to', q.to);
    params.set('page', String(q.page ?? 0));
    params.set('size', String(q.size ?? 20));
    return request<Page<AuditLog>>(`/admin/audit-logs?${params.toString()}`);
  },
};

/* ------------------------------------------------------------------ */
/* Admin: dashboard + reports + CSV                                    */
/* ------------------------------------------------------------------ */
export const dashboardApi = {
  get: () => request<Dashboard>('/admin/dashboard'),
};

export interface ReportRange { from?: string; to?: string; page?: number; size?: number; }
export const reportsApi = {
  sales: (range: ReportRange = {}) => {
    const params = rangeParams(range);
    return request<SalesReport>(`/admin/reports/sales?${params}`);
  },
  topProducts: (range: ReportRange = {}) => request<TopProductsReport>(`/admin/reports/top-products?${rangeParams(range)}`),
  reviews: (range: ReportRange = {}) => request<ReviewsReport>(`/admin/reports/reviews?${rangeParams(range)}`),
  orders: (range: ReportRange & { status?: string } = {}) => {
    const params = rangeParams(range);
    if (range.status) params.set('status', range.status);
    return request<OrdersReport>(`/admin/reports/orders?${params}`);
  },
  customers: (range: ReportRange & { role?: string; accountStatus?: string } = {}) => {
    const params = rangeParams(range);
    if (range.role) params.set('role', range.role);
    if (range.accountStatus) params.set('accountStatus', range.accountStatus);
    return request<CustomersReport>(`/admin/reports/customers?${params}`);
  },
  inventory: (range: ReportRange & { stockStatus?: string } = {}) => {
    const params = rangeParams(range);
    if (range.stockStatus) params.set('stockStatus', range.stockStatus);
    return request<InventoryReport>(`/admin/reports/inventory?${params}`);
  },
  payments: (range: ReportRange & { status?: PaymentStatus } = {}) => {
    const params = rangeParams(range);
    if (range.status) params.set('status', range.status);
    return request<PaymentsReport>(`/admin/reports/payments?${params}`);
  },
  refunds: (range: ReportRange & { status?: string } = {}) => {
    const params = rangeParams(range);
    if (range.status) params.set('status', range.status);
    return request<RefundsReport>(`/admin/reports/refunds?${params}`);
  },
  salesCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/sales.csv?${rangeParams(range)}`, 'sales.csv'),
  ordersCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/orders.csv?${rangeParams(range)}`, 'orders.csv'),
  customersCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/customers.csv?${rangeParams(range)}`, 'customers.csv'),
  inventoryCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/inventory.csv?${rangeParams(range)}`, 'inventory.csv'),
  paymentsCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/payments.csv?${rangeParams(range)}`, 'payments.csv'),
  refundsCsv: (range: ReportRange = {}) => downloadCsv(`/admin/reports/refunds.csv?${rangeParams(range)}`, 'refunds.csv'),
};

function rangeParams(range: ReportRange): URLSearchParams {
  const params = new URLSearchParams();
  if (range.from) params.set('from', range.from);
  if (range.to) params.set('to', range.to);
  if (range.page !== undefined) params.set('page', String(range.page));
  if (range.size !== undefined) params.set('size', String(range.size));
  return params;
}
