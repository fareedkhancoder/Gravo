package com.gravo.shopping;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.gravo.shopping.data.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment implements CartAdapter.OnCartItemChangeListener {

    private static final String TAG = "CartFragment";

    private FirebaseAuth mAuth;

    // UI Views
    private RecyclerView cartRecyclerView;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;

    private TextView addressTextView;
    private TextView timeSlotTextView;
    private TextView deliveryAvailabilityMessage;
    private LinearLayout deliveryAddressCard;

    private MaterialCardView instantDeliveryOption;
    private ImageView instantDeliveryRadioIcon;
    private MaterialCardView normalDeliveryOption;
    private ImageView normalDeliveryRadioIcon;
    private TextView deliveryModeLabel;
    private LinearLayout deliveryModeOptions;

    private TextView totalPriceTextView; // Subtotal
    private TextView deliveryFeeTextView;
    private TextView estimatedTotalTextView; // Grand total
    private MaterialCardView summaryCard;
    private MaterialButton placeOrderButton;

    private LinearLayout emptyCartContainer;
    private MaterialButton continueShoppingButton;

    private LinearLayout cartErrorContainer;
    private MaterialButton retryCartButton;
    private ProgressBar cartLoadingProgress;

    // State
    private boolean isInstantDelivery = true;
    private Address selectedAddress;
    private DeliveryCharge deliveryCharge;
    private String selectedPaymentMethod;
    private boolean isEligibleForFreeDelivery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        cartItemList = new ArrayList<>();

        initViews(view);
        setupRecyclerView();
        setupListeners();
        getParentFragmentManager().setFragmentResultListener(
                "ADDRESS_UPDATED_KEY",
                getViewLifecycleOwner(),
                (requestKey, result) -> loadDefaultAddress()
        );

        // 1. Load Default Address immediately
        loadDefaultAddress();

        // 2. Fetch Cart Items
        fetchCartItems();

        // 3. Set default delivery option (Instant)
        setDeliveryType(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh address when returning to the fragment in case the user updated their default address
        loadDefaultAddress();
    }

    private void initViews(View view) {
        cartRecyclerView = view.findViewById(R.id.cartRecyclerView);

        deliveryAddressCard = view.findViewById(R.id.deliveryAddressCard);
        addressTextView = view.findViewById(R.id.address);
        timeSlotTextView = view.findViewById(R.id.timeSlot);
        deliveryAvailabilityMessage = view.findViewById(R.id.deliveryAvailabilityMessage);

        deliveryModeLabel = view.findViewById(R.id.deliveryModeLabel);
        deliveryModeOptions = view.findViewById(R.id.deliveryModeOptions);
        instantDeliveryOption = view.findViewById(R.id.instantDeliveryOption);
        instantDeliveryRadioIcon = view.findViewById(R.id.instantDeliveryRadioIcon);
        normalDeliveryOption = view.findViewById(R.id.normalDeliveryOption);
        normalDeliveryRadioIcon = view.findViewById(R.id.normalDeliveryRadioIcon);

        summaryCard = view.findViewById(R.id.summaryCard);
        totalPriceTextView = view.findViewById(R.id.totalPriceTextView);
        deliveryFeeTextView = view.findViewById(R.id.deliveryFee);
        estimatedTotalTextView = view.findViewById(R.id.estimatedTotalTextView);
        placeOrderButton = view.findViewById(R.id.placeOrderButton);

        emptyCartContainer = view.findViewById(R.id.emptyCartContainer);
        continueShoppingButton = view.findViewById(R.id.continueShoppingButton);

        cartErrorContainer = view.findViewById(R.id.cartErrorContainer);
        retryCartButton = view.findViewById(R.id.retryCartButton);
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
        if (instantDeliveryOption != null) {
            instantDeliveryOption.setOnClickListener(v -> setDeliveryType(true));
        }
        if (normalDeliveryOption != null) {
            normalDeliveryOption.setOnClickListener(v -> setDeliveryType(false));
        }

        // Delivery address click -> Navigate to address selection/management
        if (deliveryAddressCard != null) {
            deliveryAddressCard.setOnClickListener(v -> {
                AddressBottomSheetFragment bottomSheet = new AddressBottomSheetFragment();
                bottomSheet.show(requireActivity().getSupportFragmentManager(), "CartAddressBottomSheet");
            });
        }

        // Place order button
        if (placeOrderButton != null) {
            placeOrderButton.setOnClickListener(v -> {
                if (cartItemList == null || cartItemList.isEmpty()) {
                    Toast.makeText(getContext(), "Your cart is empty.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedAddress == null || deliveryCharge == null || !deliveryCharge.isServiceable()
                        || !deliveryCharge.hasChargeFor(isInstantDelivery)) {
                    Toast.makeText(getContext(), "Please select a serviceable delivery address.", Toast.LENGTH_SHORT).show();
                    return;
                }

                ArrayList<String> productIds = new ArrayList<>();
                for (CartItem item : cartItemList) {
                    if (item.getProduct() != null && item.getProduct().getProductId() != null) {
                        productIds.add(item.getProduct().getProductId());
                    }
                }

                showPaymentMethodSheet(productIds);
            });
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
            retryCartButton.setOnClickListener(v -> fetchCartItems());
        }
    }

    private void showPaymentMethodSheet(ArrayList<String> productIds) {
        if (!isAdded()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_payment_method, null);
        dialog.setContentView(sheetView);

        TextView codOption = sheetView.findViewById(R.id.cashOnDeliveryOption);
        TextView upiOption = sheetView.findViewById(R.id.upiOption);

        View.OnClickListener paymentListener = v -> {
            selectedPaymentMethod = v.getId() == R.id.cashOnDeliveryOption ? "COD" : "UPI";
            dialog.dismiss();

            Intent intent = new Intent(getContext(), OrderSummaryActivity.class);
            intent.putStringArrayListExtra("CART_PRODUCT_IDS", productIds);
            intent.putExtra("SELECTED_ADDRESS", selectedAddress);
            intent.putExtra("IS_INSTANT_DELIVERY", isInstantDelivery);
            intent.putExtra("DELIVERY_CHARGE", isInstantDelivery
                    ? deliveryCharge.getInstantCharge()
                    : deliveryCharge.getNormalCharge());
            intent.putExtra("PAYMENT_METHOD", selectedPaymentMethod);
            startActivity(intent);
        };

        codOption.setOnClickListener(paymentListener);
        upiOption.setOnClickListener(paymentListener);
        dialog.show();
    }

    // ==========================================
    // 1. DEFAULT ADDRESS LOADING
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

        // Load default address: Local Room DB first, fallback to Firestore
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
                loadDeliveryCharge(address);
            }

            @Override
            public void onAddressNotFound() {
                if (!isAdded() || getContext() == null) return;
                selectedAddress = null;
                deliveryCharge = null;
                updateDeliveryAvailability("Select a delivery address to continue.", false);
                updateCartTotals();
                if (addressTextView != null) {
                    addressTextView.setText(getString(R.string.deliver_to, "Add or select a delivery address"));
                }
            }

            @Override
            public void onFailure(Exception error) {
                if (!isAdded() || getContext() == null) return;
                selectedAddress = null;
                deliveryCharge = null;
                updateDeliveryAvailability("Unable to load your delivery address.", false);
                updateCartTotals();
                if (addressTextView != null) {
                    addressTextView.setText(getString(R.string.deliver_to, "Select delivery address"));
                }
                Log.e(TAG, "Failed to load default address: " + error.getMessage());
            }
        });
    }

    private void loadDeliveryCharge(Address address) {
        deliveryCharge = null;
        updateDeliveryAvailability("Checking delivery availability...", false);
        updateCartTotals();
        if (address == null || address.getPincode() == null || address.getPincode().trim().isEmpty()) {
            updateDeliveryAvailability("A valid PIN code is required for delivery.", false);
            return;
        }

        MyApplication.getProductRepository().updateCartPincode(address.getPincode(),
                new ProductRepository.CartCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Cart PIN code updated for delivery eligibility");
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "Failed to update cart PIN code: " + error);
                    }
                });

        MyApplication.getProductRepository().getDeliveryCharge(address.getPincode(),
                new ProductRepository.DeliveryChargeCallback() {
                    @Override
                    public void onSuccess(DeliveryCharge charge) {
                        if (!isAdded() || getContext() == null) return;
                        if (!charge.isServiceable()) {
                            deliveryCharge = charge;
                            updateDeliveryAvailability("Delivery is unavailable for this PIN code.", false);
                            updateCartTotals();
                            return;
                        }
                        deliveryCharge = charge;
                        if (!charge.hasChargeFor(isInstantDelivery)) {
                            updateDeliveryAvailability("Selected delivery type is unavailable for this PIN code.", false);
                            updateCartTotals();
                            return;
                        }
                        updateDeliveryAvailability("", true);
                        updateCartTotals();
                    }

                    @Override
                    public void onFailure(Exception error) {
                        if (!isAdded() || getContext() == null) return;
                        deliveryCharge = null;
                        updateDeliveryAvailability("Delivery charges are unavailable. Please try again.", false);
                        updateCartTotals();
                        Log.e(TAG, "Failed to load delivery charge", error);
                    }
                });
    }

    private void updateDeliveryAvailability(String message, boolean available) {
        if (deliveryAvailabilityMessage != null) {
            deliveryAvailabilityMessage.setText(message);
            deliveryAvailabilityMessage.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (placeOrderButton != null) {
            placeOrderButton.setEnabled(available && cartItemList != null && !cartItemList.isEmpty());
            placeOrderButton.setAlpha(placeOrderButton.isEnabled() ? 1f : 0.55f);
        }
        if (instantDeliveryOption != null) instantDeliveryOption.setEnabled(available);
        if (normalDeliveryOption != null) normalDeliveryOption.setEnabled(available);
    }

    // ==========================================
    // 2. FETCH CART ITEMS
    // ==========================================
    private void fetchCartItems() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showEmptyCartView();
            Toast.makeText(getContext(), "Please log in to see your cart.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingView();

        String userId = currentUser.getUid();
        MyApplication.getProductRepository().getCartItems(userId, new ProductRepository.CartItemsCallback() {
            @Override
            public void onSuccess(List<CartItem> items) {
                if (!isAdded() || getContext() == null) return;

                cartItemList.clear();
                if (items != null && !items.isEmpty()) {
                    cartItemList.addAll(items);
                    if (cartAdapter != null) {
                        cartAdapter.notifyDataSetChanged();
                    }
                    updateCartTotals();
                    showCartContentView();
                    if (deliveryCharge != null && deliveryCharge.isServiceable()
                            && deliveryCharge.hasChargeFor(isInstantDelivery)) {
                        updateDeliveryAvailability("", true);
                    }
                } else {
                    showEmptyCartView();
                }
            }

            @Override
            public void onFailure(Exception error) {
                if (!isAdded() || getContext() == null) return;
                Log.e(TAG, "Failed to fetch cart items: " + error.getMessage());
                showErrorView();
            }

            @Override
            public void onFreeDeliveryEligibilityChanged(boolean eligible) {
                isEligibleForFreeDelivery = eligible;
                updateCartTotals();
            }
        });
    }

    // ==========================================
    // 3. DELIVERY TYPE SELECTION
    // ==========================================
    private void setDeliveryType(boolean isInstant) {
        this.isInstantDelivery = isInstant;
        if (getContext() == null) return;

        int blueColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_blue);
        int blueTintColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_blue_tint);
        int borderColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_border);
        int surfaceColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_surface);
        int mutedColor = ContextCompat.getColor(requireContext(), R.color.gravo_cart_muted);

        if (isInstant) {
            // Instant selected
            if (instantDeliveryOption != null) {
                instantDeliveryOption.setStrokeColor(blueColor);
                instantDeliveryOption.setCardBackgroundColor(blueTintColor);
            }
            if (instantDeliveryRadioIcon != null) {
                instantDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_check);
                instantDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(blueColor));
            }

            // Normal unselected
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
        } else {
            // Normal selected
            if (normalDeliveryOption != null) {
                normalDeliveryOption.setStrokeColor(blueColor);
                normalDeliveryOption.setCardBackgroundColor(blueTintColor);
            }
            if (normalDeliveryRadioIcon != null) {
                normalDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_check);
                normalDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(blueColor));
            }

            // Instant unselected
            if (instantDeliveryOption != null) {
                instantDeliveryOption.setStrokeColor(borderColor);
                instantDeliveryOption.setCardBackgroundColor(surfaceColor);
            }
            if (instantDeliveryRadioIcon != null) {
                instantDeliveryRadioIcon.setImageResource(R.drawable.ic_radio_uncheck);
                instantDeliveryRadioIcon.setImageTintList(ColorStateList.valueOf(mutedColor));
            }

            if (timeSlotTextView != null) {
                timeSlotTextView.setText("Today, 8 AM - 11 AM");
            }
        }

        if (deliveryCharge != null && !deliveryCharge.hasChargeFor(isInstantDelivery)) {
            updateDeliveryAvailability("Selected delivery type is unavailable for this PIN code.", false);
        } else if (deliveryCharge != null && deliveryCharge.isServiceable()) {
            updateDeliveryAvailability("", true);
        }
        updateCartTotals();
    }

    // ==========================================
    // 4. DYNAMIC TOTALS RECALCULATION
    // ==========================================
    private void updateCartTotals() {
        double subtotalPrice = 0.0;
        if (cartItemList != null) {
            for (CartItem item : cartItemList) {
                if (item != null && item.getProduct() != null) {
                    subtotalPrice += item.getProduct().getPrice() * item.getQuantity();
                }
            }
        }

        if (cartItemList == null || cartItemList.isEmpty()) {
            showEmptyCartView();
            return;
        }

        Double configuredDeliveryFee = deliveryCharge == null ? null
                : (isInstantDelivery ? deliveryCharge.getInstantCharge() : deliveryCharge.getNormalCharge());
        double deliveryFee = isEligibleForFreeDelivery
                ? 0.0
                : (configuredDeliveryFee != null ? configuredDeliveryFee : 0.0);
        double grandTotal = subtotalPrice + deliveryFee;

        if (totalPriceTextView != null) {
            totalPriceTextView.setText(String.format(Locale.US, "₹%.2f", subtotalPrice));
        }
        if (deliveryFeeTextView != null) {
            deliveryFeeTextView.setText(configuredDeliveryFee == null
                    ? getString(R.string.gravo_cart_amount_pending)
                    : isEligibleForFreeDelivery ? "FREE"
                    : String.format(Locale.US, "₹%.2f", deliveryFee));
        }
        if (estimatedTotalTextView != null) {
            estimatedTotalTextView.setText(configuredDeliveryFee == null
                    ? getString(R.string.gravo_cart_amount_pending)
                    : String.format(Locale.US, "₹%.2f", grandTotal));
        }
        if (deliveryCharge != null && deliveryCharge.isServiceable()
                && deliveryCharge.hasChargeFor(isInstantDelivery)) {
            updateDeliveryAvailability("", true);
        }
    }

    // ==========================================
    // 5. CALLBACK IMPLEMENTATION (CART ADAPTER)
    // ==========================================
    @Override
    public void onQuantityChanged(CartItem cartItem, int newQuantity) {
        updateCartTotals();
        if (cartItemList != null && !cartItemList.isEmpty()) {
            showCartContentView();
        }
    }

    @Override
    public void onItemRemoved(CartItem cartItem, int position) {
        updateCartTotals();
        if (cartItemList == null || cartItemList.isEmpty()) {
            showEmptyCartView();
        }
    }

    @Override
    public void onItemSavedToWishlist(CartItem cartItem, int position) {
        updateCartTotals();
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
    // 6. VIEW STATE MANAGEMENT
    // ==========================================
    private void showLoadingView() {
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.VISIBLE);
        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
    }

    private void showCartContentView() {
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);

        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.VISIBLE);
        if (summaryCard != null) summaryCard.setVisibility(View.VISIBLE);
        if (deliveryModeOptions != null) deliveryModeOptions.setVisibility(View.VISIBLE);
        if (deliveryModeLabel != null) deliveryModeLabel.setVisibility(View.VISIBLE);
        if (deliveryAddressCard != null) deliveryAddressCard.setVisibility(View.VISIBLE);
    }

    private void showEmptyCartView() {
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.GONE);
        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.GONE);

        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.VISIBLE);
    }

    private void showErrorView() {
        if (cartLoadingProgress != null) cartLoadingProgress.setVisibility(View.GONE);
        if (cartRecyclerView != null) cartRecyclerView.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.GONE);
        if (emptyCartContainer != null) emptyCartContainer.setVisibility(View.GONE);

        if (cartErrorContainer != null) cartErrorContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cartAdapter != null) {
            cartAdapter.clearPendingUpdates();
        }
    }
}
