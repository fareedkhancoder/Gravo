// BannerAdapter.java
package com.gravo.grava;

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
        // Infinite loop ke liye
        int actualPosition = position % productList.size();
        Product product = productList.get(actualPosition);

        holder.productNameTextView.setText(product.name);
        holder.productPriceTextView.setText("From ₹" + String.format("%.0f", product.price));

        if (product.imageUrls != null && !product.imageUrls.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(product.imageUrls.get(0))
                    .into(holder.bannerImageView);
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