package com.gravo.shopping;

import android.content.Context;
import android.graphics.Paint;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private final Context context;
    private final List<Product> productList;
    private final int layoutId;
    private final OnProductClickListener clickListener;

    private final DecimalFormat priceFormatter = new DecimalFormat("0.00");
    private final RequestOptions imageOptions = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .dontAnimate()
            .override(400, 400);

    public ProductAdapter(@NonNull Context context, List<Product> productList, int layoutId, OnProductClickListener clickListener) {
        this.context = context;
        this.productList = productList != null ? productList : new ArrayList<>();
        this.layoutId = layoutId;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        if (product == null) return;

        // 1. BRAND (Crash fix: Null check added because item_product.xml lacks this view)
        if (holder.productBrand != null) {
            String brand = product.getBrand();
            holder.productBrand.setText(brand != null ? brand : "");
        }

        // 2. PRODUCT NAME
        if (holder.productName != null) {
            String name = product.getName();
            holder.productName.setText(name != null ? name : "");
        }
        // 3. PRICES & DISCOUNT
        if (holder.productPrice != null && holder.productMRP != null && holder.productDiscount != null) {

            // Since cost price is AFTER discount, it acts as our final selling price
            double finalSellingPrice = sanitizePrice(product.getPrice());
            double discountPercent = product.getDiscountPercent();

            // Show the final selling price
            holder.productPrice.setText(formatPrice(finalSellingPrice));

            // Calculate the original MRP before the discount was applied
            // Formula: MRP = Final Price / (1 - (Discount% / 100))
            double mrp = finalSellingPrice;
            if (discountPercent > 0 && discountPercent < 100) {
                mrp = finalSellingPrice / (1.0 - (discountPercent / 100.0));
            }

            if (discountPercent > 0 && finalSellingPrice >= 0) {
                holder.productMRP.setVisibility(View.VISIBLE);
                holder.productDiscount.setVisibility(View.VISIBLE);

                holder.productMRP.setText(formatPrice(mrp));
                holder.productMRP.setPaintFlags(holder.productMRP.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

                holder.productDiscount.setText(String.format(Locale.getDefault(), "%.0f%% off", discountPercent));
            } else {
                holder.productMRP.setVisibility(View.GONE);
                holder.productDiscount.setVisibility(View.GONE);
                holder.productMRP.setText("");
                holder.productDiscount.setText("");
            }
        }

        // 4. RATING (Placeholder fix for the orphaned view)
        if (holder.productRating != null) {
            holder.productRating.setText("★ 4.5"); // Hardcoded until you add a 'rating' field to Product.java
        }

        // 5. IMAGE
        if (holder.productImageView != null) {
            loadImage(holder.productImageView, product);
        }

        // 6. CLICK LISTENER
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onProductClick(product);
            }
        });
    }

    private void loadImage(@NonNull ImageView imageView, @NonNull Product product) {
        String imageUrl = null;
        List<String> imageUrls = product.getImageUrls();

        if (imageUrls != null && !imageUrls.isEmpty()) {
            imageUrl = imageUrls.get(0);
        }

        Glide.with(imageView).clear(imageView);

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.placeholder);
            return;
        }

        Glide.with(imageView)
                .load(imageUrl)
                .apply(imageOptions)
                .thumbnail(0.25f)
                .into(imageView);
    }

    private double sanitizePrice(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return 0.0;
        return value;
    }

    private String formatPrice(double price) {
        long roundedPrice = Math.round(price);
        return "₹" + roundedPrice;
    }

    public void updateList(List<Product> newList) {
        productList.clear();
        if (newList != null) {
            productList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    @Override
    public void onViewRecycled(@NonNull ProductViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.productImageView != null) {
            Glide.with(holder.productImageView).clear(holder.productImageView);
        }
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        final ImageView productImageView;
        final TextView productBrand;
        final TextView productName;
        final TextView productPrice;
        final TextView productMRP;
        final TextView productDiscount;
        final TextView productRating; // Added reference

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.productImageView);
            productBrand = itemView.findViewById(R.id.productBrand); // Will be null for item_product.xml
            productName = itemView.findViewById(R.id.productName);
            productPrice = itemView.findViewById(R.id.productPrice);
            productMRP = itemView.findViewById(R.id.productMRP);
            productDiscount = itemView.findViewById(R.id.productDiscount);
            productRating = itemView.findViewById(R.id.productRating); // Will map to XML rating
        }
    }
}