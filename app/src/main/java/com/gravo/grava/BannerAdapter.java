package com.gravo.grava;

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

    // CHANGE 1: अब हम Product की जगह Banner लिस्ट यूज़ कर रहे हैं
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
        // Crash Prevention
        if (bannerList == null || bannerList.isEmpty()) {
            return;
        }

        // Infinite Scroll Logic
        int actualPosition = position % bannerList.size();
        Banner banner = bannerList.get(actualPosition);
        Context context = holder.itemView.getContext();

        // CHANGE 2: सिर्फ इमेज लोड करेंगे (Name/Price का कोड हटा दिया है)
        if (banner.getImage() != null && !banner.getImage().isEmpty()) {
            Glide.with(context)
                    .load(banner.getImage())
                    .into(holder.bannerImageView);
        } else {
            // Placeholder अगर इमेज न हो
            holder.bannerImageView.setImageResource(R.drawable.ic_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        if (bannerList == null || bannerList.isEmpty()) {
            return 0;
        }
        return bannerList.size() > 1 ? Integer.MAX_VALUE : bannerList.size();
    }

    public static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView bannerImageView;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            // CHANGE 3: सिर्फ ImageView को find कर रहे हैं
            bannerImageView = itemView.findViewById(R.id.bannerImageView);
        }
    }
}