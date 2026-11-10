package com.gravo.Meher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gravo.Meher.model.CancellationResult;
import com.gravo.Meher.model.OrderDetails;
import com.gravo.Meher.model.OrderProduct;
import com.gravo.Meher.model.TimelineState;
import com.gravo.Meher.viewmodel.OrderTrackViewModel;
import com.gravo.Meher.viewmodel.OrderTrackViewModelFactory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class OrderTrackActivity extends AppCompatActivity {

    private static final String EXTRA_ORDER_ID = "orderId";

    private View trackingContent;
    private TextView tvOrderStatus;
    private TextView tvOrderDate;
    private TextView tvDeliveryHeadline;
    private TextView tvEstimatedDelivery;
    private TextView tvOrderId;
    private TextView tvTimelineSubtitle;
    private TextView tvPlacedTime;
    private TextView tvConfirmedTime;
    private TextView tvPackedTime;
    private TextView tvOutForDeliveryTime;
    private TextView tvDeliveredTime;
    private TextView tvItemCount;
    private TextView tvRecipientName;
    private TextView tvDeliveryAddress;
    private TextView tvRecipientPhone;
    private TextView tvItemsTotal;
    private TextView tvDeliveryFee;
    private TextView tvDiscount;
    private TextView tvOrderTotal;
    private TextView tvPaymentMethodStatus;
    private TextView tvLastUpdated;
    private TextView btnCancelOrder;

    private View cardOrderTimeline;
    private View dotPlaced;
    private View dotConfirmed;
    private View dotPacked;
    private View dotOutForDelivery;
    private View dotDelivered;
    private View linePlaced;
    private View lineConfirmed;
    private View linePacked;
    private View lineOutForDelivery;

    private RecyclerView rvOrderItems;
    private OrderItemsAdapter itemsAdapter;

    private OrderTrackViewModel viewModel;
    private OrderDetails currentOrderDetails;
    private TimelineState currentTimelineState;

    private AnimatorSet currentTimelineAnimatorSet;
    private boolean isActivityStarted = false;
    private boolean hasAnimatedForCurrentStart = false;
    private int lastAnimatedStage = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_track);

        View root = findViewById(R.id.orderTrackingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return insets;
        });

        bindViews();
        configureProductList();
        configureActions();

        setupViewModel();
    }

    private void bindViews() {
        trackingContent = findViewById(R.id.trackingContent);

        tvOrderStatus = findViewById(R.id.tvOrderStatus);
        tvOrderDate = findViewById(R.id.tvOrderDate);
        tvDeliveryHeadline = findViewById(R.id.tvDeliveryHeadline);
        tvEstimatedDelivery = findViewById(R.id.tvEstimatedDelivery);
        tvOrderId = findViewById(R.id.tvOrderId);
        tvTimelineSubtitle = findViewById(R.id.tvTimelineSubtitle);

        tvPlacedTime = findViewById(R.id.tvPlacedTime);
        tvConfirmedTime = findViewById(R.id.tvConfirmedTime);
        tvPackedTime = findViewById(R.id.tvPackedTime);
        tvOutForDeliveryTime = findViewById(R.id.tvOutForDeliveryTime);
        tvDeliveredTime = findViewById(R.id.tvDeliveredTime);

        tvItemCount = findViewById(R.id.tvItemCount);
        tvRecipientName = findViewById(R.id.tvRecipientName);
        tvDeliveryAddress = findViewById(R.id.tvDeliveryAddress);
        tvRecipientPhone = findViewById(R.id.tvRecipientPhone);

        tvItemsTotal = findViewById(R.id.tvItemsTotal);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvOrderTotal = findViewById(R.id.tvOrderTotal);
        tvPaymentMethodStatus = findViewById(R.id.tvPaymentMethodStatus);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);

        cardOrderTimeline = findViewById(R.id.cardOrderTimeline);

        dotPlaced = findViewById(R.id.dotPlaced);
        dotConfirmed = findViewById(R.id.dotConfirmed);
        dotPacked = findViewById(R.id.dotPacked);
        dotOutForDelivery = findViewById(R.id.dotOutForDelivery);
        dotDelivered = findViewById(R.id.dotDelivered);

        linePlaced = findViewById(R.id.linePlaced);
        lineConfirmed = findViewById(R.id.lineConfirmed);
        linePacked = findViewById(R.id.linePacked);
        lineOutForDelivery = findViewById(R.id.lineOutForDelivery);

        rvOrderItems = findViewById(R.id.rvOrderItems);
    }

    private void configureProductList() {
        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
        rvOrderItems.setNestedScrollingEnabled(false);
        rvOrderItems.setHasFixedSize(false);
        itemsAdapter = new OrderItemsAdapter();
        rvOrderItems.setAdapter(itemsAdapter);
    }

    private void configureActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCopyOrderId).setOnClickListener(v -> copyOrderId());

        findViewById(R.id.btnHelpToolbar).setOnClickListener(v -> showSupportDialog());
        findViewById(R.id.btnContactSupport).setOnClickListener(v -> showSupportDialog());

        btnCancelOrder.setVisibility(View.GONE);
        btnCancelOrder.setOnClickListener(v -> confirmCancellation());
    }

    private void setupViewModel() {
        OrderTrackViewModelFactory factory = new OrderTrackViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(OrderTrackViewModel.class);

        String orderId = getOrderIdFromIntent();

        viewModel.getOrderDetails().observe(this, this::renderOrderDetails);
        viewModel.getOrderProducts().observe(this, this::renderOrderProducts);
        viewModel.getTimelineState().observe(this, this::onTimelineStateReceived);

        viewModel.getIsLoading().observe(this, isLoading ->
                trackingContent.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE));

        viewModel.getIsCancelling().observe(this, this::updateCancelButtonState);

        viewModel.getFatalError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showErrorAndClose(message);
            }
        });

        viewModel.getToastMessage().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getCancellationEvent().observe(this, event -> {
            CancellationResult result = event.getContentIfNotHandled();
            if (result != null) {
                Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.setOrderId(orderId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityStarted = true;
        hasAnimatedForCurrentStart = false;

        if (currentTimelineState != null) {
            animateTimelineIfReady(currentTimelineState);
        }
    }

    @Override
    protected void onStop() {
        isActivityStarted = false;
        cancelTimelineAnimation();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cancelTimelineAnimation();
        super.onDestroy();
    }

    private void renderOrderDetails(OrderDetails details) {
        if (details == null) return;
        this.currentOrderDetails = details;

        tvOrderStatus.setText(details.getDisplayStatus());
        try {
            tvOrderStatus.setTextColor(Color.parseColor(details.getStatusColorHex()));
        } catch (IllegalArgumentException ignored) {
            tvOrderStatus.setTextColor(Color.parseColor("#315CE8"));
        }

        tvOrderDate.setText(details.getOrderDateText());
        tvDeliveryHeadline.setText(details.getDeliveryHeadline());
        tvEstimatedDelivery.setText(details.getEstimatedDelivery());
        tvOrderId.setText(details.getDisplayOrderId());

        tvRecipientName.setText(details.getRecipientName());
        tvDeliveryAddress.setText(details.getDeliveryAddressText());
        tvRecipientPhone.setText(details.getRecipientPhone());

        tvItemsTotal.setText(details.getItemsTotalFormatted());
        tvDeliveryFee.setText(details.getDeliveryFeeFormatted());
        tvDiscount.setText(details.getDiscountFormatted());
        tvOrderTotal.setText(details.getOrderTotalFormatted());
        tvPaymentMethodStatus.setText(details.getPaymentMethodStatus());
        tvLastUpdated.setText(details.getLastUpdatedText());

        btnCancelOrder.setVisibility(details.canCancel() ? View.VISIBLE : View.GONE);
    }

    private void renderOrderProducts(List<OrderProduct> products) {
        if (products == null) products = Collections.emptyList();
        itemsAdapter.submitList(products);

        int totalQuantity = 0;
        for (OrderProduct product : products) {
            totalQuantity += product.getQuantity();
        }
        tvItemCount.setText(totalQuantity + (totalQuantity == 1 ? " item" : " items"));
    }

    private void onTimelineStateReceived(TimelineState state) {
        if (state == null) return;
        this.currentTimelineState = state;

        if (!state.isVisible()) {
            cancelTimelineAnimation();
            cardOrderTimeline.setVisibility(View.GONE);
            tvTimelineSubtitle.setText(state.getTimelineSubtitle());
            return;
        }

        cardOrderTimeline.setVisibility(View.VISIBLE);
        tvTimelineSubtitle.setText(state.getTimelineSubtitle());

        tvPlacedTime.setText(state.getPlacedTime());
        tvConfirmedTime.setText(state.getConfirmedTime());
        tvPackedTime.setText(state.getPackedTime());
        tvOutForDeliveryTime.setText(state.getOutForDeliveryTime());
        tvDeliveredTime.setText(state.getDeliveredTime());

        if (isActivityStarted) {
            if (!hasAnimatedForCurrentStart || state.getStage() != lastAnimatedStage) {
                animateTimelineIfReady(state);
            } else {
                applyStaticTimelineState(state.getStage());
            }
        } else {
            applyStaticTimelineState(state.getStage());
        }
    }

    private void applyStaticTimelineState(int stage) {
        cancelTimelineAnimation();

        View[] dots = {dotPlaced, dotConfirmed, dotPacked, dotOutForDelivery, dotDelivered};
        View[] lines = {linePlaced, lineConfirmed, linePacked, lineOutForDelivery};

        int doneColor = Color.parseColor("#22A06B");
        int pendingColor = Color.parseColor("#E4E7EC");

        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(i <= stage
                    ? R.drawable.bg_order_step_done
                    : R.drawable.bg_order_step_pending);
            dots[i].setScaleX(1.0f);
            dots[i].setScaleY(1.0f);
            dots[i].setAlpha(1.0f);
        }
        for (int i = 0; i < lines.length; i++) {
            lines[i].setBackgroundTintList(ColorStateList.valueOf(i < stage ? doneColor : pendingColor));
            lines[i].setAlpha(1.0f);
        }
    }

    private void animateTimelineIfReady(TimelineState state) {
        if (!state.isVisible() || !isActivityStarted) return;

        cancelTimelineAnimation();

        int stage = state.getStage();
        hasAnimatedForCurrentStart = true;
        lastAnimatedStage = stage;

        View[] dots = {dotPlaced, dotConfirmed, dotPacked, dotOutForDelivery, dotDelivered};
        View[] lines = {linePlaced, lineConfirmed, linePacked, lineOutForDelivery};

        int doneColor = Color.parseColor("#22A06B");
        int pendingColor = Color.parseColor("#E4E7EC");

        for (int i = 0; i < dots.length; i++) {
            if (i <= stage) {
                dots[i].setBackgroundResource(R.drawable.bg_order_step_done);
                dots[i].setScaleX(0.75f);
                dots[i].setScaleY(0.75f);
                dots[i].setAlpha(0.0f);
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_order_step_pending);
                dots[i].setScaleX(1.0f);
                dots[i].setScaleY(1.0f);
                dots[i].setAlpha(1.0f);
            }
        }

        for (int i = 0; i < lines.length; i++) {
            if (i < stage) {
                lines[i].setBackgroundTintList(ColorStateList.valueOf(doneColor));
                lines[i].setAlpha(0.0f);
            } else {
                lines[i].setBackgroundTintList(ColorStateList.valueOf(pendingColor));
                lines[i].setAlpha(1.0f);
            }
        }

        List<Animator> animators = new ArrayList<>();
        for (int i = 0; i <= stage; i++) {
            PropertyValuesHolder pvhScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.75f, 1.0f);
            PropertyValuesHolder pvhScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.75f, 1.0f);
            PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f, 1.0f);

            ObjectAnimator dotAnim = ObjectAnimator.ofPropertyValuesHolder(dots[i], pvhScaleX, pvhScaleY, pvhAlpha);
            dotAnim.setDuration(220);
            animators.add(dotAnim);

            if (i < stage) {
                ObjectAnimator lineAnim = ObjectAnimator.ofFloat(lines[i], View.ALPHA, 0.0f, 1.0f);
                lineAnim.setDuration(180);
                animators.add(lineAnim);
            }
        }

        currentTimelineAnimatorSet = new AnimatorSet();
        currentTimelineAnimatorSet.playSequentially(animators);
        currentTimelineAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentTimelineAnimatorSet == animation) {
                    currentTimelineAnimatorSet = null;
                }
            }
        });
        currentTimelineAnimatorSet.start();
    }

    private void cancelTimelineAnimation() {
        if (currentTimelineAnimatorSet != null) {
            currentTimelineAnimatorSet.removeAllListeners();
            currentTimelineAnimatorSet.cancel();
            currentTimelineAnimatorSet = null;
        }
    }

    private void updateCancelButtonState(boolean isCancelling) {
        btnCancelOrder.setEnabled(!isCancelling);
        btnCancelOrder.setAlpha(isCancelling ? 0.55f : 1.0f);
    }

    private void confirmCancellation() {
        if (currentOrderDetails == null || TextUtils.isEmpty(currentOrderDetails.getOrderId())) return;

        new AlertDialog.Builder(this)
                .setTitle("Cancel this order?")
                .setMessage("We'll send your cancellation request for this order. The final decision will be handled securely by Gravo.")
                .setNegativeButton("Keep order", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Request cancellation", (dialog, which) -> viewModel.requestCancellation())
                .show();
    }

    private String getOrderIdFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return null;

        String id = intent.getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(id)) id = intent.getStringExtra("ORDER_ID");
        if (TextUtils.isEmpty(id)) id = intent.getStringExtra("order_id");

        if (TextUtils.isEmpty(id) && intent.getData() != null) {
            Uri data = intent.getData();
            List<String> segments = data.getPathSegments();
            if (segments != null && !segments.isEmpty()) {
                id = segments.get(segments.size() - 1);
            }
        }
        return id == null ? null : id.trim();
    }

    private void copyOrderId() {
        String value = tvOrderId.getText().toString();
        if (TextUtils.isEmpty(value) || "—".contentEquals(value)) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", value));
            Toast.makeText(this, "Order ID copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSupportDialog() {
        String orderId = currentOrderDetails != null ? currentOrderDetails.getOrderId() : getOrderIdFromIntent();
        String message = "For help with this order, contact Gravo support and share this order ID:\n\n"
                + (TextUtils.isEmpty(orderId) ? "Unavailable" : orderId);

        new AlertDialog.Builder(this)
                .setTitle("Order support")
                .setMessage(message)
                .setNeutralButton("Copy order ID", (dialog, which) -> copyOrderId())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showErrorAndClose(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private String formatCurrency(double rupees) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(Math.max(0, rupees));
    }

    private class OrderItemsAdapter extends RecyclerView.Adapter<OrderItemsAdapter.ProductViewHolder> {
        private final List<OrderProduct> items = new ArrayList<>();

        void submitList(List<OrderProduct> products) {
            items.clear();
            if (products != null) items.addAll(products);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_tracking_product, parent, false);
            return new ProductViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
            OrderProduct product = items.get(position);

            holder.tvName.setText(product.getName());
            holder.tvQuantity.setText("Qty " + product.getQuantity());
            holder.tvUnitPrice.setText(product.getUnitPrice() > 0
                    ? formatCurrency(product.getUnitPrice()) + " each"
                    : "");
            holder.tvLineTotal.setText(formatCurrency(product.getLineTotal()));

            if (TextUtils.isEmpty(product.getVariant())) {
                holder.tvVariant.setVisibility(View.GONE);
            } else {
                holder.tvVariant.setVisibility(View.VISIBLE);
                holder.tvVariant.setText(product.getVariant());
            }

            holder.divider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);

            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.bg_order_product_image)
                    .error(R.drawable.bg_order_product_image)
                    .centerCrop()
                    .into(holder.ivImage);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public void onViewRecycled(@NonNull ProductViewHolder holder) {
            Glide.with(getApplicationContext()).clear(holder.ivImage);
            holder.ivImage.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        class ProductViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName;
            final TextView tvVariant;
            final TextView tvQuantity;
            final TextView tvUnitPrice;
            final TextView tvLineTotal;
            final View divider;

            ProductViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.ivProductImage);
                tvName = itemView.findViewById(R.id.tvProductName);
                tvVariant = itemView.findViewById(R.id.tvProductVariant);
                tvQuantity = itemView.findViewById(R.id.tvProductQuantity);
                tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
                tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
                divider = itemView.findViewById(R.id.productDivider);
            }
        }
    }
}
