package com.gravo.Meher.data.repository;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.gravo.Meher.Order;

import java.util.ArrayList;
import java.util.List;

public class OrderRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "OrderRepository";

    public void getOrders(String userId, MutableLiveData<List<Order>> ordersLiveData, MutableLiveData<String> errorLiveData) {
        if (userId == null || userId.trim().isEmpty()) {
            if (errorLiveData != null) errorLiveData.postValue("User ID is required.");
            return;
        }

        // 1. Primary query: Fetch directly from user's own subcollection 'users/{userId}/orders'
        db.collection("users").document(userId).collection("orders")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Order> orderList = new ArrayList<>();
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Order order = document.toObject(Order.class);
                            order.setOrderId(document.getId());
                            orderList.add(order);
                        }
                    }

                    if (!orderList.isEmpty()) {
                        ordersLiveData.postValue(orderList);
                    } else {
                        // 2. Fallback query: Fetch from root 'orders' collection if subcollection is empty
                        fetchFromRootOrdersCollection(userId, ordersLiveData, errorLiveData);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Subcollection orders query failed, falling back to root orders collection", e);
                    fetchFromRootOrdersCollection(userId, ordersLiveData, errorLiveData);
                });
    }

    private void fetchFromRootOrdersCollection(String userId, MutableLiveData<List<Order>> ordersLiveData, MutableLiveData<String> errorLiveData) {
        db.collection("orders")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Order> orderList = new ArrayList<>();
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Order order = document.toObject(Order.class);
                            order.setOrderId(document.getId());
                            orderList.add(order);
                        }
                    }
                    ordersLiveData.postValue(orderList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching orders from root collection", e);
                    if (errorLiveData != null) {
                        errorLiveData.postValue(e.getMessage());
                    }
                });
    }
}
