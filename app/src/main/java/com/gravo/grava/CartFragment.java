package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class CartFragment extends Fragment {

    private static final String TAG = "CartFragment";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private RecyclerView cartRecyclerView;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;
    private TextView totalPriceTextView;
    MaterialButton placeOrderButton;
    private double totalPrice = 0.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cart, container, false);



        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        cartItemList = new ArrayList<>();
        cartRecyclerView = view.findViewById(R.id.cartRecyclerView);
        totalPriceTextView = view.findViewById(R.id.totalPriceTextView);
        placeOrderButton = view.findViewById(R.id.placeOrderButton);

        setupRecyclerView();
        fetchCartItems();

        // CartFragment.java ke onViewCreated method mein

        placeOrderButton.setOnClickListener(v -> {
            if (cartItemList == null || cartItemList.isEmpty()) {
                Toast.makeText(getContext(), "Your cart is empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Step 1: Saare product IDs ki ek ArrayList banayein
            ArrayList<String> productIds = new ArrayList<>();
            for (CartItem item : cartItemList) {
                productIds.add(item.getProduct().getProductId());
            }

            // Step 2: Intent ke saath is ArrayList ko bhejein
            Intent intent = new Intent(getContext(), OrderSummaryActivity.class);
            intent.putStringArrayListExtra("CART_PRODUCT_IDS", productIds);
            startActivity(intent);
        });
    }


    private void setupRecyclerView() {
        cartRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        cartAdapter = new CartAdapter(getContext(), cartItemList);
        cartRecyclerView.setAdapter(cartAdapter);
    }

    private void fetchCartItems() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in to see your cart.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = currentUser.getUid();

        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && getContext() != null) {
                        cartItemList.clear();
                        totalPrice = 0.0;
                        if (task.getResult().isEmpty()) {
                            Toast.makeText(getContext(), "Your cart is empty.", Toast.LENGTH_SHORT).show();
                            updateTotalPrice();
                        }

                        for (QueryDocumentSnapshot cartDoc : task.getResult()) {
                            String productId = cartDoc.getId();
                            long quantityLong = cartDoc.contains("quantity") ? cartDoc.getLong("quantity") : 0;
                            int quantity = (int) quantityLong;

                            db.collection("products").document(productId).get()
                                    .addOnSuccessListener(productDoc -> {
                                        if (productDoc.exists() && getContext() != null) {
                                            Product product = productDoc.toObject(Product.class);
                                            product.setProductId(productDoc.getId());

                                            cartItemList.add(new CartItem(product, quantity));

                                            totalPrice += product.getPrice() * quantity;
                                            updateTotalPrice();

                                            if(cartAdapter != null) cartAdapter.notifyDataSetChanged();
                                        }
                                    })
                                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching product details", e));
                        }
                    } else {
                        Log.e(TAG, "Error fetching cart items", task.getException());
                    }
                });
    }

    private void updateTotalPrice() {
        if(totalPriceTextView != null){
            totalPriceTextView.setText("₹" + String.format("%.2f", totalPrice));
        }
    }
}
