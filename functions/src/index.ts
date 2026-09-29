import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { createHash } from "crypto";

initializeApp();

const db = getFirestore();

/**
 * ============================================================
 * CONFIGURATION
 * ============================================================
 */

const FUNCTION_REGION = "asia-south1";

// Maximum number of different products in one order.
const MAX_UNIQUE_PRODUCTS = 50;

// Hard safety limit. Product-specific maxBuyingLimit is checked separately.
const MAX_QUANTITY_PER_ITEM = 1000;

/**
 * ============================================================
 * TYPES
 * ============================================================
 */

interface OrderItemInput {
  productId: string;
  quantity: number;
}

interface CreateOrderRequest {
  items: OrderItemInput[];
  addressId: string;
  paymentMethod: "COD";
  clientRequestId: string;
}

interface ProductData {
  name?: unknown;
  pricePaise?: unknown;
  stockQuantity?: unknown;
  reservedQuantity?: unknown;
  maxBuyingLimit?: unknown;
  isActive?: unknown;
}

type NormalizedOrderItem = {
  productId: string;
  quantity: number;
};

type OrderItemSnapshot = {
  productId: string;
  name: string;
  quantity: number;
  unitPricePaise: number;
  lineTotalPaise: number;
};

type CreateOrderResult = {
  alreadyExists: boolean;
  orderId: string;
  subtotalPaise: number;
  grandTotalPaise: number;
  paymentMethod: "COD";
  paymentStatus: "PENDING" | "PAID";
  orderStatus: "CONFIRMED" | "DELIVERED" | "CANCELLED" | "UNKNOWN";
  stockStatus: "RESERVED" | "SOLD" | "RELEASED" | "UNKNOWN";
};

/**
 * ============================================================
 * HELPERS
 * ============================================================
 */

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isSafePositiveInteger(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isSafeInteger(value) &&
    value > 0
  );
}

function isSafeNonNegativeInteger(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isSafeInteger(value) &&
    value >= 0
  );
}

/**
 * Same authenticated user + same clientRequestId + same request data
 * must always represent the same order.
 */
function createRequestFingerprint(
  uid: string,
  items: NormalizedOrderItem[],
  addressId: string,
  paymentMethod: string,
  clientRequestId: string,
): string {
  const normalized = {
    uid,
    items: [...items].sort((a, b) =>
      a.productId.localeCompare(b.productId),
    ),
    addressId,
    paymentMethod,
    clientRequestId,
  };

  return createHash("sha256")
    .update(JSON.stringify(normalized))
    .digest("hex");
}

function createDeterministicOrderId(
  uid: string,
  clientRequestId: string,
): string {
  return createHash("sha256")
    .update(`${uid}:${clientRequestId}`)
    .digest("hex");
}

/**
 * ============================================================
 * CREATE ORDER — COD ONLY
 * ============================================================
 *
 * Client sends ONLY:
 *   - items: productId + quantity
 *   - addressId
 *   - paymentMethod: "COD"
 *   - clientRequestId
 *
 * Server decides:
 *   - authenticated UID
 *   - role authorization
 *   - product availability
 *   - product price
 *   - stock
 *   - reserved stock
 *   - maxBuyingLimit
 *   - subtotal / total
 *   - payment status
 *   - order status
 *   - stock reservation
 *
 * IMPORTANT:
 * COD stock is RESERVED, not immediately deducted from physical stock.
 * When the order is delivered, another trusted server function must:
 *   stockQuantity     -= quantity
 *   reservedQuantity  -= quantity
 * and set the order to DELIVERED / PAID / SOLD.
 *
 * On cancellation/rejection, another trusted server function must:
 *   reservedQuantity  -= quantity
 * and set the order to CANCELLED / RELEASED.
 * ============================================================
 */

