package com.gravo.Meher.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_click_events")
public class LocalClickEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String productId;
    public long timestamp;

    public LocalClickEvent() {
    }

    public LocalClickEvent(String productId, long timestamp) {
        this.productId = productId;
        this.timestamp = timestamp;
    }
}