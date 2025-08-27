package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class OrderConfirmedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_confirmed);

        Button continueShoppingButton = findViewById(R.id.continueShoppingButton);
        Button viewOrdersButton = findViewById(R.id.viewOrdersButton);

        continueShoppingButton.setOnClickListener(v -> navigateToHome());
        viewOrdersButton.setOnClickListener(v -> {
            // Navigate to an OrderHistoryActivity (you'll need to create this)
            // For now, it can also go to Home
            Intent intent = new Intent(OrderConfirmedActivity.this, MyOrdersActivity.class); // Replace with OrderHistoryActivity later
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // NEW: Modern way to handle the back button/gesture
        // This prevents the user from going back to the PaymentActivity
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                // Instead of going back, navigate to the home screen
                navigateToHome();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void navigateToHome() {
        Intent intent = new Intent(OrderConfirmedActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // REMOVED: The old onBackPressed() method is no longer needed.
}
