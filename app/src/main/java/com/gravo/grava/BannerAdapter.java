// BannerAdapter.java
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

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    private List<Product> productList;

    public BannerAdapter(List<Product> productList) {
        this.productList = productList;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner_slide, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        // 1. CRASH PREVENTION: Check if the list is empty before proceeding.
        if (productList == null || productList.isEmpty()) {
            return;
        }

        // 2. INFINITE SCROLL: Calculate the actual position.
        int actualPosition = position % productList.size();
        Product product = productList.get(actualPosition);

        // Get context once for reuse.
        Context context = holder.itemView.getContext();

        // 3. USE GETTERS: Access data using public getter methods.
        holder.productNameTextView.setText(product.getName());

        // 4. USE STRING RESOURCES: Set the price using the resource from strings.xml.
        String formattedPrice = context.getString(R.string.price_format_from, product.getPrice());
        holder.productPriceTextView.setText(formattedPrice);

        // 5. USE GETTERS for image URLs.
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            Glide.with(context)
                    .load(product.getImageUrls().get(0))
                    .into(holder.bannerImageView);
        } else {
            // Optional: Set a placeholder image if no images are available.
            holder.bannerImageView.setImageResource(R.drawable.ic_placeholder); // Example placeholder
        }
    }

    @Override
    public int getItemCount() {
        if (productList == null || productList.isEmpty()) {
            return 0;
        }
        return productList.size() > 1 ? Integer.MAX_VALUE : productList.size();
    }

    public static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView bannerImageView;
        TextView productNameTextView;
        TextView productPriceTextView;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerImageView = itemView.findViewById(R.id.bannerImageView);
            productNameTextView = itemView.findViewById(R.id.productNameBannerTextView);
            productPriceTextView = itemView.findViewById(R.id.productPriceBannerTextView);
        }
    }
}