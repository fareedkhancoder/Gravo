package com.gravo.shopping;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;

public class Address implements Serializable {

    @Exclude
    private String addressId;

    private String fullName;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String pincode;
    private String type;

    // Firestore field: "default"
    @PropertyName("default")
    private boolean isDefault;

    public Address() {
        // Firestore requires empty constructor
    }

    // --- Getters ---
    public String getAddressId() { return addressId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public String getType() { return type; }
    public boolean isDefault() { return isDefault; }

    // --- Setters ---
    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    @PropertyName("default")
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    // --- ToString for Display / Logs ---
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (fullName != null && !fullName.isEmpty()) {
            sb.append(fullName).append(",");
        }

        if (street != null && !street.isEmpty()) {
            sb.append(street).append(", ");
        }

        if (city != null && !city.isEmpty()) {
            sb.append(city).append(", ");
        }

        if (state != null && !state.isEmpty()) {
            sb.append(state).append(" ");
        }

        if (pincode != null && !pincode.isEmpty()) {
            sb.append("- ").append(pincode);
        }

        if (phone != null && !phone.isEmpty()) {
            sb.append(",Phone: ").append(phone);
        }

        if (type != null && !type.isEmpty()) {
            sb.append(" (").append(type).append(")");
        }

        return sb.toString().trim();
    }
}
