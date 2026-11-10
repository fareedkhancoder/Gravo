package com.gravo.Meher;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;

public class Address implements Serializable {

    @Exclude
    private String addressId;
    private String fullName;
    private String phone;
    private String alternatePhone;
    private String houseNo;
    private String street;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private String type;
    private double latitude;
    private double longitude;

    @PropertyName("default")
    private boolean isDefault;

    public Address() {}

    // --- Getters ---
    public String getAddressId() { return addressId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getAlternatePhone() { return alternatePhone; }
    public String getHouseNo() { return houseNo; }
    public String getStreet() { return street; }
    public String getLandmark() { return landmark; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public String getType() { return type; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isDefault() { return isDefault; }

    // --- Setters ---
    public void setAddressId(String addressId) { this.addressId = addressId; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAlternatePhone(String alternatePhone) { this.alternatePhone = alternatePhone; }
    public void setHouseNo(String houseNo) { this.houseNo = houseNo; }
    public void setStreet(String street) { this.street = street; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
    public void setCity(String city) { this.city = city; }
    public void setState(String state) { this.state = state; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public void setType(String type) { this.type = type; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @PropertyName("default")
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}