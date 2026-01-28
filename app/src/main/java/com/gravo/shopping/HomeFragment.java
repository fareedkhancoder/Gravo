package com.gravo.shopping;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryItemClickListener {

    // Interface for Activity communication
    public interface OnHomeFragmentInteractionListener {
        void navigateToCategories(String categoryId);
    }

    private OnHomeFragmentInteractionListener mListener;
    private static final int SLIDE_DURATION = 3000;

    // MVVM: ViewModel
    private HomeViewModel homeViewModel;

    // UI Components
    private RecyclerView categoriesRecyclerView, dealsRecyclerView, trendingRecyclerView;
    private ShimmerFrameLayout shimmertrending, shimmerdeals, shimmerbanner, shimmerCategory, shimmerBottom;
    private TextView address;
    private ViewPager2 bannerViewPager;
    private LinearLayout indicatorContainer;

    // Adapters
    private CategoryAdapter categoryAdapter;
    private ProductAdapter dealsAdapter, trendingAdapter;
    private BannerAdapter bannerAdapter;

    // Banner Logic
    private List<Banner> currentBanners = new ArrayList<>();
    private ValueAnimator progressAnimator;

    // Address & Search
    private FirebaseAuth mAuth;
    private FirebaseFirestore db; // Kept only for Address logic temporarily
    private ActivityResultLauncher<Intent> addressLauncher;
    private TextView tvNoMoreProducts;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeFragmentInteractionListener) {
            mListener = (OnHomeFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnHomeFragmentInteractionListener");
        }
    }

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

        // Initialize Firebase (Only for Address/Auth)
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1. Initialize Views
        initializeViews(view);

        // 2. Setup RecyclerViews (Layout Managers & Empty Adapters)
        setupRecyclerViews(view);

        // 3. Initialize ViewModel (The Brains 🧠)
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // 4. Start Observing Data (The Magic ✨)
        observeViewModel();
        homeViewModel.getIsEndOfList().observe(getViewLifecycleOwner(), isEnd -> {
            if (isEnd) {
                // Stop Shimmer and Show Message
                shimmerBottom.stopShimmer();
                shimmerBottom.setVisibility(View.GONE);
                tvNoMoreProducts.setVisibility(View.VISIBLE);
            }
        });


        // 5. Setup Listeners (Clicks & Scrolls)
        setupClickListeners();
        setupScrollListeners(view);
        setupBannerPageChangeCallback();

        // 6. Fetch non-ViewModel data (Address)
        addressLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            // fetchDefaultAddress(); <-- Ye DELETE karein
            homeViewModel.refreshAddress(); // <-- Ye ADD karein
        });
    }

    private void initializeViews(View view) {
        shimmertrending = view.findViewById(R.id.trendingShimmerLayout);
        shimmerdeals = view.findViewById(R.id.dealsShimmerLayout);
        shimmerbanner = view.findViewById(R.id.slideshow);
        shimmerCategory = view.findViewById(R.id.shimmer_ct);
        shimmerBottom = view.findViewById(R.id.shimmerBottom);

        address = view.findViewById(R.id.address);
        indicatorContainer = view.findViewById(R.id.indicatorContainer);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);

        // Start shimmers immediately
        shimmerbanner.startShimmer();
        shimmerCategory.startShimmer();
        shimmerdeals.startShimmer();
        shimmertrending.startShimmer();
        tvNoMoreProducts = view.findViewById(R.id.tvNoMoreProducts);
    }

    private void setupRecyclerViews(View view) {
        // Categories
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(getContext(), new ArrayList<>(), this);
        categoriesRecyclerView.setAdapter(categoryAdapter);

        // Deals
        dealsRecyclerView = view.findViewById(R.id.dealsRecyclerView);
        dealsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        dealsAdapter = new ProductAdapter(getContext(), new ArrayList<>(), R.layout.item_product, this);
        dealsRecyclerView.setAdapter(dealsAdapter);

        dealsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // dx > 0 matlab user Right side scroll kar raha hai
                if (dx > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        // Logic: Kya hum end ke paas hain? (Total - Visible <= FirstVisible + Threshold)
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount) {
                            // Next batch load karo
                            homeViewModel.loadNextDealsPage();
                        }
                    }
                }
            }
        });

        // Trending
        trendingRecyclerView = view.findViewById(R.id.trendingRecyclerView);
        trendingRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        trendingAdapter = new ProductAdapter(getContext(), new ArrayList<>(), R.layout.item_product_grid, this);
        trendingRecyclerView.setAdapter(trendingAdapter);
    }

    private void observeViewModel() {
        // --- Observe Banners ---
        homeViewModel.getBanners().observe(getViewLifecycleOwner(), banners -> {
            // Stop shimmer regardless of whether we have data or not
            shimmerbanner.stopShimmer();
            shimmerbanner.setVisibility(View.GONE);

            if (banners != null && !banners.isEmpty()) {
                currentBanners = banners;
                bannerViewPager.setVisibility(View.VISIBLE);
                indicatorContainer.setVisibility(View.VISIBLE);

                // Update adapter
                if (bannerAdapter == null) {
                    bannerAdapter = new BannerAdapter(banners);
                    bannerViewPager.setAdapter(bannerAdapter);
                } else {
                    bannerAdapter.updateList(banners);
                }
                setupBannerIndicators(banners.size());
                startBannerProgress(0);
            } else {
                // Handle empty state (optional: hide the banner area entirely)
                bannerViewPager.setVisibility(View.GONE);
                indicatorContainer.setVisibility(View.GONE);
            }

            homeViewModel.getUserAddress().observe(getViewLifecycleOwner(), userAddress -> {
                if (userAddress != null) {
                    address.setText("Deliver to: " + userAddress.toString()); // Ensure Address class has valid toString()
                } else {
                    address.setText("Set Default Address");
                }
            });
        });

        // --- Observe Categories ---
        homeViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                shimmerCategory.stopShimmer();
                shimmerCategory.setVisibility(View.GONE);
                categoriesRecyclerView.setVisibility(View.VISIBLE);

                // Update Adapter
                categoryAdapter.updateList(categories);
            }
        });

        // --- Observe Deals ---
        homeViewModel.getDeals().observe(getViewLifecycleOwner(), deals -> {
            if (deals != null) {
                shimmerdeals.stopShimmer();
                shimmerdeals.setVisibility(View.GONE);
                dealsRecyclerView.setVisibility(View.VISIBLE);

                // Update Adapter
                dealsAdapter.updateList(deals);
            }
        });

        // --- Observe Trending Products ---
        homeViewModel.getTrending().observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                shimmertrending.stopShimmer();
                shimmertrending.setVisibility(View.GONE);
                shimmerBottom.stopShimmer(); // Hide bottom loader
                shimmerBottom.setVisibility(View.GONE);
                trendingRecyclerView.setVisibility(View.VISIBLE);

                // Update Adapter
                trendingAdapter.updateList(products);
            }
        });
    }

    // --- Infinite Scroll Logic ---
    private void setupScrollListeners(View view) {
        NestedScrollView nestedScrollView = view.findViewById(R.id.nestedScrollView);
        if (nestedScrollView != null) {
            nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                // Calculate if reached bottom
                int totalHeight = v.getChildAt(0).getMeasuredHeight();
                int currentHeight = v.getMeasuredHeight();

                // Threshold (50px) to detect bottom
                if (scrollY >= (totalHeight - currentHeight - 50)) {

                    // 🛑 CRITICAL FIX: Check 'isEndOfList' BEFORE showing shimmer
                    // If we are at the end, 'isEnd' will be true, so we SKIP this block.
                    Boolean isEnd = homeViewModel.getIsEndOfList().getValue();

                    if (isEnd == null || !isEnd) {
                        // Only show shimmer if there are potentially more products
                        shimmerBottom.setVisibility(View.VISIBLE);
                        shimmerBottom.startShimmer();

                        // Ask ViewModel to load more
                        homeViewModel.loadNextTrendingPage();
                    }
                }
            });
        }
    }

    // --- Banner Animation Logic (Preserved from your code) ---
    private void setupBannerIndicators(int count) {
        indicatorContainer.removeAllViews();
        for (int i = 0; i < count; i++) {
            View v = getLayoutInflater().inflate(R.layout.item_banner_indicator, indicatorContainer, false);
            indicatorContainer.addView(v);
        }
    }

    private void setupBannerPageChangeCallback() {
        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                indicatorContainer.post(() -> startBannerProgress(position));
            }
        });
    }

    private void startBannerProgress(int position) {
        if (progressAnimator != null) {
            progressAnimator.removeAllUpdateListeners();
            progressAnimator.removeAllListeners();
            progressAnimator.cancel();
        }

        if (currentBanners.isEmpty() || indicatorContainer == null || !isAdded()) return;

        int activeWidth = (int) (40 * getResources().getDisplayMetrics().density);
        int inactiveWidth = (int) (12 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < indicatorContainer.getChildCount(); i++) {
            ProgressBar bar = (ProgressBar) indicatorContainer.getChildAt(i);
            if (bar != null) {
                if (bar.getProgressDrawable() != null) {
                    bar.getProgressDrawable().mutate();
                }

                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
                if (i == position) {
                    params.width = activeWidth;
                    bar.setProgress(0);
                } else {
                    params.width = inactiveWidth;
                    bar.setProgress(i < position ? 1000 : 0);
                }
                bar.setLayoutParams(params);
            }
        }

        // Avoid index out of bounds
        if (position >= indicatorContainer.getChildCount()) return;

        ProgressBar currentBar = (ProgressBar) indicatorContainer.getChildAt(position);
        if (currentBar == null) return;

        progressAnimator = ValueAnimator.ofInt(0, 1000);
        progressAnimator.setDuration(SLIDE_DURATION);
        progressAnimator.setInterpolator(new LinearInterpolator());

        progressAnimator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            currentBar.setProgress(val);
        });

        progressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAdded() && bannerViewPager != null && !currentBanners.isEmpty()) {
                    int nextItem = (bannerViewPager.getCurrentItem() + 1) % currentBanners.size();
                    bannerViewPager.setCurrentItem(nextItem, true);
                }
            }
        });

        progressAnimator.start();
    }

    // --- Helper Logic (Address, Clicks) ---
    private void setupClickListeners() {
        addressLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> fetchDefaultAddress());
        address.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MyAddressesActivity.class);
            intent.putExtra("DEFAULT_SETTER_MODE", true);
            addressLauncher.launch(intent);
        });

        View searchBar = getView().findViewById(R.id.search);
        if (searchBar != null) searchBar.setOnClickListener(v -> startActivity(new Intent(getActivity(), SearchActivity.class)));
    }

    // Keeping this local for now as it wasn't in our ViewModel plan yet
    private void fetchDefaultAddress() {

    }

    @Override
    public void onProductClick(Product product) {
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        if (homeViewModel != null) {
            homeViewModel.incrementClicks(product.getProductId());
        }
        startActivity(intent);
    }
    @Override
    public void onCategoryItemClick(Category category) {
        if (mListener != null) mListener.navigateToCategories(category.getId());
    }

    // --- Lifecycle Cleanup ---
    @Override
    public void onPause() {
        super.onPause();
        if (progressAnimator != null) progressAnimator.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (progressAnimator != null && progressAnimator.isPaused()) progressAnimator.resume();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
    }
}