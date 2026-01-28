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
        // Get the data for the current item
        CartItem cartItem = cartItemList.get(position);
        Product product = cartItem.getProduct();

        // Get context once for reuse
        Context context = holder.itemView.getContext();

        // Set the product data on the UI, if the product is not null
        if (product != null) {
            // Use the public getter for the product name
            holder.productNameTextView.setText(product.getName());

            // Use a string resource to format and set the price
            String formattedPrice = context.getString(R.string.price_format, product.getPrice());
            holder.productPriceTextView.setText(formattedPrice);

            // Use the public getter to load the image with Glide
            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                Glide.with(context)
                        .load(product.getImageUrls().get(0))
                        .into(holder.productImageView);
            } else {
                // Set a placeholder if the product has no image
                holder.productImageView.setImageResource(R.drawable.ic_placeholder); // Example placeholder
            }
        }

        // Display the quantity of the item in the cart
        String formattedQuantity = context.getString(R.string.quantity_format, cartItem.getQuantity());
        //holder.quantityTextView.setText(formattedQuantity); // Assuming you have a quantityTextView
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
