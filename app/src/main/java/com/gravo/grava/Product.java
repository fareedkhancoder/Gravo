package com.gravo.grava;

import com.google.firebase.firestore.Exclude;
import java.io.Serializable;
import java.util.List;

public class Product implements Serializable {

    @Exclude
    public String productId;

    public String name;
    public String description;
    public double price;
    public String brand;
    public int stockQuantity;
    public List<String> imageUrls; // storing URLs
    public String categoryId;

    public Product() {
        // Needed for Firestore
    }

    public Product(String name, String description, double price, String brand,
                   int stockQuantity, List<String> imageUrls, String categoryId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.brand = brand;
        this.stockQuantity = stockQuantity;
        this.imageUrls = imageUrls;
        this.categoryId = categoryId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    // ✅ Fixed getter
    public List<String> getImageUrl() {
        return imageUrls;
    }

    // Optional: First image only
    public String getFirstImageUrl() {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            return imageUrls.get(0);
        }
        return null;
    }
}
