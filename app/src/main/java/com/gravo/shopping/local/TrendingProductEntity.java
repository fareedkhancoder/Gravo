package com.gravo.shopping.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trending_products")
public class TrendingProductEntity {

    @PrimaryKey
    @NonNull
    public String productId = "";

    public int clicks;

    public TrendingProductEntity(@NonNull String productId, int clicks) {
        this.productId = productId;
        this.clicks = clicks;
    }
}