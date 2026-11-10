package com.gravo.Meher.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.gravo.Meher.Address;
import com.gravo.Meher.Banner;
import com.gravo.Meher.CartItem;
import com.gravo.Meher.Category;
import com.gravo.Meher.DeliveryCharge;
import com.gravo.Meher.Product;
import com.gravo.Meher.local.AddressDao;
import com.gravo.Meher.local.AddressEntity;
import com.gravo.Meher.local.AppDatabase;
import com.gravo.Meher.local.CategoryDao;
import com.gravo.Meher.local.CategoryEntity;
import com.gravo.Meher.local.ClickDao;
import com.gravo.Meher.local.ProductConverters;
import com.gravo.Meher.local.ProductDao;
import com.gravo.Meher.local.ProductEntity;
import com.gravo.Meher.local.ProductClickCount;
import com.gravo.Meher.local.ProductTimeSpendSum;
import com.gravo.Meher.local.TimeSpendEntity;
import com.gravo.Meher.local.LocalCartManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductRepository {

    private static final String TAG = "ProductRepository";

    // 5-Day Access TTL (5 * 24 * 60 * 60 * 1000 ms)
    public static final long FIVE_DAYS_MS = 5L * 24L * 60L * 60L * 1000L;
    public static final long PRODUCT_CACHE_TTL_MS = ProductSyncManager.DEFAULT_TTL_MS;
    private static final long SEARCH_MEMORY_TTL_MS = 5L * 60L * 1000L;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ProductDao dao;
    private final AddressDao addressDao;
    private final CategoryDao categoryDao;
    private final ClickDao clickDao;
    private final ProductSyncManager syncManager;
    private final long ttlMs;
    private final Context context;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, SearchCacheEntry> recentSearches = new ConcurrentHashMap<>();
    private final Map<String, DeliveryCharge> deliveryChargeCache = new ConcurrentHashMap<>();

    private ListenerRegistration addressRealtimeListener;

    public ProductRepository(Context context) {
        this(context, PRODUCT_CACHE_TTL_MS);
    }

    public ProductRepository(Context context, long ttlMs) {
        AppDatabase database = AppDatabase.getInstance(context);
        this.dao = database.productDao();
        this.addressDao = database.addressDao();
        this.clickDao = database.clickDao();
        this.categoryDao = database.categoryDao();
        this.ttlMs = ttlMs;
        this.context = context.getApplicationContext();
        this.syncManager = new ProductSyncManager(this.context, dao, ttlMs);

        // App initialization par 5 din se zyada purane unaccessed products ko cleanup karein
        cleanupStaleUnaccessedCache();
    }

    private void checkAndSaveFirstSyncTimestamp() {
        SharedPreferences prefs = context.getSharedPreferences("GravoAppPrefs", Context.MODE_PRIVATE);

        // Agar pehle se timestamp saved nahi hai, tabhi current time save karein
        if (!prefs.contains("last_sync_timestamp")) {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("last_sync_timestamp", currentTime).apply();
            Log.d(TAG, "First sync timestamp saved: " + currentTime);
        }
    }

    // Centralized method jo products insert karte waqt timestamp check bhi kar lega
    private void insertProductsAndRecordSync(List<ProductEntity> entities) {
        if (entities == null || entities.isEmpty()) return;
        checkAndSaveFirstSyncTimestamp();

        // Phir local Room DB mein products insert kar dein
        dao.insertProducts(entities);
    }

    // ==========================================
    // 1. REAL-TIME OBSERVERS (PRODUCTS & ADDRESSES)
    // ==========================================

    public LiveData<Product> observeProduct(String productId) {
        return Transformations.map(dao.observeProduct(productId), entity ->
                entity == null ? null : ProductMapper.toProduct(entity));
    }
    // ==========================================
    // 2. PRODUCT FETCHING LOGIC (LOCAL-FIRST + FALLBACK)
    // ==========================================

    public void getProduct(String productId, ProductCallback callback) {
        executor.execute(() -> {
            ProductEntity cached = dao.getProduct(productId);
            long now = System.currentTimeMillis();

            if (cached != null) {
                // Access time update karke 5-day expiration extension apply karein
                cached.lastAccessedAt = now;
                insertProductsAndRecordSync(Collections.singletonList(cached));
                postProductSuccess(callback, ProductMapper.toProduct(cached));

                // Fallback: Agar TTL expire ho chuka hai to background sync chalayein
                if (cached.expiresAt < now) refreshProduct(productId);
                return;
            }
            // Room me na milne par Firestore se fetch karein
            refreshProduct(productId, callback);
        });
    }

    public ListenerRegistration listenToProduct(String productId, ProductCallback callback) {
        // 1. Microseconds Instant Load: Pehle Room DB se cached data UI ko bhejein
        executor.execute(() -> {
            ProductEntity cached = dao.getProduct(productId);
            if (cached != null) {
                cached.lastAccessedAt = System.currentTimeMillis();
                insertProductsAndRecordSync(Collections.singletonList(cached));
                if (callback != null) postProductSuccess(callback, ProductMapper.toProduct(cached));
            }
        });

        // 2. Attach Targeted Real-time Snapshot Listener
        return db.collection("products").document(productId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Product real-time listen failed for: " + productId, error);
                        if (callback != null) mainHandler.post(() -> callback.onFailure(error));
                        return;
                    }

                    if (document != null && document.exists()) {
                        ProductEntity entity = ProductMapper.fromFirestore(document, System.currentTimeMillis(), ttlMs);
                        if (entity != null) {
                            // Firestore mein badlav aate hi Room DB update karein.
                            // Room DB update hote hi UI ka LiveData automatically trigger hoga!
                            executor.execute(() -> {
                                insertProductsAndRecordSync(Collections.singletonList(entity));
                                if (callback != null) postProductSuccess(callback, ProductMapper.toProduct(entity));
                            });
                        }
                    } else if (document != null && !document.exists()) {
                        // Agar backend se product delete kar diya gaya ho
                        executor.execute(() -> dao.deleteProduct(productId));
                        if (callback != null) mainHandler.post(() -> callback.onFailure(new IllegalStateException("Product no longer exists")));
                    }
                });
    }

    public void getProducts(List<String> productIds, ProductsCallback callback) {
        if (productIds == null || productIds.isEmpty()) {
            Log.d(TAG, "getProducts called with empty or null productIds list.");
            postProductsSuccess(callback, new ArrayList<>());
            return;
        }
        Log.d(TAG, "getProducts called for IDs count: " + productIds.size() + " -> IDs: " + productIds);

        executor.execute(() -> {
            long now = System.currentTimeMillis();
            List<ProductEntity> entities = dao.getProducts(productIds);
            Map<String, ProductEntity> byId = new HashMap<>();

            for (ProductEntity entity : entities) {
                entity.lastAccessedAt = now; // Touch access time
                insertProductsAndRecordSync(Collections.singletonList(entity));
                byId.put(entity.productId, entity);
            }

            List<String> missingIds = new ArrayList<>();
            for (String id : productIds) {
                if (!byId.containsKey(id)) {
                    missingIds.add(id);
                }
            }
            Log.d(TAG, "Local DB found: " + byId.size() + " items. Missing items to fetch from Firestore: " + missingIds.size());

            // Sabhi items local DB me mil gaye
            if (missingIds.isEmpty()) {
                postProductsSuccess(callback, toProductsInOrder(productIds, byId));
                return;
            }

            // Missing items ko Firestore se fetch karke local DB me sync karein
            db.collection("products")
                    .whereIn(FieldPath.documentId(), missingIds)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<ProductEntity> fetchedEntities = new ArrayList<>();
                        for (QueryDocumentSnapshot document : snapshot) {
                            ProductEntity entity = ProductMapper.fromFirestore(document, System.currentTimeMillis(), ttlMs);
                            if (entity != null) {
                                byId.put(entity.productId, entity);
                                fetchedEntities.add(entity);
                                Log.d(TAG, "Successfully fetched from Firestore - ID: " + entity.productId + ", Name: " + entity.name);
                            }
                        }
                        executor.execute(() -> {
                            insertProductsAndRecordSync(fetchedEntities);
                            postProductsSuccess(callback, toProductsInOrder(productIds, byId));
                        });
                    })
                    .addOnFailureListener(error -> {
                        Log.e(TAG, "Failed to fetch missing products from Firestore", error);
                        postProductsSuccess(callback, toProductsInOrder(productIds, byId));
                    });
        });
    }

    public void refreshProduct(String productId) {
        refreshProduct(productId, null);
    }

    private void refreshProduct(String productId, ProductCallback callback) {
        if (productId == null || productId.isEmpty()) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("Missing product ID"));
            return;
        }

        db.collection("products").document(productId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        executor.execute(() -> dao.deleteProduct(productId));
                        if (callback != null) mainHandler.post(() -> callback.onFailure(new IllegalStateException("Product not found")));
                        return;
                    }
                    ProductEntity entity = ProductMapper.fromFirestore(document, System.currentTimeMillis(), ttlMs);
                    if (entity == null) {
                        if (callback != null) mainHandler.post(() -> callback.onFailure(new IllegalStateException("Malformed product")));
                        return;
                    }
                    executor.execute(() -> {
                        insertProductsAndRecordSync(Collections.singletonList(entity));
                        if (callback != null) postProductSuccess(callback, ProductMapper.toProduct(entity));
                    });
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "Product refresh failed for " + productId, error);
                    if (callback != null) mainHandler.post(() -> callback.onFailure(error));
                });
    }

    // ==========================================
    // 3. ADDRESS HANDLING (LOCAL-FIRST + FIRESTORE FALLBACK)
    // ==========================================

    public interface DefaultAddressCallback {
        void onAddressFound(Address address);
        void onAddressNotFound();
        void onFailure(Exception error);
    }

    public interface DeliveryChargeCallback {
        void onSuccess(DeliveryCharge deliveryCharge);
        void onFailure(Exception error);
    }

    public interface PricingVersionCallback {
        void onPricingVersionChanged(long newPricingVersion);
        void onError(Exception error);
    }

    public ListenerRegistration observePricingVersion(PricingVersionCallback callback) {
        return db.collection("appConfig").document("cartPricing")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Pricing version snapshot listener failed", error);
                        if (callback != null) mainHandler.post(() -> callback.onError(error));
                        return;
                    }
                    if (snapshot != null && snapshot.exists() && snapshot.contains("pricingVersion")) {
                        Long ver = snapshot.getLong("pricingVersion");
                        if (ver != null && callback != null) {
                            mainHandler.post(() -> callback.onPricingVersionChanged(ver));
                        }
                    }
                });
    }

    public void getDeliveryCharge(String pincode, DeliveryChargeCallback callback) {
        if (pincode == null || pincode.trim().isEmpty()) {
            mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("Invalid PIN code")));
            return;
        }

        String normalizedPincode = pincode.trim();
        DeliveryCharge cached = deliveryChargeCache.get(normalizedPincode);
        if (cached != null) {
            mainHandler.post(() -> callback.onSuccess(cached));
            return;
        }

        db.collection("analytics").document("pincode-" + normalizedPincode).get()
                .addOnSuccessListener(document -> {
                    if (document == null || !document.exists()) {
                        mainHandler.post(() -> callback.onFailure(
                                new IllegalStateException("Delivery service is unavailable for this PIN code")));
                        return;
                    }

                    Boolean serviceableValue = document.getBoolean("isServiceable");
                    Double instantCharge = numberValueAsDouble(document.get("instant delivery charge"));
                    Double normalCharge = numberValueAsDouble(document.get("normal delivery charge"));

                    if (serviceableValue == null
                            || (Boolean.TRUE.equals(serviceableValue)
                            && (instantCharge == null || normalCharge == null
                            || instantCharge < 0 || normalCharge < 0))) {
                        mainHandler.post(() -> callback.onFailure(
                                new IllegalStateException("Delivery charge data is incomplete")));
                        return;
                    }

                    DeliveryCharge deliveryCharge = new DeliveryCharge(
                            normalizedPincode,
                            serviceableValue,
                            instantCharge,
                            normalCharge
                    );
                    deliveryChargeCache.put(normalizedPincode, deliveryCharge);
                    mainHandler.post(() -> callback.onSuccess(deliveryCharge));
                })
                .addOnFailureListener(error -> mainHandler.post(() -> callback.onFailure(error)));
    }

    public interface ClickSyncCallback {
        void onComplete();
        void onFailure(Exception error);
    }
    public void getDefaultAddress(String userId, DefaultAddressCallback callback) {
        if (userId == null || userId.trim().isEmpty()) {
            mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("Invalid user ID")));
            return;
        }

        executor.execute(() -> {
            // 1. Direct check: Kya local Room DB me DEFAULT address exist karta hai?
            AddressEntity localDefault = addressDao.getDefaultAddress(userId);

            // 2. CHECK: Agar Default Address mil gaya -> Firestore Query SKIP karein!
            if (localDefault != null) {
                Log.d(TAG, "Local DB me DEFAULT address mil gaya. Firestore query skipped!");
                mainHandler.post(() -> callback.onAddressFound(toAddress(localDefault)));
                return; // EARLY EXIT: Network hit completely skipped
            }

            // 3. Agar Local DB me Default Address NAHI milta -> Firestore se saare addresses fetch karein
            Log.d(TAG, "Local DB me default address nahi mila. Firestore se saare addresses fetch ho rahe hain...");
            syncAllAddressesFromFirestore(userId, callback);
        });
    }

    /**
     * One-time fetch: Local DB me default address missing hone par hi chalega
     */
    private void syncAllAddressesFromFirestore(String userId, DefaultAddressCallback callback) {
        db.collection("users").document(userId).collection("addresses")
                .get() // Single network query call
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        if (callback != null) mainHandler.post(callback::onAddressNotFound);
                        return;
                    }

                    List<AddressEntity> allAddresses = new ArrayList<>();
                    AddressEntity defaultEntity = null;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        AddressEntity entity = addressEntityFromFirestore(doc, userId);
                        allAddresses.add(entity);
                        if (entity.isDefault) {
                            defaultEntity = entity;
                        }
                    }

                    AddressEntity finalDefault = defaultEntity;
                    executor.execute(() -> {
                        // Saare addresses ko local Room DB me bulk insert/replace karein
                        addressDao.clearUserAddresses(userId);
                        addressDao.insertAddresses(allAddresses);

                        if (callback != null) {
                            if (finalDefault != null) {
                                mainHandler.post(() -> callback.onAddressFound(toAddress(finalDefault)));
                            } else {
                                mainHandler.post(callback::onAddressNotFound);
                            }
                        }
                    });
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onFailure(error));
                    }
                });
    }

    public void recordProductClick(String productId) {
        if (productId == null || productId.trim().isEmpty()) return;
        executor.execute(() -> {
            if (clickDao.incrementExisting(productId) == 0) {
                clickDao.upsertClickCount(new ProductClickCount(productId, 1));
            }
        });
        recordProductTimeSpent(productId, 5000L);
    }

    public void recordProductTimeSpent(String productId, long timeSpentMs) {
        if (productId == null || productId.trim().isEmpty() || timeSpentMs <= 0) return;
        executor.execute(() -> {
            try {
                AppDatabase.getInstance(context).timeSpendDao()
                        .insertTimeSpend(new TimeSpendEntity(productId, timeSpentMs, System.currentTimeMillis()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to record time spend", e);
            }
        });
    }

    public void syncProductClickCounts(ClickSyncCallback callback) {
        executor.execute(() -> {
            List<ProductClickCount> pending = clickDao.getClickCounts();
            if (pending.isEmpty()) {
                mainHandler.post(callback::onComplete);
                return;
            }
            Map<String, Object> increments = new HashMap<>();
            for (ProductClickCount count : pending) {
                increments.put(count.productId, count.clickCount);
            }
            db.collection("analytics").document("analytics").get()
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException() == null
                                    ? new IllegalStateException("Analytics read failed")
                                    : task.getException();
                        }
                        Map<String, Object> existing = new HashMap<>();
                        Object value = task.getResult().get("productIds");
                        if (value instanceof Map) existing.putAll((Map<String, Object>) value);
                        for (Map.Entry<String, Object> entry : increments.entrySet()) {
                            existing.put(entry.getKey(),
                                    numberValue(existing.get(entry.getKey()))
                                            + numberValue(entry.getValue()));
                        }
                        return db.collection("analytics").document("analytics")
                                .set(Collections.singletonMap("productIds", existing),
                                        SetOptions.merge());
                    })
                    .addOnSuccessListener(unused -> executor.execute(() -> {
                        for (ProductClickCount count : pending) {
                            clickDao.decrementClickCount(count.productId, count.clickCount);
                            clickDao.deleteIfEmpty(count.productId);
                        }
                        mainHandler.post(callback::onComplete);
                    }))
                    .addOnFailureListener(error -> mainHandler.post(() -> callback.onFailure(error)));
        });
    }
    // ==================== TIME SPEND BASED PRODUCTS ====================

    public interface TimeSpendProductsCallback {
        void onSuccess(List<Product> products, boolean isEnd);
        void onFailure(Exception e);
    }

    /**
     * Top time-spent products with pagination (10 items per page)
     * @param page 0-based page number
     */
    public void getTopTimeSpentProducts(int page, int pageSize, TimeSpendProductsCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Get all aggregated time spends (already ordered DESC)
                List<ProductTimeSpendSum> allTimeSpends =
                        AppDatabase.getInstance(context).timeSpendDao().getAggregatedTimeSpends();

                if (allTimeSpends == null || allTimeSpends.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), true));
                    return;
                }

                // 2. Pagination logic
                int fromIndex = page * pageSize;
                if (fromIndex >= allTimeSpends.size()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), true));
                    return;
                }

                int toIndex = Math.min(fromIndex + pageSize, allTimeSpends.size());
                List<ProductTimeSpendSum> pageItems = allTimeSpends.subList(fromIndex, toIndex);

                // 3. Extract product IDs
                List<String> productIds = new ArrayList<>();
                for (ProductTimeSpendSum item : pageItems) {
                    productIds.add(item.productId);
                }

                // 4. Fetch products from repository (local first)
                getProducts(productIds, products -> {
                    // Maintain the same order as time spent ranking
                    Map<String, Product> productMap = new HashMap<>();
                    for (Product p : products) {
                        productMap.put(p.getProductId(), p);
                    }

                    List<Product> orderedProducts = new ArrayList<>();
                    for (String id : productIds) {
                        Product p = productMap.get(id);
                        if (p != null) orderedProducts.add(p);
                    }

                    boolean isEnd = toIndex >= allTimeSpends.size();
                    mainHandler.post(() -> callback.onSuccess(orderedProducts, isEnd));
                });

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    private void startAddressRealtimeListener(String userId) {
        if (addressRealtimeListener != null) return;

        addressRealtimeListener = db.collection("users").document(userId).collection("addresses")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    executor.execute(() -> {
                        List<AddressEntity> updated = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            AddressEntity entity = doc.toObject(AddressEntity.class);
                            entity.addressId = doc.getId();
                            entity.userId = userId;
                            updated.add(entity);
                        }
                        addressDao.clearUserAddresses(userId);
                        addressDao.insertAddresses(updated);
                    });
                });
    }

    // ==========================================
    // 4. CATEGORIES, SEARCH & PAGINATION
    // ==========================================

    public void getProductsByCategory(String categoryId, ProductsCallback callback) {
        executor.execute(() -> {
            List<ProductEntity> cached = dao.getProductsByCategory(categoryId);
            long now = System.currentTimeMillis();
            boolean hasFreshCache = !cached.isEmpty();

            for (ProductEntity entity : cached) {
                if (entity.expiresAt <= now) {
                    hasFreshCache = false;
                    break;
                }
            }

            if (hasFreshCache) {
                postProductsSuccess(callback, toProducts(cached));
                return;
            }

            if (!cached.isEmpty()) postProductsSuccess(callback, toProducts(cached));

            db.collection("products").whereEqualTo("categoryId", categoryId).get()
                    .addOnSuccessListener(snapshot -> {
                        List<ProductEntity> entities = new ArrayList<>();
                        for (QueryDocumentSnapshot document : snapshot) {
                            ProductEntity entity = ProductMapper.fromFirestore(document, System.currentTimeMillis(), ttlMs);
                            if (entity != null) entities.add(entity);
                        }
                        executor.execute(() -> {
                            insertProductsAndRecordSync(entities);
                            postProductsSuccess(callback, toProducts(dao.getProductsByCategory(categoryId)));
                        });
                    }).addOnFailureListener(error -> {
                        if (cached.isEmpty()) mainHandler.post(() -> callback.onFailure(error));
                    });
        });
    }

    public void saveProducts(List<Product> products, ProductsCallback callback) {
        executor.execute(() -> {
            List<ProductEntity> entities = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Product product : products) {
                ProductEntity entity = ProductMapper.fromProduct(product);
                entity.cachedAt = now;
                entity.expiresAt = now + ttlMs;
                entity.lastAccessedAt = now;
                entities.add(entity);
            }
            insertProductsAndRecordSync(entities);
            postProductsSuccess(callback, toProducts(dao.getProducts(productIds(products))));
        });
    }

    public void searchProducts(List<String> keywords, ProductsCallback callback) {
        if (keywords == null || keywords.isEmpty()) {
            callback.onSuccess(Collections.emptyList());
            return;
        }
        String cacheKey = joinKeywords(keywords);
        long now = System.currentTimeMillis();
        SearchCacheEntry recent = recentSearches.get(cacheKey);
        if (recent != null && now - recent.cachedAt < SEARCH_MEMORY_TTL_MS) {
            mainHandler.post(() -> callback.onSuccess(recent.products));
            return;
        }

        executor.execute(() -> {
            List<Product> localMatches = findLocalMatches(keywords, dao.getAllProducts());
            if (!localMatches.isEmpty()) mainHandler.post(() -> callback.onSuccess(localMatches));
        });

        db.collection("products").whereArrayContainsAny("tags_lowercase", keywords).get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> remote = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        Product product = document.toObject(Product.class);
                        product.setProductId(document.getId());
                        remote.add(product);
                    }
                    saveProducts(remote, products -> {
                        recentSearches.put(cacheKey, new SearchCacheEntry(System.currentTimeMillis(), products));
                        mainHandler.post(() -> callback.onSuccess(products));
                    });
                }).addOnFailureListener(error -> mainHandler.post(() -> callback.onFailure(error)));
    }

    // ==========================================
    // 5. CACHE PURGE & CLEANUP LOGIC (5 DAYS RULE)
    // ==========================================

    public void cleanupStaleUnaccessedCache() {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            long fiveDaysAgo = now - FIVE_DAYS_MS;

            // 5 din se zyada unaccessed + expired products purge
            int deletedCount = dao.deleteExpiredProducts(now, fiveDaysAgo);
            Log.d(TAG, "Cleaned up " + deletedCount + " stale products unaccessed for 5 days.");
        });
    }

    // ==========================================
    // 6. HELPER METHODS & CALLBACK INTERFACES
    // ==========================================

    private List<String> productIds(List<Product> products) {
        List<String> ids = new ArrayList<>();
        for (Product product : products) ids.add(product.getProductId());
        return ids;
    }

    private String joinKeywords(List<String> keywords) {
        List<String> normalized = new ArrayList<>(keywords);
        Collections.sort(normalized);
        return String.join(" ", normalized);
    }

    private List<Product> findLocalMatches(List<String> keywords, List<ProductEntity> entities) {
        List<Product> matches = new ArrayList<>();
        for (ProductEntity entity : entities) {
            List<String> tags = ProductConverters.toStrings(entity.tagsLowercaseJson);
            boolean matchesAny = false;
            for (String keyword : keywords) {
                if (tags.contains(keyword)) {
                    matchesAny = true;
                    break;
                }
            }
            if (matchesAny) matches.add(ProductMapper.toProduct(entity));
        }
        return matches;
    }

    private List<Product> toProducts(List<ProductEntity> entities) {
        List<Product> result = new ArrayList<>();
        for (ProductEntity entity : entities) result.add(ProductMapper.toProduct(entity));
        return result;
    }

    private List<Product> toProductsInOrder(List<String> ids, Map<String, ProductEntity> byId) {
        List<Product> result = new ArrayList<>();
        for (String id : ids) if (byId.containsKey(id)) result.add(ProductMapper.toProduct(byId.get(id)));
        return result;
    }

    private AddressEntity addressEntityFromFirestore(DocumentSnapshot document, String userId) {
        AddressEntity entity = new AddressEntity();
        entity.addressId = document.getId();
        entity.userId = userId;
        entity.fullName = document.getString("fullName");
        entity.phoneNumber = document.getString("phone");
        entity.areaStreet = document.getString("street");
        entity.city = document.getString("city");
        entity.state = document.getString("state");
        entity.pincode = document.getString("pincode");

        // ✅ ADD THIS LINE:
        entity.type = document.getString("type") != null ? document.getString("type") : "HOME";

        Boolean isDefault = document.getBoolean("default");
        entity.isDefault = Boolean.TRUE.equals(isDefault);
        return entity;
    }
    public void getCategories(CategoriesCallback callback) {
        executor.execute(() -> {
            // Step 1: Pehle Local Room DB me check karein
            List<CategoryEntity> cachedEntities = categoryDao.getAllCategories();
            if (cachedEntities != null && !cachedEntities.isEmpty()) {
                List<Category> cachedCategories = new ArrayList<>();
                for (CategoryEntity entity : cachedEntities) {
                    Category cat = new Category();
                    cat.setId(entity.categoryId);
                    cat.setName(entity.name);
                    cat.setIconUrl(entity.imageUrl);
                    cachedCategories.add(cat);
                }
                mainHandler.post(() -> callback.onSuccess(cachedCategories));
                return;
            }

            // Step 2: Local me na milne par Firebase Firestore se fetch karein
            db.collection("categories").get()
                    .addOnSuccessListener(snapshot -> {
                        List<Category> firebaseCategories = new ArrayList<>();
                        List<CategoryEntity> entitiesToSave = new ArrayList<>();
                        long now = System.currentTimeMillis();

                        for (QueryDocumentSnapshot doc : snapshot) {
                            Category category = doc.toObject(Category.class);
                            category.setId(doc.getId());
                            firebaseCategories.add(category);

                            CategoryEntity entity = new CategoryEntity();
                            entity.categoryId = doc.getId();
                            entity.name = category.getName();
                            entity.imageUrl = category.getIconUrl();
                            entity.cachedAt = now;
                            entitiesToSave.add(entity);
                        }

                        // Step 3: Local DB me save karke UI ko data bhej dein
                        executor.execute(() -> {
                            categoryDao.insertCategories(entitiesToSave);
                            mainHandler.post(() -> callback.onSuccess(firebaseCategories));
                        });
                    })
                    .addOnFailureListener(error -> mainHandler.post(() -> callback.onFailure(error)));
        });
    }

    private Address toAddress(AddressEntity entity) {
        Address address = new Address();
        address.setAddressId(entity.addressId);
        address.setFullName(entity.fullName);
        address.setPhone(entity.phoneNumber);
        address.setStreet(entity.areaStreet);
        address.setCity(entity.city);
        address.setState(entity.state);
        address.setPincode(entity.pincode);

        // ✅ ADD THIS LINE:
        address.setType(entity.type != null ? entity.type : "HOME");

        address.setDefault(entity.isDefault);
        return address;
    }

    private long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Double numberValueAsDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private void postProductSuccess(ProductCallback callback, Product product) {
        mainHandler.post(() -> callback.onSuccess(product));
    }

    private void postProductsSuccess(ProductsCallback callback, List<Product> products) {
        mainHandler.post(() -> callback.onSuccess(products));
    }

    private void postPageSuccess(PageCallback callback, List<Product> products, DocumentSnapshot cursor) {
        mainHandler.post(() -> callback.onSuccess(products, cursor));
    }

    private static final class SearchCacheEntry {
        final long cachedAt;
        final List<Product> products;

        SearchCacheEntry(long cachedAt, List<Product> products) {
            this.cachedAt = cachedAt;
            this.products = products;
        }
    }

    public interface ProductCallback {
        void onSuccess(Product product);
        void onFailure(Exception error);
    }
    public interface CategoriesCallback {
        void onSuccess(List<Category> categories);
        void onFailure(Exception error);
    }

    public interface ProductsCallback {
        void onSuccess(List<Product> products);
        default void onFailure(Exception error) { Log.w(TAG, "Product query failed", error); }
    }

    public interface PageCallback {
        void onSuccess(List<Product> products, DocumentSnapshot lastVisible);
        void onError(Exception error);
    }

    public interface CartItemsCallback {
        void onSuccess(List<CartItem> items);
        default void onFreeDeliveryEligibilityChanged(boolean eligible) {}
        default void onFailure(Exception error) { Log.w(TAG, "Cart products unavailable", error); }
    }
    public interface BannersCallback {
        void onSuccess(List<Banner> banners);
        default void onFailure(Exception error) { Log.w(TAG, "Banners fetch failed", error); }
    }
    public void getBanners(List<String> bannerIds, BannersCallback callback) {
        if (bannerIds == null || bannerIds.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        // Sirf unhi IDs wale banner documents fetch karein jo list mein aaye hain
        db.collection("banners")
                .whereIn(FieldPath.documentId(), bannerIds)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Banner> bannerList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Banner banner = doc.toObject(Banner.class);
                        if (banner != null) {
                            bannerList.add(banner);
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(bannerList));
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Failed to fetch banners", error);
                    mainHandler.post(() -> callback.onFailure(error));
                });
    }

    public void getCartItems(String userId, CartItemsCallback callback) {
        Map<String, Integer> quantities = LocalCartManager.getItems(context);
        getProducts(new ArrayList<>(quantities.keySet()), products -> {
            List<CartItem> items = new ArrayList<>();
            for (Product product : products) {
                items.add(new CartItem(product, quantities.getOrDefault(product.getProductId(), 1)));
            }
            mainHandler.post(() -> {
                callback.onSuccess(items);
                callback.onFreeDeliveryEligibilityChanged(false);
            });
        });
    }

    public ProductSyncManager getSyncManager() { return syncManager; }
    public void startSync() { syncManager.startProductListener(); }
    public void stopSync() {
        syncManager.stopProductListener();
        if (addressRealtimeListener != null) {
            addressRealtimeListener.remove();
            addressRealtimeListener = null;
        }
    }

    // 1. Serviceability Callback Interface (ProductRepository class ke andar kisi interface section me add karein)
    public interface ServiceabilityCallback {
        void onServiceable();
        void onNotServiceable(String message);
        void onError(Exception e);
    }

    // 2. Pincode Serviceability Check Method
    public void checkPincodeServiceability(String pincode, ServiceabilityCallback callback) {
        if (pincode == null || pincode.trim().isEmpty()) {
            mainHandler.post(() -> callback.onNotServiceable("Please enter a valid pincode"));
            return;
        }

        String documentId = "pincode-" + pincode.trim();

        // Firestore ke 'analytics' collection mein check karein ki pincode document exist karta hai ya nahi
        db.collection("analytics").document(documentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()
                            && !Boolean.FALSE.equals(documentSnapshot.getBoolean("isServiceable"))) {
                        mainHandler.post(callback::onServiceable);
                    } else {
                        mainHandler.post(() -> callback.onNotServiceable("We are not servicing yet here"));
                    }
                })
                .addOnFailureListener(error -> mainHandler.post(() -> callback.onError(error)));
    }

    public void getProductsByCategoryPaginated(String categoryId, DocumentSnapshot lastDoc, int limit, PaginatedProductsCallback callback) {
        Query query = db.collection("products")
                .whereEqualTo("categoryId", categoryId)
                .limit(limit);

        if (lastDoc != null) {
            query = query.startAfter(lastDoc);
        }

        // 🌟 FIX: Final variable banayein taaki lambda ke andar pass ho sake
        final Query finalQuery = query;

        finalQuery.get(Source.CACHE).addOnSuccessListener(cacheSnapshots -> {
            if (cacheSnapshots != null && !cacheSnapshots.isEmpty()) {
                processPaginatedSnapshots(cacheSnapshots, limit, callback);
            } else {
                fetchProductsFromServer(finalQuery, limit, callback);
            }
        }).addOnFailureListener(e -> {
            fetchProductsFromServer(finalQuery, limit, callback);
        });
    }

    // Helper 1: Server Fetch Fallback
    private void fetchProductsFromServer(Query query, int limit, PaginatedProductsCallback callback) {
        query.get(Source.SERVER).addOnSuccessListener(serverSnapshots -> {
            processPaginatedSnapshots(serverSnapshots, limit, callback);
        }).addOnFailureListener(callback::onFailure);
    }

    // Helper 2: Common Snapshot Processing
    private void processPaginatedSnapshots(QuerySnapshot queryDocumentSnapshots, int limit, PaginatedProductsCallback callback) {
        List<Product> products = new ArrayList<>();
        for (DocumentSnapshot doc : queryDocumentSnapshots) {
            Product product = doc.toObject(Product.class);
            if (product != null) {
                product.setProductId(doc.getId());
                products.add(product);
            }
        }

        boolean isEnd = queryDocumentSnapshots.size() < limit;
        DocumentSnapshot newLastDoc = queryDocumentSnapshots.isEmpty() ?
                null : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);

        callback.onSuccess(products, newLastDoc, isEnd);
    }

    // ==========================================
    // CART MANAGEMENT (SINGLE PRODUCT)
    // ==========================================

    // 1. Missing Interface for View Model
    public interface CartCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public void updateCartPincode(String pincode, CartCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            mainHandler.post(() -> callback.onFailure("Please login to update cart"));
            return;
        }
        if (pincode == null || pincode.trim().isEmpty()) {
            mainHandler.post(() -> callback.onFailure("Invalid PIN code"));
            return;
        }

        Map<String, Object> cartData = new HashMap<>();
        cartData.put("pincode", pincode.trim());
        cartData.put("updatedAt", System.currentTimeMillis());
        cartSummaryReference(user.getUid())
                .set(cartData, SetOptions.merge())
                .addOnSuccessListener(unused -> mainHandler.post(callback::onSuccess))
                .addOnFailureListener(error -> mainHandler.post(() ->
                        callback.onFailure(error.getMessage())));
    }
    public interface PaginatedProductsCallback {
        void onSuccess(List<Product> products, DocumentSnapshot lastDoc, boolean isEnd);
        void onFailure(Exception e);
    }

    // 2. Real-time Cart Status Check
    public void checkCartStatus(String productId, MutableLiveData<Integer> cartQuantity) {
        cartQuantity.postValue(LocalCartManager.getQuantity(context, productId));
    }

    // 3. Update or Remove Quantity in Cart
    public void updateCartQuantity(String productId, int newQuantity, CartCallback callback) {
        if (productId == null || productId.trim().isEmpty()) {
            if (callback != null) mainHandler.post(() -> callback.onFailure("Invalid product"));
            return;
        }

        // 1. Instantly update the local UI cart state
        LocalCartManager.setQuantity(context, productId, Math.max(0, newQuantity));
        CartEligibilityManager.recordCartModification(context);

        // 2. Check if user is logged in before syncing to Firestore
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // If they are a guest, just keep it local and succeed
            if (callback != null) mainHandler.post(callback::onSuccess);
            return;
        }

        // 3. Rebuild the entire cart array directly from the local manager
        Map<String, Integer> localItems = LocalCartManager.getItems(context);
        List<Map<String, Object>> productsArray = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : localItems.entrySet()) {
            if (entry.getValue() > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("productId", entry.getKey());
                item.put("quantity", entry.getValue());
                productsArray.add(item);
            }
        }

        // 4. Push the fully synced array to Firestore
        Map<String, Object> cartData = new HashMap<>();
        cartData.put("products", productsArray);
        cartData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .collection("cart").document("cart")
                .set(cartData, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (callback != null) mainHandler.post(callback::onSuccess);
                })
                .addOnFailureListener(error -> {
                    if (callback != null) mainHandler.post(() -> callback.onFailure(error.getMessage()));
                });
    }

    private DocumentReference cartSummaryReference(String userId) {
        return db.collection("users").document(userId).collection("cart").document("cart");
    }
}