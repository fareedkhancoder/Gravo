package com.gravo.Meher;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gravo.Meher.data.repository.CartEligibilityManager;
import com.gravo.Meher.data.repository.ProductRepository;
import com.razorpay.PaymentData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
public class CartFragment extends Fragment implements CartAdapter.OnCartItemChangeListener {

    private static final String TAG = "CartFragment";
    private static final long MIN_LOADING_DURATION_MS = 600L;

    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    // UI Views
    private RecyclerView cartRecyclerView;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;

    private TextView addressTextView;
    private TextView timeSlotTextView;
    private LinearLayout deliveryAddressCard;

    private MaterialCardView normalDeliveryOption;
    private ImageView normalDeliveryRadioIcon;
    private MaterialCardView instantDeliveryOption;
    private ImageView instantDeliveryRadioIcon;

    private MaterialCardView freeDeliveryCard;
    private TextView totalPriceTextView; // Subtotal
    private MaterialCardView summaryCard;

    private MaterialButton placeOrderButton;

    private LinearLayout emptyCartContainer;
    private MaterialButton continueShoppingButton;

    private LinearLayout cartErrorContainer;
    private MaterialButton retryCartButton;
    private View cartLoadingOverlay;
    private ProgressBar cartLoadingProgress;

    // Loading & Handlers
    private long fetchStartTime = 0L;
    private final Handler loadingHandler = new Handler(Looper.getMainLooper());
    private String cachedNormalTimeSlotText = null;

    // State
    private boolean isInstantDelivery = false; // Restored & saved persistently
    private Address selectedAddress;

    // Real-Time Listeners
    private ListenerRegistration cartSnapshotListener;
    private final List<ListenerRegistration> productSnapshotListeners = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        cartItemList = new ArrayList<>();

        initViews(view);
        setupRecyclerView();
        setupListeners();

        getParentFragmentManager().setFragmentResultListener(
                "ADDRESS_UPDATED_KEY",
                getViewLifecycleOwner(),
                (requestKey, result) -> loadDefaultAddress()
        );

        // 1. Restore persistent delivery selection (default: NORMAL)
        if (getContext() != null) {
            String savedDeliveryType = CartEligibilityManager.getSelectedDeliveryType(requireContext());
            isInstantDelivery = CartEligibilityManager.TYPE_INSTANT.equalsIgnoreCase(savedDeliveryType);
        }

        // 2. Hide free delivery card completely on start
        if (freeDeliveryCard != null) {
            freeDeliveryCard.setVisibility(View.GONE);
        }

        // 4. Load Default Address
        loadDefaultAddress();

        // 5. Initialize delivery option UI with restored selection
        setDeliveryType(isInstantDelivery, false);

