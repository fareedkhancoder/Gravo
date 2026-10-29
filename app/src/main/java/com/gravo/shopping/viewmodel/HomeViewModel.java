package com.gravo.shopping.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.Address;
import com.gravo.shopping.Banner;
import com.gravo.shopping.Category;
import com.gravo.shopping.DeliverySlot;
import com.gravo.shopping.Product;
import com.gravo.shopping.data.repository.ProductRepository;
import com.gravo.shopping.local.AppDatabase;
import com.gravo.shopping.local.TrendingProductEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeViewModel extends AndroidViewModel {

    private final ProductRepository repository;

    // ==========================================
    // UI LIVE DATA VARIABLES
    // ==========================================

    private final MutableLiveData<Boolean> unserviceablePincodeEvent = new MutableLiveData<>();
    private final MutableLiveData<List<Banner>> banners = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Product>> deals = new MutableLiveData<>();
    private final MutableLiveData<List<Product>> trending = new MutableLiveData<>();

    private final MutableLiveData<String> timeSlotLiveData = new MutableLiveData<>();



    public LiveData<String> getTimeSlotLiveData() {
        return timeSlotLiveData;
    }

    public MutableLiveData<Boolean> getUnserviceablePincodeEvent() {
        return unserviceablePincodeEvent;
    }

    private final MutableLiveData<Boolean> isEndOfTrending = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> requireLoginEvent = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> addressNotFoundEvent = new MutableLiveData<>(false);
    private final MutableLiveData<Address> currentAddress = new MutableLiveData<>();
    public LiveData<Address> getCurrentAddress() { return currentAddress; }

    // ==========================================
    // TRENDING PAGINATION VARIABLES
    // ==========================================
    private final List<Product> loadedTrending = new ArrayList<>();
    private List<String> allTrendingIds = new ArrayList<>();
    private int nextTrendingIndex = 10; // Pehle 10 deals ke liye use ho chuke hain, isliye trending 10 ke baad se shuru hoga
    private static final int PAGE_SIZE = 10;
    private boolean isLoadingTrending = false;
    private boolean isStartupDone = false;

    // ==========================================
    // GETTERS FOR UI OBSERVATION
    // ==========================================
    public LiveData<List<Banner>> getBanners() { return banners; }
    public LiveData<List<Category>> getCategories() { return categories; }
    public LiveData<List<Product>> getDeals() { return deals; }
    public LiveData<List<Product>> getTrending() { return trending; }
    public LiveData<Boolean> getIsEndOfTrending() { return isEndOfTrending; }
    public LiveData<Boolean> getRequireLoginEvent() { return requireLoginEvent; }
    public LiveData<Boolean> getAddressNotFoundEvent() { return addressNotFoundEvent; }

    // Constructor: AndroidViewModel requires Application context
    public HomeViewModel(@NonNull Application application) {
        super(application);
        // Repository initialize ki ja rahi hai
        repository = new ProductRepository(application);
    }

    // ==========================================
    // STARTUP TRIGGER METHOD
    // ==========================================
    public void startDataLoad() {
        if (isStartupDone) return;
        isStartupDone = true;
        fetchUserAddressAndData();
    }

    // ==========================================
    // STEP 1 & 2: AUTH & ADDRESS FETCHING
    // ==========================================
    private void fetchUserAddressAndData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Agar user logged in nahi hai, toh login event trigger karein
        if (currentUser == null) {
            requireLoginEvent.postValue(true);
            return;
        }

        String userId = currentUser.getUid();

        // Repository se default address mangwaya ja raha hai
        repository.getDefaultAddress(userId, new ProductRepository.DefaultAddressCallback() {

            @Override
            public void onAddressFound(Address address) {
                if (address == null || address.getPincode() == null || address.getPincode().trim().isEmpty()) {
                    addressNotFoundEvent.postValue(true);
                    return;
                }

                currentAddress.postValue(address);

                String pincode = address.getPincode().trim();
                fetchBannersAndCategories();

                // 🌟 SINGLE FIRESTORE READ FOR PINCODE DATA
                FirebaseFirestore.getInstance()
                        .collection("analytics")
                        .document("pincode-" + pincode)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {

                            if (!documentSnapshot.exists()) {
                                deals.postValue(new ArrayList<>());
                                banners.postValue(new ArrayList<>());
                                isEndOfTrending.postValue(true);
                                return;
                            }

                            // 1. Serviceability Check
                            Boolean isServiceableObj = documentSnapshot.getBoolean("isServiceable");
                            boolean isServiceable = Boolean.TRUE.equals(isServiceableObj);

                            // 2. Process Slots directly for UI LiveData (No Room DB overhead)
                            Object slotsObj = documentSnapshot.get("slots");
                            if (slotsObj instanceof List) {
                                String slotsJson = new com.google.gson.Gson().toJson(slotsObj);
                                List<DeliverySlot> slotsList = new com.google.gson.Gson().fromJson(
                                        slotsJson,
                                        new com.google.gson.reflect.TypeToken<List<DeliverySlot>>(){}.getType()
                                );
                                String timeSlotText = calculateDynamicTimeSlot(slotsList);
                                timeSlotLiveData.postValue(timeSlotText); // UI Instant Update
                            }

                            // 3. Agar Pincode Serviceable NAHI hai, toh aage ke network calls & DB processing skip karein
                            if (!isServiceable) {
                                banners.postValue(new ArrayList<>());
                                deals.postValue(new ArrayList<>());
                                isEndOfTrending.postValue(true);
                                unserviceablePincodeEvent.postValue(true);
                                return;
                            }

                            // 4. Banners Extract & Fetch
                            List<String> bannerIds = new ArrayList<>();
                            if (documentSnapshot.contains("banners")) {
                                Object bannersObj = documentSnapshot.get("banners");
                                if (bannersObj instanceof List) {
                                    bannerIds = (List<String>) bannersObj;
                                }
                            }

                            if (!bannerIds.isEmpty()) {
                                repository.getBanners(bannerIds, new ProductRepository.BannersCallback() {
                                    @Override
                                    public void onSuccess(List<Banner> bannerList) {
                                        banners.postValue(bannerList);
                                    }

                                    @Override
                                    public void onFailure(Exception error) {
                                        banners.postValue(new ArrayList<>());
                                    }
                                });
                            } else {
                                banners.postValue(new ArrayList<>());
                            }

                            // 5. Products Processing & Room DB (Sirf Trending Pagination ke liye)
                            if (documentSnapshot.contains("productIds")) {
                                Map<String, Object> productMap = (Map<String, Object>) documentSnapshot.get("productIds");

                                if (productMap != null && !productMap.isEmpty()) {
                                    List<TrendingProductEntity> entities = new ArrayList<>();
                                    for (Map.Entry<String, Object> entry : productMap.entrySet()) {
                                        String productId = entry.getKey();
                                        int clicks = 0;
                                        try {
                                            clicks = (int) Double.parseDouble(entry.getValue().toString());
                                        } catch (Exception e) {
                                            clicks = 0;
                                        }
                                        entities.add(new TrendingProductEntity(productId, clicks));
                                    }

                                    new Thread(() -> {
                                        AppDatabase db = AppDatabase.getInstance(getApplication());
                                        db.trendingDao().deleteAll();
                                        db.trendingDao().insertAll(entities);

                                        List<TrendingProductEntity> topEntities = db.trendingDao().getAllTrending();
                                        List<String> allIds = new ArrayList<>();
                                        if (topEntities != null) {
                                            for (TrendingProductEntity entity : topEntities) {
                                                allIds.add(entity.productId);
                                            }
                                        }

                                        if (!allIds.isEmpty()) {
                                            int dealsEndIndex = Math.min(10, allIds.size());
                                            List<String> dealIds = new ArrayList<>(allIds.subList(0, dealsEndIndex));

                                            repository.getProducts(dealIds, new ProductRepository.ProductsCallback() {
                                                @Override
                                                public void onSuccess(List<Product> products) {
                                                    deals.postValue(products);
                                                }
                                                @Override
                                                public void onFailure(Exception error) {
                                                    deals.postValue(new ArrayList<>());
                                                }
                                            });

                                            if (allIds.size() > 10) {
                                                allTrendingIds = allIds;
                                                nextTrendingIndex = 10;
                                                loadedTrending.clear();
                                                isEndOfTrending.postValue(false);
                                                loadNextTrendingPage();
                                            } else {
                                                isEndOfTrending.postValue(true);
                                            }
                                        } else {
                                            deals.postValue(new ArrayList<>());
                                            isEndOfTrending.postValue(true);
                                        }
                                    }).start();
                                } else {
                                    deals.postValue(new ArrayList<>());
                                    isEndOfTrending.postValue(true);
                                }
                            } else {
                                deals.postValue(new ArrayList<>());
                                isEndOfTrending.postValue(true);
                            }
                        })
                        .addOnFailureListener(e -> {
                            deals.postValue(new ArrayList<>());
                            banners.postValue(new ArrayList<>());
                            isEndOfTrending.postValue(true);
                        });
            }

            @Override
            public void onAddressNotFound() {
                addressNotFoundEvent.postValue(true);
            }

            @Override
            public void onFailure(Exception error) {
                addressNotFoundEvent.postValue(true);
            }
        });
    }

    // ==========================================
    // FETCH BANNERS AND CATEGORIES
    // ==========================================
    private void fetchBannersAndCategories() {
        // 2. Categories ko callback ke sath fetch karke LiveData mein post karein
        repository.getCategories(new ProductRepository.CategoriesCallback() {
            @Override
            public void onSuccess(List<Category> categoryList) {
                categories.postValue(categoryList); // Yahan data LiveData ko milega
            }

            @Override
            public void onFailure(Exception error) {
                error.printStackTrace();
            }
        });
    }

    private String calculateDynamicTimeSlot(List<DeliverySlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return "Standard Delivery Available";
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);

        for (DeliverySlot slot : slots) {
            if (currentHour >= slot.getStartHour() && currentHour < slot.getEndHour()) {
                if (slot.getDayOffset() > 0) {
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, slot.getDayOffset());
                }

                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault());
                String dateStr = dateFormat.format(calendar.getTime());
                String dayPrefix = (slot.getDayOffset() == 0) ? "Today" : "Tomorrow";

                return dayPrefix + ", " + dateStr + ", " + slot.getTimeText();
            }
        }

        return "Standard Delivery Available";
    }

    // ==========================================
    // STEP 4: TRENDING PAGINATION METHOD
    // ==========================================
    public void loadNextTrendingPage() {
        if (isLoadingTrending || allTrendingIds.isEmpty()) return;

        // Agar saare trending items khatam ho chuke hain
        if (nextTrendingIndex >= allTrendingIds.size()) {
            isEndOfTrending.postValue(true);
            return;
        }

        isLoadingTrending = true;

        // Agle 10 items ki sublist nikalna (Pagination range)
        int endIndex = Math.min(nextTrendingIndex + PAGE_SIZE, allTrendingIds.size());
        List<String> pageIds = new ArrayList<>(allTrendingIds.subList(nextTrendingIndex, endIndex));

        // Repository ka use karke pagination page fetch karna[cite: 1, 2]
        repository.getProducts(pageIds, new ProductRepository.ProductsCallback() {
            @Override
            public void onSuccess(List<Product> products) {
                loadedTrending.addAll(products);
                trending.postValue(new ArrayList<>(loadedTrending)); // Trending list update ki gayi

                nextTrendingIndex = endIndex; // Next index update kiya gaya
                isLoadingTrending = false;

                // Check karein ki list khatam ho gayi hai ya nahi
                if (nextTrendingIndex >= allTrendingIds.size()) {
                    isEndOfTrending.postValue(true);
                }
            }

            @Override
            public void onFailure(Exception error) {
                isLoadingTrending = false;
                error.printStackTrace();
            }
        });
    }
    public void refreshData() {
        isStartupDone = false;
        fetchUserAddressAndData();
    }
    public void incrementClicks(String productId) {
        repository.recordProductClick(productId);
    }
}