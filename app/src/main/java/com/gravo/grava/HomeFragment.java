package com.gravo.grava;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    public interface OnHomeFragmentInteractionListener {
        void navigateToCategories(String categoryId);
    }

    private OnHomeFragmentInteractionListener mListener;
    private static final String TAG = "HomeFragment";
    private static final int SLIDE_DURATION = 3000; // 3 seconds per banner

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // UI Components
    private RecyclerView categoriesRecyclerView, dealsRecyclerView, trendingRecyclerView;
    private ShimmerFrameLayout shimmertrending, shimmerdeals, shimmerbanner, shimmerCategory;
    private TextView address;
    private ViewPager2 bannerViewPager;
    private LinearLayout indicatorContainer;

    // Adapters & Lists
    private CategoryAdapter categoryAdapter;
    private ProductAdapter dealsAdapter, trendingAdapter;
    private BannerAdapter bannerAdapter;
    private List<Category> categoryList;
    private List<Product> dealsList, trendingList;
    private List<Banner> bannerList;

    // Pagination ke liye naye variables
    private com.google.firebase.firestore.DocumentSnapshot lastDealsVisible, lastTrendingVisible;
    private boolean isDealsLoading = false, isTrendingLoading = false;
    private boolean isDealsLastPage = false, isTrendingLastPage = false;
    private static final int PAGE_SIZE = 5;

    // Animation Logic
    private ValueAnimator progressAnimator;
    private ActivityResultLauncher<Intent> addressLauncher;

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

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        categoryList = new ArrayList<>();
        dealsList = new ArrayList<>();
        trendingList = new ArrayList<>();
        bannerList = new ArrayList<>();

        initializeViews(view);
        setupClickListeners();
        setupRecyclerViews(view);
        setupBannerViewPager();

        setupDealsScrollListener();
        setupTrendingScrollListener(view);

        fetchAllData();
    }

    private void initializeViews(@NonNull View view) {
        shimmertrending = view.findViewById(R.id.trendingShimmerLayout);
        shimmerdeals = view.findViewById(R.id.dealsShimmerLayout);
        shimmerbanner = view.findViewById(R.id.slideshow);
        shimmerCategory = view.findViewById(R.id.shimmer_ct);
        address = view.findViewById(R.id.address);
        indicatorContainer = view.findViewById(R.id.indicatorContainer);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
    }

    private void setupRecyclerViews(View view) {
        // Categories
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(getContext(), categoryList, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);

        // Deals
        dealsRecyclerView = view.findViewById(R.id.dealsRecyclerView);
        dealsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        dealsAdapter = new ProductAdapter(getContext(), dealsList, R.layout.item_product, this);
        dealsRecyclerView.setAdapter(dealsAdapter);

        // Trending
        trendingRecyclerView = view.findViewById(R.id.trendingRecyclerView);
        trendingRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        trendingAdapter = new ProductAdapter(getContext(), trendingList, R.layout.item_product_grid, this);
        trendingRecyclerView.setAdapter(trendingAdapter);
    }

    private void setupBannerViewPager() {
        bannerAdapter = new BannerAdapter(bannerList);
        bannerViewPager.setAdapter(bannerAdapter);

        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // Purane callbacks hata kar naya start karein
                indicatorContainer.post(() -> startBannerProgress(position));
            }
        });
    }

    private void startBannerProgress(int position) {
        // 1. Purane animator ko cancel aur clear karein
        if (progressAnimator != null) {
            progressAnimator.removeAllUpdateListeners();
            progressAnimator.removeAllListeners();
            progressAnimator.cancel();
        }

        if (bannerList.isEmpty() || indicatorContainer == null || !isAdded()) return;

        int activeWidth = (int) (40 * getResources().getDisplayMetrics().density);
        int inactiveWidth = (int) (12 * getResources().getDisplayMetrics().density);

        // 2. Bars ki state ko force-reset karein
        for (int i = 0; i < indicatorContainer.getChildCount(); i++) {
            ProgressBar bar = (ProgressBar) indicatorContainer.getChildAt(i);
            if (bar != null) {
                // Drawable sharing band karein
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

        ProgressBar currentBar = (ProgressBar) indicatorContainer.getChildAt(position);
        if (currentBar == null) return;

        // 3. Naya animator setup karein
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
                // Page change logic tabhi chalayein jab animation poori ho
                if (isAdded() && bannerViewPager != null && !bannerList.isEmpty()) {
                    int nextItem = (bannerViewPager.getCurrentItem() + 1) % bannerList.size();
                    bannerViewPager.setCurrentItem(nextItem, true);
                }
            }
        });

        progressAnimator.start();
    }

    private void fetchAllData() {
        fetchCategories();
        fetchBanners();
        fetchDeals();
        fetchTrendingProducts();
        fetchDefaultAddress();
    }

    private void fetchBanners() {
        db.collection("Banners").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && isAdded()) {
                shimmerbanner.stopShimmer();
                shimmerbanner.setVisibility(View.GONE);
                bannerViewPager.setVisibility(View.VISIBLE);
                indicatorContainer.setVisibility(View.VISIBLE);

                bannerList.clear();
                indicatorContainer.removeAllViews();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    bannerList.add(document.toObject(Banner.class));
                    // Inflate progress segment for each banner
                    View v = getLayoutInflater().inflate(R.layout.layout_banner_indicator, indicatorContainer, false);
                    indicatorContainer.addView(v);
                }

                bannerAdapter.notifyDataSetChanged();
                if (!bannerList.isEmpty()) startBannerProgress(0);
                Log.d(TAG, "Banners found: " + bannerList.size());
                Log.d(TAG, "Indicator container null? " + (indicatorContainer == null));
            }
        });
    }

    private void fetchCategories() {
        db.collection("categories").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && isAdded()) {
                shimmerCategory.stopShimmer();
                shimmerCategory.setVisibility(View.GONE);
                categoriesRecyclerView.setVisibility(View.VISIBLE);
                categoryList.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) categoryList.add(doc.toObject(Category.class));
                categoryAdapter.notifyDataSetChanged();
            }
        });
    }

    private void fetchDeals() {
        isDealsLoading = true;

        // Agar list khali hai toh shimmer dikhayein (Pehle load ke liye)
        if (dealsList.isEmpty()) {
            shimmerdeals.startShimmer();
            shimmerdeals.setVisibility(View.VISIBLE);
        }

        Query query = db.collection("products").limit(PAGE_SIZE);
        if (lastDealsVisible != null) {
            query = query.startAfter(lastDealsVisible); // Pichle batch ke baad se start karein
        }

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                shimmerdeals.stopShimmer();
                shimmerdeals.setVisibility(View.GONE);
                dealsRecyclerView.setVisibility(View.VISIBLE);

                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Product p = doc.toObject(Product.class);
                    p.setProductId(doc.getId());
                    dealsList.add(p);
                }

                if (!task.getResult().isEmpty()) {
                    lastDealsVisible = task.getResult().getDocuments().get(task.getResult().size() - 1);
                    dealsAdapter.notifyDataSetChanged();
                } else {
                    isDealsLastPage = true; // Data khatam
                }
            }
            isDealsLoading = false;
        });
    }
    private void setupTrendingScrollListener(View view) {
        androidx.core.widget.NestedScrollView nestedScrollView = view.findViewById(R.id.nestedScrollView);

        if (nestedScrollView != null) {
            nestedScrollView.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                // Calculate total height of the content inside the scrollview
                int totalHeight = v.getChildAt(0).getMeasuredHeight();
                int currentHeight = v.getMeasuredHeight();

                // Check if user has scrolled to bottom (allowing a small 50px buffer)
                if (scrollY >= (totalHeight - currentHeight - 50)) {
                    if (!isTrendingLoading && !isTrendingLastPage) {
                        Log.d(TAG, "Loading more trending products...");
                        fetchTrendingProducts();
                    }
                }
            });
        }
    }

    private void fetchTrendingProducts() {
        isTrendingLoading = true;

        if (trendingList.isEmpty()) {
            shimmertrending.startShimmer();
            shimmertrending.setVisibility(View.VISIBLE);
        }

        Query query = db.collection("products")
                .orderBy("stockQuantity", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastTrendingVisible != null) {
            query = query.startAfter(lastTrendingVisible);
        }

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                shimmertrending.stopShimmer();
                shimmertrending.setVisibility(View.GONE);
                trendingRecyclerView.setVisibility(View.VISIBLE);

                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Product p = doc.toObject(Product.class);
                    p.setProductId(doc.getId());
                    trendingList.add(p);
                }

                if (!task.getResult().isEmpty()) {
                    lastTrendingVisible = task.getResult().getDocuments().get(task.getResult().size() - 1);
                    trendingAdapter.notifyDataSetChanged();
                } else {
                    isTrendingLastPage = true;
                }
            }
            isTrendingLoading = false;
        });
    }

    private void fetchDefaultAddress() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { address.setText("Login to set address"); return; }
        db.collection("users").document(user.getUid()).collection("addresses")
                .whereEqualTo("default", true).limit(1).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Address addr = task.getResult().getDocuments().get(0).toObject(Address.class);
                        address.setText("Deliver to: " + addr.toString());
                    } else {
                        address.setText("Set Default Address");
                    }
                });
    }

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
    private void setupDealsScrollListener() {
        dealsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastVisibleItemPosition() >= dealsList.size() - 2) {
                    if (!isDealsLoading && !isDealsLastPage) {
                        fetchDeals(); // Agla batch load karein
                    }
                }
            }
        });
    }

    @Override
    public void onProductClick(Product product) {
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        startActivity(intent);
    }

    @Override
    public void onCategoryItemClick(Category category) {
        if (mListener != null) mListener.navigateToCategories(category.getId());
    }

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