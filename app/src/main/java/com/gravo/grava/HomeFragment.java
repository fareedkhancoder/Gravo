package com.gravo.grava;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryItemClickListener {

    // 1. Interface for communication with the host activity
    public interface OnHomeFragmentInteractionListener {
        void navigateToCategories(String categoryId);
    }
    private OnHomeFragmentInteractionListener mListener;

    private static final String TAG = "HomeFragment";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // UI Components
    private RecyclerView categoriesRecyclerView, dealsRecyclerView, trendingRecyclerView;
    private ShimmerFrameLayout shimmertrending, shimmerdeals, shimmerbanner, shimmerCategory;
    private TextView address;
    private ViewPager2 bannerViewPager;

    // Adapters
    private CategoryAdapter categoryAdapter;
    private ProductAdapter dealsAdapter;
    private ProductAdapter trendingAdapter;
    private BannerAdapter bannerAdapter; // CORRECTED: Removed extra semicolon

    // Data Lists
    private List<Category> categoryList;
    private List<Product> dealsList;
    private List<Product> trendingList;
    private List<Product> bannerProductList;

    // Auto-slide Handler
    private final Handler sliderHandler = new Handler();
    private Runnable sliderRunnable;

    private ActivityResultLauncher<Intent> addressLauncher;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeFragmentInteractionListener) {
            mListener = (OnHomeFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnHomeFragmentInteractionListener");
        }
    }

    // ADDED: Best practice to nullify listener on detach
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "FlowTracker: onViewCreated -> Fragment view is created.");

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize all data lists
        categoryList = new ArrayList<>();
        dealsList = new ArrayList<>();
        trendingList = new ArrayList<>();
        bannerProductList = new ArrayList<>();

        // Initialize UI Components
        initializeViews(view);
        setupClickListeners();

        // Setup all UI components and their adapters
        setupCategoriesRecyclerView(view);
        setupDealsRecyclerView(view);
        setupTrendingRecyclerView(view);
        setupBannerViewPager(view);

        // Fetch all data from Firestore
        fetchAllData();
    }

    private void initializeViews(@NonNull View view) {
        shimmertrending = view.findViewById(R.id.trendingShimmerLayout);
        shimmerdeals = view.findViewById(R.id.dealsShimmerLayout);
        shimmerbanner = view.findViewById(R.id.slideshow);
        shimmerCategory = view.findViewById(R.id.shimmer_ct);
        address = view.findViewById(R.id.address);
    }

    private void setupClickListeners() {
        // Address click listener
        addressLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Refresh address text regardless of result
                    fetchDefaultAddress();
                }
        );
        address.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MyAddressesActivity.class);
            intent.putExtra("DEFAULT_SETTER_MODE", true);
            addressLauncher.launch(intent);
        });

        // Search bar click listener (assuming R.id.search is the container)
        View searchBarCard = getView().findViewById(R.id.search);
        if (searchBarCard != null) {
            searchBarCard.setOnClickListener(v -> {
                if (getActivity() != null) {
                    startActivity(new Intent(getActivity(), SearchActivity.class));
                }
            });
        }
    }

    private void fetchAllData() {
        fetchCategories();
        fetchBannerProducts();
        fetchDeals();
        fetchTrendingProducts();
        fetchDefaultAddress();
    }

    // --- UI Setup Methods ---

    private void setupCategoriesRecyclerView(View view) {
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(getContext(), categoryList, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupDealsRecyclerView(View view) {
        dealsRecyclerView = view.findViewById(R.id.dealsRecyclerView);
        dealsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        dealsAdapter = new ProductAdapter(getContext(), dealsList, R.layout.item_product, this);
        dealsRecyclerView.setAdapter(dealsAdapter);
    }

    private void setupTrendingRecyclerView(View view) {
        trendingRecyclerView = view.findViewById(R.id.trendingRecyclerView);
        trendingRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        trendingAdapter = new ProductAdapter(getContext(), trendingList, R.layout.item_product_grid, this);
        trendingRecyclerView.setAdapter(trendingAdapter);
    }

    private void setupBannerViewPager(View view) {
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        bannerAdapter = new BannerAdapter(bannerProductList);
        bannerViewPager.setAdapter(bannerAdapter);

        sliderRunnable = () -> {
            if (bannerViewPager != null) {
                bannerViewPager.setCurrentItem(bannerViewPager.getCurrentItem() + 1);
            }
        };

        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                sliderHandler.removeCallbacks(sliderRunnable);
                sliderHandler.postDelayed(sliderRunnable, 3000);
            }
        });
    }

    // --- Data Fetching Methods ---

    private void fetchCategories() {
        db.collection("categories").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                // CORRECTED: Stop shimmer outside the loop to handle empty results
                shimmerCategory.stopShimmer();
                shimmerCategory.setVisibility(View.GONE);
                categoriesRecyclerView.setVisibility(View.VISIBLE);

                categoryList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    categoryList.add(document.toObject(Category.class));
                }
                if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();
            }
        });
    }

    private void fetchBannerProducts() {
        db.collection("products").orderBy("stockQuantity", Query.Direction.ASCENDING).limit(4).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                // CORRECTED: Stop shimmer outside the loop
                shimmerbanner.stopShimmer();
                shimmerbanner.setVisibility(View.GONE);
                bannerViewPager.setVisibility(View.VISIBLE);

                bannerProductList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Product product = document.toObject(Product.class);
                    product.setProductId(document.getId());
                    bannerProductList.add(product);
                }
                if (bannerAdapter != null) bannerAdapter.notifyDataSetChanged();
            }
        });
    }

    private void fetchDeals() {
        db.collection("products").limit(5).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                // CORRECTED: Stop shimmer outside the loop
                shimmerdeals.stopShimmer();
                shimmerdeals.setVisibility(View.GONE);
                dealsRecyclerView.setVisibility(View.VISIBLE);

                dealsList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Product product = document.toObject(Product.class);
                    product.setProductId(document.getId());
                    dealsList.add(product);
                }
                if (dealsAdapter != null) dealsAdapter.notifyDataSetChanged();
            }
        });
    }

    private void fetchTrendingProducts() {
        db.collection("products").orderBy("stockQuantity", Query.Direction.DESCENDING).limit(6).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                // CORRECTED: Stop shimmer outside the loop
                shimmertrending.stopShimmer();
                shimmertrending.setVisibility(View.GONE);
                trendingRecyclerView.setVisibility(View.VISIBLE);

                trendingList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Product product = document.toObject(Product.class);
                    product.setProductId(document.getId());
                    trendingList.add(product);
                }
                if (trendingAdapter != null) trendingAdapter.notifyDataSetChanged();
            }
        });
    }

    private void fetchDefaultAddress() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            address.setText("No user logged in.");
            return;
        }
        String userId = currentUser.getUid();
        db.collection("users").document(userId).collection("addresses")
                .whereEqualTo("default", true)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            address.setText("No default address set.");
                        } else {
                            QueryDocumentSnapshot document = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                            Address defaultAddress = document.toObject(Address.class);
                            address.setText("Deliver To: " + defaultAddress.toString());
                        }
                    } else {
                        Log.e(TAG, "Error fetching default address", task.getException());
                        address.setText("Could not load address.");
                    }
                });
    }

    // --- Click Listener Implementation ---
    @Override
    public void onProductClick(Product product) {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        startActivity(intent);
    }

    @Override
    public void onCategoryItemClick(Category category) {
        if (mListener != null) {
            mListener.navigateToCategories(category.getId());
            Log.d(TAG, "onCategoryItemClick: sending category id" + category.getId());
        }
    }

    // --- Lifecycle and Runnable for Banner ---
    @Override
    public void onPause() {
        super.onPause();
        sliderHandler.removeCallbacks(sliderRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start slider only if there are items
        if(bannerProductList != null && !bannerProductList.isEmpty()){
            sliderHandler.postDelayed(sliderRunnable, 3000);
        }
    }
}