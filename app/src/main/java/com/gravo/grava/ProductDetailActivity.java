package com.gravo.grava;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String productId;

    // Image Slider
    private ViewPager2 productImagesViewPager;
    private TabLayout viewPagerIndicator;

    // Cart Logic
    private TextView addToCartBtn, buyNowBtn;
    private LinearLayout quantitySelector;
    private TextView tvQuantity;
    private View btnMinus, btnPlus;

    // Product Details
    private TextView productNameTextView, productPriceTextView, productDescriptionTextView;

    // Layout containers
    private AppBarLayout appBarLayout;
    private View toolbarContent;

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

        // 1. Initialize Views
        initViews();

        // 2. Setup Toolbar Fading Effect
        setupToolbarFadeEffect();

        // 3. Setup Firebase
        db = FirebaseFirestore.getInstance();
        productId = getIntent().getStringExtra("PRODUCT_ID");

        if (productId != null) {
            fetchProductDetails(productId);
        } else {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 4. Setup Click Listeners
        setupButtons();
    }

    private void initViews() {
        appBarLayout = findViewById(R.id.appBarLayout);
        toolbarContent = findViewById(R.id.toolbar_content);

        productImagesViewPager = findViewById(R.id.productImagesViewPager);
        viewPagerIndicator = findViewById(R.id.viewPagerIndicator);
        productPriceTextView = findViewById(R.id.productPriceTextViewDetail);
        productDescriptionTextView = findViewById(R.id.productDescriptionTextViewDetail);

        buyNowBtn = findViewById(R.id.buyNowButton);
        addToCartBtn = findViewById(R.id.addToCartButton);
        quantitySelector = findViewById(R.id.quantitySelector);
        btnMinus = findViewById(R.id.minusButton);
        btnPlus = findViewById(R.id.PlusBtn);
        tvQuantity = findViewById(R.id.quantityTextView);
    }

    private void setupToolbarFadeEffect() {
        final float FADE_SPEED = 2.5f; // Adjusted for a nice snappy fade

        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScrollRange = appBarLayout.getTotalScrollRange();
            float standardPercentage = (float) Math.abs(verticalOffset) / totalScrollRange;

            // Calculate alpha (starts at 1.0, goes to 0.0)
            float alpha = 1 - (standardPercentage * FADE_SPEED);

            // Prevent negative alpha
            if (alpha < 0) alpha = 0;

            toolbarContent.setAlpha(alpha);
        });
    }

    private void fetchProductDetails(String id) {
        db.collection("products").document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Product product = documentSnapshot.toObject(Product.class);

                        if (product != null) {
                            // --- 1. BRAND ---
                            TextView brandTextView = findViewById(R.id.productBrand);
                            // Handle null brand gracefully
                            String brand = product.getBrand() != null ? product.getBrand() : "";
                            brandTextView.setText(brand);

                            TextView nameTextView = findViewById(R.id.productNameTextView);
                            String name = product.getName() != null ? product.getName() : "";
                            nameTextView.setText(name);



                            // --- 2. PRICING LOGIC ---
                            // Your model has 'price' (Selling Price) and 'discountPercent'
                            double sellingPrice = product.getPrice();
                            double discountPercent = product.getDiscountPercent();

                            // Set Final Selling Price
                            productPriceTextView.setText("₹" + String.format("%.0f", sellingPrice));

                            TextView mrpTextView = findViewById(R.id.productMRP);
                            TextView discountTextView = findViewById(R.id.productDiscount);

                            if (discountPercent > 0) {
                                // Calculate Original MRP based on discount
                                // Formula: MRP = SellingPrice * 100 / (100 - Discount%)
                                double mrp = (sellingPrice * 100) / (100 - discountPercent);

                                // Show MRP with Strikethrough
                                mrpTextView.setText("₹" + String.format("%.0f", mrp));
                                mrpTextView.setPaintFlags(mrpTextView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                                mrpTextView.setVisibility(View.VISIBLE);

                                // Show Discount Percentage
                                discountTextView.setText((int) discountPercent + "% off");
                                discountTextView.setVisibility(View.VISIBLE);
                            } else {
                                // No discount: Hide MRP and Offer Tag
                                mrpTextView.setVisibility(View.GONE);
                                discountTextView.setVisibility(View.GONE);
                            }

                            // --- 3. DESCRIPTION ---
                            productDescriptionTextView.setText(product.getDescription());

                            // --- 4. IMAGE SLIDER ---
                            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                                ProductImagesAdapter adapter = new ProductImagesAdapter(this, product.getImageUrls());
                                productImagesViewPager.setAdapter(adapter);
                                new TabLayoutMediator(viewPagerIndicator, productImagesViewPager, (tab, position) -> {}).attach();
                            }

                            // --- 5. SPECIFICATIONS ---
                            displaySpecifications(product);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching product", e));
    }

    private void displaySpecifications(Product product) {
        LinearLayout specsContainer = findViewById(R.id.specificationsContainer);

        // 1. Get the map directly from the Product model
        Map<String, String> specsMap = product.getSpecifications();

        // 2. Check if null or empty
        if (specsMap == null || specsMap.isEmpty()) {
            specsContainer.setVisibility(View.GONE);
            return;
        }

        specsContainer.setVisibility(View.VISIBLE);

        // 3. Clear old dynamic rows, keep the Header (Index 0)
        // We check childCount > 1 so we don't accidentally remove the "Product Highlights" header
        int childCount = specsContainer.getChildCount();
        if (childCount > 1) {
            specsContainer.removeViews(1, childCount - 1);
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        // 4. Iterate and Inflate
        for (Map.Entry<String, String> entry : specsMap.entrySet()) {
            // Inflate the row layout
            View rowView = inflater.inflate(R.layout.layout_specification_item, specsContainer, false);

            TextView keyText = rowView.findViewById(R.id.specKey);
            TextView valueText = rowView.findViewById(R.id.specValue);

            String key = entry.getKey();
            String value = entry.getValue();

            // Logic to Capitalize First Letter of Key safely
            if (key != null && !key.isEmpty()) {
                keyText.setText(key.substring(0, 1).toUpperCase() + key.substring(1));
            } else {
                keyText.setText(key);
            }

            // Set Value
            valueText.setText(value);

            specsContainer.addView(rowView);
        }
    }

    private void setupButtons() {
        buyNowBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ProductDetailActivity.this, OrderSummaryActivity.class);
            intent.putExtra("PRODUCT_ID", productId);
            startActivity(intent);
        });

        // Current Quantity Tracker
        final int[] currentQuantity = {1};

        addToCartBtn.setOnClickListener(v -> {
            // Switch UI to Quantity Selector
            addToCartBtn.setVisibility(View.GONE);
            quantitySelector.setVisibility(View.VISIBLE);

            currentQuantity[0] = 1;
            tvQuantity.setText(String.valueOf(currentQuantity[0]));

            addProductToCart(currentQuantity[0]);
        });

        btnPlus.setOnClickListener(v -> {
            currentQuantity[0]++;
            tvQuantity.setText(String.valueOf(currentQuantity[0]));
            updateCartQuantity(currentQuantity[0]);
        });

        btnMinus.setOnClickListener(v -> {
            if (currentQuantity[0] > 1) {
                currentQuantity[0]--;
                tvQuantity.setText(String.valueOf(currentQuantity[0]));
                updateCartQuantity(currentQuantity[0]);
            } else {
                // Remove from cart logic
                quantitySelector.setVisibility(View.GONE);
                addToCartBtn.setVisibility(View.VISIBLE);
                removeCartItem(); // Helper function
            }
        });
    }

    private void addProductToCart(int quantity) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> cartItem = new HashMap<>();
        cartItem.put("quantity", quantity);
        cartItem.put("productId", productId);
        cartItem.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(currentUser.getUid())
                .collection("cart").document(productId)
                .set(cartItem)
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add to cart", Toast.LENGTH_SHORT).show());
    }

    private void updateCartQuantity(int quantity) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .collection("cart").document(productId)
                    .update("quantity", quantity);
        }
    }

    private void removeCartItem() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .collection("cart").document(productId)
                    .delete();
        }
    }
}