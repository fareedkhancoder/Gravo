package com.gravo.grava;// ProductDetailActivity.java

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String productId;

    private ImageView productImageView;
    private MaterialButton cart, buy;
    private TextView productNameTextView, productPriceTextView, productDescriptionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        productImageView = findViewById(R.id.productImageViewDetail);
        productNameTextView = findViewById(R.id.productNameTextViewDetail);
        productPriceTextView = findViewById(R.id.productPriceTextViewDetail); // Added initialization
        productDescriptionTextView = findViewById(R.id.productDescriptionTextViewDetail); // Added initialization
        buy = findViewById(R.id.buyNowButton);
        cart = findViewById(R.id.addToCartButton);

        db = FirebaseFirestore.getInstance();
        productId = getIntent().getStringExtra("PRODUCT_ID");
        Log.d(TAG, "Received Product ID: " + productId);


        if (productId != null) {
            fetchProductDetails(productId);
        }
        else {
            Log.e(TAG, "Error: Product ID is null!");
            Toast.makeText(this, "Could not find product", Toast.LENGTH_SHORT).show();
        }

        buy.setOnClickListener(v -> {
            Intent intent = new Intent(ProductDetailActivity.this, OrderSummaryActivity.class);
            intent.putExtra("PRODUCT_ID", productId);
            startActivity(intent);
        });
        cart.setOnClickListener(v -> {
            addProductToCart();
        });

    }
    private void addProductToCart() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Step 1: Check karein ki user logged in hai ya nahin
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to add items to your cart", Toast.LENGTH_SHORT).show();
            // Yahan aap user ko login screen par bhej sakte hain
            return;
        }

        // Step 2: User ki unique ID prapt karein
        String userId = currentUser.getUid();

        // Step 3: Naya cart item banayein
        Map<String, Object> cartItem = new HashMap<>();
        cartItem.put("quantity", 1);

        // Step 4: Item ko user ke cart sub-collection mein add karein
        db.collection("users").document(userId)
                .collection("cart").document(productId) // Product ID ko document ID banate hain
                .set(cartItem) // .set() ka istemal karte hain taaki item dobara add na ho
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Product added to cart successfully!");
                    Toast.makeText(ProductDetailActivity.this, "Added to Cart", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error adding product to cart", e);
                    Toast.makeText(ProductDetailActivity.this, "Failed to add to cart", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchProductDetails(String id) {
        Log.d(TAG, "Fetching details for product ID: " + id);
        db.collection("products").document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "Firestore query successful.");
                    if (documentSnapshot.exists()) {
                        Log.i(TAG, "SUCCESS: Document exists!");
                        Product product = documentSnapshot.toObject(Product.class);
                        if (product != null) {
                            Log.d(TAG, "Product Name from Firestore: " + product.name);
                            productNameTextView.setText(product.name);
                            productPriceTextView.setText("₹" + String.format("%.2f", product.price));
                            productDescriptionTextView.setText(product.description);

                            if (product.imageUrls != null && !product.imageUrls.isEmpty()) {
                                Glide.with(this).load(product.imageUrls.get(0)).into(productImageView);
                            }
                        }else{
                            Log.e(TAG, "ERROR: Product object is null. Check if field names in your Product.java class and Firestore document match.");
                        }
                    }else{
                        Log.e(TAG, "ERROR: Document does not exist for ID: " + id);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "FAILURE: Firestore query failed.", e);
                });
    }
}