package com.gravo.Meher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    private List<Banner> bannerList;

    public BannerAdapter(List<Banner> bannerList) {
        this.bannerList = bannerList;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner_slide, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        if (bannerList == null || bannerList.isEmpty()) {
            return;
        }

        // Infinite Scroll Calculation
        int actualPosition = position % bannerList.size();
        Banner banner = bannerList.get(actualPosition);
        Context context = holder.itemView.getContext();

        String bannerUrl = banner != null ? banner.getImageUrl() : null;
        if (bannerUrl != null && !bannerUrl.trim().isEmpty() && !bannerUrl.equalsIgnoreCase("null")) {
            Glide.with(context)
                    .load(bannerUrl)
                    .placeholder(R.drawable.photo_placeholder)
                    .error(R.drawable.photo_placeholder)
                    .centerCrop()
                    .into(holder.bannerImageView);
        } else {
            // Fallback image if URL is missing
            holder.bannerImageView.setImageResource(R.drawable.photo_placeholder);
        }
    }

    // Allows HomeFragment to update banners without recreating the adapter
    public void updateList(List<Banner> newBanners) {
        this.bannerList = newBanners;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (bannerList == null || bannerList.isEmpty()) {
            return 0;
        }
        // Enable infinite scrolling only if we have more than 1 item
        return bannerList.size() > 1 ? Integer.MAX_VALUE : bannerList.size();
    }

    public static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView bannerImageView;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerImageView = itemView.findViewById(R.id.bannerImageView);
        }
    }
}