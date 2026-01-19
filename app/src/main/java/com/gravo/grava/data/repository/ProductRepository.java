package com.gravo.grava.data.repository;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ProductRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // Callback interface for Write operations (Success/Fail)
    public interface CartCallback {
        void onSuccess();
        void onFailure(String error);
    }

    // Check if product is already in cart (to toggle "Add" vs "Qty")
    public void checkCartStatus(String productId, MutableLiveData<Integer> qtyLiveData) {
        if (auth.getCurrentUser() == null) return;

        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("cart").document(productId)
                .addSnapshotListener((doc, e) -> {
                    if (doc != null && doc.exists()) {
                        // If item exists, post its quantity
                        Long qty = doc.getLong("quantity");
                        qtyLiveData.postValue(qty != null ? qty.intValue() : 0);
                    } else {
                        // Item not in cart
                        qtyLiveData.postValue(0);
                    }
                });
    }

    // Add or Update Cart
    public void updateCartQuantity(String productId, int quantity, CartCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onFailure("User not logged in");
            return;
        }

        if (quantity == 0) {
            // Remove item
            db.collection("users").document(auth.getCurrentUser().getUid())
                    .collection("cart").document(productId)
                    .delete()
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } else {
            // Add/Update item
            Map<String, Object> cartItem = new HashMap<>();
            cartItem.put("quantity", quantity);
            cartItem.put("productId", productId);
            cartItem.put("timestamp", System.currentTimeMillis());

            db.collection("users").document(auth.getCurrentUser().getUid())
                    .collection("cart").document(productId)
                    .set(cartItem)
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        }
    }
}