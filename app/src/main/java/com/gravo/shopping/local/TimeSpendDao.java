package com.gravo.shopping.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TimeSpendDao {

    @Insert
    void insertTimeSpend(TimeSpendEntity timeSpend);

    @Query("SELECT * FROM time_spends WHERE productId = :productId")
    List<TimeSpendEntity> getTimeSpendsForProduct(String productId);

    // Particular product par total time spend calculate karta hai
    @Query("SELECT SUM(timeSpentMs) FROM time_spends WHERE productId = :productId")
    Long getTotalTimeSpentForProduct(String productId);

    // Group By query - Saare products par user dwara bitaya gaya total time calculate karne ke liye
    @Query("SELECT productId, SUM(timeSpentMs) as totalTimeSpentMs FROM time_spends GROUP BY productId ORDER BY totalTimeSpentMs DESC")
    List<ProductTimeSpendSum> getAggregatedTimeSpends();

    // Specified time (e.g., 24 hrs) se purana data purge karne ke liye
    @Query("DELETE FROM time_spends WHERE timestamp < :cutoffTimestamp")
    int deleteOldTimeSpends(long cutoffTimestamp);

    @Query("DELETE FROM time_spends")
    void clearAllTimeSpends();
}