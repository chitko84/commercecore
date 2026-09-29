/* TypeScript mirrors of the CommerceCore backend DTOs. */

export type UserRole = 'CUSTOMER' | 'STAFF' | 'ADMIN';
export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'LOCKED' | 'DEACTIVATED';
export type OrderStatus =
  | 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type PaymentMethod = 'CARD' | 'ONLINE_BANKING' | 'E_WALLET';
export type PaymentStatus =
  | 'PENDING' | 'AUTHORIZED' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED' | 'PARTIALLY_REFUNDED';
export type RefundStatus = 'PENDING' | 'COMPLETED' | 'FAILED';
export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'UNKNOWN';
export type CartLineStatus = 'AVAILABLE' | 'INSUFFICIENT_STOCK' | 'OUT_OF_STOCK' | 'UNAVAILABLE';
export type ShippingServiceLevel = 'STANDARD' | 'EXPRESS';
export type TrackingStatus =
  | 'LABEL_CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'EXCEPTION';
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type NotificationType =
  | 'WELCOME' | 'PASSWORD_CHANGED' | 'PASSWORD_RESET'
  | 'ORDER_CREATED' | 'ORDER_CONFIRMED' | 'ORDER_PROCESSING' | 'ORDER_SHIPPED'
  | 'ORDER_DELIVERED' | 'ORDER_CANCELLED'
  | 'PAYMENT_SUCCEEDED' | 'PAYMENT_FAILED' | 'REFUND_COMPLETED';
export type AdminAlertType = 'LOW_STOCK_ALERT' | 'FAILED_PAYMENT_ALERT';
export type PromotionType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';
export type PromotionScope = 'CART' | 'PRODUCT' | 'CATEGORY';
export type MovementType =
  | 'RESTOCK' | 'ADJUSTMENT_INCREASE' | 'ADJUSTMENT_DECREASE' | 'RESERVATION'
  | 'RELEASE' | 'SALE' | 'SALE_REVERSAL' | 'RESERVATION_EXPIRED';
export type ReservationStatus = 'ACTIVE' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED' | 'CANCELLED';

/* --- Common paging --- */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/* --- ApiError --- */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path?: string;
  traceId?: string;
  fieldErrors?: { field: string; message: string }[];
}

/* --- Auth --- */
export interface RegisterRequest { fullName: string; email: string; password: string; phone?: string; }
export interface RegistrationResponse { id: string; email: string; accountStatus: AccountStatus; emailVerified: boolean; }
export interface LoginRequest { email: string; password: string; }
export interface AuthTokensResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}
export interface MessageResponse { message: string; }
export interface ResetPasswordRequest { token: string; newPassword: string; }

/* --- Profile / users --- */
export interface ProfileResponse {
  id: string; email: string; fullName: string; phone: string | null;
  role: UserRole; emailVerified: boolean; accountStatus: AccountStatus; createdAt: string;
}
export interface UpdateProfileRequest { fullName?: string; phone?: string; }
export interface ChangePasswordRequest { currentPassword: string; newPassword: string; }
export interface AdminUserResponse {
  id: string; email: string; fullName: string; phone: string | null;
  role: UserRole; accountStatus: AccountStatus; emailVerified: boolean;
  failedLoginAttempts: number; lockedUntil: string | null; createdAt: string; updatedAt: string;
}
export interface ChangeRoleRequest { role: UserRole; }

/* --- Addresses --- */
export interface AddressRequest {
  label: string; recipientName: string; phone: string; addressLine1: string; addressLine2?: string;
  city: string; stateRegion: string; postalCode: string; countryCode: string;
  defaultShipping: boolean; defaultBilling: boolean;
}
export interface AddressResponse {
  id: string; label: string; recipientName: string; phone: string; addressLine1: string;
  addressLine2: string | null; city: string; stateRegion: string; postalCode: string;
  countryCode: string; defaultShipping: boolean; defaultBilling: boolean;
  createdAt: string; updatedAt: string;
}

