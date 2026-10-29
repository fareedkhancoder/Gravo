package com.gravo.shopping.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAddress(AddressEntity address);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAddresses(List<AddressEntity> addresses);

    @Update
    void updateAddress(AddressEntity address);

    @Query("SELECT * FROM addresses WHERE userId = :userId")
    LiveData<List<AddressEntity>> observeAddressesForUser(String userId);

    @Query("SELECT * FROM addresses WHERE userId = :userId")
    List<AddressEntity> getAddressesForUser(String userId);

    @Query("SELECT * FROM addresses WHERE addressId = :addressId LIMIT 1")
    AddressEntity getAddressById(String addressId);

    @Query("SELECT * FROM addresses WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    AddressEntity getDefaultAddress(String userId);

    @Query("DELETE FROM addresses WHERE addressId = :addressId")
    void deleteAddressById(String addressId);

    @Delete
    void deleteAddress(AddressEntity address);

    @Query("DELETE FROM addresses WHERE userId = :userId")
    void clearUserAddresses(String userId);
}
