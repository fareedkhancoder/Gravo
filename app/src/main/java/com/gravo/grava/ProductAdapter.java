// ProductAdapter.java
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

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {


    public interface OnProductClickListener {
        void onProductClick(Product product);
    }


    private Context context;
    private List<Product> productList;
    private OnProductClickListener clickListener; // NAYA: Listener variable
    private int layoutId; // To allow using different layouts (list vs grid)

    public ProductAdapter(Context context, List<Product> productList, int layoutId, OnProductClickListener clickListener) {
        this.context = context;
        this.productList = productList;
        this.layoutId = layoutId;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.productNameTextView.setText(product.name);
        holder.productPriceTextView.setText("₹" + String.format("%.2f", product.price));

        if (product.imageUrls != null && !product.imageUrls.isEmpty()) {
            Glide.with(context)
                    .load(product.imageUrls.get(0))
                    .into(holder.productImageView);
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

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView;
        TextView productNameTextView;
        TextView productPriceTextView;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.productImageView);
            productNameTextView = itemView.findViewById(R.id.productNameTextView);
            productPriceTextView = itemView.findViewById(R.id.productPriceTextView);
        }
    }
}