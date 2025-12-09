package com.gravo.grava;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity implements HomeFragment.OnHomeFragmentInteractionListener {
    private long backPressedTime;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            replaceFragment(new HomeFragment());
        }

        // Set up the listener for user taps
        setupBottomNavigationListener();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Get the current fragment
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                // Check if the current fragment is HomeFragment
                if (currentFragment instanceof HomeFragment) {
                    // Logic for "Press again to exit"
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        if (backToast != null) backToast.cancel();
                        finish(); // Exit the app
                    } else {
                        backToast = Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT);
                        backToast.show();
                    }
                    backPressedTime = System.currentTimeMillis();
                } else {
                    // If not on HomeFragment, go to HomeFragment
                    BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

                    // We simply change the selected item to Home.
                    // This triggers the BottomNavigation listener we already set up,
                    // which handles the fragment replacement for us.
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                }
            }
        });
    }

    /**
     * Sets up the listener for the BottomNavigationView.
     * This logic is in its own method so it can be re-applied after being temporarily removed.
     */
    private void setupBottomNavigationListener() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {

            // --- NEW CODE STARTS HERE ---
            // Check if the user clicked the tab they are currently on
            if (bottomNavigationView.getSelectedItemId() == item.getItemId()) {
                vibrator(40);

                return false; // Return false to prevent reload
            }
            // --- NEW CODE ENDS HERE ---

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                replaceFragment(new HomeFragment());
                vibrator(30);
                return true;
            } else if (itemId == R.id.nav_cart) {
                replaceFragment(new CartFragment());
                vibrator(30);
                return true;
            } else if (itemId == R.id.nav_account) {
                replaceFragment(new AccountFragment());
                vibrator(30);
                return true;
            } else if (itemId == R.id.nav_categories) {
                replaceFragment(new CategoriesFragment());
                vibrator(30);
                return true;
            }
            return false;
        });
    }
    private void vibrator(int duration){
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            // Vibrate for 50 milliseconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Deprecated in API 26, but necessary for older phones
                v.vibrate(duration);
            }
        }
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public void navigateToCategories(String categoryId) {
        // 1. Create the fragment instance with its arguments
        Fragment categoriesFragment = CategoriesFragment.newInstance(categoryId);
        Log.d(TAG, "Home Activity: received categoryId from HomeFragment: " + categoryId + "Sending it to CategoriesFragment");

        // 2. Perform the fragment transaction
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, categoriesFragment);
        fragmentTransaction.addToBackStack(null); // Correct to add to back stack for this navigation
        fragmentTransaction.commit();

        // 3. ✨ FIX: Manually update the BottomNavigationView's selected item
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // Temporarily disable the listener to prevent it from creating a second fragment.
        bottomNavigationView.setOnItemSelectedListener(null);
        // Update the UI to show the correct tab as selected.
        bottomNavigationView.setSelectedItemId(R.id.nav_categories);
        // Re-attach the listener so it works for manual user taps again.
        setupBottomNavigationListener();
    }

}