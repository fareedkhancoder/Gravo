package com.gravo.grava.data.repository;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.gravo.grava.Order;

import java.util.ArrayList;
import java.util.List;

public class OrderRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "OrderRepository";

    // Calls Firestore and updates the LiveData provided by the ViewModel
    public void getOrders(String userId, MutableLiveData<List<Order>> ordersLiveData, MutableLiveData<String> errorLiveData) {
        db.collection("orders")
                .whereEqualTo("userId", userId)
                .orderBy("orderDate", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Order> orderList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Order order = document.toObject(Order.class);
                            order.setOrderId(document.getId());
                            orderList.add(order);
                        }
                        // Success: Post the data to LiveData
                        ordersLiveData.postValue(orderList);
                    } else {
                        Log.e(TAG, "Error getting orders: ", task.getException());
                        // Failure: Post error message
                        errorLiveData.postValue(task.getException().getMessage());
                    }
                });
    }
}