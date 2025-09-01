package com.gravo.grava;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OrderSummaryActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Data
    private ArrayList<CartItem> summaryItems;
    private List<Address> userAddressList;
    private Address selectedAddress;
    private double mrp = 0.0, discount = 0.0, deliveryFee = 40.0, totalPayable = 0.0;
    private boolean isFromCart = false;

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

        Toolbar toolbar = findViewById(R.id.toolbarOrderSummary);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

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
                    selectedAddress = (defaultAddress != null) ? defaultAddress : (userAddressList.isEmpty() ? null : userAddressList.get(0));
                    updateAddressUI();
                });
    }

    private void fetchProductDetails(String productId) {
        db.collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setProductId(doc.getId());
                            summaryItems.add(new CartItem(product, 1));
                            calculateAndDisplayPriceDetails();
                            checkoutAdapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    private void fetchCartDetails(String userId, List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return;

        db.collection("users").document(userId).collection("cart").whereIn(com.google.firebase.firestore.FieldPath.documentId(), productIds)
                .get()
                .addOnSuccessListener(cartSnapshots -> {
                    Map<String, Integer> quantityMap = new HashMap<>();
                    for (QueryDocumentSnapshot cartDoc : cartSnapshots) {
                        Long quantity = cartDoc.getLong("quantity");
                        if (quantity != null) {
                            quantityMap.put(cartDoc.getId(), quantity.intValue());
                        }
                    }

                    db.collection("products").whereIn(com.google.firebase.firestore.FieldPath.documentId(), productIds)
                            .get()
                            .addOnSuccessListener(productSnapshots -> {
                                summaryItems.clear();
                                for (QueryDocumentSnapshot productDoc : productSnapshots) {
                                    Product product = productDoc.toObject(Product.class);
                                    product.setProductId(productDoc.getId());
                                    int quantity = quantityMap.getOrDefault(productDoc.getId(), 1);
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
        deliveryFee = (mrp - discount > 500) ? 0.0 : 40.0; // Example: Free delivery on orders over 500
        totalPayable = mrp - discount + deliveryFee;

        mrpTextView.setText(String.format("₹%.2f", mrp));
        discountTextView.setText(String.format("- ₹%.2f", discount));
        deliveryFeeTextView.setText(deliveryFee == 0.0 ? "FREE" : String.format("₹%.2f", deliveryFee));
        totalAmountTextView.setText(String.format("₹%.2f", totalPayable));
        finalAmountTextView.setText(String.format("₹%.2f", totalPayable));
    }
}
