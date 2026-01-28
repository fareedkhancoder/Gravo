package com.gravo.shopping.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.gravo.shopping.data.repository.ProductRepository;

public class ProductViewModel extends ViewModel {

    private final ProductRepository repository;
    private final MutableLiveData<Integer> cartQuantity = new MutableLiveData<>(0);
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

    public ProductViewModel() {
        repository = new ProductRepository();
    }

    // Activity calls this when it starts
    public void monitorCartStatus(String productId) {
        repository.checkCartStatus(productId, cartQuantity);
    }

    // Activity calls this when "+" or "-" is clicked
    public void setQuantity(String productId, int newQuantity) {
        repository.updateCartQuantity(productId, newQuantity, new ProductRepository.CartCallback() {
            @Override
            public void onSuccess() {
                // UI updates automatically via the 'monitorCartStatus' listener
            }

            @Override
            public void onFailure(String error) {
                toastMessage.postValue(error);
            }
        });
    }

    public LiveData<Integer> getCartQuantity() { return cartQuantity; }
    public LiveData<String> getToastMessage() { return toastMessage; }
}