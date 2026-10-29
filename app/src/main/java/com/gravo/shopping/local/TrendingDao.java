package com.gravo.shopping.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gravo.shopping.local.TrendingProductEntity;

import java.util.List;

@Dao
public interface TrendingDao {

    // 1. Ek sath saare products save karne ke liye (agar ID pehle se hai toh replace kar dega)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TrendingProductEntity> products);

    // 2. Highest clicks wale products nikalne ke liye (Descending order)
    @Query("SELECT * FROM trending_products ORDER BY clicks DESC LIMIT :limit")
    List<TrendingProductEntity> getTopTrending(int limit);
    @Query("SELECT * FROM trending_products ORDER BY clicks DESC")
    List<TrendingProductEntity> getAllTrending();

    // 3. Kisi specific product ke clicks check karne ke liye
    @Query("SELECT clicks FROM trending_products WHERE productId = :productId")
    int getClicksByProductId(String productId);

    // 4. Poora table clear karne ke liye (jab naya pincode update ho)
    @Query("DELETE FROM trending_products")
    void deleteAll();
}