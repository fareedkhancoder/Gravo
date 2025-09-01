// CategoryAdapter.java
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

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private Context context;
    private List<Category> categoryList;
    // 1. Declare the listener variable
    private OnCategoryItemClickListener mListener;

    // 2. Define the click listener interface
    public interface OnCategoryItemClickListener {
        void onCategoryItemClick(Category category);
    }

    // 3. Update the constructor to accept the listener
    public CategoryAdapter(Context context, List<Category> categoryList, OnCategoryItemClickListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.mListener = listener; // Assign the listener
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categoryList.get(position);
        holder.categoryNameTextView.setText(category.getName());

        Glide.with(context)
                .load(category.getIconUrl()) // Assuming getIconUrl() is correct
                .into(holder.categoryImageView);

        // 4. Set the click listener on the entire item view
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                // Pass the clicked category object back to the fragment
                mListener.onCategoryItemClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView categoryImageView;
        TextView categoryNameTextView;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // Make sure these IDs match your R.layout.item_category XML file
            categoryImageView = itemView.findViewById(R.id.categoryImageView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
        }
    }
}