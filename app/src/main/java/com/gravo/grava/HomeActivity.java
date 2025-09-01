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

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // --- Best Practice Improvement ---
        // The inefficient `fetchAndStoreAllTags` method has been removed.
        // Pre-loading all product tags on app start is not scalable and leads to poor performance.
        // Search suggestion data should be fetched on-demand from the search screen
        // or from a dedicated, aggregated document in Firestore, not by querying the entire collection.

        // TODO: Implement Google Play In-App Updates.
        // It's a good practice to check for flexible updates here.
        // This allows the user to continue using the app while the update downloads.
        // checkForAppUpdate();

        if (savedInstanceState == null) {
            replaceFragment(new HomeFragment());
        }

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
                replaceFragment(new CategoriesFragment());
            }
            return false;
        });
    }



    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        // It's good practice not to add the first fragment to the back stack.
        // fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    // Add this method inside your HomeActivity.java file

    @Override
    public void navigateToCategories(String categoryId) {

        // 2. Use the newInstance factory method to create the fragment with the ID
        Fragment categoriesFragment = CategoriesFragment.newInstance(categoryId);
        Log.d(TAG, "navigateToCategories: received categoryId" + categoryId);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        fragmentTransaction.replace(R.id.fragment_container, categoriesFragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }
}
