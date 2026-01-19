package com.gravo.grava.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.gravo.grava.Order;
import com.gravo.grava.data.repository.OrderRepository;
import java.util.List;

public class OrdersViewModel extends ViewModel {

    private final OrderRepository repository;
    private final MutableLiveData<List<Order>> orders = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    public OrdersViewModel() {
        repository = new OrderRepository();
    }

    // This method is called by the UI
    public void loadOrders(String userId) {
        isLoading.setValue(true);
        // We pass the LiveData to the repo so it can update them
        repository.getOrders(userId, orders, errorMessage);
    }

    // Getters for the UI to observe
    public LiveData<List<Order>> getOrders() {
        return orders;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}