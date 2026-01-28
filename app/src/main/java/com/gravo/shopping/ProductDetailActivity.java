package com.gravo.shopping;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.viewmodel.ProductViewModel;

import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    // Dependencies
    private FirebaseFirestore db; // Kept only for reading Product Details (Phase 4 Refactor)
    private ProductViewModel viewModel; // MVVM: Handles Cart Logic

    // Data
    private String productId;

    // UI Components
    private ViewPager2 productImagesViewPager;
    private TabLayout viewPagerIndicator;
    private TextView addToCartBtn, buyNowBtn;
    private LinearLayout quantitySelector;
    private TextView tvQuantity;
    private View btnMinus, btnPlus;
    private TextView productPriceTextView, productDescriptionTextView;

    // Layout containers for animations
    private AppBarLayout appBarLayout;
    private View toolbarContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_detail);

        // Handle System Bars (Edge to Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Initialize UI & Dependencies
        initViews();
        db = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        // 2. Get Product ID
        productId = getIntent().getStringExtra("PRODUCT_ID");
        if (productId == null) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 3. MVVM: Start Monitoring Cart Status
        // This tells the ViewModel to start listening to Firestore for this product
        viewModel.monitorCartStatus(productId);

        // 4. MVVM: Observe LiveData
        observeViewModel();

        // 5. Setup UI Logic
        setupToolbarFadeEffect();
        setupButtons();

        // 6. Fetch Product Data (Visuals)
        // Note: Ideally, this read logic should also move to ViewModel in the future
        fetchProductDetails(productId);
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

    /**
     * MVVM Pattern: Observe changes from the ViewModel.
     * The Activity does NOT calculate logic, it just reacts to data changes.
     */
    private void observeViewModel() {
        // Observer 1: Watch Cart Quantity
        viewModel.getCartQuantity().observe(this, qty -> {
            // Update the Quantity Text
            tvQuantity.setText(String.valueOf(qty));

            // Toggle Visibility based on quantity
            if (qty > 0) {
                // Item is in cart -> Show Selector, Hide 'Add' button
                addToCartBtn.setVisibility(View.GONE);
                quantitySelector.setVisibility(View.VISIBLE);
            } else {
                // Item not in cart -> Show 'Add' button, Hide Selector
                addToCartBtn.setVisibility(View.VISIBLE);
                quantitySelector.setVisibility(View.GONE);
            }
        });

        // Observer 2: Watch for Errors (Toast messages)
        viewModel.getToastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        // Buy Now - Navigation logic stays in Activity
        buyNowBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ProductDetailActivity.this, OrderSummaryActivity.class);
            intent.putExtra("PRODUCT_ID", productId);
            startActivity(intent);
        });

        // --- MVVM CLICK LISTENERS ---
        // We do NOT update the UI here manually. We just ask the ViewModel to do work.

        // 1. Add to Cart (Sets quantity to 1)
        addToCartBtn.setOnClickListener(v -> viewModel.setQuantity(productId, 1));

        // 2. Increase Quantity
        btnPlus.setOnClickListener(v -> {
            // Get safe current value
            int current = viewModel.getCartQuantity().getValue() != null ? viewModel.getCartQuantity().getValue() : 0;
            viewModel.setQuantity(productId, current + 1);
        });

        // 3. Decrease Quantity
        btnMinus.setOnClickListener(v -> {
            int current = viewModel.getCartQuantity().getValue() != null ? viewModel.getCartQuantity().getValue() : 0;
            if (current > 0) {
                // If 1 -> 0, Repository will handle deletion automatically
                viewModel.setQuantity(productId, current - 1);
            }
        });
    }

    /**
     * UI Polish: Fades the toolbar elements as the user scrolls up.
     */
    private void setupToolbarFadeEffect() {
        final float FADE_SPEED = 2.5f;

        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScrollRange = appBarLayout.getTotalScrollRange();
            float standardPercentage = (float) Math.abs(verticalOffset) / totalScrollRange;

            float alpha = 1 - (standardPercentage * FADE_SPEED);
            if (alpha < 0) alpha = 0;

            toolbarContent.setAlpha(alpha);
        });
    }

    // --- OLD MVC LOGIC (For Reading Data) ---
    // We kept this here to ensure the page still loads images/specs correctly
    // while we focus on fixing the Cart Logic first.
    private void fetchProductDetails(String id) {
        db.collection("products").document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Product product = documentSnapshot.toObject(Product.class);

                        if (product != null) {
                            // 1. Basic Info
                            TextView brandTextView = findViewById(R.id.productBrand);
                            String brand = product.getBrand() != null ? product.getBrand() : "";
                            brandTextView.setText(brand);

                            TextView nameTextView = findViewById(R.id.productNameTextView);
                            String name = product.getName() != null ? product.getName() : "";
                            nameTextView.setText(name);

                            // 2. Pricing & Discount Logic
                            double sellingPrice = product.getPrice();
                            double discountPercent = product.getDiscountPercent();

                            productPriceTextView.setText("₹" + String.format("%.0f", sellingPrice));

                            TextView mrpTextView = findViewById(R.id.productMRP);
                            TextView discountTextView = findViewById(R.id.productDiscount);

                            if (discountPercent > 0) {
                                double mrp = (sellingPrice * 100) / (100 - discountPercent);
                                mrpTextView.setText("₹" + String.format("%.0f", mrp));
                                mrpTextView.setPaintFlags(mrpTextView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                                mrpTextView.setVisibility(View.VISIBLE);

                                discountTextView.setText((int) discountPercent + "% off");
                                discountTextView.setVisibility(View.VISIBLE);
                            } else {
                                mrpTextView.setVisibility(View.GONE);
                                discountTextView.setVisibility(View.GONE);
                            }

                            // 3. Description & Images
                            productDescriptionTextView.setText(product.getDescription());

                            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                                ProductImagesAdapter adapter = new ProductImagesAdapter(this, product.getImageUrls());
                                productImagesViewPager.setAdapter(adapter);
                                new TabLayoutMediator(viewPagerIndicator, productImagesViewPager, (tab, position) -> {}).attach();
                            }

                            // 4. Dynamic Specifications
                            displaySpecifications(product);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching product", e));
    }

    private void displaySpecifications(Product product) {
        LinearLayout specsContainer = findViewById(R.id.specificationsContainer);
        Map<String, String> specsMap = product.getSpecifications();

        if (specsMap == null || specsMap.isEmpty()) {
            specsContainer.setVisibility(View.GONE);
            return;
        }

        specsContainer.setVisibility(View.VISIBLE);

        // Keep header, remove old rows
        int childCount = specsContainer.getChildCount();
        if (childCount > 1) {
            specsContainer.removeViews(1, childCount - 1);
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (Map.Entry<String, String> entry : specsMap.entrySet()) {
            View rowView = inflater.inflate(R.layout.item_spec, specsContainer, false);

            TextView keyText = rowView.findViewById(R.id.specKey);
            TextView valueText = rowView.findViewById(R.id.specValue);

            String key = entry.getKey();
            String value = entry.getValue();

            if (key != null && !key.isEmpty()) {
                keyText.setText(key.substring(0, 1).toUpperCase() + key.substring(1));
            } else {
                keyText.setText(key);
            }
            valueText.setText(value);

            specsContainer.addView(rowView);
        }
    }
}