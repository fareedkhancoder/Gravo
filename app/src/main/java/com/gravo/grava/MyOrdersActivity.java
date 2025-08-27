package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

// MODIFIED: Implement the click listener interface
public class MyOrdersActivity extends AppCompatActivity implements OrderAdapter.OnOrderClickListener {

    private static final String TAG = "MyOrdersActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private RecyclerView ordersRecyclerView;
    private OrderAdapter orderAdapter;
    private List<Order> orderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_orders);

        Toolbar toolbar = findViewById(R.id.toolbarMyOrders);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        orderList = new ArrayList<>();
        ordersRecyclerView = findViewById(R.id.ordersRecyclerView);
        ordersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // MODIFIED: Pass 'this' as the listener
        orderAdapter = new OrderAdapter(this, orderList, this);
        ordersRecyclerView.setAdapter(orderAdapter);

        fetchUserOrders();
    }

    private void fetchUserOrders() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to see your orders.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String userId = currentUser.getUid();

        db.collection("orders")
                .whereEqualTo("userId", userId) // Sirf current user ke orders fetch karein
                .orderBy("orderDate", Query.Direction.DESCENDING) // Sabse naye order upar
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        orderList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Order order = document.toObject(Order.class);
                            order.setOrderId(document.getId()); // Document ID ko set karein
                            orderList.add(order);
                        }
                        orderAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Successfully fetched " + orderList.size() + " orders.");
                    } else {
                        Log.e(TAG, "Error getting orders: ", task.getException());
                        Toast.makeText(MyOrdersActivity.this, "Failed to load orders.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // NEW: Handle the click event from the adapter
    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(MyOrdersActivity.this, OrderTrackActivity.class);
        intent.putExtra("ORDER_ID", order.getOrderId());
        startActivity(intent);
    }
}
