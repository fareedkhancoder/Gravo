package com.gravo.shopping.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.gravo.shopping.Product;
import com.gravo.shopping.R;

import java.util.ArrayList;
import java.util.List;

public class TimeSpentProductAdapter extends RecyclerView.Adapter<TimeSpentProductAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private final Context context;
    private final List<Product> productList = new ArrayList<>();
    private final OnProductClickListener clickListener;

    private final RequestOptions imageOptions = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .centerCrop();

    public TimeSpentProductAdapter(Context context, OnProductClickListener clickListener) {
        this.context = context;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.squared_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);
        if (product == null) return;

        // Name
        if (holder.productName != null) {
            holder.productName.setText(product.getName() != null ? product.getName() : "");
        }

        // Image
        if (holder.productImage != null) {
            String imageUrl = null;
            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                imageUrl = product.getImageUrls().get(0);
            }

            Glide.with(holder.productImage)
                    .load(imageUrl)
                    .apply(imageOptions)
                    .into(holder.productImage);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onProductClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    // ========== Important methods ==========

    public void setProducts(List<Product> newList) {
        productList.clear();
        if (newList != null) {
            productList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public void addProducts(List<Product> moreProducts) {
        if (moreProducts == null || moreProducts.isEmpty()) return;
        int start = productList.size();
        productList.addAll(moreProducts);
        notifyItemRangeInserted(start, moreProducts.size());
    }

    public List<Product> getCurrentList() {
        return new ArrayList<>(productList);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.productImageView);
            productName = itemView.findViewById(R.id.productName);
        }
    }
}