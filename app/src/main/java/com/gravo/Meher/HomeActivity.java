package com.gravo.Meher;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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

import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;

public class HomeActivity extends AppCompatActivity implements HomeFragment.OnHomeFragmentInteractionListener, PaymentResultWithDataListener {

    private static final int ADDRESS_REQUEST_CODE = 4101;
    private long backPressedTime;
    private Toast backToast;
    private View rootView;

    // Define colors for Active (Blue) and Inactive (Grey)
    private final int COLOR_ACTIVE = Color.parseColor("#2874F0");
    private final int COLOR_INACTIVE = Color.parseColor("#555555");

    // UI Components for the Custom Nav Bar
    private LinearLayout btnHome, btnCategories, btnAccount, btnCart;
    private ImageView imgHome, imgCat, imgAccount, imgCart;
    private TextView txtHome, txtCat, txtAccount, txtCart;
    private StartupPreloadCoordinator startupCoordinator;
    private View startupOverlay;
    private TextView startupStatus;
    private View startupRetry;
    private boolean startupReady;

    // To track which tab is currently selected
    private int currentTabId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        startupOverlay = findViewById(R.id.startupLoadingOverlay);
        startupStatus = findViewById(R.id.startupStatus);
        startupRetry = findViewById(R.id.startupRetry);
        if (startupRetry != null) {
            startupRetry.setOnClickListener(v -> beginStartupPreload());
        }

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        rootView = findViewById(R.id.main);

        // 1. Bind Views
        initNavViews();

        startupCoordinator = new StartupPreloadCoordinator(this);
        beginStartupPreload();

        // 3. Set Click Listeners
        setupCustomNavigation();