/* --- Catalog --- */
export interface CategorySummary { id: string; name: string; slug: string; }
export interface CategoryResponse {
  id: string; name: string; slug: string; description: string | null;
  parent: CategorySummary | null; children: CategorySummary[]; active: boolean;
  createdAt: string; updatedAt: string;
}
export interface CategoryRequest {
  name: string; slug: string; description?: string; parentCategoryId?: string | null; active: boolean;
}
export interface MediaResponse {
  id: string; mediaUrl: string; altText: string; displayOrder: number; primary: boolean; createdAt: string;
}
export interface MediaRequest { mediaUrl: string; altText: string; displayOrder: number; primary: boolean; }
export interface ProductSummary {
  id: string; name: string; slug: string; sku: string; brand: string;
  basePrice: string; salePrice: string | null; effectivePrice: string;
  category: CategorySummary; primaryMedia: MediaResponse | null;
  active: boolean; availability: StockStatus; createdAt: string;
}
export interface VariantResponse {
  id: string; productId: string; sku: string; priceOverride: string | null;
  effectivePrice: string; active: boolean;
  attributes: { name: string; value: string; displayOrder: number }[];
  availability: StockStatus; createdAt: string; updatedAt: string;
}
export interface ProductResponse {
  id: string; name: string; slug: string; description: string; sku: string; brand: string;
  basePrice: string; salePrice: string | null; effectivePrice: string;
  category: CategorySummary; media: MediaResponse[]; variants: VariantResponse[];
  active: boolean; availability: StockStatus; createdAt: string; updatedAt: string;
}
export interface ProductRequest {
  name: string; slug: string; description: string; sku: string; brand: string;
  basePrice: string; salePrice: string | null; categoryId: string; active: boolean;
}
export interface VariantRequest {
  sku: string; priceOverride: string | null; active: boolean;
  attributes: { name: string; value: string; displayOrder: number }[];
}

/* --- Inventory --- */
export interface InventoryResponse {
  id: string; productId: string; variantId: string | null; totalQuantity: number;
  reservedQuantity: number; soldQuantity: number; availableQuantity: number;
  lowStockThreshold: number; stockStatus: StockStatus; createdAt: string; updatedAt: string;
}
export interface InventoryHistory {
  id: string; movementType: MovementType; quantity: number;
  beforeTotal: number; beforeReserved: number; beforeSold: number;
  afterTotal: number; afterReserved: number; afterSold: number;
  reference: string | null; reason: string | null; actorUserId: string | null; occurredAt: string;
}
export interface InitializeRequest { productId: string; variantId?: string | null; initialQuantity: number; lowStockThreshold: number; }
export interface RestockRequest { quantity: number; reference?: string; reason?: string; }
export interface AdjustmentRequest { delta: number; reason: string; reference?: string; }

/* --- Cart --- */
export interface CartLine {
  id: string; productId: string; variantId: string | null; productName: string; sku: string;
  quantity: number; unitPrice: string; lineSubtotal: string; status: CartLineStatus;
  availableQuantity: number; updatedAt: string;
}
export interface PromotionPricing {
  id: string; code: string; valid: boolean; errorCode: string | null;
  eligibleSubtotal: string; discount: string; freeShippingEligible: boolean;
}
export interface CartResponse {
  id: string; items: CartLine[]; subtotal: string; promotionDiscount: string;
  discountedMerchandiseTotal: string; freeShippingEligible: boolean;
  promotion: PromotionPricing | null; readyForCheckout: boolean; updatedAt: string;
}
export interface AddItemRequest { productId: string; variantId?: string | null; quantity: number; }

/* --- Wishlist --- */
export interface WishlistItem {
  id: string; productId: string; variantId: string | null; productName: string; sku: string;
  currentPrice: string; status: CartLineStatus; createdAt: string;
}
export interface WishlistResponse { items: WishlistItem[]; }
export interface AddWishlistRequest { productId: string; variantId?: string | null; }

