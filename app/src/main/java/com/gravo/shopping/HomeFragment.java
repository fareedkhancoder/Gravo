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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.gravo.shopping.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryItemClickListener {

    // Interface for Activity communication
    public interface OnHomeFragmentInteractionListener {
        void navigateToCategories(String categoryId);
        void navigateToCart();
    }

    private OnHomeFragmentInteractionListener mListener;
    private static final int SLIDE_DURATION = 3000;

    // MVVM: ViewModel
    private HomeViewModel homeViewModel;

    // UI Components
    private RecyclerView categoriesRecyclerView, dealsRecyclerView, trendingRecyclerView;
    private ShimmerFrameLayout shimmertrending, shimmerdeals, shimmerbanner, shimmerCategory, shimmerBottom;
    private TextView address, timeSlot;
    private ImageView btnCart, btnNotification;
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
    private ActivityResultLauncher<Intent> addressLauncher;
    private TextView tvNoMoreProducts;
    private boolean addressPromptShown;

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
        // 1. Initialize Views
        initializeViews(view);

        // 2. Setup RecyclerViews (Layout Managers & Empty Adapters)
        setupRecyclerViews(view);

        // 3. Initialize ViewModel (The Brains 🧠)
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        observeViewModel(); // Saare LiveData observe karna shuru karein
        homeViewModel.startDataLoad(); // Data fetch karna start karein

        // 4. Start Observing Data (The Magic ✨)
        homeViewModel.getRequireLoginEvent().observe(getViewLifecycleOwner(), requireLogin -> {
            if (requireLogin != null && requireLogin) {

                // 1. Toast dikhayein
                android.widget.Toast.makeText(requireContext(), "Login again to browse", android.widget.Toast.LENGTH_SHORT).show();

                // 2. MainActivity par bhej dein
                android.content.Intent intent = new android.content.Intent(requireContext(), MainActivity.class);
                // Ye flags purani back-history clear kar denge taaki user back press karke wapas Home par na aa paye
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            }
        });

        homeViewModel.getAddressNotFoundEvent().observe(getViewLifecycleOwner(), isNotFound -> {
            if (Boolean.TRUE.equals(isNotFound)) {
                android.content.Intent intent = new android.content.Intent(requireContext(), AddEditAddressActivity.class);
                startActivity(intent);
                requireActivity().finish();
            }
        });
        homeViewModel.getCurrentAddress().observe(getViewLifecycleOwner(), currentAddr -> {
            if (currentAddr != null) {
                StringBuilder builder = new StringBuilder("Deliver to: ");

                // 1. Street / Area (Lower level)
                if (currentAddr.getStreet() != null && !currentAddr.getStreet().trim().isEmpty()) {
                    builder.append(currentAddr.getStreet()).append(", ");
                }

                // 2. City
                if (currentAddr.getCity() != null && !currentAddr.getCity().trim().isEmpty()) {
                    builder.append(currentAddr.getCity()).append(", ");
                }

                // 3. State
                if (currentAddr.getState() != null && !currentAddr.getState().trim().isEmpty()) {
                    builder.append(currentAddr.getState());
                }

                // 4. Pincode (Higher level)
                if (currentAddr.getPincode() != null && !currentAddr.getPincode().trim().isEmpty()) {
                    builder.append(" - ").append(currentAddr.getPincode());
                }

                address.setText(builder.toString());
            }

            requireActivity().getSupportFragmentManager().setFragmentResultListener(
                    "ADDRESS_UPDATED_KEY",
                    getViewLifecycleOwner(),
                    (requestKey, result) -> {
                        // Jaise hi signal milega, ye code run hoga
                        // ViewModel ko data wapas fetch karne ke liye bolein
                        if (homeViewModel != null) {
                            homeViewModel.refreshData(); // Ye aapke page ko smoothly naye data ke sath load kar dega
                        }
                    }
            );
        });



        // 5. Setup Listeners (Clicks & Scrolls)
        setupClickListeners();
        setupScrollListeners(view);
        setupBannerPageChangeCallback();

        // 6. Fetch non-ViewModel data (Address)
        addressLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                r -> homeViewModel.refreshData());
    }


    private void initializeViews(View view) {
        shimmertrending = view.findViewById(R.id.trendingShimmerLayout);
        shimmerdeals = view.findViewById(R.id.dealsShimmerLayout);
        shimmerbanner = view.findViewById(R.id.slideshow);
        shimmerCategory = view.findViewById(R.id.shimmer_ct);
        shimmerBottom = view.findViewById(R.id.shimmerBottom);

        address = view.findViewById(R.id.address);
        timeSlot = view.findViewById(R.id.timeSlot);
        indicatorContainer = view.findViewById(R.id.indicatorContainer);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        btnCart = view.findViewById(R.id.btn_cart);
        btnNotification = view.findViewById(R.id.btn_notification);


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

        });
        homeViewModel.getTimeSlotLiveData().observe(getViewLifecycleOwner(), slotText -> {
            if (slotText != null && timeSlot != null) {
                timeSlot.setText(slotText);
            }
        });
        homeViewModel.getUnserviceablePincodeEvent().observe(getViewLifecycleOwner(), isUnserviceable -> {
            if (isUnserviceable != null && isUnserviceable) {
                address.setText("Address is not serviceable for this pincode.");
                Toast.makeText(requireContext(), "We do not deliver to this pincode yet. Please change location.", Toast.LENGTH_LONG).show();
                // Optional: User ko direct Address Change dialog/screen par bhej sakte hain
            }
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
                    Boolean isEnd = homeViewModel.getIsEndOfTrending().getValue();

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
        address.setOnClickListener(v -> {
            openAddressPicker();
        });
        btnCart.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.navigateToCart();
            }
        });
        btnNotification.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), NotificationActivity.class));
        });

        View searchBar = getView().findViewById(R.id.search);
        if (searchBar != null) searchBar.setOnClickListener(v -> startActivity(new Intent(getActivity(), SearchActivity.class)));
    }

    private void openAddressPicker() {
        // Purani lines (addressLauncher wali) yaha se hata dena

        AddressBottomSheetFragment bottomSheet = new AddressBottomSheetFragment();

        // Is line se ye sheet, app ke navigation bar ke upar overlay hogi bina nayi Activity banaye.
        bottomSheet.show(requireActivity().getSupportFragmentManager(), "AddressBottomSheet");
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