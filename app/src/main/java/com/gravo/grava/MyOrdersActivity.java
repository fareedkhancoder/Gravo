package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider; // Important Import
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.gravo.grava.viewmodel.OrdersViewModel;

import java.util.ArrayList;

public class MyOrdersActivity extends AppCompatActivity implements OrderAdapter.OnOrderClickListener {

    private OrderAdapter orderAdapter;
    private OrdersViewModel ordersViewModel; // Reference to ViewModel
    // You might need to add a ProgressBar to your XML layout for better UX
    private ProgressBar loadingIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_orders);

        // 1. Setup UI
        Toolbar toolbar = findViewById(R.id.toolbarMyOrders);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        RecyclerView ordersRecyclerView = findViewById(R.id.ordersRecyclerView);
        ordersRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Adapter with empty list
        orderAdapter = new OrderAdapter(this, new ArrayList<>(), this);
        ordersRecyclerView.setAdapter(orderAdapter);

        // 2. Initialize ViewModel
        ordersViewModel = new ViewModelProvider(this).get(OrdersViewModel.class);

        // 3. Observe Data (This code runs whenever data changes)
        ordersViewModel.getOrders().observe(this, orderList -> {
            // Update the UI
            orderAdapter.updateList(orderList); // *See note below about Adapter
            // Hide loading indicator if you have one
        });

        ordersViewModel.getErrorMessage().observe(this, error -> {
            Toast.makeText(MyOrdersActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
        });

        // 4. Trigger the fetch
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (userId != null) {
            ordersViewModel.loadOrders(userId);
        } else {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(MyOrdersActivity.this, OrderTrackActivity.class);
        intent.putExtra("ORDER_ID", order.getOrderId());
        startActivity(intent);
    }
}