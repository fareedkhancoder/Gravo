package com.gravo.Meher.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.gravo.Meher.data.repository.ProductRepository;
import com.gravo.Meher.Product;
import com.gravo.Meher.ProductQuantityPolicy;
import com.gravo.Meher.MyApplication;

public class ProductViewModel extends ViewModel {

    private final ProductRepository repository;
    private ListenerRegistration productListener;
    private final MutableLiveData<Integer> cartQuantity = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> quantityUpdating = new MutableLiveData<>(false);
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
        if (Boolean.TRUE.equals(quantityUpdating.getValue())) return;
        int previousQty = cartQuantity.getValue() != null ? cartQuantity.getValue() : 0;
        Product product = observeProduct(productId).getValue();

        if (newQuantity < 0) {
            toastMessage.setValue("Quantity cannot be negative");
            return;
        }
        if (product != null && newQuantity > 0 && !ProductQuantityPolicy.canAdd(product, newQuantity)) {
            toastMessage.setValue(ProductQuantityPolicy.getLimitMessage(product));
            cartQuantity.setValue(previousQty);
            return;
        }

        quantityUpdating.setValue(true);

        repository.updateCartQuantity(productId, newQuantity, new ProductRepository.CartCallback() {
            @Override
            public void onSuccess() {
                cartQuantity.postValue(newQuantity);
                quantityUpdating.postValue(false);
            }

            @Override
            public void onFailure(String error) {
                // Keep the last confirmed quantity when persistence fails.
                cartQuantity.postValue(previousQty);
                toastMessage.postValue(error);
                quantityUpdating.postValue(false);
            }
        });
    }

    public void recordProductTimeSpent(String productId, long timeSpentMs) {
        if (repository != null) {
            repository.recordProductTimeSpent(productId, timeSpentMs);
        }
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
    public LiveData<Boolean> getQuantityUpdating() { return quantityUpdating; }
}