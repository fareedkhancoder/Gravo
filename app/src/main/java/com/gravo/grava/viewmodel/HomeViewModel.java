package com.gravo.grava.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.firestore.DocumentSnapshot;
import com.gravo.grava.Banner;
import com.gravo.grava.Category;
import com.gravo.grava.Product;
import com.gravo.grava.data.repository.HomeRepository;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final HomeRepository repository;

    // LiveData for UI
    private final MutableLiveData<List<Banner>> banners = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Product>> deals = new MutableLiveData<>();
    private final MutableLiveData<List<Product>> trending = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isEndOfList = new MutableLiveData<>(false);

    // Pagination State
    private final List<Product> currentTrendingList = new ArrayList<>();
    private DocumentSnapshot lastVisibleProduct;
    private boolean isTrendingLoading = false;
    private boolean isLastPage = false;

    public HomeViewModel() {
        repository = new HomeRepository();
        loadInitialData();
    }
    public LiveData<Boolean> getIsEndOfList() {
        return isEndOfList;
    }

    private void loadInitialData() {
        repository.getBanners(banners);
        repository.getCategories(categories);
        repository.getDeals(deals);
        loadNextTrendingPage(); // Load first page of trending
    }

    public void loadNextTrendingPage() {
        if (isTrendingLoading || isLastPage) return;

        isTrendingLoading = true;

        // Optional: Show shimmer when starting to load (if you want strict control)
        // isLoading.postValue(true);

        repository.getTrendingProducts(lastVisibleProduct, new HomeRepository.TrendingCallback() {
            @Override
            public void onSuccess(List<Product> newProducts, DocumentSnapshot lastVisible) {
                if (newProducts.isEmpty()) {
                    isLastPage = true;
                    // 3. Notify the UI that we reached the end
                    isEndOfList.postValue(true);
                } else {
                    currentTrendingList.addAll(newProducts);
                    trending.postValue(currentTrendingList);
                    lastVisibleProduct = lastVisible;
                }
                isTrendingLoading = false;
            }

            @Override
            public void onError(Exception e) {
                isTrendingLoading = false;
            }
        });
    }



    public void incrementClicks(String productId) {
        repository.incrementClicks(productId);
    }

    // Getters
    public LiveData<List<Banner>> getBanners() { return banners; }
    public LiveData<List<Category>> getCategories() { return categories; }
    public LiveData<List<Product>> getDeals() { return deals; }
    public LiveData<List<Product>> getTrending() { return trending; }
}