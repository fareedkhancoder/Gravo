package com.gravo.shopping;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.gravo.shopping.data.repository.ProductRepository;
import com.gravo.shopping.MyApplication;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OrderSummaryActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ProductRepository productRepository;

    // Data
    private ArrayList<CartItem> summaryItems;
    private List<Address> userAddressList;
    private Address selectedAddress;
    private double mrp = 0.0, discount = 0.0, deliveryFee = 40.0, totalPayable = 0.0;
    private boolean isFromCart = false;
    private boolean isInstantDelivery = true;
    private Double configuredDeliveryCharge;

    // UI Elements
    private TextView userNameAddress, fullAddress, mrpTextView, discountTextView, deliveryFeeTextView, totalAmountTextView, finalAmountTextView;
    private RecyclerView orderItemsRecyclerView;
    private CheckoutAdapter checkoutAdapter; // Make sure you have this adapter for displaying items
    private Button continueButton, changeAddressButton;

    // Launcher for getting selected address
    private ActivityResultLauncher<Intent> addressSelectionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_summary);

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbarOrderSummary);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        productRepository = MyApplication.getProductRepository();
        if (getIntent().hasExtra("SELECTED_ADDRESS")) {
            selectedAddress = (Address) getIntent().getSerializableExtra("SELECTED_ADDRESS");
        }
        isInstantDelivery = getIntent().getBooleanExtra("IS_INSTANT_DELIVERY", true);
        if (getIntent().hasExtra("DELIVERY_CHARGE")) {
            configuredDeliveryCharge = getIntent().getDoubleExtra("DELIVERY_CHARGE", 0.0);
        }

        // Initialize Views
        userNameAddress = findViewById(R.id.userNameAddress);
        fullAddress = findViewById(R.id.fullAddress);
        mrpTextView = findViewById(R.id.mrpTextView);
        discountTextView = findViewById(R.id.discountTextView);
        deliveryFeeTextView = findViewById(R.id.deliveryFeeTextView);
        totalAmountTextView = findViewById(R.id.totalAmountTextView);
        finalAmountTextView = findViewById(R.id.finalAmountTextView);
        continueButton = findViewById(R.id.continueButton);
        changeAddressButton = findViewById(R.id.changeAddressButton);
        continueButton.setEnabled(false);

        // Setup RecyclerView for order items
        summaryItems = new ArrayList<>();
        userAddressList = new ArrayList<>();
        orderItemsRecyclerView = findViewById(R.id.orderItemsRecyclerView);
        orderItemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        checkoutAdapter = new CheckoutAdapter(this, summaryItems);
        orderItemsRecyclerView.setAdapter(checkoutAdapter);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to place an order.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize the launcher for address selection
        addressSelectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String selectedAddressId = result.getData().getStringExtra("SELECTED_ADDRESS_ID");
                        if (selectedAddressId != null) {
                            // Find the selected address from the fetched list and update the UI
                            for (Address addr : userAddressList) {
                                if (addr.getAddressId().equals(selectedAddressId)) {
                                    selectedAddress = addr;
                                    updateAddressUI();
                                    loadDeliveryChargeForSelectedAddress();
                                    break;
                                }
                            }
                        }
                    }
                });

        // Set listeners
        changeAddressButton.setOnClickListener(v -> launchAddressSelection());
        continueButton.setOnClickListener(v -> proceedToPayment());

        // Fetch all necessary data
        fetchUserAddresses(currentUser.getUid());
        handleIntentData(currentUser.getUid());
    }
    private void proceedToPayment() {
        if (summaryItems.isEmpty() || selectedAddress == null) {
            Toast.makeText(this, "Please select an address and ensure your cart is not empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // IMPORTANT: Your Address, CartItem, and Product classes must implement Serializable
        Intent intent = new Intent(OrderSummaryActivity.this, PaymentActivity.class);
        intent.putExtra("SELECTED_ADDRESS", (Serializable) selectedAddress);
        intent.putExtra("SUMMARY_ITEMS", summaryItems);
        intent.putExtra("TOTAL_PAYABLE", totalPayable);
        intent.putExtra("IS_FROM_CART", isFromCart);
        if (getIntent().hasExtra("PAYMENT_METHOD")) {
            intent.putExtra("PAYMENT_METHOD", getIntent().getStringExtra("PAYMENT_METHOD"));
        }
        startActivity(intent);
    }

    private void handleIntentData(String userId) {
        if (getIntent().hasExtra("CART_PRODUCT_IDS")) {
            isFromCart = true;
            ArrayList<String> productIds = getIntent().getStringArrayListExtra("CART_PRODUCT_IDS");
            fetchCartDetails(userId, productIds);
        } else if (getIntent().hasExtra("PRODUCT_ID")) {
            isFromCart = false;
            String productId = getIntent().getStringExtra("PRODUCT_ID");
            fetchProductDetails(productId);
        }
    }

    private void launchAddressSelection() {
        Intent intent = new Intent(OrderSummaryActivity.this, MyAddressesActivity.class);
        intent.putExtra("SELECT_ADDRESS_MODE", true);
        addressSelectionLauncher.launch(intent);
    }

    private void updateAddressUI() {
        if (selectedAddress != null) {
            userNameAddress.setText(selectedAddress.getFullName());
            // Build a more detailed address string including the phone number
            String completeAddress = selectedAddress.getStreet() + ", " +
                    selectedAddress.getCity() + ", " +
                    selectedAddress.getState() + " - " +
                    selectedAddress.getPincode() + "\n" + // New line for phone
                    "Phone: " + selectedAddress.getPhone(); // Assuming Address class has getPhone()
            fullAddress.setText(completeAddress);
        } else {
            userNameAddress.setText("No Address Found");
            fullAddress.setText("Please add a delivery address.");
        }
    }

    private void fetchUserAddresses(String userId) {
        db.collection("users").document(userId).collection("addresses").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userAddressList.clear();
                    Address defaultAddress = null;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Address address = doc.toObject(Address.class);
                        address.setAddressId(doc.getId());
                        userAddressList.add(address);
                        if (address.isDefault()) {
                            defaultAddress = address;
                        }
                    }
                    // Select the default address if available, otherwise the first one
                    if (selectedAddress == null) {
                        selectedAddress = (defaultAddress != null) ? defaultAddress
                                : (userAddressList.isEmpty() ? null : userAddressList.get(0));
                    }
                    updateAddressUI();
                    loadDeliveryChargeForSelectedAddress();
                });
    }

    private void loadDeliveryChargeForSelectedAddress() {
        if (selectedAddress == null || selectedAddress.getPincode() == null
                || selectedAddress.getPincode().trim().isEmpty()) {
            configuredDeliveryCharge = null;
            continueButton.setEnabled(false);
            return;
        }

        productRepository.getDeliveryCharge(selectedAddress.getPincode(),
                new ProductRepository.DeliveryChargeCallback() {
                    @Override
                    public void onSuccess(DeliveryCharge charge) {
                        if (!charge.isServiceable() || !charge.hasChargeFor(isInstantDelivery)) {
                            configuredDeliveryCharge = null;
                            continueButton.setEnabled(false);
                            Toast.makeText(OrderSummaryActivity.this,
                                    "Delivery is unavailable for the selected address.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        configuredDeliveryCharge = isInstantDelivery
                                ? charge.getInstantCharge()
                                : charge.getNormalCharge();
                        continueButton.setEnabled(true);
                        calculateAndDisplayPriceDetails();
                    }

                    @Override
                    public void onFailure(Exception error) {
                        configuredDeliveryCharge = null;
                        continueButton.setEnabled(false);
                        Toast.makeText(OrderSummaryActivity.this,
                                "Unable to load delivery charges.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchProductDetails(String productId) {
        productRepository.getProduct(productId, new ProductRepository.ProductCallback() {
                    @Override public void onSuccess(Product product) {
                            summaryItems.add(new CartItem(product, 1));
                            calculateAndDisplayPriceDetails();
                            checkoutAdapter.notifyDataSetChanged();
                    }
                    @Override public void onFailure(Exception error) {
                        Toast.makeText(OrderSummaryActivity.this, "Product unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchCartDetails(String userId, List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return;

        db.collection("users").document(userId).collection("cart").document("cart").get()
                .addOnSuccessListener(cartSnapshots -> {
                    Map<String, Integer> quantityMap = new HashMap<>();
                    List<Map<String, Object>> cartProducts = (List<Map<String, Object>>) cartSnapshots.get("products");
                    if (cartProducts != null) {
                        for (Map<String, Object> cartProduct : cartProducts) {
                            String productId = cartProduct.get("productId") instanceof String
                                    ? (String) cartProduct.get("productId") : null;
                            Object rawQuantity = cartProduct.get("quantity");
                            if (productId != null && rawQuantity instanceof Number
                                    && productIds.contains(productId)) {
                                quantityMap.put(productId, ((Number) rawQuantity).intValue());
                            }
                        }
                    }

                    productRepository.getProducts(productIds, products -> {
                                summaryItems.clear();
                                for (Product product : products) {
                                    int quantity = quantityMap.getOrDefault(product.getProductId(), 1);
                                    summaryItems.add(new CartItem(product, quantity));
                                }
                                calculateAndDisplayPriceDetails();
                                checkoutAdapter.notifyDataSetChanged();
                            });
                });
    }

    private void calculateAndDisplayPriceDetails() {
        mrp = 0.0;
        for (CartItem item : summaryItems) {
            mrp += item.getProduct().getPrice() * item.getQuantity();
        }
        discount = mrp * 0.10; // Example: 10% discount
        boolean hasConfiguredCharge = configuredDeliveryCharge != null;
        deliveryFee = hasConfiguredCharge ? configuredDeliveryCharge : 0.0;
        totalPayable = mrp - discount + deliveryFee;

        mrpTextView.setText(String.format("₹%.2f", mrp));
        discountTextView.setText(String.format("- ₹%.2f", discount));
        deliveryFeeTextView.setText(hasConfiguredCharge
                ? (deliveryFee == 0.0 ? "FREE" : String.format("₹%.2f", deliveryFee))
                : "—");
        totalAmountTextView.setText(String.format("₹%.2f", totalPayable));
        finalAmountTextView.setText(String.format("₹%.2f", totalPayable));
    }
}