export const createOrder = onCall(
  {
    region: FUNCTION_REGION,

    // Production deployment should use App Check.
    enforceAppCheck: true,
  },

  async (request): Promise<CreateOrderResult & {
    success: true;
    amountPaise: number;
    stockStatus: "RESERVED";
    payment: {
      provider: "COD";
      gatewayOrderId: null;
    };
  }> => {
    /**
     * ----------------------------------------------------------
     * 1. AUTHENTICATION
     * ----------------------------------------------------------
     */

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Authentication required.",
      );
    }

    // NEVER trust request.data.uid for identity.
    const uid = request.auth.uid;

    /**
     * ----------------------------------------------------------
     * 2. ROLE AUTHORIZATION
     * ----------------------------------------------------------
     * role must come from Firebase Custom Claims assigned by
     * trusted server/admin logic.
     */

    const role = request.auth.token.role;

    if (role !== "customer") {
      throw new HttpsError(
        "permission-denied",
        "Only customer accounts can create orders.",
      );
    }

    /**
     * ----------------------------------------------------------
     * 3. REQUEST VALIDATION
     * ----------------------------------------------------------
     */

    if (!isPlainObject(request.data)) {
      throw new HttpsError(
        "invalid-argument",
        "Invalid request.",
      );
    }

    const data = request.data as Partial<CreateOrderRequest>;

    // items
    if (!Array.isArray(data.items)) {
      throw new HttpsError(
        "invalid-argument",
        "items must be an array.",
      );
    }

    if (
      data.items.length === 0 ||
      data.items.length > MAX_UNIQUE_PRODUCTS
    ) {
      throw new HttpsError(
        "invalid-argument",
        `Order must contain 1-${MAX_UNIQUE_PRODUCTS} products.`,
      );
    }

    // addressId
    if (
      typeof data.addressId !== "string" ||
      data.addressId.trim().length === 0 ||
      data.addressId.length > 150
    ) {
      throw new HttpsError(
        "invalid-argument",
        "Invalid addressId.",
      );
    }

    const addressId = data.addressId.trim();

    // COD is the only enabled payment method in this deployment.
    if (data.paymentMethod !== "COD") {
      throw new HttpsError(
        "failed-precondition",
        "Only COD payment is available right now.",
      );
    }

    const paymentMethod = data.paymentMethod;

    // clientRequestId is required for idempotency.
    if (
      typeof data.clientRequestId !== "string" ||
      data.clientRequestId.trim().length === 0 ||
      data.clientRequestId.length > 128
    ) {
      throw new HttpsError(
        "invalid-argument",
        "clientRequestId is required.",
      );
    }

    const clientRequestId = data.clientRequestId.trim();

    /**
     * ----------------------------------------------------------
     * 4. NORMALIZE + VALIDATE ITEMS
     * ----------------------------------------------------------
     */

    const normalizedItems: NormalizedOrderItem[] = [];
    const seenProductIds = new Set<string>();

    for (const rawItem of data.items) {
      if (!isPlainObject(rawItem)) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid order item.",
        );
      }

      const productId = rawItem.productId;
      const quantity = rawItem.quantity;

      if (
        typeof productId !== "string" ||
        productId.trim().length === 0 ||
        productId.length > 150
      ) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid productId.",
        );
      }

      const normalizedProductId = productId.trim();

      if (seenProductIds.has(normalizedProductId)) {
        throw new HttpsError(
          "invalid-argument",
          `Duplicate product: ${normalizedProductId}`,
        );
      }

      seenProductIds.add(normalizedProductId);

      if (!isSafePositiveInteger(quantity)) {
        throw new HttpsError(
          "invalid-argument",
          `Invalid quantity for ${normalizedProductId}.`,
        );
      }

      if (quantity > MAX_QUANTITY_PER_ITEM) {
        throw new HttpsError(
          "invalid-argument",
          `Quantity exceeds the maximum allowed value of ${MAX_QUANTITY_PER_ITEM}.`,
        );
      }

      normalizedItems.push({
        productId: normalizedProductId,
        quantity,
      });
    }

    /**
     * ----------------------------------------------------------
     * 5. IDEMPOTENCY
     * ----------------------------------------------------------
     */

    const requestFingerprint = createRequestFingerprint(
      uid,
      normalizedItems,
      addressId,
      paymentMethod,
      clientRequestId,
    );

    const orderId = createDeterministicOrderId(
      uid,
      clientRequestId,
    );

    const orderRef = db.collection("orders").doc(orderId);

    /**
     * ----------------------------------------------------------
     * 6. FIRESTORE TRANSACTION
     * ----------------------------------------------------------
     *
     * All product reads, stock checks, reservation increments and
     * order creation happen atomically.
     */

    try {
      const result = await db.runTransaction(async (transaction) => {
        /**
         * A. Existing order / idempotency check
         */
        const existingOrderSnapshot = await transaction.get(orderRef);

        if (existingOrderSnapshot.exists) {
          const existingOrder = existingOrderSnapshot.data();

          if (
            existingOrder?.requestFingerprint !== requestFingerprint
          ) {
            throw new HttpsError(
              "already-exists",
              "This clientRequestId was already used for a different order.",
            );
          }

          return {
            alreadyExists: true,
            orderId,
            subtotalPaise:
              isSafeNonNegativeInteger(existingOrder?.subtotalPaise)
                ? existingOrder.subtotalPaise
                : 0,
            grandTotalPaise:
              isSafeNonNegativeInteger(existingOrder?.grandTotalPaise)
                ? existingOrder.grandTotalPaise
                : 0,
            paymentMethod: "COD" as const,
            paymentStatus:
              existingOrder?.paymentStatus === "PAID"
                ? ("PAID" as const)
                : ("PENDING" as const),
            orderStatus:
              existingOrder?.orderStatus === "DELIVERED"
                ? ("DELIVERED" as const)
                : existingOrder?.orderStatus === "CANCELLED"
                  ? ("CANCELLED" as const)
                  : (existingOrder?.orderStatus === "CONFIRMED"
                      ? ("CONFIRMED" as const)
                      : ("UNKNOWN" as const)),
            stockStatus:
              existingOrder?.stockStatus === "SOLD"
                ? ("SOLD" as const)
                : existingOrder?.stockStatus === "RELEASED"
                  ? ("RELEASED" as const)
                  : existingOrder?.stockStatus === "RESERVED"
                    ? ("RESERVED" as const)
                    : ("UNKNOWN" as const),
          };
        }

        /**
         * B. Address ownership
         *
         * Because the path contains the authenticated UID, the
         * selected address automatically belongs to this user.
         */
        const addressRef = db
          .collection("users")
          .doc(uid)
          .collection("addresses")
          .doc(addressId);

        const addressSnapshot = await transaction.get(addressRef);

        if (!addressSnapshot.exists) {
          throw new HttpsError(
            "not-found",
            "The selected address does not exist.",
          );
        }

        const addressData = addressSnapshot.data();

        /**
         * C. Read all products
         */
        const productRefs: FirebaseFirestore.DocumentReference[] = [];
        const productSnapshots: FirebaseFirestore.DocumentSnapshot[] = [];

        for (const item of normalizedItems) {
          const productRef = db
            .collection("products")
            .doc(item.productId);

          productRefs.push(productRef);

          const productSnapshot = await transaction.get(productRef);
          productSnapshots.push(productSnapshot);
        }

        /**
         * D. Validate products + calculate server-side totals
         */
        const orderItems: OrderItemSnapshot[] = [];
        let subtotalPaise = 0;

        for (let i = 0; i < normalizedItems.length; i++) {
          const requestedItem = normalizedItems[i];
          const productSnapshot = productSnapshots[i];

          if (!productSnapshot.exists) {
            throw new HttpsError(
              "not-found",
              `Product ${requestedItem.productId} does not exist.`,
            );
          }

          const product = productSnapshot.data() as ProductData;

          // Product must be active.
          if (product.isActive !== true) {
            throw new HttpsError(
              "failed-precondition",
              `Product ${requestedItem.productId} is not available.`,
            );
          }

          // Server-side price is authoritative.
          if (!isSafeNonNegativeInteger(product.pricePaise)) {
            throw new HttpsError(
              "failed-precondition",
              `Product ${requestedItem.productId} has invalid price configuration.`,
            );
          }

          // Physical stock.
          if (!isSafeNonNegativeInteger(product.stockQuantity)) {
            throw new HttpsError(
              "failed-precondition",
              `Product ${requestedItem.productId} has invalid stock configuration.`,
            );
          }

          // Existing reservations default to 0.
          const reservedQuantity =
            product.reservedQuantity === null ||
            product.reservedQuantity === undefined
              ? 0
              : product.reservedQuantity;

          if (!isSafeNonNegativeInteger(reservedQuantity)) {
            throw new HttpsError(
              "failed-precondition",
              `Product ${requestedItem.productId} has invalid reserved stock configuration.`,
            );
          }

          // Reservation cannot exceed physical stock.
          if (reservedQuantity > product.stockQuantity) {
            throw new HttpsError(
              "failed-precondition",
              `Product ${requestedItem.productId} has invalid inventory state.`,
            );
          }

          /**
           * MAX BUYING LIMIT — PER ORDER
           *
           * number -> enforce
           * null   -> ignore
           * missing/undefined -> ignore
           * invalid non-null value -> configuration error
           */
          const maxBuyingLimit = product.maxBuyingLimit;

          if (
            maxBuyingLimit !== null &&
            maxBuyingLimit !== undefined
          ) {
            if (!isSafePositiveInteger(maxBuyingLimit)) {
              throw new HttpsError(
                "failed-precondition",
                `Product ${requestedItem.productId} has invalid maxBuyingLimit configuration.`,
              );
            }

            if (requestedItem.quantity > maxBuyingLimit) {
              const productName =
                typeof product.name === "string" && product.name.trim().length > 0
                  ? product.name
                  : "This product";

              throw new HttpsError(
                "failed-precondition",
                `${productName} has a maximum buying limit of ${maxBuyingLimit} unit(s) per order.`,
              );
            }
          }

          // Available stock = physical stock - current reservations.
          const availableStock =
            product.stockQuantity - reservedQuantity;

          if (availableStock < requestedItem.quantity) {
            const productName =
              typeof product.name === "string" && product.name.trim().length > 0
                ? product.name
                : "This product";

            throw new HttpsError(
              "failed-precondition",
              `${productName} has insufficient stock.`,
            );
          }

          // Server-side amount calculation in paise.
          const lineTotalPaise =
            product.pricePaise * requestedItem.quantity;

          if (!Number.isSafeInteger(lineTotalPaise)) {
            throw new HttpsError(
              "failed-precondition",
              "Order amount is too large.",
            );
          }

          subtotalPaise += lineTotalPaise;

          if (!Number.isSafeInteger(subtotalPaise)) {
            throw new HttpsError(
              "failed-precondition",
              "Order total is too large.",
            );
          }

          orderItems.push({
            productId: requestedItem.productId,
            name:
              typeof product.name === "string" && product.name.trim().length > 0
                ? product.name
                : "Product",
            quantity: requestedItem.quantity,
            unitPricePaise: product.pricePaise,
            lineTotalPaise,
          });
        }

        /**
         * E. Trusted server-side charges
         *
         * Currently there are no discounts, shipping charges or tax
         * calculations supplied by the client. Add your trusted
         * business rules here later.
         */
        const discountPaise = 0;
        const shippingPaise = 0;
        const taxPaise = 0;

        const grandTotalPaise =
          subtotalPaise -
          discountPaise +
          shippingPaise +
          taxPaise;

        if (
          !Number.isSafeInteger(grandTotalPaise) ||
          grandTotalPaise < 0
        ) {
          throw new HttpsError(
            "failed-precondition",
            "Invalid final order amount.",
          );
        }

        /**
         * F. COD STOCK RESERVATION
         *
         * Do NOT decrement stockQuantity here.
         * Reserve the units until delivery or cancellation.
         */
        for (let i = 0; i < normalizedItems.length; i++) {
          const item = normalizedItems[i];

          transaction.update(productRefs[i], {
            reservedQuantity: FieldValue.increment(item.quantity),
            updatedAt: FieldValue.serverTimestamp(),
          });
        }

        /**
         * G. Create order snapshot
         */
        const orderData = {
          orderId,
          userId: uid,

          // Idempotency / audit.
          clientRequestId,
          requestFingerprint,

          currency: "INR",

          items: orderItems,
          shippingAddress: addressData,

          subtotalPaise,
          discountPaise,
          shippingPaise,
          taxPaise,
          grandTotalPaise,

          paymentMethod: "COD",

          // COD payment is still pending until delivery/payment collection.
          paymentStatus: "PENDING",

          // Order is accepted, but inventory is only reserved.
          orderStatus: "CONFIRMED",
          stockStatus: "RESERVED",

          // COD has no payment gateway order and no reservation expiry.
          paymentProvider: null,
          paymentGatewayOrderId: null,
          reservationExpiresAt: null,

          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),

          schemaVersion: 1,
        };

        transaction.create(orderRef, orderData);

        return {
          alreadyExists: false,
          orderId,
          subtotalPaise,
          grandTotalPaise,
          paymentMethod: "COD" as const,
          paymentStatus: "PENDING" as const,
          orderStatus: "CONFIRMED" as const,
          stockStatus: "RESERVED" as const,
        };
      });

      /**
       * ----------------------------------------------------------
       * 7. SAFE CLIENT RESPONSE
       * ----------------------------------------------------------
       */
      return {
        success: true,
        alreadyExists: result.alreadyExists,
        orderId: result.orderId,

        paymentMethod: "COD",
        paymentStatus: result.paymentStatus,
        orderStatus: result.orderStatus,

        amountPaise: result.grandTotalPaise,

        stockStatus: result.stockStatus,

        payment: {
          provider: "COD",
          gatewayOrderId: null,
        },
      };
    } catch (error) {
      if (error instanceof HttpsError) {
        throw error;
      }

      console.error("createOrder internal error", error);

      throw new HttpsError(
        "internal",
        "Unable to create order.",
      );
    }
  },
);
