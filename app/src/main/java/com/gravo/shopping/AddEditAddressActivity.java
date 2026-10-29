package com.gravo.shopping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.viewmodel.AddEditAddressViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class AddEditAddressActivity extends AppCompatActivity {

    private static final String TAG = "AddEditAddress";

    private static final double FALLBACK_LAT = 28.5333;
    private static final double FALLBACK_LNG = 79.5833;
    private static final float MAP_ZOOM = 16f;
    private static final double AUTOCOMPLETE_RADIUS_METERS = 10_000d;
    private static final long SEARCH_DEBOUNCE_MS = 400L;

    private AddEditAddressViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private CancellationTokenSource locationCancellationSource;

    private GoogleMap googleMap;
    private boolean isMapMode = false;
    private boolean hasMapLocation = false;

    private FrameLayout mapContainer;
    private View pincodeUseMyLocationContainer;
    private View pincodeLayout;
    private View stateCityContainer;
    private View houseNoLayout;
    private View roadAreaLayout;
    private View addressTypeContainer;

    private TextView tvSelectedAddress;
    private TextView tvAddAlternatePhone;
    private TextInputEditText fullNameEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText pincodeEditText;
    private TextInputEditText stateEditText;
    private TextInputEditText cityEditText;
    private TextInputEditText houseNoEditText;
    private TextInputEditText roadAreaEditText;
    private TextInputEditText alternatePhoneEditText;
    private TextInputLayout alternatePhoneLayout;

    private MaterialButton btnUseMyLocation;
    private MaterialButton btnHome;
    private MaterialButton btnWork;
    private MaterialButton saveAddressButton;
    private FloatingActionButton btnCurrentLocation;
    private MaterialToolbar toolbar;

    private ViewGroup originalButtonParent;
    private int originalButtonIndex = -1;
    private ViewGroup.LayoutParams originalButtonLayoutParams;

    private boolean isEditMode = false;
    private String addressIdToEdit;
    private String selectedType = "HOME";
    private boolean isDefaultAddress = false;

    private double savedLat = 0.0;
    private double savedLng = 0.0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<AutocompletePrediction> currentPredictions = new ArrayList<>();
    private boolean isUpdatingMapAddress = false;

    private ListPopupWindow statePopup;
    private ListPopupWindow cityPopup;
    private ListPopupWindow streetPopup;

    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fineGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_FINE_LOCATION)
                        );
                        boolean coarseGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_COARSE_LOCATION)
                        );

                        if (fineGranted || coarseGranted) {
                            loadBestAvailableLocation();
                        } else {
                            Log.d(TAG, "Location permission denied. Using fallback location.");
                            moveMapToFallbackLocation();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_address);

        setupWindowInsets();

        if (!initDependencies()) {
            return;
        }

        if (!initViews()) {
            Toast.makeText(this, "Unable to initialize address screen.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupObservers();
        setupDropdowns();
        setupStreetAutocomplete();
        setupClickListeners();
        readIntent();
    }

    private void setupWindowInsets() {
        View main = findViewById(R.id.main);
        if (main == null) {
            Log.e(TAG, "Main layout view is missing.");
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(main, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return insets;
        });
    }

    private boolean initDependencies() {
        try {
            viewModel = new ViewModelProvider(this).get(AddEditAddressViewModel.class);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            if (!Places.isInitialized()) {
                String mapsKey = getString(R.string.google_maps_key);
                if (mapsKey == null || mapsKey.trim().isEmpty()) {
                    Log.e(TAG, "google_maps_key is empty.");
                    placesClient = null;
                } else {
                    Places.initializeWithNewPlacesApiEnabled(
                            getApplicationContext(),
                            mapsKey
                    );
                }
            }

            if (Places.isInitialized()) {
                placesClient = Places.createClient(this);
                sessionToken = AutocompleteSessionToken.newInstance();
            }

            return viewModel != null && fusedLocationClient != null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize dependencies.", e);
            return false;
        }
    }

    private boolean initViews() {
        toolbar = findViewById(R.id.toolbarAddEditAddress);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        pincodeEditText = findViewById(R.id.pincodeEditText);
        stateEditText = findViewById(R.id.stateEditText);
        cityEditText = findViewById(R.id.cityEditText);
        houseNoEditText = findViewById(R.id.houseNoEditText);
        roadAreaEditText = findViewById(R.id.roadAreaEditText);
        alternatePhoneEditText = findViewById(R.id.alternatePhoneEditText);
        alternatePhoneLayout = findViewById(R.id.alternatePhoneLayout);

        tvAddAlternatePhone = findViewById(R.id.tvAddAlternatePhone);
        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);

        btnUseMyLocation = findViewById(R.id.btnUseMyLocation);
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation);
        btnHome = findViewById(R.id.btnHome);
        btnWork = findViewById(R.id.btnWork);
        saveAddressButton = findViewById(R.id.saveAddressButton);

        mapContainer = findViewById(R.id.mapContainer);
        pincodeUseMyLocationContainer = findViewById(R.id.pincodeUseMyLocationContainer);
        pincodeLayout = findViewById(R.id.pincodeLayout);
        stateCityContainer = findViewById(R.id.stateCityContainer);
        houseNoLayout = findViewById(R.id.houseNoLayout);
        roadAreaLayout = findViewById(R.id.roadAreaLayout);
        addressTypeContainer = findViewById(R.id.addressTypeContainer);

        if (toolbar == null
                || fullNameEditText == null
                || phoneEditText == null
                || pincodeEditText == null
                || stateEditText == null
                || cityEditText == null
                || houseNoEditText == null
                || roadAreaEditText == null
                || alternatePhoneEditText == null
                || alternatePhoneLayout == null
                || tvAddAlternatePhone == null
                || tvSelectedAddress == null
                || btnUseMyLocation == null
                || btnCurrentLocation == null
                || btnHome == null
                || btnWork == null
                || saveAddressButton == null
                || mapContainer == null
                || pincodeUseMyLocationContainer == null
                || pincodeLayout == null
                || stateCityContainer == null
                || houseNoLayout == null
                || roadAreaLayout == null
                || addressTypeContainer == null) {

            Log.e(TAG, "One or more required views are missing from activity_add_edit_address.xml.");
            return false;
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (isMapMode) {
                enterManualMode();
            } else {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        originalButtonParent = (ViewGroup) btnUseMyLocation.getParent();
        if (originalButtonParent == null) {
            Log.e(TAG, "Select on Map button has no parent.");
            return false;
        }

        originalButtonIndex = originalButtonParent.indexOfChild(btnUseMyLocation);
        originalButtonLayoutParams = btnUseMyLocation.getLayoutParams();

        if (originalButtonIndex < 0 || originalButtonLayoutParams == null) {
            Log.e(TAG, "Unable to preserve Select on Map button layout params.");
            return false;
        }

        updateTypeSelectionUI();
        return true;
    }

    private void setupObservers() {
        viewModel.getSaveSuccess().observe(this, success -> {
            if (!Boolean.TRUE.equals(success) || isFinishing() || isDestroyed()) {
                return;
            }

            Toast.makeText(this, "Address saved successfully!", Toast.LENGTH_SHORT).show();
            finish();
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (saveAddressButton == null || error == null || error.trim().isEmpty()) {
                return;
            }

            saveAddressButton.setEnabled(true);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    private void setupClickListeners() {
        btnUseMyLocation.setOnClickListener(v -> {
            if (isMapMode) {
                enterManualMode();
            } else {
                enterMapMode();
            }
        });

        btnCurrentLocation.setOnClickListener(v -> {
            if (!isMapMode) {
                return;
            }
            requestPermissionThenLoadLocation();
        });

        btnHome.setOnClickListener(v -> {
            selectedType = "HOME";
            updateTypeSelectionUI();
        });

        btnWork.setOnClickListener(v -> {
            selectedType = "WORK";
            updateTypeSelectionUI();
        });

        tvAddAlternatePhone.setOnClickListener(v -> {
            tvAddAlternatePhone.setVisibility(View.GONE);
            alternatePhoneLayout.setVisibility(View.VISIBLE);
            alternatePhoneEditText.requestFocus();
        });

        alternatePhoneLayout.setEndIconOnClickListener(v -> {
            alternatePhoneEditText.setText("");
            alternatePhoneLayout.setVisibility(View.GONE);
            tvAddAlternatePhone.setVisibility(View.VISIBLE);
        });

        saveAddressButton.setOnClickListener(v -> validateAndSave());
    }

    private void readIntent() {
        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        if (extras == null) {
            return;
        }

        String addressId = extras.getString("ADDRESS_ID");
        if (addressId == null || addressId.trim().isEmpty()) {
            return;
        }

        isEditMode = true;
        addressIdToEdit = addressId;
        toolbar.setTitle("Edit Address");
        loadAddressDetails();
    }

    private void enterMapMode() {
        if (isMapMode || mapContainer == null || btnUseMyLocation == null) {
            return;
        }
        dismissAllPopups();

        isMapMode = true;

        pincodeUseMyLocationContainer.setVisibility(View.GONE);
        stateCityContainer.setVisibility(View.GONE);
        houseNoLayout.setVisibility(View.GONE);
        roadAreaLayout.setVisibility(View.GONE);
        addressTypeContainer.setVisibility(View.GONE);
        mapContainer.setVisibility(View.VISIBLE);

        ViewGroup currentParent = getSafeParent(btnUseMyLocation);
        if (currentParent != null) {
            currentParent.removeView(btnUseMyLocation);
        }

        btnUseMyLocation.setText("Add Manually");

        FrameLayout.LayoutParams mapButtonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        mapButtonParams.gravity = Gravity.TOP | Gravity.END;

        int margin = dpToPx(12);
        mapButtonParams.setMargins(margin, margin, margin, margin);

        mapContainer.addView(btnUseMyLocation, mapButtonParams);
        btnUseMyLocation.bringToFront();

        setupMap();
    }

    private void enterManualMode() {
        if (!isMapMode || btnUseMyLocation == null) {
            return;
        }

        isMapMode = false;

        ViewGroup currentParent = getSafeParent(btnUseMyLocation);
        if (currentParent != null) {
            currentParent.removeView(btnUseMyLocation);
        }

        restoreButtonToOriginalParent();
        btnUseMyLocation.setText("Select on Map");

        mapContainer.setVisibility(View.GONE);
        pincodeUseMyLocationContainer.setVisibility(View.VISIBLE);
        pincodeLayout.setVisibility(View.VISIBLE);
        stateCityContainer.setVisibility(View.VISIBLE);
        houseNoLayout.setVisibility(View.VISIBLE);
        roadAreaLayout.setVisibility(View.VISIBLE);
        addressTypeContainer.setVisibility(View.VISIBLE);
    }

    private void restoreButtonToOriginalParent() {
        if (originalButtonParent == null || btnUseMyLocation == null) {
            return;
        }

        int safeIndex = Math.max(
                0,
                Math.min(originalButtonIndex, originalButtonParent.getChildCount())
        );

        if (btnUseMyLocation.getParent() != null) {
            return;
        }

        originalButtonParent.addView(
                btnUseMyLocation,
                safeIndex,
                originalButtonLayoutParams
        );
    }

    private ViewGroup getSafeParent(View view) {
        if (view == null) {
            return null;
        }
        return view.getParent() instanceof ViewGroup
                ? (ViewGroup) view.getParent()
                : null;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void updateTypeSelectionUI() {
        if (btnHome == null || btnWork == null) {
            return;
        }

        if ("HOME".equals(selectedType)) {
            btnHome.setBackgroundTintList(getResources().getColorStateList(R.color.dialog_accent_sky_blue));
            btnHome.setTextColor(getResources().getColor(R.color.white));
            btnWork.setBackgroundTintList(getResources().getColorStateList(android.R.color.transparent));
            btnWork.setTextColor(getResources().getColor(R.color.text_gray));
        } else {
            btnWork.setBackgroundTintList(getResources().getColorStateList(R.color.dialog_accent_sky_blue));
            btnWork.setTextColor(getResources().getColor(R.color.white));
            btnHome.setBackgroundTintList(getResources().getColorStateList(android.R.color.transparent));
            btnHome.setTextColor(getResources().getColor(R.color.text_gray));
        }
    }

    @SuppressLint("MissingPermission")
    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);

        if (mapFragment == null) {
            Log.e(TAG, "SupportMapFragment not found.");
            setSelectedAddress("Map is unavailable.");
            return;
        }

        mapFragment.getMapAsync(map -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            googleMap = map;
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(true);

            googleMap.setOnCameraIdleListener(() -> {
                if (!isMapMode || googleMap == null) {
                    return;
                }

                LatLng target = googleMap.getCameraPosition() != null
                        ? googleMap.getCameraPosition().target
                        : null;

                if (target == null || !isValidCoordinates(target.latitude, target.longitude)) {
                    return;
                }

                savedLat = target.latitude;
                savedLng = target.longitude;
                hasMapLocation = true;
                fetchAddressFromCoordinates(target.latitude, target.longitude);
            });

            requestPermissionThenLoadLocation();
        });
    }

    private void requestPermissionThenLoadLocation() {
        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            moveMapToFallbackLocation();
            return;
        }

        loadBestAvailableLocation();
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void loadBestAvailableLocation() {
        if (googleMap == null) {
            return;
        }

        if (!hasLocationPermission()) {
            moveMapToFallbackLocation();
            return;
        }

        if (!isGpsEnabled()) {
            Log.d(TAG, "GPS is OFF. Using fallback location.");
            moveMapToFallbackLocation();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, lastLocation -> {
                    if (lastLocation != null
                            && isValidCoordinates(lastLocation.getLatitude(), lastLocation.getLongitude())) {

                        moveMapToLocation(
                                lastLocation.getLatitude(),
                                lastLocation.getLongitude()
                        );

                        fetchCurrentLocationForMap();
                    } else {
                        fetchCurrentLocationForMap();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to obtain last location.", e);
                    fetchCurrentLocationForMap();
                });
    }

    @SuppressLint("MissingPermission")
    private void fetchCurrentLocationForMap() {
        if (googleMap == null || !hasLocationPermission()) {
            if (!hasMapLocation) {
                moveMapToFallbackLocation();
            }
            return;
        }

        if (!isGpsEnabled()) {
            if (!hasMapLocation) {
                moveMapToFallbackLocation();
            }
            return;
        }

        if (locationCancellationSource != null) {
            locationCancellationSource.cancel();
        }

        locationCancellationSource = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        locationCancellationSource.getToken()
                )
                .addOnSuccessListener(this, location -> {
                    if (location != null
                            && isValidCoordinates(location.getLatitude(), location.getLongitude())) {

                        moveMapToLocation(
                                location.getLatitude(),
                                location.getLongitude()
                        );
                    } else if (!hasMapLocation) {
                        moveMapToFallbackLocation();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to obtain current location.", e);
                    if (!hasMapLocation) {
                        moveMapToFallbackLocation();
                    }
                });
    }

    private boolean isGpsEnabled() {
        LocationManager locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager == null) {
            return false;
        }

        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            Log.e(TAG, "Unable to check GPS status.", e);
            return false;
        }
    }

    private void moveMapToLocation(double latitude, double longitude) {
        if (googleMap == null || !isValidCoordinates(latitude, longitude)) {
            return;
        }

        savedLat = latitude;
        savedLng = longitude;
        hasMapLocation = true;

        googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        new LatLng(latitude, longitude),
                        MAP_ZOOM
                )
        );
    }

    private void moveMapToFallbackLocation() {
        if (googleMap == null) {
            return;
        }

        savedLat = FALLBACK_LAT;
        savedLng = FALLBACK_LNG;
        hasMapLocation = true;

        googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        new LatLng(FALLBACK_LAT, FALLBACK_LNG),
                        MAP_ZOOM
                )
        );
    }

    private boolean isValidCoordinates(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !Double.isInfinite(latitude)
                && !Double.isInfinite(longitude)
                && latitude >= -90.0
                && latitude <= 90.0
                && longitude >= -180.0
                && longitude <= 180.0;
    }

    private void fetchAddressFromCoordinates(double latitude, double longitude) {
        if (!isValidCoordinates(latitude, longitude)
                || isFinishing()
                || isDestroyed()) {
            return;
        }

        setSelectedAddress("Finding address...");

        try {
            executorService.execute(() -> {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());

                if (!Geocoder.isPresent()) {
                    runOnUiThread(() -> setSelectedAddress("Address lookup is unavailable."));
                    return;
                }

                try {
                    List<android.location.Address> addresses =
                            geocoder.getFromLocation(latitude, longitude, 1);

                    if (addresses == null || addresses.isEmpty()) {
                        runOnUiThread(() -> setSelectedAddress("Move map to select a location."));
                        return;
                    }

                    android.location.Address address = addresses.get(0);
                    runOnUiThread(() -> applyGeocodedAddress(address));

                } catch (IOException e) {
                    Log.e(TAG, "Reverse geocoding failed.", e);
                    runOnUiThread(() -> setSelectedAddress("Unable to read address."));
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected geocoder error.", e);
                    runOnUiThread(() -> setSelectedAddress("Unable to read address."));
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Geocoder executor is shutting down.", e);
        }
    }

    private void applyGeocodedAddress(android.location.Address address) {

        if (address == null || isFinishing() || isDestroyed()) {
            return;
        }

        isUpdatingMapAddress = true;

        String postalCode = safeText(address.getPostalCode());
        String locality = safeText(address.getLocality());
        String adminArea = safeText(address.getAdminArea());
        String subLocality = safeText(address.getSubLocality());
        String thoroughfare = safeText(address.getThoroughfare());

        if (!postalCode.isEmpty()) {
            pincodeEditText.setText(postalCode);
        }

        if (!locality.isEmpty()) {
            cityEditText.setText(locality);
        }

        if (!adminArea.isEmpty()) {
            stateEditText.setText(adminArea);
        }

        String area = !subLocality.isEmpty()
                ? subLocality
                : thoroughfare;

        if (!area.isEmpty()) {
            roadAreaEditText.setText(area);
        }

        isUpdatingMapAddress = false;

        dismissAllPopups();

        setSelectedAddress(buildAddressSummary(address));
    }

    private String buildAddressSummary(android.location.Address address) {
        if (address == null) {
            return "Move map to select a location.";
        }

        List<String> parts = new ArrayList<>();
        addIfNotEmpty(parts, address.getSubLocality());
        addIfNotEmpty(parts, address.getLocality());
        addIfNotEmpty(parts, address.getAdminArea());
        addIfNotEmpty(parts, address.getPostalCode());

        if (parts.isEmpty()) {
            String fullAddress = address.getAddressLine(0);
            return safeText(fullAddress).isEmpty()
                    ? "Move map to select a location."
                    : safeText(fullAddress);
        }

        return android.text.TextUtils.join(", ", parts);
    }

    private void addIfNotEmpty(List<String> parts, String value) {
        String text = safeText(value);
        if (!text.isEmpty() && !parts.contains(text)) {
            parts.add(text);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void setSelectedAddress(String text) {
        if (tvSelectedAddress == null) {
            return;
        }
        tvSelectedAddress.setText(
                text == null || text.trim().isEmpty()
                        ? "Move map to select a location."
                        : text
        );
    }

    private void setupDropdowns() {
        statePopup = setupListPopupWindow(
                stateEditText,
                Collections.singletonList("Uttar Pradesh")
        );

        cityPopup = setupListPopupWindow(
                cityEditText,
                Collections.singletonList("Bareilly")
        );
    }

    private ListPopupWindow setupListPopupWindow(
            TextInputEditText editText,
            List<String> originalData
    ) {
        if (editText == null || originalData == null || originalData.isEmpty()) {
            return null;
        }

        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(editText);
        popup.setWidth(ListPopupWindow.MATCH_PARENT);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>(originalData)
        );

        popup.setAdapter(adapter);

        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (isMapMode) {
                popup.dismiss();
                return;
            }

            String selected = adapter.getItem(position);

            if (selected != null) {
                isUpdatingMapAddress = true;
                editText.setText(selected);
                isUpdatingMapAddress = false;
            }

            popup.dismiss();
        });

        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                // NEVER show dropdown while map mode is active
                if (isMapMode || isUpdatingMapAddress) {
                    popup.dismiss();
                    return;
                }

                String text = s == null ? "" : s.toString().trim();

                if (text.isEmpty()) {
                    popup.dismiss();
                    return;
                }

                adapter.getFilter().filter(text);

                if (!popup.isShowing()) {
                    popup.show();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        return popup;
    }
    private void dismissAllPopups() {

        if (statePopup != null) {
            statePopup.dismiss();
        }

        if (cityPopup != null) {
            cityPopup.dismiss();
        }

        if (streetPopup != null) {
            streetPopup.dismiss();
        }

        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    private void setupStreetAutocomplete() {
        if (roadAreaEditText == null || placesClient == null) {
            return;
        }

        streetPopup = new ListPopupWindow(this);
        streetPopup.setAnchorView(roadAreaEditText);
        streetPopup.setWidth(ListPopupWindow.MATCH_PARENT);

        ArrayAdapter<String> streetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );
        streetPopup.setAdapter(streetAdapter);

        streetPopup.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= currentPredictions.size()) {
                return;
            }

            AutocompletePrediction prediction = currentPredictions.get(position);
            if (prediction == null) {
                return;
            }

            String text = prediction.getFullText(null) != null
                    ? prediction.getFullText(null).toString()
                    : "";

            if (!text.isEmpty()) {
                roadAreaEditText.setText(text);
            }

            streetPopup.dismiss();
            fetchPlaceDetails(prediction.getPlaceId());
        });

        roadAreaEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isMapMode || isUpdatingMapAddress) {
                    if (searchRunnable != null) {
                        handler.removeCallbacks(searchRunnable);
                        searchRunnable = null;
                    }

                    if (streetPopup != null) {
                        streetPopup.dismiss();
                    }

                    return;
                }
                String query = s == null ? "" : s.toString().trim();

                if (query.length() < 2 || placesClient == null) {
                    if (searchRunnable != null) {
                        handler.removeCallbacks(searchRunnable);
                    }
                    streetPopup.dismiss();
                    return;
                }

                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> searchPlaces(query, streetPopup, streetAdapter);
                handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No-op
            }
        });
    }

    private void searchPlaces(
            String query,
            ListPopupWindow streetPopup,
            ArrayAdapter<String> streetAdapter
    ) {
        if (placesClient == null || query == null || query.trim().length() < 2) {
            return;
        }

        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance();
        }

        double centerLat = hasMapLocation ? savedLat : FALLBACK_LAT;
        double centerLng = hasMapLocation ? savedLng : FALLBACK_LNG;

        CircularBounds restriction = CircularBounds.newInstance(
                new LatLng(centerLat, centerLng),
                AUTOCOMPLETE_RADIUS_METERS
        );

        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(sessionToken)
                        .setCountries(Collections.singletonList("IN"))
                        .setLocationRestriction(restriction)
                        .setQuery(query.trim())
                        .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(this, response -> {
                    if (response == null || isFinishing() || isDestroyed()) {
                        return;
                    }

                    currentPredictions.clear();
                    streetAdapter.clear();

                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        if (prediction == null) {
                            continue;
                        }

                        currentPredictions.add(prediction);

                        String text = prediction.getFullText(null) != null
                                ? prediction.getFullText(null).toString()
                                : "";

                        if (!text.isEmpty()) {
                            streetAdapter.add(text);
                        }
                    }

                    streetAdapter.notifyDataSetChanged();

                    if (!currentPredictions.isEmpty()) {
                        streetPopup.show();
                    } else {
                        streetPopup.dismiss();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Places autocomplete failed.", e);
                    currentPredictions.clear();
                    streetAdapter.clear();
                    streetAdapter.notifyDataSetChanged();
                    streetPopup.dismiss();
                });
    }

    private void fetchPlaceDetails(String placeId) {
        if (placesClient == null || placeId == null || placeId.trim().isEmpty()) {
            return;
        }

        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.NAME,
                Place.Field.LAT_LNG
        );

        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance();
        }

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, fields)
                .setSessionToken(sessionToken)
                .build();

        placesClient.fetchPlace(request)
                .addOnSuccessListener(this, response -> {
                    if (response == null || response.getPlace() == null) {
                        return;
                    }

                    Place place = response.getPlace();
                    LatLng latLng = place.getLatLng();

                    if (latLng != null
                            && isValidCoordinates(latLng.latitude, latLng.longitude)) {

                        savedLat = latLng.latitude;
                        savedLng = latLng.longitude;
                        hasMapLocation = true;
                    }

                    sessionToken = AutocompleteSessionToken.newInstance();
                })
                .addOnFailureListener(this, e ->
                        Log.e(TAG, "Failed to fetch place details.", e)
                );
    }

    private void loadAddressDetails() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (addressIdToEdit == null || addressIdToEdit.trim().isEmpty()) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("addresses")
                .document(addressIdToEdit)
                .get()
                .addOnSuccessListener(this, doc -> {
                    if (doc == null || !doc.exists() || isFinishing() || isDestroyed()) {
                        return;
                    }

                    Address address = doc.toObject(Address.class);
                    if (address == null) {
                        return;
                    }

                    fullNameEditText.setText(safeText(address.getFullName()));
                    phoneEditText.setText(safeText(address.getPhone()));
                    houseNoEditText.setText(safeText(address.getHouseNo()));
                    roadAreaEditText.setText(safeText(address.getStreet()));
                    cityEditText.setText(safeText(address.getCity()));
                    stateEditText.setText(safeText(address.getState()));
                    pincodeEditText.setText(safeText(address.getPincode()));

                    selectedType = safeText(address.getType()).isEmpty()
                            ? "HOME"
                            : address.getType().toUpperCase(Locale.ROOT);
                    isDefaultAddress = address.isDefault();

                    if (isValidCoordinates(address.getLatitude(), address.getLongitude())) {
                        savedLat = address.getLatitude();
                        savedLng = address.getLongitude();
                    }

                    String alternatePhone = safeText(address.getAlternatePhone());
                    if (!alternatePhone.isEmpty()) {
                        alternatePhoneEditText.setText(alternatePhone);
                        alternatePhoneLayout.setVisibility(View.VISIBLE);
                        tvAddAlternatePhone.setVisibility(View.GONE);
                    } else {
                        alternatePhoneLayout.setVisibility(View.GONE);
                        tvAddAlternatePhone.setVisibility(View.VISIBLE);
                    }

                    updateTypeSelectionUI();
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to load address.", e);
                    Toast.makeText(this, "Failed to load address.", Toast.LENGTH_SHORT).show();
                });
    }

    private void validateAndSave() {

        if (viewModel == null || saveAddressButton == null) {
            return;
        }

        String fullName = getText(fullNameEditText);
        String phone = getText(phoneEditText);
        String pincode = getText(pincodeEditText);
        String state = getText(stateEditText);
        String city = getText(cityEditText);
        String houseNo = getText(houseNoEditText);
        String roadArea = getText(roadAreaEditText);

        String alternatePhone =
                alternatePhoneLayout != null
                        && alternatePhoneLayout.getVisibility() == View.VISIBLE
                        ? getText(alternatePhoneEditText)
                        : "";

        // Common fields are always required
        if (fullName.isEmpty()) {
            showValidationError("Please enter full name.");
            return;
        }

        if (phone.isEmpty()) {
            showValidationError("Please enter phone number.");
            return;
        }

        /*
         * MAP MODE
         *
         * Address fields are populated from reverse geocoding.
         * Do not validate the hidden manual UI itself.
         */
        if (isMapMode) {

            if (!hasMapLocation) {
                showValidationError(
                        "Please select a location on the map."
                );
                return;
            }

            if (pincode.isEmpty()
                    || state.isEmpty()
                    || city.isEmpty()
                    || roadArea.isEmpty()) {

                showValidationError(
                        "Unable to determine the complete address. Move the map slightly and try again."
                );
                return;
            }

        } else {

            // MANUAL MODE
            if (pincode.isEmpty()) {
                showValidationError("Please enter pincode.");
                return;
            }

            if (state.isEmpty()) {
                showValidationError("Please select state.");
                return;
            }

            if (city.isEmpty()) {
                showValidationError("Please select city.");
                return;
            }

            if (houseNo.isEmpty()) {
                showValidationError("Please enter house/building number.");
                return;
            }

            if (roadArea.isEmpty()) {
                showValidationError("Please enter road/area/colony.");
                return;
            }
        }

        saveAddressButton.setEnabled(false);

        Address address = new Address();

        if (isEditMode && addressIdToEdit != null) {
            address.setAddressId(addressIdToEdit);
        }

        address.setFullName(fullName);
        address.setPhone(phone);
        address.setPincode(pincode);
        address.setState(state);
        address.setCity(city);
        address.setHouseNo(houseNo);
        address.setStreet(roadArea);
        address.setType(selectedType);
        address.setDefault(isDefaultAddress);
        address.setLatitude(savedLat);
        address.setLongitude(savedLng);
        address.setAlternatePhone(
                alternatePhone.isEmpty() ? null : alternatePhone
        );

        viewModel.checkServiceabilityAndSave(
                address,
                isEditMode
        );
    }
    private void showValidationError(String message) {

        if (saveAddressButton != null) {
            saveAddressButton.setEnabled(true);
        }

        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "Please complete the address."
                        : message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (locationCancellationSource != null) {
            locationCancellationSource.cancel();
            locationCancellationSource = null;
        }

        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }

        executorService.shutdownNow();
        currentPredictions.clear();
        googleMap = null;

        super.onDestroy();
    }
}
