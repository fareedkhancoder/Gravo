package com.gravo.grava;

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

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private Context context;
    private List<CartItem> cartItemList;

    public CartAdapter(Context context, List<CartItem> cartItemList) {
        this.context = context;
        this.cartItemList = cartItemList;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // item_cart.xml layout ko inflate karein
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        // Current item ka data prapt karein
        CartItem cartItem = cartItemList.get(position);
        Product product = cartItem.getProduct();

        if (product != null) {
            // UI par data set karein
            holder.productNameTextView.setText(product.name);
            holder.productPriceTextView.setText("₹" + String.format("%.2f", product.price));

            // Glide ka istemal karke image load karein
            if (product.imageUrls != null && !product.imageUrls.isEmpty()) {
                Glide.with(context)
                        .load(product.imageUrls.get(0))
                        .into(holder.productImageView);
            }
        }
        // Aap yahan quantity (cartItem.getQuantity()) bhi dikha sakte hain
    }

    @Override
    public int getItemCount() {
        return cartItemList.size();
    }

    /**
     * ViewHolder class jo item_cart.xml ke views ko hold karti hai
     */
    public static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView;
        TextView productNameTextView, productPriceTextView;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.productImageViewCart);
            productNameTextView = itemView.findViewById(R.id.productNameTextViewCart);
            productPriceTextView = itemView.findViewById(R.id.productPriceTextViewCart);
        }
    }
}
