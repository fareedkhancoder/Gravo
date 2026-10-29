package com.gravo.shopping.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "addresses")
public class AddressEntity {
    @PrimaryKey
    @NonNull
    public String addressId = "";
    public String userId;
    public String fullName;
    public String phoneNumber;
    public String alternatePhone; // Naya field
    public String houseNo;
    public String areaStreet;
    public String landmark; // Naya field
    public String city;
    public String state;
    public String pincode;
    public boolean isDefault;
    public String type;

    public double latitude;
    public double longitude;

    public AddressEntity() {}
}