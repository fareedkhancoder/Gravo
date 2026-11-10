package com.gravo.Meher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.gravo.Meher.adapters.GridSpacingItemDecoration;
import com.gravo.Meher.adapters.TimeSpentProductAdapter;
import com.gravo.Meher.data.repository.AccountRepository;
import com.gravo.Meher.data.repository.ProductRepository;
import com.gravo.Meher.local.CachedOrderEntity;
import com.gravo.Meher.local.UserLifetimeStatsEntity;
import com.gravo.Meher.local.CachedWishlistItemEntity;
import com.gravo.Meher.viewmodel.ProductViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;

public class AccountFragment extends Fragment implements WishlistAdapter.OnWishlistActionListener {

    private static final String TAG = "AccountFragment";

    private FirebaseAuth mAuth;
    private AccountRepository accountRepository;
    private ProductRepository productRepository;
    private ProductViewModel productViewModel;

    // UI Components
    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvLifetimeSpending;
    private TextView tvLifetimeOrders;
    private TextView tvBio;
    private TextView tvEmail;
    private TextView tvPhone;

    private TextView btnTrackOrders;
    private TextView btnEditProfile;
    private LinearLayout btnAddresses;
    private LinearLayout btnSuggestUs;
    private ImageView btnMenu;

    // Ongoing Orders UI
    private TextView tvOngoingOrdersTitle;
    private LinearLayout ongoingOrdersContainer;
    private List<CachedOrderEntity> ongoingOrders = new ArrayList<>();
    private final Handler ongoingOrderHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> ongoingOrderAnimations = new ArrayList<>();
    private static final long ONGOING_ORDER_SWITCH_DELAY_MS = 2500L;
    private static final long ONGOING_ORDER_FADE_DURATION_MS = 220L;

    // Recently Viewed
    private TextView tvRecentsTitle;
    private RecyclerView recentsRecyclerView;
    private TimeSpentProductAdapter recentsAdapter;
    private int recentsCurrentPage = 0;
    private boolean recentsIsLoading = false;
    private boolean recentsIsLastPage = false;
    private static final int PAGE_SIZE = 10;

    // Wishlist UI
    private RecyclerView wishlistRecyclerView;
    private ShimmerFrameLayout wishlistShimmerLayout;
    private WishlistAdapter wishlistAdapter;
    private int wishlistOffset;
    private boolean wishlistLoading;
    private boolean wishlistEnd;
    private static final int WISHLIST_PAGE_SIZE = 10;
    private final ArrayList<CachedWishlistItemEntity> wishlistItems = new ArrayList<>();
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        accountRepository = new AccountRepository(requireContext());
        productRepository = new ProductRepository(requireContext());
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        initViews(view);
        setupRecentsRecyclerView(view.findViewById(R.id.gridRecycler));
        setupWishlistRecyclerView();
        setupClickListeners();

        loadUserProfileData();
        observeOngoingOrders();
        observeLifetimeStats();
        observeWishlistData();
        loadRecentsFirstPage();

