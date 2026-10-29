package com.gravo.shopping.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "time_spends")
public class TimeSpendEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String productId;
    public long timeSpentMs;
    public long timestamp;

    public TimeSpendEntity(String productId, long timeSpentMs, long timestamp) {
        this.productId = productId;
        this.timeSpentMs = timeSpentMs;
        this.timestamp = timestamp;
    }
}