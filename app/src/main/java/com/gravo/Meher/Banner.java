package com.gravo.Meher;

public class Banner {
    private String bannerId;
    private String imageUrl; // 'image' ko badal kar 'imageUrl' kar diya hai
    private String pincode;
    private String targetProductId;

    // Required empty constructor for Firestore
    public Banner() { }

    public Banner(String bannerId, String imageUrl, String pincode, String targetProductId) {
        this.bannerId = bannerId;
        this.imageUrl = imageUrl;
        this.pincode = pincode;
        this.targetProductId = targetProductId;
    }

    public String getBannerId() {
        return bannerId;
    }

    public void setBannerId(String bannerId) {
        this.bannerId = bannerId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getTargetProductId() {
        return targetProductId;
    }

    public void setTargetProductId(String targetProductId) {
        this.targetProductId = targetProductId;
    }
}