        // Background cache-first sync
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            accountRepository.syncWishlist(uid, new AccountRepository.ActionCallback() {
                @Override public void onSuccess() { loadWishlistFirstPage(); }
                @Override public void onFailure(Exception error) { loadWishlistFirstPage(); }
            });
            accountRepository.syncOrders(uid, null);
        }
    }

    private void initViews(View view) {
        ivAvatar = view.findViewById(R.id.ivAvatar);
        tvName = view.findViewById(R.id.tvName);
        tvBio = view.findViewById(R.id.tvBio);
        tvEmail = view.findViewById(R.id.GmailText);
        tvPhone = view.findViewById(R.id.PhoneText);

        ViewGroup statsLayout = view.findViewById(R.id.stats);
        if (statsLayout != null && statsLayout.getChildCount() >= 2) {
            View spendingCol = statsLayout.getChildAt(0);
            View ordersCol = statsLayout.getChildAt(1);
            if (spendingCol instanceof ViewGroup) {
                tvLifetimeSpending = (TextView) ((ViewGroup) spendingCol).getChildAt(0);
            }
            if (ordersCol instanceof ViewGroup) {
                tvLifetimeOrders = (TextView) ((ViewGroup) ordersCol).getChildAt(0);
            }
        }

        btnTrackOrders = view.findViewById(R.id.btnEdit);
        btnEditProfile = view.findViewById(R.id.btnShare);
        btnAddresses = view.findViewById(R.id.address);
        btnMenu = view.findViewById(R.id.btnMenu);

        ViewGroup tabsLayout = view.findViewById(R.id.tabs);
        if (tabsLayout != null) {
            View child = tabsLayout.getChildAt(0);
            if (child instanceof ViewGroup) {
                View innerRow = ((ViewGroup) child).getChildAt(0);
                if (innerRow instanceof ViewGroup && ((ViewGroup) innerRow).getChildCount() >= 2) {
                    btnSuggestUs = (LinearLayout) ((ViewGroup) innerRow).getChildAt(1);
                }
            }
        }

        tvOngoingOrdersTitle = view.findViewById(R.id.tvOngoingOrdersTitle);
        ongoingOrdersContainer = view.findViewById(R.id.ongoingOrdersContainer);

        tvRecentsTitle = view.findViewById(R.id.recents);
        recentsRecyclerView = view.findViewById(R.id.gridRecycler);

        wishlistRecyclerView = view.findViewById(R.id.wishlistRecyler);
        wishlistShimmerLayout = view.findViewById(R.id.wishlistShimmerLayout);
        if (wishlistRecyclerView != null) {
            wishlistRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy <= 0 || wishlistLoading || wishlistEnd) return;
                    if (!recyclerView.canScrollHorizontally(1)) loadNextWishlistPage();
                }
            });
        }
    }

    private void setupClickListeners() {
        if (btnTrackOrders != null) {
            btnTrackOrders.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), MyOrdersActivity.class));
            });
        }
        View notification = requireView().findViewById(R.id.notification);
        if (notification != null) {
            notification.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), NotificationActivity.class)));
            notification.setContentDescription("Open notifications");
        }

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                EditProfileBottomSheetFragment sheet = new EditProfileBottomSheetFragment();
                sheet.setOnProfileUpdatedListener(this::loadUserProfileData);
                sheet.show(getParentFragmentManager(), "EditProfileSheet");
            });
        }

        if (btnAddresses != null) {
            btnAddresses.setOnClickListener(v -> startActivity(new Intent(requireContext(), MyAddressesActivity.class)));
        }

        if (btnSuggestUs != null) {
            btnSuggestUs.setOnClickListener(v -> {
                SuggestUsBottomSheetFragment sheet = new SuggestUsBottomSheetFragment();
                sheet.show(getParentFragmentManager(), "SuggestUsSheet");
            });
        }

        if (btnMenu != null) {
            btnMenu.setOnClickListener(this::showPopupMenu);
        }

        if (ongoingOrdersContainer != null) {
            ongoingOrdersContainer.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MyOrdersActivity.class)));
        }
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add("Settings");
        popup.getMenu().add("Contact Us");
        popup.getMenu().add("Logout");

        popup.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if ("Logout".equals(title)) {
                confirmLogout();
                return true;
            } else if ("Contact Us".equals(title)) {
                openContactUs();
                return true;
            } else if ("Settings".equals(title)) {
                Toast.makeText(requireContext(), "Gravo v1.0 (Latest)", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void openContactUs() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:support@gravo.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Gravo Customer Support");
        try {
            startActivity(Intent.createChooser(intent, "Contact Gravo Support"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Support Email: support@gravo.com", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null) {
                        accountRepository.clearUserCache(currentUser.getUid());
                    }
                    mAuth.signOut();
                    Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadUserProfileData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        accountRepository.getUserProfile(uid, new AccountRepository.UserProfileCallback() {
            @Override
            public void onSuccess(AccountRepository.UserProfile profile) {
                if (!isAdded()) return;

                if (tvName != null) tvName.setText(profile.name != null ? profile.name : "Gravo User");
                if (tvEmail != null) tvEmail.setText("Email: " + (profile.email != null ? profile.email : "Not set"));
                if (tvPhone != null) tvPhone.setText("Phone No: " + (profile.phone != null ? profile.phone : "Not set"));

                loadProfileAvatar(profile.avatarUrl);
            }

            private void loadProfileAvatar(String value) {
                if (ivAvatar == null) return;
                ivAvatar.setImageResource(R.drawable.ic_account);
                if (value == null || value.trim().isEmpty()) return;
                String source = value.trim();
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    Glide.with(ivAvatar).load(source).placeholder(R.drawable.ic_account)
                            .error(R.drawable.ic_account).into(ivAvatar);
                    return;
                }
                try {
                    FirebaseStorage storage = FirebaseStorage.getInstance();
                    StorageReference ref = source.startsWith("gs://")
                            ? storage.getReferenceFromUrl(source) : storage.getReference(source);
                    ref.getDownloadUrl().addOnSuccessListener(uri ->
                            Glide.with(ivAvatar).load(uri).placeholder(R.drawable.ic_account)
                                    .error(R.drawable.ic_account).into(ivAvatar));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid profile image reference", e);
                }
            }

            @Override
            public void onFailure(Exception error) {
                Log.w(TAG, "Failed to load profile", error);
            }
        });

        productRepository.getDefaultAddress(uid, new ProductRepository.DefaultAddressCallback() {
            @Override
            public void onAddressFound(Address address) {
                if (!isAdded() || tvBio == null) return;
                if (address != null) {
                    StringBuilder sb = new StringBuilder();
                    if (address.getStreet() != null) sb.append(address.getStreet());
                    if (address.getCity() != null) sb.append(", ").append(address.getCity());
                    if (address.getPincode() != null) sb.append(" - ").append(address.getPincode());
                    tvBio.setText(sb.length() > 0 ? sb.toString() : "Address set");
                }
            }

            @Override
            public void onAddressNotFound() {
                if (isAdded() && tvBio != null) tvBio.setText("No default address set");
            }

            @Override
            public void onFailure(Exception error) {
                if (isAdded() && tvBio != null) tvBio.setText("Address unavailable");
            }
        });
    }

    private void observeOngoingOrders() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        accountRepository.getOngoingOrdersLiveData(currentUser.getUid()).observe(getViewLifecycleOwner(), orders -> {
            if (!isAdded()) return;

            List<CachedOrderEntity> undeliveredOrders = new ArrayList<>();
            if (orders != null) {
                for (CachedOrderEntity order : orders) {
                    if (order != null && !isDeliveredStatus(order.orderStatus)) {
                        undeliveredOrders.add(order);
                    }
                }
            }

            if (!undeliveredOrders.isEmpty()) {
                ongoingOrders = undeliveredOrders;
                if (tvOngoingOrdersTitle != null) tvOngoingOrdersTitle.setVisibility(View.VISIBLE);
                if (ongoingOrdersContainer != null) ongoingOrdersContainer.setVisibility(View.VISIBLE);
                renderOngoingOrders(undeliveredOrders);
            } else {
                ongoingOrders.clear();
                if (tvOngoingOrdersTitle != null) tvOngoingOrdersTitle.setVisibility(View.GONE);
                if (ongoingOrdersContainer != null) ongoingOrdersContainer.setVisibility(View.GONE);
            }
        });
    }

    private boolean isDeliveredStatus(String status) {
        if (status == null) return false;
        return "DELIVERED".equals(status.trim().toUpperCase(Locale.ROOT));
    }

    private void renderOngoingOrders(List<CachedOrderEntity> orders) {
        if (ongoingOrdersContainer == null) return;
        stopOngoingOrderAnimations();
        ongoingOrdersContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (CachedOrderEntity order : orders) {
            if (order == null || isDeliveredStatus(order.orderStatus)) continue;
            View card = inflater.inflate(R.layout.item_ongoing_order, ongoingOrdersContainer, false);
            TextView title = card.findViewById(R.id.ongoingOrderTitle);
            TextView status = card.findViewById(R.id.ongoingOrderStatus);
            TextView amount = card.findViewById(R.id.ongoingOrderAmount);
            ImageView image = card.findViewById(R.id.ongoingOrderImage);

            List<String> productNames = parseJsonStrings(order.productNamesJson);
            List<String> productImages = parseJsonStrings(order.productImageUrlsJson);
            if (productNames.isEmpty() && order.firstProductName != null) {
                productNames.add(order.firstProductName);
            }
            if (productImages.isEmpty()) {
                productImages.add(order.firstProductImageUrl);
            }

            String productName = productNames.isEmpty() ? null : productNames.get(0);
            if (!isDisplayableProductName(productName)) {
                productName = "Product name unavailable";
            }
            title.setText(productName);

            status.setText(formatStatusText(order.orderStatus));
            amount.setText(String.format(Locale.US, "₹%.2f • %d item(s)",
                    order.grandTotalPaise / 100.0, order.totalItems > 0 ? order.totalItems : 1));

            showOngoingOrderImage(image, productImages.isEmpty() ? null : productImages.get(0));
            if (productNames.size() > 1) {
                preloadOngoingOrderImages(productImages);
                startOngoingOrderAnimation(title, image, productNames, productImages);
            }
            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), OrderTrackActivity.class);
                intent.putExtra("ORDER_ID", order.orderId);
                startActivity(intent);
            });
            ongoingOrdersContainer.addView(card);
        }
    }

    private void startOngoingOrderAnimation(TextView title, ImageView image,
                                            List<String> productNames, List<String> productImages) {
        final int[] index = {0};
        Runnable[] animation = new Runnable[1];
        animation[0] = () -> {
            if (!isAdded() || title.getWindowToken() == null) return;

            title.animate().alpha(0.35f).setDuration(ONGOING_ORDER_FADE_DURATION_MS).start();
            image.animate().alpha(0.35f).setDuration(ONGOING_ORDER_FADE_DURATION_MS)
                    .withEndAction(() -> {
                        index[0] = (index[0] + 1) % productNames.size();
                        String nextName = productNames.get(index[0]);
                        title.setText(isDisplayableProductName(nextName)
                                ? nextName : "Product name unavailable");
                        String nextImage = index[0] < productImages.size()
                                ? productImages.get(index[0]) : null;
                        showOngoingOrderImage(image, nextImage);
                        title.animate().alpha(1f).setDuration(ONGOING_ORDER_FADE_DURATION_MS).start();
                        image.animate().alpha(1f).setDuration(ONGOING_ORDER_FADE_DURATION_MS)
                                .withEndAction(() -> ongoingOrderHandler.postDelayed(
                                        animation[0], ONGOING_ORDER_SWITCH_DELAY_MS)).start();
                    }).start();
        };
        ongoingOrderAnimations.add(animation[0]);
        ongoingOrderHandler.postDelayed(animation[0], ONGOING_ORDER_SWITCH_DELAY_MS);
    }

    private void showOngoingOrderImage(ImageView image, String imageUrl) {
        if (image == null) return;
        if (imageUrl == null || imageUrl.trim().isEmpty() || imageUrl.equalsIgnoreCase("null")) {
            image.setImageResource(R.drawable.ic_delivery);
            return;
        }

        Glide.with(image)
                .load(imageUrl)
                .placeholder(R.drawable.ic_delivery)
                .error(R.drawable.ic_delivery)
                .dontAnimate()
                .into(image);
    }

    private boolean isDisplayableProductName(String name) {
        return name != null
                && !name.trim().isEmpty()
                && !name.equalsIgnoreCase("null")
                && !name.equalsIgnoreCase("product")
                && !name.equalsIgnoreCase("product name unavailable");
    }

    private void preloadOngoingOrderImages(List<String> imageUrls) {
        for (String imageUrl : imageUrls) {
            if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrl.equalsIgnoreCase("null")) {
                Glide.with(this).load(imageUrl).preload();
            }
        }
    }

    private List<String> parseJsonStrings(String json) {
        List<String> values = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return values;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                values.add(array.isNull(i) ? null : array.optString(i, null));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Invalid ongoing order product data", e);
        }
        return values;
    }

    private void stopOngoingOrderAnimations() {
        for (Runnable animation : ongoingOrderAnimations) {
            ongoingOrderHandler.removeCallbacks(animation);
        }
        ongoingOrderAnimations.clear();
    }

    @Override
    public void onStop() {
        stopOngoingOrderAnimations();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (ongoingOrdersContainer != null && !ongoingOrders.isEmpty()) {
            renderOngoingOrders(ongoingOrders);
        }
    }

    @Override
    public void onDestroyView() {
        ongoingOrders.clear();
        accountRepository.stopLifetimeStatsListener();
        stopOngoingOrderAnimations();
        ongoingOrderHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void observeLifetimeStats() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        accountRepository.observeLifetimeStats(currentUser.getUid())
                .observe(getViewLifecycleOwner(), this::renderLifetimeStats);
    }

    private void renderLifetimeStats(UserLifetimeStatsEntity stats) {
        if (stats == null) return;
        if (tvLifetimeSpending != null) {
            tvLifetimeSpending.setText(String.format(Locale.US, "₹ %.2f",
                    stats.lifetimeSpendingPaise / 100.0));
        }
        if (tvLifetimeOrders != null) {
            tvLifetimeOrders.setText(String.valueOf(stats.lifetimeOrderCount));
        }
    }

    private String formatStatusText(String status) {
        if (status == null) return "Processing";
        switch (status.toUpperCase()) {
            case "CONFIRMED":
                return "Order Confirmed • Processing";
            case "PROCESSING":
                return "Preparing your order";
            case "SHIPPED":
                return "Shipped • On the way";
            case "OUT_FOR_DELIVERY":
                return "Out for Delivery today";
            case "PENDING_CHECKOUT":
            case "AWAITING_PAYMENT":
                return "Payment Pending";
            default:
                return status;
        }
    }

    private void setupWishlistRecyclerView() {
        if (wishlistRecyclerView == null) return;

        wishlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        wishlistAdapter = new WishlistAdapter(requireContext(), this);
        wishlistRecyclerView.setAdapter(wishlistAdapter);
    }

    private void observeWishlistData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        loadWishlistFirstPage();
    }

    private void loadWishlistFirstPage() {
        wishlistOffset = 0;
        wishlistEnd = false;
        wishlistItems.clear();
        if (wishlistAdapter != null) wishlistAdapter.submitList(new ArrayList<>());
        loadNextWishlistPage();
    }

    private void loadNextWishlistPage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || wishlistLoading || wishlistEnd) return;
        wishlistLoading = true;
        accountRepository.getWishlistPage(currentUser.getUid(), WISHLIST_PAGE_SIZE, wishlistOffset,
                new AccountRepository.WishlistPageCallback() {
                    @Override public void onSuccess(List<CachedWishlistItemEntity> items) {
                        wishlistLoading = false;
                        if (!isAdded()) return;
                        if (items == null || items.isEmpty()) {
                            wishlistEnd = true;
                        } else {
                            HashSet<String> seen = new HashSet<>();
                            for (CachedWishlistItemEntity item : wishlistItems) seen.add(item.productId);
                            for (CachedWishlistItemEntity item : items) {
                                if (item != null && seen.add(item.productId)) wishlistItems.add(item);
                            }
                            wishlistOffset += items.size();
                            wishlistEnd = items.size() < WISHLIST_PAGE_SIZE;
                        }
                        if (wishlistShimmerLayout != null) wishlistShimmerLayout.setVisibility(View.GONE);
                        if (wishlistRecyclerView != null) {
                            wishlistRecyclerView.setVisibility(wishlistItems.isEmpty() ? View.GONE : View.VISIBLE);
                        }
                        if (wishlistAdapter != null) wishlistAdapter.submitList(new ArrayList<>(wishlistItems));
                    }
                    @Override public void onFailure(Exception error) {
                        wishlistLoading = false;
                        if (wishlistShimmerLayout != null) wishlistShimmerLayout.setVisibility(View.GONE);
                        Log.e(TAG, "Failed to load wishlist page", error);
                    }
                });
    }

    @Override
    public void onAddToCart(CachedWishlistItemEntity item) {
        if (item == null || item.productId == null) return;
        productViewModel.setQuantity(item.productId, 1);
        Toast.makeText(requireContext(), "Added to cart!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRemoveFromWishlist(CachedWishlistItemEntity item) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || item == null) return;

        accountRepository.removeFromWishlist(currentUser.getUid(), item.productId, new AccountRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Removed from wishlist", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Failed to remove item", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecentsRecyclerView(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recentsRecyclerView = recyclerView;

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, 16, true));

        recentsAdapter = new TimeSpentProductAdapter(requireContext(), product -> {
            if (product != null && product.getProductId() != null) {
                Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
                intent.putExtra("PRODUCT_ID", product.getProductId());
                startActivity(intent);
            }
        });

        recyclerView.setAdapter(recentsAdapter);
    }

    private void loadRecentsFirstPage() {
        recentsCurrentPage = 0;
        recentsIsLastPage = false;
        loadRecentsNextPage();
    }

    private void loadRecentsNextPage() {
        if (recentsIsLoading || recentsIsLastPage) return;
        recentsIsLoading = true;

        productRepository.getTopTimeSpentProducts(recentsCurrentPage, PAGE_SIZE,
                new ProductRepository.TimeSpendProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products, boolean isEnd) {
                        if (!isAdded()) return;
                        recentsIsLoading = false;
                        recentsIsLastPage = isEnd;

                        if (recentsCurrentPage == 0) {
                            recentsAdapter.setProducts(products);
                        } else {
                            recentsAdapter.addProducts(products);
                        }
                        recentsCurrentPage++;

                        boolean hasItems = recentsAdapter != null && recentsAdapter.getItemCount() > 0;
                        if (tvRecentsTitle != null) {
                            tvRecentsTitle.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                        }
                        if (recentsRecyclerView != null) {
                            recentsRecyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (!isAdded()) return;
                        recentsIsLoading = false;
                        Log.e(TAG, "Failed to load recent products", e);

                        boolean hasItems = recentsAdapter != null && recentsAdapter.getItemCount() > 0;
                        if (tvRecentsTitle != null) {
                            tvRecentsTitle.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                        }
                        if (recentsRecyclerView != null) {
                            recentsRecyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                        }
                    }
                });
    }
}
