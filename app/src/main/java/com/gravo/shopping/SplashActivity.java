package com.gravo.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    // A minimal delay to ensure the splash screen is visible briefly on fast devices.
    private static final int SPLASH_VISIBLE_DELAY = 500; // 0.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This is necessary for the splash screen to be visible on most Android versions.
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                // User is logged in, go to HomeActivity.
                startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            } else {
                // User is not logged in, go to MainActivity.
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            // Finish this activity to remove it from the back stack.
            finish();
        }, SPLASH_VISIBLE_DELAY);
    }
}
