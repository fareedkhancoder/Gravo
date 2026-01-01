package com.gravo.grava;

public class Banner {
    // This name 'image' must match exactly the field name in your Firestore document
    private String image;

    // Empty constructor is required for Firestore
    public Banner() { }

    public Banner(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}