package com.gravo.shopping;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderTrackActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    // Header
    private ImageButton btnBack;
    private TextView tvOrderId;

    // Timeline
    private RecyclerView rvOrderSteps;
    private OrderStatusAdapter statusAdapter;
    private List<OrderStatus> statusList;

    // Delivery & Tracking
    private TextView tvDeliveryDate, tvDeliveryStatus, tvCourier, tvTrackingNumber;

    // Order Summary
    private ImageView ivProductThumb;
    private TextView tvProductName, tvProductQty, tvProductPrice;

    // Delivery Info
    private TextView tvRecipient, tvDeliveryAddress, tvPaymentMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_track);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupStatusTimeline();

        String orderId = getIntent().getStringExtra("ORDER_ID");
        if (orderId != null) {
            tvOrderId.setText("Order ID: #" + orderId);
            fetchOrderDetails(orderId);
        } else {
            Toast.makeText(this, "Order ID not found.", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        // Header
        btnBack = findViewById(R.id.btnBack);
        tvOrderId = findViewById(R.id.tvOrderId);

        // Timeline
        rvOrderSteps = findViewById(R.id.rvOrderSteps);

        // Delivery & Tracking
        tvDeliveryDate = findViewById(R.id.tvDeliveryDate);
        tvDeliveryStatus = findViewById(R.id.tvDeliveryStatus);
        tvCourier = findViewById(R.id.tvCourier);
        tvTrackingNumber = findViewById(R.id.tvTrackingNumber);

        // Order Summary
        ivProductThumb = findViewById(R.id.ivProductThumb);
        tvProductName = findViewById(R.id.tvProductName);
        tvProductQty = findViewById(R.id.tvProductQty);
        tvProductPrice = findViewById(R.id.tvProductPrice);

        // Delivery Info
        tvRecipient = findViewById(R.id.tvRecipient);
        tvDeliveryAddress = findViewById(R.id.tvDeliveryAddress);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
    }

    private void setupStatusTimeline() {
        rvOrderSteps.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        statusList = new ArrayList<>();
        // Initialize with default steps
        statusList.add(new OrderStatus("Pending", false));
        statusList.add(new OrderStatus("Processing", false));
        statusList.add(new OrderStatus("Shipped", false));
        statusList.add(new OrderStatus("Delivered", false));
        statusAdapter = new OrderStatusAdapter(statusList);
        rvOrderSteps.setAdapter(statusAdapter);
    }

    private void fetchOrderDetails(String orderId) {
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Order order = documentSnapshot.toObject(Order.class);
                        if (order != null) {
                            populateUI(order);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load order details.", Toast.LENGTH_SHORT).show());
    }

    private void populateUI(Order order) {
        // Update Status Timeline
        updateStatus(order.getOrderStatus());
        tvDeliveryStatus.setText(order.getOrderStatus());

        // Set Delivery Info
        if (order.getShippingAddress() != null) {
            Address address = order.getShippingAddress();
            tvRecipient.setText(address.getFullName() + ", " + address.getPhone());
            String fullAddress = address.getStreet() + ", " + address.getCity() + ", " + address.getState() + " - " + address.getPincode();
            tvDeliveryAddress.setText(fullAddress);
        }
        tvPaymentMethod.setText("Payment: " + order.getPaymentMethod());

        // Set Estimated Delivery
        if (order.getOrderDate() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(order.getOrderDate().toDate());
            cal.add(Calendar.DAY_OF_YEAR, 3); // Estimate 3 days for delivery
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvDeliveryDate.setText("Expected Delivery: " + sdf.format(cal.getTime()));
        }

        // Set Order Summary (showing the first item as an example)
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            Map<String, Object> firstItem = order.getItems().get(0);
            tvProductName.setText((String) firstItem.get("name"));
            tvProductQty.setText("Qty: " + firstItem.get("quantity"));
            tvProductPrice.setText(String.format("₹%.2f", firstItem.get("priceAtPurchase")));
            // In a real app, you would use Glide or Picasso to load the product image here
            // Glide.with(this).load(imageUrl).into(ivProductThumb);
        }
    }

    private void updateStatus(String currentStatus) {
        if (currentStatus == null) return;
        String lowerCaseStatus = currentStatus.toLowerCase();

        boolean placed = false, processing = false, shipped = false, delivered = false;

        switch(lowerCaseStatus) {
            case "delivered":
                delivered = true;
            case "shipped":
                shipped = true;
            case "processing":
                processing = true;
            case "pending":
                placed = true;
                break;
        }

        statusList.clear();
        statusList.add(new OrderStatus("Placed", placed));
        statusList.add(new OrderStatus("Processing", processing));
        statusList.add(new OrderStatus("Shipped", shipped));
        statusList.add(new OrderStatus("Delivered", delivered));
        statusAdapter.notifyDataSetChanged();
    }
}
