package com.gravo.shopping.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "product_click_counts")
public class ProductClickCount {
    @PrimaryKey
    @NonNull
    public String productId;
    public int clickCount;

    public ProductClickCount() {
    }

    public ProductClickCount(@NonNull String productId, int clickCount) {
        this.productId = productId;
        this.clickCount = clickCount;
    }
}