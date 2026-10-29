package com.gravo.shopping;

import android.content.Context;
import android.content.Intent;
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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.data.repository.ProductRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private static final String TAG = "CartAdapter";

    public interface OnCartItemChangeListener {
        void onQuantityChanged(CartItem cartItem, int newQuantity);
        void onItemRemoved(CartItem cartItem, int position);
        void onItemSavedToWishlist(CartItem cartItem, int position);
        void onItemClicked(CartItem cartItem);
    }

    private final Context context;
    private final List<CartItem> cartItemList;
    private OnCartItemChangeListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingUpdateRunnables = new HashMap<>();

    public CartAdapter(Context context, List<CartItem> cartItemList) {
        this.context = context;
        this.cartItemList = cartItemList;
    }

    public void setOnCartItemChangeListener(OnCartItemChangeListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem cartItem = cartItemList.get(position);
        if (cartItem == null) return;
        Product product = cartItem.getProduct();

        Context ctx = holder.itemView.getContext();

        // 1. Display Product Details
        if (product != null) {
            if (holder.productNameTextView != null) {
                holder.productNameTextView.setText(product.getName() != null ? product.getName() : "");
            }

            if (holder.productPriceTextView != null) {
                holder.productPriceTextView.setText(ctx.getString(R.string.price_format, product.getPrice()));
            }

            if (holder.productImageView != null) {
                if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                    Glide.with(ctx)
                            .load(product.getImageUrls().get(0))
                            .into(holder.productImageView);
                } else {
                    holder.productImageView.setImageResource(R.drawable.ic_placeholder);
                }
            }
        }

        // 2. Display Quantity & Line Total
        int quantity = cartItem.getQuantity();
        String formattedQuantity = ctx.getString(R.string.quantity_format, quantity);

        if (holder.quantityTextView != null) {
            holder.quantityTextView.setText(formattedQuantity);
        }
        if (holder.multiplier != null) {
            holder.multiplier.setText(String.format("× %s", formattedQuantity));
        }
        if (product != null) {
            double lineTotal = product.getPrice() * quantity;
            if (holder.totalPriceTextView != null) {
                holder.totalPriceTextView.setText(ctx.getString(R.string.price_format, lineTotal));
            }
        }

        // 3. Navigate to Product Details on Item Click
        holder.itemView.setOnClickListener(v -> {
            if (product != null && product.getProductId() != null) {
                if (listener != null) {
                    listener.onItemClicked(cartItem);
                } else {
                    Intent intent = new Intent(context, ProductDetailActivity.class);
                    intent.putExtra("PRODUCT_ID", product.getProductId());
                    context.startActivity(intent);
                }
            } else {
                Toast.makeText(context, "Product details unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Quantity Increment (+ Button)
        if (holder.plusButton != null) {
            holder.plusButton.setOnClickListener(v -> {
                if (product == null) return;
                int currentQty = cartItem.getQuantity();
                int newQty = currentQty + 1;
                updateItemQuantity(holder, cartItem, product, newQty);
            });
        }

        // 5. Quantity Decrement (- Button)
        if (holder.minusButton != null) {
            holder.minusButton.setOnClickListener(v -> {
                if (product == null) return;
                int currentQty = cartItem.getQuantity();
                if (currentQty <= 1) {
                    // Prevent invalid quantity (< 1)
                    return;
                }
                int newQty = currentQty - 1;
                updateItemQuantity(holder, cartItem, product, newQty);
            });
        }

        // 6. Delete Button (Remove Item)
        if (holder.remove != null) {
            holder.remove.setOnClickListener(v -> removeItem(holder, cartItem));
        }

        // 7. Save to Wishlist Button
        if (holder.save != null) {
            holder.save.setOnClickListener(v -> saveToWishlist(holder, cartItem));
        }
    }

    private void updateItemQuantity(CartViewHolder holder, CartItem cartItem, Product product, int newQty) {
        int previousQty = cartItem.getQuantity();
        cartItem.setQuantity(newQty);

        // Update local UI immediately
        Context ctx = holder.itemView.getContext();
        String formattedQty = ctx.getString(R.string.quantity_format, newQty);
        if (holder.quantityTextView != null) {
            holder.quantityTextView.setText(formattedQty);
        }
        if (holder.multiplier != null) {
            holder.multiplier.setText(String.format("× %s", formattedQty));
        }
        if (holder.totalPriceTextView != null) {
            holder.totalPriceTextView.setText(ctx.getString(R.string.price_format, product.getPrice() * newQty));
        }

        // Notify Fragment immediately to recalculate cart totals dynamically
        if (listener != null) {
            listener.onQuantityChanged(cartItem, newQty);
        }

        // Debounce 1-second delay before saving to Firestore
        String productId = product.getProductId();
        if (productId != null) {
            Runnable existingRunnable = pendingUpdateRunnables.get(productId);
            if (existingRunnable != null) {
                handler.removeCallbacks(existingRunnable);
            }

            Runnable runnable = () -> {
                pendingUpdateRunnables.remove(productId);
                MyApplication.getProductRepository().updateCartQuantity(productId, newQty, new ProductRepository.CartCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Successfully updated quantity in Firestore for product: " + productId);
                    }

                    @Override
                    public void onFailure(String error) {
                        // Do not overwrite a newer optimistic change that may already be in progress.
                        if (cartItem.getQuantity() == newQty) {
                            cartItem.setQuantity(previousQty);
                            int position = cartItemList.indexOf(cartItem);
                            if (position != -1) {
                                notifyItemChanged(position);
                            }
                            if (listener != null) {
                                listener.onQuantityChanged(cartItem, previousQty);
                            }
                            Toast.makeText(context, "Quantity update failed. Previous quantity restored.", Toast.LENGTH_SHORT).show();
                        }
                        Log.e(TAG, "Failed to update quantity in Firestore for product: " + productId + " - " + error);
                    }
                });
            };

            pendingUpdateRunnables.put(productId, runnable);
            handler.postDelayed(runnable, 1000);
        }
    }

    private void removeItem(CartViewHolder holder, CartItem cartItem) {
        int pos = holder.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION || pos >= cartItemList.size()) return;

        Product product = cartItem.getProduct();
        if (product == null) return;
        String productId = product.getProductId();
        int previousPosition = pos;

        // Cancel pending runnable if any
        if (productId != null) {
            Runnable existingRunnable = pendingUpdateRunnables.get(productId);
            if (existingRunnable != null) {
                handler.removeCallbacks(existingRunnable);
                pendingUpdateRunnables.remove(productId);
            }
        }

        // Immediately remove item from list in UI
        cartItemList.remove(pos);
        notifyItemRemoved(pos);
        notifyItemRangeChanged(pos, cartItemList.size() - pos);

        // Notify fragment to update totals and check empty state
        if (listener != null) {
            listener.onItemRemoved(cartItem, pos);
        }

        // Synchronize deletion with Firestore
        if (productId != null) {
            MyApplication.getProductRepository().updateCartQuantity(productId, 0, new ProductRepository.CartCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Successfully removed item from Firestore cart: " + productId);
                }

                @Override
                public void onFailure(String error) {
                    int restorePosition = Math.min(previousPosition, cartItemList.size());
                    cartItemList.add(restorePosition, cartItem);
                    notifyItemInserted(restorePosition);
                    if (listener != null) {
                        listener.onQuantityChanged(cartItem, cartItem.getQuantity());
                    }
                    Toast.makeText(context, "Item removal failed. Item restored.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to remove item from Firestore cart: " + error);
                }
            });
        }
    }

    private void saveToWishlist(CartViewHolder holder, CartItem cartItem) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "Please log in to save items to wishlist.", Toast.LENGTH_SHORT).show();
            return;
        }

        Product product = cartItem.getProduct();
        if (product == null || product.getProductId() == null) {
            Toast.makeText(context, "Product information unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        String productId = product.getProductId();

        // Disable button during operation to prevent duplicate clicks
        if (holder.save != null) {
            holder.save.setEnabled(false);
        }

        Map<String, Object> wishlistData = new HashMap<>();
        wishlistData.put("productId", productId);
        wishlistData.put("timestamp", FieldValue.serverTimestamp());

        // Step 1: Save product ID + server timestamp to wishlist subcollection
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("wishlist")
                .document(productId)
                .set(wishlistData)
                .addOnSuccessListener(aVoid -> {
                    // Step 2: On wishlist operation success, remove product from cart in Firestore
                    MyApplication.getProductRepository().updateCartQuantity(productId, 0, new ProductRepository.CartCallback() {
                        @Override
                        public void onSuccess() {
                            if (holder.save != null) {
                                holder.save.setEnabled(true);
                            }

                            int pos = holder.getBindingAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION && pos < cartItemList.size()) {
                                cartItemList.remove(pos);
                                notifyItemRemoved(pos);
                                notifyItemRangeChanged(pos, cartItemList.size() - pos);
                            }

                            if (listener != null) {
                                listener.onItemSavedToWishlist(cartItem, pos);
                            }

                            Toast.makeText(context, "Saved to wishlist!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            if (holder.save != null) {
                                holder.save.setEnabled(true);
                            }
                            Toast.makeText(context, "Failed to remove item from cart: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (holder.save != null) {
                        holder.save.setEnabled(true);
                    }
                    Toast.makeText(context, "Failed to save to wishlist: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    public void clearPendingUpdates() {
        for (Runnable runnable : pendingUpdateRunnables.values()) {
            if (runnable != null) {
                handler.removeCallbacks(runnable);
            }
        }
        pendingUpdateRunnables.clear();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        clearPendingUpdates();
    }

    @Override
    public int getItemCount() {
        return cartItemList != null ? cartItemList.size() : 0;
    }

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView, plusButton, minusButton;
        TextView productNameTextView, productPriceTextView, quantityTextView, totalPriceTextView, save, multiplier;
        LinearLayout remove;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.productImageViewCart);
            productNameTextView = itemView.findViewById(R.id.productNameTextViewCart);
            productPriceTextView = itemView.findViewById(R.id.productPriceTextViewCart);
            plusButton = itemView.findViewById(R.id.PlusBtn);
            minusButton = itemView.findViewById(R.id.minusButton);
            quantityTextView = itemView.findViewById(R.id.quantityTextView);
            totalPriceTextView = itemView.findViewById(R.id.totalPriceTextView);
            if (totalPriceTextView == null) {
                totalPriceTextView = itemView.findViewById(R.id.totalAmountTextView);
            }
            save = itemView.findViewById(R.id.saveInWishlistButton);
            remove = itemView.findViewById(R.id.removeContainer);
            multiplier = itemView.findViewById(R.id.multiplier);
        }
    }
}