/* --- Promotions --- */
export interface PromotionResponse {
  id: string; code: string; name: string; description: string | null;
  type: PromotionType; scope: PromotionScope; discountValue: string; minimumSpend: string;
  maximumDiscount: string | null; startsAt: string; endsAt: string;
  totalUsageLimit: number | null; perUserUsageLimit: number | null; active: boolean;
  targetProductId: string | null; targetCategoryId: string | null;
  totalRedemptions: number; createdAt: string; updatedAt: string;
}
export interface PromotionRequest {
  code: string; name: string; description?: string; type: PromotionType; scope: PromotionScope;
  discountValue: string; minimumSpend: string; maximumDiscount?: string | null;
  startsAt: string; endsAt: string; totalUsageLimit?: number | null; perUserUsageLimit?: number | null;
  active: boolean; targetProductId?: string | null; targetCategoryId?: string | null;
}

/* --- Orders --- */
export interface CheckoutRequest { shippingAddressId: string; billingAddressId?: string; shippingServiceLevel: ShippingServiceLevel; }
export interface OrderItemSnapshot {
  id: string; sourceProductId: string; sourceVariantId: string | null; productName: string;
  sku: string; quantity: number; unitPrice: string; lineSubtotal: string;
  attributes: { name: string; value: string; displayOrder: number }[];
}
export interface OrderAddressSnapshot {
  type: 'SHIPPING' | 'BILLING'; sourceAddressId: string; recipientName: string; phone: string;
  addressLine1: string; addressLine2: string | null; city: string; stateRegion: string;
  postalCode: string; countryCode: string;
}
export interface OrderPromotionSnapshot {
  id: string; code: string; name: string; type: PromotionType;
  merchandiseDiscount: string; freeShipping: boolean;
}
export interface OrderStatusEvent {
  fromStatus: OrderStatus | null; toStatus: OrderStatus; actorUserId: string | null;
  reason: string | null; reference: string | null; occurredAt: string;
}
export interface TrackingEvent { status: TrackingStatus; location: string | null; note: string | null; occurredAt: string; }
export interface ShipmentResponse {
  id: string; serviceLevel: ShippingServiceLevel; carrier: string; trackingNumber: string;
  estimatedDeliveryAt: string | null; shippedAt: string | null; deliveredAt: string | null;
  events: TrackingEvent[];
}
export interface OrderResponse {
  id: string; orderNumber: string; status: OrderStatus; items: OrderItemSnapshot[];
  addresses: OrderAddressSnapshot[]; subtotal: string; merchandiseDiscount: string;
  discountedMerchandiseTotal: string; shippingFeeBeforeDiscount: string; shippingDiscount: string;
  shippingFee: string; taxAmount: string; grandTotal: string;
  promotion: OrderPromotionSnapshot | null; shippingServiceLevel: ShippingServiceLevel;
  shippingEstimatedDays: number; reservationExpiresAt: string | null;
  createdAt: string; updatedAt: string;
  statusHistory: OrderStatusEvent[]; shipment: ShipmentResponse | null;
}
export interface ShipmentRequest { carrier: string; trackingNumber: string; estimatedDeliveryAt?: string | null; }

/* --- Payments --- */
export interface PaymentRequest { paymentMethod: PaymentMethod; paymentMethodToken?: string; }
export interface PaymentResponse {
  id: string; orderId: string; paymentMethod: PaymentMethod; status: PaymentStatus;
  amount: string; providerPaymentReference: string | null; reconciliationRequired: boolean;
  createdAt: string; updatedAt: string;
}
export interface RefundResponse {
  id: string; paymentId: string; amount: string; status: RefundStatus;
  providerRefundReference: string | null; reconciliationRequired: boolean; createdAt: string;
}