        // 6. Start Real-Time Firestore Cart & Product listeners
        startRealtimeCartListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDefaultAddress();
    }

    private void initViews(View view) {
        cartRecyclerView = view.findViewById(R.id.cartRecyclerView);

        deliveryAddressCard = view.findViewById(R.id.deliveryAddressCard);
        addressTextView = view.findViewById(R.id.address);
        timeSlotTextView = view.findViewById(R.id.timeSlot);

        normalDeliveryOption = view.findViewById(R.id.normalDeliveryOption);
        normalDeliveryRadioIcon = view.findViewById(R.id.normalDeliveryRadioIcon);
        instantDeliveryOption = view.findViewById(R.id.instantDeliveryOption);
        instantDeliveryRadioIcon = view.findViewById(R.id.instantDeliveryRadioIcon);

        freeDeliveryCard = view.findViewById(R.id.freeDeliveryCard);

        summaryCard = view.findViewById(R.id.summaryCard);
        totalPriceTextView = view.findViewById(R.id.totalPriceTextView);

        placeOrderButton = view.findViewById(R.id.placeOrderButton);

        emptyCartContainer = view.findViewById(R.id.emptyCartContainer);
        continueShoppingButton = view.findViewById(R.id.continueShoppingButton);

        cartErrorContainer = view.findViewById(R.id.cartErrorContainer);
        retryCartButton = view.findViewById(R.id.retryCartButton);
        cartLoadingOverlay = view.findViewById(R.id.cartLoadingOverlay);
        cartLoadingProgress = view.findViewById(R.id.cartLoadingProgress);
    }

    private void setupRecyclerView() {
        cartRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        cartAdapter = new CartAdapter(getContext(), cartItemList);
        cartAdapter.setOnCartItemChangeListener(this);
        cartRecyclerView.setAdapter(cartAdapter);
    }

    private void setupListeners() {
        // Delivery type toggles
        if (normalDeliveryOption != null) {
            normalDeliveryOption.setOnClickListener(v -> setDeliveryType(false, true));
        }
        if (instantDeliveryOption != null) {
            instantDeliveryOption.setOnClickListener(v -> setDeliveryType(true, true));
        }

        // Delivery address click -> Navigate to address selection
        if (deliveryAddressCard != null) {
            deliveryAddressCard.setOnClickListener(v -> {
                AddressBottomSheetFragment bottomSheet = new AddressBottomSheetFragment();
                bottomSheet.show(requireActivity().getSupportFragmentManager(), "CartAddressBottomSheet");
            });
        }

        // Checkout Button Click Listener
        if (placeOrderButton != null) {
            placeOrderButton.setOnClickListener(v -> handleCheckoutClick());
        }

        // Continue shopping button in empty state
        if (continueShoppingButton != null) {
            continueShoppingButton.setOnClickListener(v -> {
                if (getActivity() instanceof HomeActivity) {
                    View navHome = getActivity().findViewById(R.id.nav_home);
                    if (navHome != null) navHome.performClick();
                } else if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        // Retry button on error
        if (retryCartButton != null) {
            retryCartButton.setOnClickListener(v -> startRealtimeCartListener());
        }
    }

    // =========================================================================
    // REAL-TIME FIRESTORE SYNCHRONIZATION (CART & PRODUCT STOCK/PRICING)
    // =========================================================================

    /**
     * Listens to real-time changes on the authenticated user's cart document directly from Firestore.
     */
    @SuppressWarnings("unchecked")
    private void startRealtimeCartListener() {
        showLoadingView();
        fetchStartTime = System.currentTimeMillis();
        MyApplication.getProductRepository().getCartItems(null, new ProductRepository.CartItemsCallback() {
            @Override
            public void onSuccess(List<CartItem> items) {
                if (!isAdded()) return;
                cartItemList.clear();
                cartItemList.addAll(items);
                cartAdapter.notifyDataSetChanged();
                if (cartItemList.isEmpty()) showEmptyCartView();
                else {
                    showCartContentView();
                    updateSubtotalOnly();
                }
            }

            @Override
            public void onFailure(Exception error) {
                if (isAdded()) showErrorView();
            }
        });
    }

    /**
     * Attaches live snapshot listeners for product details directly from Firestore
     * so that price changes, stock availability drops, and max buying limits sync in real-time.
     */
    private void syncProductsListener(List<String> productIds, Map<String, Integer> productQuantities) {
        stopProductSnapshotListeners();

        if (productIds == null || productIds.isEmpty()) {
            cartItemList.clear();
            if (cartAdapter != null) cartAdapter.notifyDataSetChanged();
            showEmptyCartView();
            return;
        }

        List<List<String>> batches = partitionList(productIds, 30);
        Map<String, Product> freshProductsMap = new HashMap<>();

        for (List<String> batchIds : batches) {
            ListenerRegistration reg = mFirestore
                    .collection("products")
                    .whereIn(FieldPath.documentId(), batchIds)
                    .addSnapshotListener((querySnapshot, error) -> {
                        if (!isAdded() || getContext() == null) return;

                        if (error != null) {
                            Log.e(TAG, "Products snapshot listener error: ", error);
                            if (cartItemList.isEmpty()) {
                                showErrorView();
                            }
                            return;
                        }

                        if (querySnapshot != null) {
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                Product p = doc.toObject(Product.class);
                                if (p != null) {
                                    p.setProductId(doc.getId());
                                    freshProductsMap.put(doc.getId(), p);
                                }
                            }
                        }

                        List<CartItem> newCartItems = new ArrayList<>();
                        for (String pid : productIds) {
                            Integer qtyVal = productQuantities.get(pid);
                            int qty = qtyVal != null ? qtyVal : 1;
                            Product product = freshProductsMap.get(pid);

                            CartItem item = new CartItem();
                            item.setQuantity(qty);

                            if (product != null) {
                                item.setProduct(product);
                                int effectiveStock = product.getEffectiveStockQuantity();
                                int limit = product.getMaxBuyingLimit();
                                int maxAllowed = product.getMaxAllowedQuantity();

                                if (!product.isAvailableForPurchase() || maxAllowed <= 0) {
                                    item.setOutOfStock(true);
                                    item.setQuantityAdjusted(false);
                                    item.setStatusBadgeText("Out of Stock");
                                } else {
                                    int normalizedQuantity = Math.max(1, Math.min(qty, maxAllowed));
                                    boolean quantityAdjusted = normalizedQuantity != qty;
                                    if (quantityAdjusted) {
                                        qty = normalizedQuantity;
                                        MyApplication.getProductRepository().updateCartQuantity(
                                                pid, normalizedQuantity, null);
                                    }
                                    item.setQuantity(qty);

                                    if (quantityAdjusted) {
                                        item.setOutOfStock(false);
                                        item.setQuantityAdjusted(true);
                                        item.setStatusBadgeText("Max limit: " + maxAllowed);
                                    } else if (limit > 0 && effectiveStock < limit && effectiveStock > 0) {
                                        item.setOutOfStock(false);
                                        item.setStatusBadgeText("Only " + effectiveStock + " left");
                                        item.setQuantityAdjusted(false);
                                    } else {
                                        item.setOutOfStock(false);
                                        item.setQuantityAdjusted(false);
                                        item.setStatusBadgeText(null);
                                    }
                                }
                            } else {
                                Product placeholder = new Product();
                                placeholder.setProductId(pid);
                                placeholder.setName("Unavailable Product");
                                placeholder.setPrice(0.0);
                                placeholder.setStockQuantity(0);
                                item.setProduct(placeholder);
                                item.setOutOfStock(true);
                                item.setStatusBadgeText("Product Unavailable");
                            }
                            newCartItems.add(item);
                        }

                        cartItemList.clear();
                        cartItemList.addAll(newCartItems);

                        if (cartAdapter != null) {
                            cartAdapter.notifyDataSetChanged();
                        }

                        postDelayedWithLoadingThreshold(() -> {
                            if (!isAdded() || getContext() == null) return;
                            showCartContentView();
                            updateSubtotalOnly();
                        });
                    });

            productSnapshotListeners.add(reg);
        }
    }

    private void stopCartSnapshotListener() {
        if (cartSnapshotListener != null) {
            cartSnapshotListener.remove();
            cartSnapshotListener = null;
        }
    }

    private void stopProductSnapshotListeners() {
        for (ListenerRegistration reg : productSnapshotListeners) {
            if (reg != null) {
                reg.remove();
            }
        }
        productSnapshotListeners.clear();
    }

    private void postDelayedWithLoadingThreshold(Runnable action) {
        long elapsed = System.currentTimeMillis() - fetchStartTime;
        long delay = Math.max(0, MIN_LOADING_DURATION_MS - elapsed);
        loadingHandler.postDelayed(() -> {
            if (isAdded()) {
                action.run();
            }
        }, delay);
    }

    private static <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            partitions.add(new ArrayList<>(list.subList(i, Math.min(i + chunkSize, list.size()))));
        }
        return partitions;
    }

    // ==========================================
    // DELIVERY TYPE SELECTION & PERSISTENCE
    // ==========================================
    private void setDeliveryType(boolean isInstant, boolean saveSelection) {
        this.isInstantDelivery = isInstant;
        if (getContext() == null) return;

        if (saveSelection) {
            CartEligibilityManager.saveSelectedDeliveryType(requireContext(),
                    isInstant ? CartEligibilityManager.TYPE_INSTANT : CartEligibilityManager.TYPE_NORMAL);
        }

        int blueColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_blue);
        int blueTintColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_blue_tint);
        int borderColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_border);
        int surfaceColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_surface);
        int mutedColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_muted);

        if (!isInstant) {
            if (normalDeliveryOption != null) {
                normalDeliveryOption.setStrokeColor(blueColor);
                normalDeliveryOption.setCardBackgroundColor(blueTintColor);
            }
            if (normalDeliveryRadioIcon != null) {
                normalDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_check);
                normalDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(blueColor));
            }

            if (instantDeliveryOption != null) {
                instantDeliveryOption.setStrokeColor(borderColor);
                instantDeliveryOption.setCardBackgroundColor(surfaceColor);
            }
            if (instantDeliveryRadioIcon != null) {
                instantDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_uncheck);
                instantDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(mutedColor));
            }

            if (timeSlotTextView != null) {
                timeSlotTextView.setText(cachedNormalTimeSlotText != null ? cachedNormalTimeSlotText : "Standard Delivery Available");
            }
        } else {
            if (instantDeliveryOption != null) {
                instantDeliveryOption.setStrokeColor(blueColor);
                instantDeliveryOption.setCardBackgroundColor(blueTintColor);
            }
            if (instantDeliveryRadioIcon != null) {
                instantDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_check);
                instantDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(blueColor));
            }

            if (normalDeliveryOption != null) {
                normalDeliveryOption.setStrokeColor(borderColor);
                normalDeliveryOption.setCardBackgroundColor(surfaceColor);
            }
            if (normalDeliveryRadioIcon != null) {
                normalDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_uncheck);
                normalDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(mutedColor));
            }

            if (timeSlotTextView != null) {
                timeSlotTextView.setText("Instant (30-45 mins)");
            }
        }
    }

    // ==========================================
    // DEFAULT ADDRESS LOADING
    // ==========================================
    private void loadDefaultAddress() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (addressTextView != null) {
                addressTextView.setText(getString(R.string.deliver_to, "Login to select address"));
            }
            return;
        }

        String userId = currentUser.getUid();
        if (addressTextView != null) {
            addressTextView.setText(getString(R.string.deliver_to, "Fetching address..."));
        }

        MyApplication.getProductRepository().getDefaultAddress(userId, new ProductRepository.DefaultAddressCallback() {
            @Override
            public void onAddressFound(Address address) {
                if (!isAdded() || getContext() == null) return;
                selectedAddress = address;
                if (addressTextView != null && address != null) {
                    StringBuilder sb = new StringBuilder();
                    boolean addedField = false;

                    if (address.getFullName() != null && !address.getFullName().trim().isEmpty()) {
                        sb.append(address.getFullName());
                        addedField = true;
                    }
                    if (address.getStreet() != null && !address.getStreet().trim().isEmpty()) {
                        if (addedField) sb.append(", ");
                        sb.append(address.getStreet());
                        addedField = true;
                    }
                    if (address.getCity() != null && !address.getCity().trim().isEmpty()) {
                        if (addedField) sb.append(", ");
                        sb.append(address.getCity());
                        addedField = true;
                    }
                    if (address.getPincode() != null && !address.getPincode().trim().isEmpty()) {
                        if (addedField) sb.append(" - ");
                        sb.append(address.getPincode());
                    }

                    String fullAddressStr = sb.length() > 0 ? sb.toString() : "Address details set";
                    addressTextView.setText(getString(R.string.deliver_to, fullAddressStr));
                }
                loadPincodeTimeSlot(address);
            }

            @Override
            public void onAddressNotFound() {
                if (!isAdded() || getContext() == null) return;
                selectedAddress = null;
                if (addressTextView != null) {
                    addressTextView.setText(getString(R.string.deliver_to, "Add or select a delivery address"));
                }
            }

            @Override
            public void onFailure(Exception error) {
                if (!isAdded() || getContext() == null) return;
                selectedAddress = null;
                if (addressTextView != null) {
                    addressTextView.setText(getString(R.string.deliver_to, "Select delivery address"));
                }
                Log.e(TAG, "Failed to load default address: " + error.getMessage());
            }
        });
    }

    private void loadPincodeTimeSlot(Address address) {
        if (address == null || address.getPincode() == null || address.getPincode().trim().isEmpty()) return;

        String pincode = address.getPincode().trim();
        mFirestore.collection("analytics")
                .document("pincode-" + pincode)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Object slotsObj = documentSnapshot.get("slots");
                        if (slotsObj instanceof List) {
                            String slotsJson = new Gson().toJson(slotsObj);
                            List<DeliverySlot> slotsList = new Gson().fromJson(
                                    slotsJson,
                                    new TypeToken<List<DeliverySlot>>(){}.getType()
                            );
                            cachedNormalTimeSlotText = calculateDynamicTimeSlot(slotsList);
                            if (!isInstantDelivery && timeSlotTextView != null) {
                                timeSlotTextView.setText(cachedNormalTimeSlotText);
                            }
                        }
                    }
                });
    }

    private String calculateDynamicTimeSlot(List<DeliverySlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return "Standard Delivery Available";
        }

        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);

        for (DeliverySlot slot : slots) {
            if (currentHour >= slot.getStartHour() && currentHour < slot.getEndHour()) {
                if (slot.getDayOffset() > 0) {
                    calendar.add(Calendar.DAY_OF_MONTH, slot.getDayOffset());
                }

                SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM", Locale.getDefault());
                String dateStr = dateFormat.format(calendar.getTime());
                String dayPrefix = (slot.getDayOffset() == 0) ? "Today" : "Tomorrow";

                return dayPrefix + ", " + dateStr + ", " + slot.getTimeText();
            }
        }

        return "Standard Delivery Available";
    }

    // ==========================================
    // CART SUBTOTAL CALCULATION
    // ==========================================
    private boolean hasOutOfStockItems() {
        if (cartItemList == null) return false;
        for (CartItem item : cartItemList) {
            if (item != null && item.isOutOfStock()) {
                return true;
            }
        }
        return false;
    }

    private void updateSubtotalOnly() {
        double subtotalPrice = 0.0;
        boolean hasOutOfStock = false;

        if (cartItemList != null) {
            for (CartItem item : cartItemList) {
                if (item != null && item.getProduct() != null) {
                    if (item.isOutOfStock()) {
                        hasOutOfStock = true;
                    } else {
                        subtotalPrice += item.getProduct().getPrice() * item.getQuantity();
                    }
                }
            }
        }

        if (cartItemList == null || cartItemList.isEmpty()) {
            showEmptyCartView();
            return;
        }

        if (totalPriceTextView != null) {
            totalPriceTextView.setText(String.format(Locale.US, "₹%.2f", subtotalPrice));
        }

        if (placeOrderButton != null) {
            boolean canProceed = !hasOutOfStock && !cartItemList.isEmpty();
            placeOrderButton.setEnabled(canProceed);
            placeOrderButton.setAlpha(canProceed ? 1.0f : 0.55f);
        }
    }

    // =========================================================================
    // CHECKOUT CLICK & PAYMENT METHODS LOOKUP FROM ANALYTICS/PINCODE-{PINCODE}
    // =========================================================================
    private void handleCheckoutClick() {
        if (cartItemList == null || cartItemList.isEmpty()) {
            Toast.makeText(getContext(), "Your cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hasOutOfStockItems()) {
            Toast.makeText(getContext(), "Please remove out-of-stock items before checking out.", Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedAddress == null || selectedAddress.getPincode() == null || selectedAddress.getPincode().trim().isEmpty()) {
            Toast.makeText(getContext(), "Please select a delivery address with a valid pincode.", Toast.LENGTH_SHORT).show();
            AddressBottomSheetFragment bottomSheet = new AddressBottomSheetFragment();
            bottomSheet.show(requireActivity().getSupportFragmentManager(), "CartAddressBottomSheet");
            return;
        }

        fetchPaymentMethodsAndShowSheet(selectedAddress.getPincode().trim());
    }

    /**
     * Fetches analytics/pincode-{pincode} document from Firestore and reads `availablePaymentMethods` array.
     * Index 0: Cash on Delivery (COD)
     * Index 1: Online (UPI)
     */
    private void fetchPaymentMethodsAndShowSheet(String pincode) {
        showLoadingView();

        mFirestore.collection("analytics")
                .document("pincode-" + pincode)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    showCartContentView();

                    boolean codAvailable = true;   // Default fallback
                    boolean onlineAvailable = true; // Default fallback

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Object methodsObj = documentSnapshot.get("availablePaymentMethods");
                        if (methodsObj instanceof List) {
                            List<?> methods = (List<?>) methodsObj;
                            if (!methods.isEmpty() && methods.get(0) instanceof Boolean) {
                                codAvailable = (Boolean) methods.get(0);
                            }
                            if (methods.size() > 1 && methods.get(1) instanceof Boolean) {
                                onlineAvailable = (Boolean) methods.get(1);
                            }
                        }
                    }

                    showPaymentMethodSheet(codAvailable, onlineAvailable);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    showCartContentView();
                    Log.e(TAG, "Failed to fetch payment methods for pincode: " + pincode, e);
                    // Fallback to enabling available payment methods
                    showPaymentMethodSheet(true, true);
                });
    }

    private void showPaymentMethodSheet(boolean codAvailable, boolean onlineAvailable) {
        if (!isAdded()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_payment_method, (ViewGroup) getView(), false);
        dialog.setContentView(sheetView);

        TextView codOption = sheetView.findViewById(R.id.cashOnDeliveryOption);
        TextView upiOption = sheetView.findViewById(R.id.upiOption);
        ProgressBar sheetProgressBar = sheetView.findViewById(R.id.paymentSheetProgressBar);

        if (sheetProgressBar != null) {
            sheetProgressBar.setVisibility(View.GONE);
        }

        // 1. COD Option (Index 0)
        if (codOption != null) {
            if (codAvailable) {
                codOption.setEnabled(true);
                codOption.setAlpha(1.0f);
                codOption.setText("Cash on Delivery (COD)");
                codOption.setOnClickListener(v -> {
                    dialog.dismiss();
                    verifyAndNavigateToOrderDetail("COD");
                });
            } else {
                codOption.setEnabled(false);
                codOption.setAlpha(0.4f);
                codOption.setText("Cash on Delivery (Unavailable for this pincode)");
                codOption.setOnClickListener(v ->
                        Toast.makeText(getContext(), "COD is unavailable for your delivery pincode.", Toast.LENGTH_SHORT).show()
                );
            }
        }

        // 2. Online/UPI Option (Index 1)
        if (upiOption != null) {
            if (onlineAvailable) {
                upiOption.setEnabled(true);
                upiOption.setAlpha(1.0f);
                upiOption.setText("Online (UPI)");
                upiOption.setOnClickListener(v -> {
                    dialog.dismiss();
                    verifyAndNavigateToOrderDetail("RAZORPAY");
                });
            } else {
                upiOption.setEnabled(false);
                upiOption.setAlpha(0.4f);
                upiOption.setText("Online (UPI) (Unavailable for this pincode)");
                upiOption.setOnClickListener(v ->
                        Toast.makeText(getContext(), "Online payment is unavailable for your delivery pincode.", Toast.LENGTH_SHORT).show()
                );
            }
        }

        dialog.show();
    }

    @SuppressWarnings("unchecked")
    private void verifyAndNavigateToOrderDetail(String paymentMethod) {
        if (!isAdded() || getContext() == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in to continue checkout.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(requireContext(), MainActivity.class);
            startActivity(intent);
            return;
        }

        if (selectedAddress == null || selectedAddress.getAddressId() == null) {
            Toast.makeText(getContext(), "Please select a valid delivery address.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingView();

        Map<String, Object> payload = new HashMap<>();
        payload.put("deliveryId", selectedAddress.getAddressId());
        payload.put("deliveryType", isInstantDelivery ? "INSTANT" : "NORMAL");
        payload.put("paymentMethod", paymentMethod);

        currentUser.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    if (!isAdded() || getContext() == null) return;

                    FirebaseFunctions.getInstance()
                            .getHttpsCallable("confirmOrderDetails")
                            .call(payload)
                            .addOnSuccessListener(result -> {
                                if (!isAdded() || getContext() == null) return;
                                showCartContentView();

                                Map<String, Object> resData = (Map<String, Object>) result.getData();
                                if (resData == null) {
                                    Toast.makeText(getContext(), "Unable to verify order details.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Number deliveryChargeNum = (Number) resData.get("deliveryCharge");
                                Number totalBillNum = (Number) resData.get("totalBill");

                                double deliveryCharge = deliveryChargeNum != null ? deliveryChargeNum.doubleValue() : 0.0;
                                double totalBill = totalBillNum != null ? totalBillNum.doubleValue() : 0.0;

                                Intent intent = new Intent(requireContext(), OrderDetailActivity.class);
                                intent.putExtra(OrderDetailActivity.EXTRA_DELIVERY_ID, selectedAddress.getAddressId());
                                intent.putExtra(OrderDetailActivity.EXTRA_DELIVERY_TYPE, isInstantDelivery ? "INSTANT" : "NORMAL");
                                intent.putExtra(OrderDetailActivity.EXTRA_PAYMENT_METHOD, paymentMethod);
                                intent.putExtra(OrderDetailActivity.EXTRA_DELIVERY_CHARGE, deliveryCharge);
                                intent.putExtra(OrderDetailActivity.EXTRA_TOTAL_BILL, totalBill);
                                intent.putExtra(OrderDetailActivity.EXTRA_ADDRESS_JSON, new Gson().toJson(selectedAddress));
                                intent.putExtra(OrderDetailActivity.EXTRA_CART_ITEMS_JSON, new Gson().toJson(cartItemList));
                                startActivity(intent);
                            })
                            .addOnFailureListener(error -> {
                                if (!isAdded() || getContext() == null) return;
                                showCartContentView();
                                String msg = error != null && error.getMessage() != null
                                        ? error.getMessage()
                                        : "Could not verify order details. Please try again.";
                                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(tokenError -> {
                    if (!isAdded() || getContext() == null) return;
                    showCartContentView();
                    Toast.makeText(getContext(), "Authentication failed. Please log in again.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    startActivity(intent);
                });
    }

    // Payment Handlers (Placeholders for HomeActivity compatibility)
    public void handleRazorpaySuccess(String razorpayPaymentId, PaymentData paymentData) {
        if (!isAdded()) return;
        Toast.makeText(getContext(), "Payment successful: " + razorpayPaymentId, Toast.LENGTH_SHORT).show();
    }

    public void handleRazorpayError(int code, String response, PaymentData paymentData) {
        if (!isAdded()) return;
        Toast.makeText(getContext(), "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // CART ADAPTER CALLBACKS
    // ==========================================
    @Override
    public void onQuantityChanged(CartItem cartItem, int newQuantity) {
        updateSubtotalOnly();
        if (cartItemList != null && !cartItemList.isEmpty()) {
            showCartContentView();
        }
    }

    @Override
    public void onItemRemoved(CartItem cartItem, int position) {
        updateSubtotalOnly();
        if (cartItemList == null || cartItemList.isEmpty()) {
            showEmptyCartView();
        }
    }

    @Override
    public void onItemSavedToWishlist(CartItem cartItem, int position) {
        updateSubtotalOnly();
        if (cartItemList == null || cartItemList.isEmpty()) {
            showEmptyCartView();
        }
    }

    @Override
    public void onItemClicked(CartItem cartItem) {
        if (cartItem != null && cartItem.getProduct() != null && cartItem.getProduct().getProductId() != null) {
            Intent intent = new Intent(getContext(), ProductDetailActivity.class);
            intent.putExtra("PRODUCT_ID", cartItem.getProduct().getProductId());
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "Product details unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // VIEW STATE MANAGEMENT
    // ==========================================
    private void showLoadingView() {
        if (cartLoadingOverlay != null) cartLoadingOverlay.setVisibility(View.VISIBLE);
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.VISIBLE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);
    }

    private void showCartContentView() {
        if (cartLoadingOverlay != null) cartLoadingOverlay.setVisibility(View.GONE);
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);

        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.VISIBLE);
        if (summaryCard != null) summaryCard.setVisibility(View.VISIBLE);
        if (deliveryAddressCard != null) deliveryAddressCard.setVisibility(View.VISIBLE);
    }

    private void showEmptyCartView() {
        loadingHandler.removeCallbacksAndMessages(null);
        if (cartLoadingOverlay != null) cartLoadingOverlay.setVisibility(View.GONE);
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.GONE);
        if (deliveryAddressCard != null) deliveryAddressCard.setVisibility(View.GONE);
        if (freeDeliveryCard != null) freeDeliveryCard.setVisibility(View.GONE);

        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.VISIBLE);
    }

    private void showErrorView() {
        if (cartLoadingOverlay != null) cartLoadingOverlay.setVisibility(View.GONE);
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);

        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        loadingHandler.removeCallbacksAndMessages(null);
        stopCartSnapshotListener();
        stopProductSnapshotListeners();
        if (cartAdapter != null) {
            cartAdapter.clearPendingUpdates();
        }
    }
}
