package com.gravo.grava;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity implements HomeFragment.OnHomeFragmentInteractionListener {

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
    }

    /**
     * Sets up the listener for the BottomNavigationView.
     * This logic is in its own method so it can be re-applied after being temporarily removed.
     */
    private void setupBottomNavigationListener() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                replaceFragment(new HomeFragment());
                return true;
            } else if (itemId == R.id.nav_cart) {
                replaceFragment(new CartFragment());
                return true;
            } else if (itemId == R.id.nav_account) {
                replaceFragment(new AccountFragment());
                return true;
            } else if (itemId == R.id.nav_categories) {
                // This now only triggers for a direct user tap on the categories icon
                replaceFragment(new CategoriesFragment());
                return true;
            }
            return false;
        });
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