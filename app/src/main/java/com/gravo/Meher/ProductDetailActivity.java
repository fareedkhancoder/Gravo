package com.gravo.Meher;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.gravo.Meher.data.repository.ProductRepository;
import com.gravo.Meher.local.LocalCartManager;
import com.gravo.Meher.viewmodel.ProductViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ProductDetailActivity extends AppCompatActivity {

    private ProductViewModel viewModel;
    private String productId;
    private long viewStartTime = 0;

    // UI Components
    private ViewPager2 productImagesViewPager;
    private TabLayout viewPagerIndicator;
    private TextView addToCartBtn;
    private LinearLayout quantitySelector;
    private TextView tvQuantity;
    private View btnMinus, btnPlus;
    private TextView productPriceTextView, productDescriptionTextView;

    private TextView productBrandTextView, productNameTextView;
    private TextView productMRPTextView, productDiscountTextView;
    private TextView tvStockStatusTitleTextView, tvStockStatusSubtitleTextView;
    private TextView tvDeliveryPincodeTextView;
    private TextView tvBottomBarPriceTextView, btnChangeAddressTextView;
    private ImageView btnBackImageView;
    private ImageView btnShareImageView, btnWishlistImageView, cartIconImageView;
    private View cartContainer;
    private TextView productRatingTextView, variantTitleTextView, sellerTagTextView, cartBadgeTextView;
    private LinearLayout ratingsReviewsContainer;
    private ProductRepository productRepository;
    private int lastProductQuantity;
    private String renderedVariantSignature;
    private Product currentProduct;
    private View variantLoadingOverlay;
    private final List<MaterialCardView> variantCards = new ArrayList<>();
    private final List<String> renderedVariantIds = new ArrayList<>();
    private final Map<String, Product> variantProductCache = new HashMap<>();

    private LinearLayout specificationsHeaderLayout;
    private LinearLayout specificationsContainer;
    private LinearLayout packagingOptionsLayout;
    private ImageView ivExpandSpecsImageView;

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

        initViews();

        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        productRepository = MyApplication.getProductRepository();

        productId = getIntent().getStringExtra("PRODUCT_ID");
        if (productId == null) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel.observeProduct(productId).observe(this, this::renderProduct);
        viewModel.monitorCartStatus(productId);
        observeViewModel();

        setupToolbarFadeEffect();
        setupButtons();

        viewModel.startListeningToProduct(productId);
        updateCartBadge();
        loadWishlistState();
        getSupportFragmentManager().setFragmentResultListener(
                "ADDRESS_UPDATED_KEY", this, (requestKey, result) -> loadDefaultAddressPincode());
        loadDefaultAddressPincode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewStartTime = System.currentTimeMillis();
        if (productId != null && viewModel != null) {
            viewModel.recordProductTimeSpent(productId, 3000L);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (productId != null && viewStartTime > 0 && viewModel != null) {
            long duration = System.currentTimeMillis() - viewStartTime;
            if (duration > 0) {
                viewModel.recordProductTimeSpent(productId, duration);
            }
        }
    }

    private void initViews() {
        appBarLayout = findViewById(R.id.appBarLayout);
        toolbarContent = findViewById(R.id.toolbar_content);
        btnBackImageView = findViewById(R.id.btnBack);

        productImagesViewPager = findViewById(R.id.productImagesViewPager);
        viewPagerIndicator = findViewById(R.id.viewPagerIndicator);

        productBrandTextView = findViewById(R.id.productBrand);
        productNameTextView = findViewById(R.id.productNameTextView);
        productPriceTextView = findViewById(R.id.productPriceTextViewDetail);
        productMRPTextView = findViewById(R.id.productMRP);
        productDiscountTextView = findViewById(R.id.productDiscount);
        productDescriptionTextView = findViewById(R.id.productDescriptionTextViewDetail);
        productRatingTextView = findViewById(R.id.productRating);
        ratingsReviewsContainer = findViewById(R.id.ratingsReviewsContainer);
        variantTitleTextView = findViewById(R.id.variantTitle);
        sellerTagTextView = findViewById(R.id.tvSellerTag);
        btnShareImageView = findViewById(R.id.btnShare);
        btnWishlistImageView = findViewById(R.id.btnWishlist);
        cartContainer = findViewById(R.id.cart_container);
        cartIconImageView = findViewById(R.id.cartIcon);
        cartBadgeTextView = findViewById(R.id.cartBadgeTextView);

        tvStockStatusTitleTextView = findViewById(R.id.tvStockStatusTitle);
        tvStockStatusSubtitleTextView = findViewById(R.id.tvStockStatusSubtitle);
        tvDeliveryPincodeTextView = findViewById(R.id.tvDeliveryPincode);
        btnChangeAddressTextView = findViewById(R.id.btnChangeAddress);
        tvBottomBarPriceTextView = findViewById(R.id.tvBottomBarPrice);

        specificationsHeaderLayout = findViewById(R.id.specificationsHeaderLayout);
        specificationsContainer = findViewById(R.id.specificationsContainer);
        packagingOptionsLayout = findViewById(R.id.packagingOptionsLayout);
        variantLoadingOverlay = findViewById(R.id.variantLoadingOverlay);
        ivExpandSpecsImageView = findViewById(R.id.ivExpandSpecs);

        addToCartBtn = findViewById(R.id.addToCartButton);
        quantitySelector = findViewById(R.id.quantitySelector);
        btnMinus = findViewById(R.id.minusButton);
        btnPlus = findViewById(R.id.PlusBtn);
        tvQuantity = findViewById(R.id.quantityTextView);

        if (btnBackImageView != null) {
            btnBackImageView.setOnClickListener(v -> finish());
        }
        if (btnChangeAddressTextView != null) {
            btnChangeAddressTextView.setOnClickListener(v -> {
                AddressBottomSheetFragment bottomSheet = new AddressBottomSheetFragment();
                bottomSheet.show(getSupportFragmentManager(), "ProductDetailAddressBottomSheet");
            });
        }
        if (specificationsHeaderLayout != null) {
            specificationsHeaderLayout.setOnClickListener(v -> {
                if (specificationsContainer != null) {
                    boolean isCurrentlyVisible = specificationsContainer.getVisibility() == View.VISIBLE;
                    if (isCurrentlyVisible) {
                        specificationsContainer.setVisibility(View.GONE);
                        if (ivExpandSpecsImageView != null) {
                            ivExpandSpecsImageView.animate().rotation(0).setDuration(200).start();
                        }
                    } else {
                        specificationsContainer.setVisibility(View.VISIBLE);
                        if (ivExpandSpecsImageView != null) {
                            ivExpandSpecsImageView.animate().rotation(180).setDuration(200).start();
                        }
                    }
                }
            });
        }
    }

    private void observeViewModel() {
        viewModel.getCartQuantity().observe(this, qty -> {
            int previousQuantity = lastProductQuantity;
            lastProductQuantity = qty == null ? 0 : qty;
            Product product = currentProduct;
            int maximum = ProductQuantityPolicy.getMaximum(product);
            if (lastProductQuantity > maximum && maximum > 0) {
                viewModel.setQuantity(productId, maximum);
                return;
            }
            if (qty != null && qty > 0) {
                addToCartBtn.setVisibility(View.GONE);
                quantitySelector.setVisibility(View.VISIBLE);
                tvQuantity.setText(String.valueOf(qty));
            } else {
                addToCartBtn.setVisibility(View.VISIBLE);
                quantitySelector.setVisibility(View.GONE);
            }
            if (lastProductQuantity > previousQuantity && cartIconImageView != null) {
                cartIconImageView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120)
                        .withEndAction(() -> cartIconImageView.animate().scaleX(1f).scaleY(1f)
                                .setDuration(120)).start();
            }
            updateCartBadge();
        });

        viewModel.getToastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getQuantityUpdating().observe(this, updating -> {
            boolean enabled = !Boolean.TRUE.equals(updating);
            btnPlus.setEnabled(enabled);
            btnMinus.setEnabled(enabled);
            addToCartBtn.setEnabled(enabled && addToCartBtn.getVisibility() == View.VISIBLE);
            btnPlus.setAlpha(enabled ? 1f : 0.5f);
            btnMinus.setAlpha(enabled ? 1f : 0.5f);
        });
    }

    private void setupButtons() {
        btnShareImageView.setOnClickListener(v -> shareProduct());
        btnWishlistImageView.setOnClickListener(v -> toggleWishlist());
        cartContainer.setOnClickListener(v -> {
            if (cartIconImageView != null) {
                cartIconImageView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100)
                        .withEndAction(() -> cartIconImageView.animate().scaleX(1f).scaleY(1f)
                                .setDuration(100)).start();
            }

            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("OPEN_CART", true);
            startActivity(intent);
        });
        addToCartBtn.setOnClickListener(v -> viewModel.setQuantity(productId, 1));

        btnPlus.setOnClickListener(v -> {
            int current = viewModel.getCartQuantity().getValue() != null ? viewModel.getCartQuantity().getValue() : 0;

            // FIX: Use the already loaded currentProduct instead of observing LiveData again
            Product product = currentProduct;

            if (!ProductQuantityPolicy.canAdd(product, current + 1)) {
                Toast.makeText(this, ProductQuantityPolicy.getLimitMessage(product), Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.setQuantity(productId, current + 1);
        });

        btnMinus.setOnClickListener(v -> {
            int current = viewModel.getCartQuantity().getValue() != null ? viewModel.getCartQuantity().getValue() : 0;

            if (current > 1) {
                // Quantity 1 se zyada hai to minus karega
                viewModel.setQuantity(productId, current - 1);
            } else if (current == 1) {
                // Quantity 1 hai aur user ne minus dabaya, to cart se delete kar dega
                viewModel.setQuantity(productId, 0);
            }
        });
    }

    private void loadDefaultAddressPincode() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setDeliveryPincodeStatus("Please log in to check delivery availability", false);
            return;
        }

        setDeliveryPincodeStatus("Checking delivery availability...", false);
        productRepository.getDefaultAddress(user.getUid(), new ProductRepository.DefaultAddressCallback() {
            @Override
            public void onAddressFound(Address address) {
                if (address == null || address.getPincode() == null
                        || address.getPincode().trim().isEmpty()) {
                    setDeliveryPincodeStatus("Add a valid default address to check delivery", false);
                    return;
                }
                String pincode = address.getPincode().trim();
                productRepository.checkPincodeServiceability(pincode,
                        new ProductRepository.ServiceabilityCallback() {
                            @Override
                            public void onServiceable() {
                                setDeliveryPincodeStatus("Delivery available to " + pincode, true);
                            }

                            @Override
                            public void onNotServiceable(String message) {
                                setDeliveryPincodeStatus(message, false);
                            }

                            @Override
                            public void onError(Exception error) {
                                setDeliveryPincodeStatus("Could not check delivery availability", false);
                            }
                        });
            }

            @Override
            public void onAddressNotFound() {
                setDeliveryPincodeStatus("Add a default address to check delivery", false);
            }

            @Override
            public void onFailure(Exception error) {
                setDeliveryPincodeStatus("Could not load default address", false);
            }
        });
    }

    private void setDeliveryPincodeStatus(String message, boolean available) {
        if (tvDeliveryPincodeTextView == null) return;
        tvDeliveryPincodeTextView.setText(message);
        tvDeliveryPincodeTextView.setTextColor(available ? 0xFF12B76A : 0xFFA1A1AA);
    }

    private void renderProduct(Product product) {
        if (product == null) return;
        if (product.getProductId() != null && !product.getProductId().equals(productId)) return;
        currentProduct = product;
        int cartQuantity = viewModel.getCartQuantity().getValue() != null
                ? viewModel.getCartQuantity().getValue() : 0;
        int maximumQuantity = ProductQuantityPolicy.getMaximum(product);
        if (cartQuantity > maximumQuantity && maximumQuantity > 0) {
            viewModel.setQuantity(productId, maximumQuantity);
        }

        // 1. Brand & Title
        if (productBrandTextView != null) {
            String brand = product.getBrand();
            productBrandTextView.setText(brand != null && !brand.isEmpty() ? brand.toUpperCase(Locale.US) : "GRAVO");
        }
        if (productNameTextView != null) {
            productNameTextView.setText(product.getName() != null ? product.getName() : "");
        }
        if (sellerTagTextView != null) sellerTagTextView.setText("Gravo");
        renderRating(product);

        // 2. Price & MRP
        double price = product.getPrice();
        double discountPercent = product.getDiscountPercent();
        long roundedPrice = Math.round(price);
        String formattedPrice = "₹" + roundedPrice;

        productPriceTextView.setText(formattedPrice);
        if (tvBottomBarPriceTextView != null) {
            tvBottomBarPriceTextView.setText(formattedPrice);
        }

        if (productMRPTextView != null && productDiscountTextView != null) {
            Double mrpValue = product.getMRP();
            if (mrpValue != null && mrpValue > 0 && price < mrpValue) {
                double actualDiscount = ((mrpValue - price) / mrpValue) * 100.0;

                // Only show if discount is meaningful (greater than 0)
                if (actualDiscount > 0) {
                    long roundedMrp = Math.round(mrpValue);
                    productMRPTextView.setVisibility(View.VISIBLE);
                    productDiscountTextView.setVisibility(View.VISIBLE);
                    productMRPTextView.setText("₹" + roundedMrp);
                    productMRPTextView.setPaintFlags(productMRPTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    productDiscountTextView.setText(String.format(Locale.US, "%.0f%% OFF", actualDiscount));
                } else {
                    productMRPTextView.setVisibility(View.GONE);
                    productDiscountTextView.setVisibility(View.GONE);
                }
            } else {
                // MRP not available or price >= MRP → hide both
                productMRPTextView.setVisibility(View.GONE);
                productDiscountTextView.setVisibility(View.GONE);
            }
        }

        // 3. Description
        if (productDescriptionTextView != null) {
            String desc = product.getDescription();
            productDescriptionTextView.setText(desc != null && !desc.isEmpty()
                    ? desc
                    : "Product information, ingredients, specifications and other important details.");
        }

        // 4. Stock & Availability Status
        int effectiveStock = product.getEffectiveStockQuantity();
        int limit = product.getMaxBuyingLimit();

        if (!product.isAvailableForPurchase() || effectiveStock <= 0) {
            addToCartBtn.setEnabled(false);
            addToCartBtn.setText("Out of Stock");
            addToCartBtn.setAlpha(0.6f);
            if (tvStockStatusTitleTextView != null) {
                tvStockStatusTitleTextView.setText("Out of stock");
                tvStockStatusTitleTextView.setTextColor(0xFFEF4444);
            }
            if (tvStockStatusSubtitleTextView != null) {
                tvStockStatusSubtitleTextView.setText("Currently unavailable for delivery");
            }
        } else if (limit > 0 && effectiveStock < limit) {
            addToCartBtn.setEnabled(true);
            addToCartBtn.setAlpha(1.0f);
            if (tvStockStatusTitleTextView != null) {
                tvStockStatusTitleTextView.setText("Only " + effectiveStock + " left");
                tvStockStatusTitleTextView.setTextColor(0xFFF59E0B);
            }
            if (tvStockStatusSubtitleTextView != null) {
                tvStockStatusSubtitleTextView.setText("Limited quantity available");
            }
        } else {
            addToCartBtn.setEnabled(true);
            addToCartBtn.setText("Add to Cart");
            addToCartBtn.setAlpha(1.0f);
            if (tvStockStatusTitleTextView != null) {
                tvStockStatusTitleTextView.setText("In stock");
                tvStockStatusTitleTextView.setTextColor(0xFF000000);
            }
            if (tvStockStatusSubtitleTextView != null) {
                tvStockStatusSubtitleTextView.setText("Available for delivery");
            }
        }

        // 5. Specifications
        renderSpecifications(product.getSpecifications());
        if (variantTitleTextView != null) {
            String variantTitle = product.getVariantTitle();
            boolean hasVariantTitle = variantTitle != null && !variantTitle.trim().isEmpty();
            variantTitleTextView.setVisibility(hasVariantTitle ? View.VISIBLE : View.GONE);
            if (hasVariantTitle) {
                variantTitleTextView.setText(variantTitle);
            }
        }
        renderPackagingOptions(product.getVariantMap());

        // 6. Images ViewPager2
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            ProductImagesAdapter adapter = new ProductImagesAdapter(this, product.getImageUrls());
            productImagesViewPager.setAdapter(adapter);

            new TabLayoutMediator(viewPagerIndicator, productImagesViewPager,
                    (tab, position) -> {}
            ).attach();
        }
    }

    private void renderRating(Product product) {
        Map<String, Double> ratings = product.getRatingMap();
        Map<String, String> reviews = product.getReviewMap();
        int ratingCount = ratings == null ? 0 : ratings.size();
        int reviewCount = reviews == null ? 0 : reviews.size();

        if (productRatingTextView != null) {
            if (ratingCount == 0 && reviewCount == 0) {
                productRatingTextView.setText("No ratings or reviews yet");
            } else if (ratingCount == 0) {
                productRatingTextView.setText(String.format(Locale.US, "No ratings  ·  %d reviews", reviewCount));
            } else {
                double total = 0;
                int validRatings = 0;
                for (Double rating : ratings.values()) {
                    if (rating != null && !rating.isNaN()) {
                        total += rating;
                        validRatings++;
                    }
                }
                if (validRatings == 0) {
                    productRatingTextView.setText(String.format(Locale.US, "No valid ratings  ·  %d reviews", reviewCount));
                } else {
                    String ratingText = String.format(Locale.US, "★ %.1f (%d ratings)  ·  %d reviews",
                            total / validRatings, validRatings, reviewCount);
                    SpannableString styledRating = new SpannableString(ratingText);
                    styledRating.setSpan(new ForegroundColorSpan(0xFFFFD700), 0, 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    productRatingTextView.setText(styledRating);
                }
            }
        }
        renderReviews(ratings, reviews);
    }

    private void renderReviews(Map<String, Double> ratings, Map<String, String> reviews) {
        if (ratingsReviewsContainer == null) return;
        ratingsReviewsContainer.removeAllViews();

        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (ratings != null) {
            for (String username : ratings.keySet()) {
                if (username != null && !username.trim().isEmpty()) usernames.add(username);
            }
        }
        if (reviews != null) {
            for (String username : reviews.keySet()) {
                if (username != null && !username.trim().isEmpty()) usernames.add(username);
            }
        }

        if (usernames.isEmpty()) {
            addReviewText("No customer ratings or written reviews are available yet.", 0xFFA1A1AA, false);
            return;
        }

        for (String username : usernames) {
            if (username == null || username.trim().isEmpty()) continue;
            Double rating = ratings == null ? null : ratings.get(username);
            String review = reviews == null ? null : reviews.get(username);
            StringBuilder text = new StringBuilder(username.trim());
            if (rating != null && !rating.isNaN()) {
                text.append("  ").append(String.format(Locale.US, "★ %.1f", rating));
            }
            if (review != null && !review.trim().isEmpty()) {
                text.append("\n").append(review.trim());
            }
            addReviewText(text.toString(), 0xFFE4E4E7, true);
        }
    }

    private void addReviewText(String text, int color, boolean addBottomMargin) {
        TextView reviewView = new TextView(this);
        reviewView.setText(text);
        reviewView.setTextColor(color);
        reviewView.setTextSize(13);
        reviewView.setLineSpacing(2, 1);
        reviewView.setPadding(0, 6, 0, addBottomMargin ? 10 : 6);
        ratingsReviewsContainer.addView(reviewView);
    }

    private void renderPackagingOptions(Map<String, Object> variantMap) {
            if (packagingOptionsLayout == null) return;
            if (variantMap == null || variantMap.isEmpty()) {
                renderedVariantSignature = "";
                packagingOptionsLayout.removeAllViews();
                variantCards.clear();
                renderedVariantIds.clear();
                packagingOptionsLayout.setVisibility(View.GONE);
                return;
            }
            List<String> orderedIds = new ArrayList<>();
            Map<String, String> namesById = new HashMap<>();
            for (Map.Entry<String, Object> entry : variantMap.entrySet()) {
                if (entry.getValue() == null) continue;
                String id = String.valueOf(entry.getValue()).trim();
                if (!id.isEmpty()) {
                    orderedIds.add(id);
                    namesById.put(id, entry.getKey());
                }
            }
            List<String> stableIds = new ArrayList<>();
            for (String id : renderedVariantIds) {
                if (orderedIds.contains(id)) stableIds.add(id);
            }
            for (String id : orderedIds) {
                if (!stableIds.contains(id)) stableIds.add(id);
            }
            orderedIds = stableIds;
            List<String> orderedNames = new ArrayList<>();
            for (String id : orderedIds) orderedNames.add(namesById.get(id));
            final List<String> variantIdsForRender = new ArrayList<>(orderedIds);
            final List<String> variantNamesForRender = new ArrayList<>(orderedNames);
            String signature = variantIdsForRender.toString();
            boolean structureChanged = !variantIdsForRender.equals(renderedVariantIds);
            renderedVariantSignature = signature;
            if (structureChanged) {
                packagingOptionsLayout.removeAllViews();
                variantCards.clear();
                renderedVariantIds.clear();
                renderedVariantIds.addAll(variantIdsForRender);
            }
            packagingOptionsLayout.setVisibility(View.VISIBLE);
            productRepository.getProducts(variantIdsForRender, products -> {
                if (!signature.equals(renderedVariantSignature)) return;
                Map<String, Product> byId = new HashMap<>();
                for (Product item : products) {
                    byId.put(item.getProductId(), item);
                    variantProductCache.put(item.getProductId(), item);
                }
                for (int index = 0; index < variantIdsForRender.size(); index++) {
                    String variantId = variantIdsForRender.get(index);
                    Product variant = byId.get(variantId);
                    if (variant == null) continue;
                    MaterialCardView card;
                    if (structureChanged) {
                        card = createVariantCard(index, variantNamesForRender.get(index), variantId);
                        variantCards.add(card);
                        packagingOptionsLayout.addView(card);
                    } else {
                        card = variantCards.get(index);
                    }
                    updateVariantCard(card, variantNamesForRender.get(index), variant);
                }
            });
    }

    // Helper method to convert DP to Pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private MaterialCardView createVariantCard(int index, String name, String variantId) {
        MaterialCardView card = new MaterialCardView(this);

        // Use dpToPx for all dimensions to match XML
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(110), dpToPx(56));
        int margin = dpToPx(5);
        params.setMargins(index == 0 ? 0 : margin, 0, margin, 0);
        card.setLayoutParams(params);
        card.setRadius(dpToPx(12));
        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        textLayout.setGravity(Gravity.CENTER);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        // Add padding to prevent text from touching the edges
        int padding = dpToPx(4);
        textLayout.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        // Prevent long names from breaking the layout
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView price = new TextView(this);
        price.setTextColor(0xFFA1A1AA);
        price.setTextSize(11);
        price.setGravity(Gravity.CENTER);

        TextView availability = new TextView(this);
        availability.setTextSize(10);
        availability.setGravity(Gravity.CENTER);

        textLayout.addView(title);
        textLayout.addView(price);
        textLayout.addView(availability);

        card.addView(textLayout);
        card.setTag(new TextView[]{title, price, availability});
        card.setOnClickListener(v -> switchVariant(variantId));

        return card;
    }

    private void updateVariantCard(MaterialCardView card, String name, Product variant) {
        TextView[] labels = (TextView[]) card.getTag();
        labels[0].setText(name);
        labels[0].setTextColor(0xFF000000);
        labels[1].setText("₹" + Math.round(variant.getPrice()));
        boolean selected = variant.getProductId().equals(productId);
        int maxQuantity = variant.getMaxAllowedQuantity();
        boolean available = variant.isAvailableForPurchase() && maxQuantity > 0;
        int stock = variant.getEffectiveStockQuantity();
        boolean lowStock = available && stock <= 5;
        labels[2].setText(!available ? "Unavailable" : lowStock ? "Only " + stock + " left" : "Available");
        labels[2].setTextColor(!available ? 0xFFEF4444 : lowStock ? 0xFFF59E0B : 0xFF12B76A);
        card.setEnabled(available);
        card.setClickable(available);
        card.setAlpha(available ? 1f : 0.45f);
        card.setCardBackgroundColor(selected ? 0x8090EE90 : 0xFFffffff);
        card.setStrokeColor(selected ? 0xFF12B76A : 0xFF27272A);
        card.setStrokeWidth(selected ? 2 : 1);
    }

    private void switchVariant(String variantId) {
        if (variantId == null || variantId.equals(productId)) return;
        setVariantLoading(true);
        Product cachedVariant = variantProductCache.get(variantId);
        if (cachedVariant != null) {
            applyVariant(cachedVariant, variantId);
            return;
        }
        productRepository.getProduct(variantId, new ProductRepository.ProductCallback() {
            @Override
            public void onSuccess(Product product) {
                if (product == null) {
                    setVariantLoading(false);
                    return;
                }
                variantProductCache.put(variantId, product);
                applyVariant(product, variantId);
            }

            @Override
            public void onFailure(Exception error) {
                setVariantLoading(false);
                Toast.makeText(ProductDetailActivity.this,
                        "Could not load this variant", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyVariant(Product product, String variantId) {
        productId = variantId;
        currentProduct = product;
        renderedVariantSignature = "";
        renderProduct(product);
        viewModel.monitorCartStatus(productId);
        viewModel.startListeningToProduct(productId);
        setVariantLoading(false);
    }

    private void setVariantLoading(boolean loading) {
        if (variantLoadingOverlay != null) {
            variantLoadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void renderSpecifications(Map<String, String> specifications) {
        if (specificationsHeaderLayout == null || specificationsContainer == null) return;

        if (specifications == null || specifications.isEmpty()) {
            specificationsHeaderLayout.setVisibility(View.GONE);
            specificationsContainer.setVisibility(View.GONE);
            return;
        }

        specificationsHeaderLayout.setVisibility(View.VISIBLE);
        specificationsContainer.removeAllViews();

        int index = 0;
        int size = specifications.size();

        for (Map.Entry<String, String> entry : specifications.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || value == null) continue;

            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);

            TextView keyTextView = new TextView(this);
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
            );
            keyTextView.setLayoutParams(keyParams);
            keyTextView.setText(key);
            keyTextView.setTextColor(0xFFA1A1AA);
            keyTextView.setTextSize(13);

            TextView valueTextView = new TextView(this);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
            );
            valueTextView.setLayoutParams(valueParams);
            valueTextView.setText(value);
            valueTextView.setTextColor(0xFF000000);
            valueTextView.setTextSize(13);
            valueTextView.setTypeface(null, Typeface.BOLD);
            valueTextView.setGravity(Gravity.END);

            row.addView(keyTextView);
            row.addView(valueTextView);
            specificationsContainer.addView(row);

            if (index < size - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1
                );
                dividerParams.setMargins(0, 4, 0, 4);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(0xFF27272A);
                specificationsContainer.addView(divider);
            }

            index++;
        }
    }

    private void setupToolbarFadeEffect() {
        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            float totalScrollRange = appBarLayout1.getTotalScrollRange();
            float alpha = Math.abs(verticalOffset) / totalScrollRange;
            toolbarContent.setAlpha(alpha);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (cartBadgeTextView == null) return;
        int count = LocalCartManager.getTotalCount(this);
        cartBadgeTextView.setText(String.valueOf(count));
        cartBadgeTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void shareProduct() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Check out " + productNameTextView.getText()
                + " on Gravo: " + productId);
        startActivity(Intent.createChooser(share, "Share product"));
    }

    private void loadWishlistState() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        FirebaseFirestore.getInstance().collection("users")
                .document(FirebaseAuth.getInstance().getCurrentUser().getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    Object raw = snapshot.get("wishlist");
                    boolean saved = raw instanceof List && ((List<?>) raw).contains(productId);
                    setWishlistTint(saved);
                });
    }

    private void toggleWishlist() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please log in to use wishlist", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    Object raw = snapshot.get("wishlist");
                    boolean saved = raw instanceof List && ((List<?>) raw).contains(productId);
                    FieldValue update = saved ? FieldValue.arrayRemove(productId) : FieldValue.arrayUnion(productId);
                    FirebaseFirestore.getInstance().collection("users").document(userId)
                            .set(Collections.singletonMap("wishlist", update), SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                setWishlistTint(!saved);
                                Toast.makeText(this, saved ? "Removed from wishlist" : "Added to wishlist",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(error -> Toast.makeText(this, "Could not update wishlist",
                                    Toast.LENGTH_SHORT).show());
                });
    }

    private void setWishlistTint(boolean saved) {
        if (btnWishlistImageView != null) {
            btnWishlistImageView.setImageTintList(ColorStateList.valueOf(
                    saved ? 0xFFFF4081 : 0xFF000000));
        }
    }
}
