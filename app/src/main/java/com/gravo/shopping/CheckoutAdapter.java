package com.gravo.shopping;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class CheckoutAdapter extends RecyclerView.Adapter<CheckoutAdapter.CheckoutViewHolder> {

    private Context context;
    private List<CartItem> cartItemList; // Ab yeh CartItem ki list hai

    public CheckoutAdapter(Context context, List<CartItem> cartItemList) {
        this.context = context;
        this.cartItemList = cartItemList;
    }

    @NonNull
    @Override
    public CheckoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CheckoutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CheckoutViewHolder holder, int position) {
        CartItem cartItem = cartItemList.get(position);
        if (cartItem == null) return;
        Product product = cartItem.getProduct();

        if (product != null) {
            if (holder.productNameTextView != null) {
                holder.productNameTextView.setText(product.getName());
            }
            if (holder.productPriceTextView != null) {
                holder.productPriceTextView.setText("₹" + String.format("%.2f", product.getPrice()));
            }
            if (holder.quantityTextView != null) {
                holder.quantityTextView.setText("Qty: " + cartItem.getQuantity());
            }

            if (holder.productImageView != null && product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                Glide.with(context)
                        .load(product.getImageUrls().get(0))
                        .into(holder.productImageView);
            }
        }
    }

    @Override
    public int getItemCount() {
        return cartItemList != null ? cartItemList.size() : 0;
    }

    public static class CheckoutViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView;
        TextView productNameTextView, productPriceTextView, quantityTextView; // Quantity TextView add karein

        public CheckoutViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.productImageViewCart);
            productNameTextView = itemView.findViewById(R.id.productNameTextViewCart);
            productPriceTextView = itemView.findViewById(R.id.productPriceTextViewCart);
            quantityTextView = itemView.findViewById(R.id.quantityTextView); // Quantity TextView bind karein
        }
    }
}
