package com.gravo.shopping;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private final Context context;
    private final List<Product> productList;
    private final OnProductClickListener clickListener;
    private final int layoutId;

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
        holder.productNameTextView.setText(product.getName());
        holder.productPriceTextView.setText("₹" + String.format("%.2f", product.getPrice()));

        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            Glide.with(context)
                    .load(product.getImageUrls().get(0))
                    .into(holder.productImageView);
        }

        // --- CORE LOGIC TO MANAGE BUTTON VISIBILITY ---
        if (product.getQuantityInCart() == 0) {
            // Show "Add" button, hide quantity selector
            holder.addButton.setVisibility(View.VISIBLE);
            holder.quantitySelector.setVisibility(View.GONE);
        } else {
            // Hide "Add" button, show quantity selector
            holder.addButton.setVisibility(View.GONE);
            holder.quantitySelector.setVisibility(View.VISIBLE);
            holder.quantityTextView.setText(String.valueOf(product.getQuantityInCart()));
        }

        // --- CLICK LISTENERS ---
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onProductClick(product);
            }
        });

        holder.addButton.setOnClickListener(v -> {
            product.setQuantityInCart(1);
            notifyItemChanged(holder.getAdapterPosition()); // Refresh this item to show the stepper
        });

        holder.plusButton.setOnClickListener(v -> {
            int currentQuantity = product.getQuantityInCart();
            product.setQuantityInCart(currentQuantity + 1);
            holder.quantityTextView.setText(String.valueOf(product.getQuantityInCart()));
        });

        holder.minusButton.setOnClickListener(v -> {
            int currentQuantity = product.getQuantityInCart();
            if (currentQuantity > 0) {
                product.setQuantityInCart(currentQuantity - 1);
                // If quantity becomes 0, refresh the item to show the "Add" button again
                if (product.getQuantityInCart() == 0) {
                    notifyItemChanged(holder.getAdapterPosition());
                } else {
                    holder.quantityTextView.setText(String.valueOf(product.getQuantityInCart()));
                }
            }
        });
    }
    public void updateList(List<Product> newList) {
        this.productList.clear();
        this.productList.addAll(newList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        // Existing views
        ImageView productImageView;
        TextView productNameTextView;
        TextView productPriceTextView;

        // New views for the add/quantity button
        TextView addButton;
        LinearLayout quantitySelector;
        ImageButton minusButton;
        TextView quantityTextView;
        ImageButton plusButton;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            // Existing
            productImageView = itemView.findViewById(R.id.productImageView);
            productNameTextView = itemView.findViewById(R.id.productNameTextView);
            productPriceTextView = itemView.findViewById(R.id.productPriceTextView);

            // New views from the layout
            addButton = itemView.findViewById(R.id.addButton);
            quantitySelector = itemView.findViewById(R.id.quantitySelector);
            minusButton = itemView.findViewById(R.id.minusButton);
            quantityTextView = itemView.findViewById(R.id.quantityTextView);
            plusButton = itemView.findViewById(R.id.PlusBtn);
        }


    }
}