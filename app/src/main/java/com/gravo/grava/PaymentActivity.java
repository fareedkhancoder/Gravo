package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PaymentActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private Button placeOrderButton;

    // Data from OrderSummaryActivity
    private Address selectedAddress;
    private ArrayList<CartItem> summaryItems;
    private double totalPayable;
    private boolean isFromCart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        Toolbar toolbar = findViewById(R.id.toolbarPayment);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        db = FirebaseFirestore.getInstance();
        placeOrderButton = findViewById(R.id.placeOrderButton);
        TextView finalAmountTextView = findViewById(R.id.finalAmountTextView);

        // Get data from intent
        Intent intent = getIntent();
        selectedAddress = (Address) intent.getSerializableExtra("SELECTED_ADDRESS");
        summaryItems = (ArrayList<CartItem>) intent.getSerializableExtra("SUMMARY_ITEMS");
        totalPayable = intent.getDoubleExtra("TOTAL_PAYABLE", 0.0);
        isFromCart = intent.getBooleanExtra("IS_FROM_CART", false);

        finalAmountTextView.setText(String.format("₹%.2f", totalPayable));

        // Setup placeholder listeners
        findViewById(R.id.upiOption).setOnClickListener(v -> showComingSoonToast());
        findViewById(R.id.cardOption).setOnClickListener(v -> showComingSoonToast());
        findViewById(R.id.netBankingOption).setOnClickListener(v -> showComingSoonToast());

        RadioButton codRadio = findViewById(R.id.radioCashOnDelivery);
        codRadio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            placeOrderButton.setEnabled(isChecked);
        });

        placeOrderButton.setOnClickListener(v -> {
            if (codRadio.isChecked()) {
                placeOrderWithCOD();
            } else {
                Toast.makeText(this, "Please select a payment method.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showComingSoonToast() {
        Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void placeOrderWithCOD() {
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        if (summaryItems == null || summaryItems.isEmpty() || selectedAddress == null) {
            Toast.makeText(this, "Order details are missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        placeOrderButton.setEnabled(false); // Prevent multiple clicks

        WriteBatch batch = db.batch();
        DocumentReference orderRef = db.collection("orders").document();

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("userId", userId);
        orderData.put("orderDate", FieldValue.serverTimestamp());
        orderData.put("shippingAddress", selectedAddress);
        orderData.put("totalAmount", totalPayable);
        // TODO: ADD MORE PAYMENT METHODS IN FUTURE LIKE PHONE PAY, PAYTM
        orderData.put("paymentMethod", "Cash on Delivery");
        orderData.put("orderStatus", "Pending");

        List<Map<String, Object>> orderItemsList = new ArrayList<>();
        for (CartItem item : summaryItems) {
            DocumentReference productRef = db.collection("products").document(item.getProduct().getProductId());
            batch.update(productRef, "stockQuantity", FieldValue.increment(-item.getQuantity()));

            Map<String, Object> orderItemMap = new HashMap<>();
            orderItemMap.put("productName", item.getProduct().getName());
            // --- KEY ADDITION: Save Name here so it shows in your Dashboard immediately ---
            // Make sure your Address class has a getName() or getFullName() method
            orderData.put("customerName", selectedAddress.getFullName());
            // -----------------------------------------------------------------------------
            orderItemMap.put("CategoryId", item.getProduct().getCategoryId());
            orderItemMap.put("priceAtPurchase", item.getProduct().getPrice());
            orderItemMap.put("quantity", item.getQuantity());
            // Save only the first image (thumbnail)
            List<String> urls = item.getProduct().getImageUrls();
            String thumbnail = (urls != null && !urls.isEmpty()) ? urls.get(0) : "";
            orderItemMap.put("imageUrl", thumbnail);// Add this line
            orderItemsList.add(orderItemMap);
        }
        orderData.put("items", orderItemsList);
        batch.set(orderRef, orderData);
        if (isFromCart) {
            for (CartItem item : summaryItems) {
                DocumentReference cartItemRef = db.collection("users").document(userId)
                        .collection("cart").document(item.getProduct().getProductId());
                batch.delete(cartItemRef);
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_LONG).show();
            Intent successIntent = new Intent(this, OrderConfirmedActivity.class);
            successIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(successIntent);
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Order Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            placeOrderButton.setEnabled(true);
        });
    }
}
