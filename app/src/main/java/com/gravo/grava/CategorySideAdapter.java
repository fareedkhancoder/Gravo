package com.gravo.grava;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CategorySideAdapter extends RecyclerView.Adapter<CategorySideAdapter.CategorySideViewHolder> {

    private final Context context;
    private final List<Category> categoryList;
    private final OnCategorySideClickListener mListener;
    private int selectedPosition = 0; // To keep track of the selected item

    // Click listener interface
    public interface OnCategorySideClickListener {
        void onCategorySideClick(int position);
    }

    public CategorySideAdapter(Context context, List<Category> categoryList, OnCategorySideClickListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.mListener = listener;
    }

    // Method to update the selected position from the fragment
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    @NonNull
    @Override
    public CategorySideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_side_nav, parent, false);
        return new CategorySideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategorySideViewHolder holder, int position) {
        Category category = categoryList.get(position);
        holder.categoryNameTextView.setText(category.getName());

        Glide.with(context)
                .load(category.getIconUrl())
                .into(holder.categoryImageView);

        // *** VISUAL SELECTION LOGIC ***
        // Change background color based on whether the item is selected or not
        if (selectedPosition == position) {
            holder.itemView.setBackgroundColor(Color.WHITE); // Or any other highlight color
            holder.categoryNameTextView.setTextColor(ContextCompat.getColor(context, R.color.purple_500)); // Example highlight text color
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT); // Default background
            holder.categoryNameTextView.setTextColor(Color.BLACK);
        }

        // Set the click listener on the item view
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCategorySideClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class CategorySideViewHolder extends RecyclerView.ViewHolder {
        ImageView categoryImageView;
        TextView categoryNameTextView;

        public CategorySideViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryImageView = itemView.findViewById(R.id.categoryImageView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
        }
    }
}