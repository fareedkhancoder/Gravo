package com.gravo.shopping.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.gravo.shopping.data.repository.ProductRepository;
import com.gravo.shopping.Product;
import com.gravo.shopping.MyApplication;

public class ProductViewModel extends ViewModel {

    private final ProductRepository repository;
    private ListenerRegistration productListener;
    private final MutableLiveData<Integer> cartQuantity = new MutableLiveData<>(0);
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

    public ProductViewModel() {
        repository = MyApplication.getProductRepository();
    }

    // Targeted Real-time Snapshot Listener
    public void startListeningToProduct(String productId) {
        if (productListener != null) {
            productListener.remove(); // Purana listener clean karein
        }

        productListener = repository.listenToProduct(productId, new ProductRepository.ProductCallback() {
            @Override
            public void onSuccess(Product product) {
                // Room DB background mein update ho gaya hai
            }

            @Override
            public void onFailure(Exception error) {
                toastMessage.postValue("Failed to sync product updates");
            }
        });
    }

    // Room DB Observation for UI rendering
    public LiveData<Product> observeProduct(String productId) {
        return repository.observeProduct(productId);
    }

    // Monitor Cart Status in Real-time
    public void monitorCartStatus(String productId) {
        repository.checkCartStatus(productId, cartQuantity);
    }

    // Handle Cart Addition / Removal / Quantity Update
    public void setQuantity(String productId, int newQuantity) {
        repository.updateCartQuantity(productId, newQuantity, new ProductRepository.CartCallback() {
            @Override
            public void onSuccess() {
                // UI 'monitorCartStatus' ke zariye automatically update ho jayega
            }

            @Override
            public void onFailure(String error) {
                toastMessage.postValue(error);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Screen destroy hote hi snapshot listener remove hoga (Cost & Memory Safety)
        if (productListener != null) {
            productListener.remove();
            productListener = null;
        }
    }

    public LiveData<Integer> getCartQuantity() { return cartQuantity; }
    public LiveData<String> getToastMessage() { return toastMessage; }
}