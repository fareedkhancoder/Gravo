package com.gravo.grava.data.repository;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.gravo.grava.Address;
import com.gravo.grava.Banner;
import com.gravo.grava.Category;
import com.gravo.grava.Product;

import java.util.ArrayList;
import java.util.List;

public class HomeRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "HomeRepository";

    // 1. Fetch Banners
    public void getBanners(MutableLiveData<List<Banner>> liveData) {
        db.collection("Banners").get().addOnSuccessListener(snapshots -> {
            List<Banner> list = snapshots.toObjects(Banner.class);
            liveData.postValue(list);
        }).addOnFailureListener(e -> Log.e(TAG, "Banners error", e));
    }

    // 2. Fetch Categories
    public void getCategories(MutableLiveData<List<Category>> liveData) {
        db.collection("categories").orderBy("name").get().addOnSuccessListener(snapshots -> {
            List<Category> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                Category c = doc.toObject(Category.class);
                c.setId(doc.getId()); // Helper to ensure ID is set
                list.add(c);
            }
            liveData.postValue(list);
        }).addOnFailureListener(e -> Log.e(TAG, "Categories error", e));
    }

    public void incrementClicks(String productId) {
        if (productId != null) {
            FirebaseFirestore.getInstance()
                    .collection("products")
                    .document(productId)
                    .update("clicks", FieldValue.increment(1));
        }
    }

    // 3. Fetch Deals (Assuming 'is_new' or specific query)
    // Purana getDeals hata kar ye naya wala lagayein
    public void getDeals(DocumentSnapshot startAfter, TrendingCallback callback) {
        Query query = db.collection("products")
                .orderBy("clicks", Query.Direction.DESCENDING)
                .limit(10); // Batch size

        // Pagination Logic: Agar purana data hai, toh uske aage se shuru karo
        if (startAfter != null) {
            query = query.startAfter(startAfter);
        }

        query.get().addOnSuccessListener(snapshots -> {
            List<Product> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                Product p = doc.toObject(Product.class);
                p.setProductId(doc.getId());
                list.add(p);
            }

            // Aakhri document dhoondo (Next batch ke liye)
            DocumentSnapshot lastVisible = null;
            if (!snapshots.isEmpty()) {
                lastVisible = snapshots.getDocuments().get(snapshots.size() - 1);
            }

            // Data wapas bhejo
            callback.onSuccess(list, lastVisible);

        }).addOnFailureListener(callback::onError);
    }

    public void getDefaultAddress(String userId, MutableLiveData<Address> liveData) {
        db.collection("users").document(userId).collection("addresses")
                .whereEqualTo("default", true)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        Address address = snapshots.getDocuments().get(0).toObject(Address.class);
                        liveData.postValue(address);
                    } else {
                        liveData.postValue(null); // No default address found
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Address error", e));
    }

    // 4. Fetch Trending (With Pagination)
    // We need a custom callback interface here because we need to return both Data AND the Last Document
    public interface TrendingCallback {
        void onSuccess(List<Product> products, DocumentSnapshot lastVisible);
        void onError(Exception e);
    }

    public void getTrendingProducts(DocumentSnapshot startAfter, TrendingCallback callback) {
        Query query = db.collection("products").limit(10);

        if (startAfter != null) {
            query = query.startAfter(startAfter);
        }

        query.get().addOnSuccessListener(snapshots -> {
            List<Product> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                Product p = doc.toObject(Product.class);
                p.setProductId(doc.getId());
                list.add(p);
            }

            DocumentSnapshot lastVisible = null;
            if (!snapshots.isEmpty()) {
                lastVisible = snapshots.getDocuments().get(snapshots.size() - 1);
            }

            callback.onSuccess(list, lastVisible);
        }).addOnFailureListener(callback::onError);
    }
}