        // 4. Back Press Logic
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                if (currentFragment instanceof HomeFragment) {
                    // Double press to exit logic
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        if (backToast != null) backToast.cancel();
                        finish();
                    } else {
                        backToast = Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT);
                        backToast.show();
                    }

                    backPressedTime = System.currentTimeMillis();
                } else {
                    // Go back to Home Fragment
                    replaceFragment(new HomeFragment());
                    updateNavUI(R.id.nav_home);
                    currentTabId = R.id.nav_home;
                }
            }
        });
    }

    private void beginStartupPreload() {
        startupReady = false;
        if (startupOverlay != null) startupOverlay.setVisibility(View.VISIBLE);
        if (startupRetry != null) startupRetry.setVisibility(View.GONE);
        startupCoordinator.start(this, new StartupPreloadCoordinator.Callback() {
            @Override
            public void onStage(String message) {
                if (startupStatus != null) startupStatus.setText(message);
            }

            @Override
            public void onAddressRequired() {
                if (startupStatus != null) startupStatus.setText("Choose your delivery address...");
                Intent intent = new Intent(HomeActivity.this, AddEditAddressActivity.class);
                startActivityForResult(intent, ADDRESS_REQUEST_CODE);
            }

            @Override
            public void onReady() {
                showHomeAfterStartup();
            }

            @Override
            public void onFailure(String message) {
                if (startupStatus != null) startupStatus.setText(message);
                if (startupRetry != null) startupRetry.setVisibility(View.VISIBLE);
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showHomeAfterStartup() {
        if (startupReady || isFinishing() || isDestroyed()) return;
        startupReady = true;
        if (startupOverlay != null) startupOverlay.setVisibility(View.GONE);
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            if (getIntent().getBooleanExtra("OPEN_CART", false)) {
                replaceFragment(new CartFragment());
                updateNavUI(R.id.nav_cart);
            } else {
                replaceFragment(new HomeFragment());
                updateNavUI(R.id.nav_home);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADDRESS_REQUEST_CODE && resultCode == RESULT_OK) {
            beginStartupPreload();
        }
    }

    @Override
    protected void onDestroy() {
        if (startupCoordinator != null) startupCoordinator.shutdown();
        super.onDestroy();
    }

    private void initNavViews() {
        // Layout Containers (Clickable areas)
        btnHome = findViewById(R.id.nav_home);
        btnCategories = findViewById(R.id.nav_categories);
        btnAccount = findViewById(R.id.nav_account);
        btnCart = findViewById(R.id.nav_cart);

        // Icons
        imgHome = findViewById(R.id.img_home);
        imgCat = findViewById(R.id.img_cat);
        imgAccount = findViewById(R.id.img_account);
        imgCart = findViewById(R.id.img_cart);

        // Texts
        txtHome = findViewById(R.id.txt_home);
        txtCat = findViewById(R.id.txt_cat);
        txtAccount = findViewById(R.id.txt_account);
        txtCart = findViewById(R.id.txt_cart);
    }

    private void setupCustomNavigation() {
        // --- HOME CLICK ---
        btnHome.setOnClickListener(v -> {
            if (currentTabId == R.id.nav_home) return; // Already here
            vibrator(30);
            replaceFragment(new HomeFragment());
            updateNavUI(R.id.nav_home);
        });

        // --- CATEGORIES CLICK ---
        btnCategories.setOnClickListener(v -> {
            if (currentTabId == R.id.nav_categories) return;
            vibrator(30);
            replaceFragment(new CategoriesFragment());
            updateNavUI(R.id.nav_categories);
        });

        // --- ACCOUNT CLICK ---
        btnAccount.setOnClickListener(v -> {
            if (currentTabId == R.id.nav_account) return;
            vibrator(30);
            replaceFragment(new AccountFragment());
            updateNavUI(R.id.nav_account);
        });

        // --- CART CLICK ---
        btnCart.setOnClickListener(v -> {
            if (currentTabId == R.id.nav_cart) return;
            vibrator(30);
            replaceFragment(new CartFragment());
            updateNavUI(R.id.nav_cart);
        });
    }

    private void updateNavUI(int selectedId) {
        currentTabId = selectedId;

        imgHome.setColorFilter(COLOR_INACTIVE); txtHome.setTextColor(COLOR_INACTIVE);
        imgCat.setColorFilter(COLOR_INACTIVE); txtCat.setTextColor(COLOR_INACTIVE);
        imgAccount.setColorFilter(COLOR_INACTIVE); txtAccount.setTextColor(COLOR_INACTIVE);
        imgCart.setColorFilter(COLOR_INACTIVE); txtCart.setTextColor(COLOR_INACTIVE);

        if (selectedId == R.id.nav_home) {
            rootView.setBackgroundResource(R.drawable.background2);
            imgHome.setColorFilter(COLOR_ACTIVE);
            txtHome.setTextColor(COLOR_ACTIVE);
        } else if (selectedId == R.id.nav_categories) {
            rootView.setBackgroundResource(R.drawable.background2);
            imgCat.setColorFilter(COLOR_ACTIVE);
            txtCat.setTextColor(COLOR_ACTIVE);
        } else if (selectedId == R.id.nav_account) {
            imgAccount.setColorFilter(COLOR_ACTIVE);
            txtAccount.setTextColor(COLOR_ACTIVE);
            rootView.setBackgroundResource(R.drawable.bg_toolbar_white_with_line);
        } else if (selectedId == R.id.nav_cart) {
            rootView.setBackgroundColor(Color.WHITE);
            imgCart.setColorFilter(COLOR_ACTIVE);
            txtCart.setTextColor(COLOR_ACTIVE);
        }
    }

    private void vibrator(int duration){
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
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
        Fragment categoriesFragment = CategoriesFragment.newInstance(categoryId);
        Log.d(TAG, "Home Activity: received categoryId: " + categoryId);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, categoriesFragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        updateNavUI(R.id.nav_categories);
    }

    @Override
    public void navigateToCart() {
        if (currentTabId == R.id.nav_cart) return;
        vibrator(30);
        replaceFragment(new CartFragment());
        updateNavUI(R.id.nav_cart);
    }

    // Razorpay Callbacks
    @Override
    public void onPaymentSuccess(String razorpayPaymentId, PaymentData paymentData) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof CartFragment) {
            ((CartFragment) currentFragment).handleRazorpaySuccess(razorpayPaymentId, paymentData);
        }
    }

    @Override
    public void onPaymentError(int code, String response, PaymentData paymentData) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof CartFragment) {
            ((CartFragment) currentFragment).handleRazorpayError(code, response, paymentData);
        }
    }
}