/* --- Reviews --- */
export interface PublicReview {
  id: string; authorName: string; rating: number; title: string | null; body: string | null; createdAt: string;
}
export interface MyReview {
  id: string; productId: string; productName: string; productSlug: string; rating: number;
  title: string | null; body: string | null; status: ReviewStatus; createdAt: string; updatedAt: string;
}
export interface ReviewSummary {
  reviewCount: number; averageRating: string; distribution: Record<string, number>;
}
export interface AdminReview {
  id: string; productId: string; productName: string; customerId: string; rating: number;
  title: string | null; body: string | null; status: ReviewStatus; createdAt: string; updatedAt: string;
}
export interface ReviewRequest { rating: number; title?: string; body?: string; }

/* --- Notifications --- */
export interface Notification {
  id: string; type: NotificationType; title: string; body: string;
  resourceType: string | null; resourceId: string | null; read: boolean; createdAt: string;
}
export interface UnreadCount { unreadCount: number; }

/* --- Admin alerts --- */
export interface AdminAlert {
  id: string; type: AdminAlertType; title: string; body: string;
  resourceType: string | null; resourceId: string | null; acknowledged: boolean; createdAt: string;
}
export interface UnacknowledgedCount { unacknowledgedCount: number; }

/* --- Audit --- */
export interface AuditLog {
  id: string; occurredAt: string; actorUserId: string | null; action: string;
  category: string; resourceType: string; resourceId: string | null; details: string | null;
}

/* --- Dashboard --- */
export interface RecentOrder {
  id: string; orderNumber: string; status: OrderStatus; customerEmail: string;
  grandTotal: string; createdAt: string;
}
export interface Dashboard {
  totalRevenue: string; netRevenue: string; ordersByStatus: Record<OrderStatus, number>;
  customerCount: number; activeProductCount: number; lowStockCount: number;
  failedPaymentCount: number; recentOrders: RecentOrder[];
}

/* --- Reports --- */
export interface SalesDay { date: string; orderCount: number; grossSales: string; refundCount: number; refunds: string; netSales: string; }
export interface SalesReport {
  from: string; toExclusive: string; totalOrders: number; totalRefunds: number;
  grossSales: string; refunds: string; netSales: string; days: SalesDay[];
}
export interface TopProduct { productName: string; sku: string; unitsSold: number; revenue: string; }
export interface TopProductsReport { from: string; toExclusive: string; products: TopProduct[]; }
export interface ReviewsReport { from: string; toExclusive: string; reviewCount: number; averageRating: string; distribution: Record<string, number>; }
export interface OrderRow { id: string; orderNumber: string; status: OrderStatus; customerEmail: string; grandTotal: string; createdAt: string; }
export interface OrdersReport {
  from: string; toExclusive: string; totalOrders: number; totalAmount: string;
  orders: OrderRow[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface CustomerRow {
  id: string; email: string; fullName: string; role: UserRole; accountStatus: AccountStatus;
  emailVerified: boolean; createdAt: string;
}
export interface CustomersReport {
  from: string; toExclusive: string; totalCustomers: number; newCustomers: number; totalSpent: string;
  customers: CustomerRow[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface InventoryRow {
  id: string; ownerType: string; sku: string; ownerName: string; totalQuantity: number;
  reservedQuantity: number; soldQuantity: number; availableQuantity: number;
  lowStockThreshold: number; stockStatus: StockStatus; updatedAt: string;
}
export interface InventoryReport {
  from: string; toExclusive: string; inStock: number; lowStock: number; outOfStock: number;
  rows: InventoryRow[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface PaymentRow {
  id: string; orderNumber: string; customerEmail: string; paymentMethod: PaymentMethod;
  status: PaymentStatus; amount: string; reconciliationRequired: boolean; createdAt: string;
}
export interface PaymentsReport {
  from: string; toExclusive: string; totalPayments: number; totalAmount: string;
  byStatus: Record<string, number>; payments: PaymentRow[];
  page: number; size: number; totalElements: number; totalPages: number;
}
export interface RefundRow {
  id: string; orderNumber: string; amount: string; status: RefundStatus;
  createdByEmail: string; createdAt: string;
}
export interface RefundsReport {
  from: string; toExclusive: string; totalRefunds: number; totalAmount: string;
  refunds: RefundRow[]; page: number; size: number; totalElements: number; totalPages: number;
}