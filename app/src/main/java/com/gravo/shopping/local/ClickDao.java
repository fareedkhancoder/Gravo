package com.gravo.shopping.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ClickDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertClickCount(ProductClickCount clickCount);

    @Query("UPDATE product_click_counts SET clickCount = clickCount + 1 WHERE productId = :productId")
    int incrementExisting(String productId);

    @Query("SELECT * FROM product_click_counts WHERE productId = :productId LIMIT 1")
    ProductClickCount getClickCount(String productId);

    @Query("SELECT * FROM product_click_counts")
    List<ProductClickCount> getClickCounts();

    @Query("UPDATE product_click_counts SET clickCount = clickCount - :syncedCount WHERE productId = :productId")
    void decrementClickCount(String productId, int syncedCount);

    @Query("DELETE FROM product_click_counts WHERE productId = :productId AND clickCount <= 0")
    void deleteIfEmpty(String productId);

    @Insert
    void insertClick(LocalClickEvent click);

    @Query("SELECT * FROM local_click_events ORDER BY timestamp DESC")
    List<LocalClickEvent> getAllClicks();

    // Startup Sync ke liye: Har productId ke total clicks calculate karta hai
    @Query("SELECT productId, COUNT(*) as clickCount FROM local_click_events GROUP BY productId")
    List<ProductClickCount> getAggregatedClicks();

    // 12 se 24 ghante purane click events delete karne ke liye
    @Query("DELETE FROM local_click_events WHERE timestamp < :cutoffTimestamp")
    int deleteOldClicks(long cutoffTimestamp);

    // Sync complete hone ke baad saare temporary clicks clear karne ke liye
    @Query("DELETE FROM local_click_events")
    void clearAllClicks();
}