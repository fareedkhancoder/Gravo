package com.gravo.shopping;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Product implements Serializable {

    @DocumentId // Automatically maps the document ID from Firestore.
    private String productId;

    private String name;
    private int clicks;
    private String description;
    private double price;
    private String brand;
    private int stockQuantity;
    private List<String> imageUrls;
    private String categoryId;

    // --- Fields from previous warnings ---
    private double discountPercent;
    private double costPrice;
    private String sellerId;
    private Map<String, String> specifications;
    private Date createdAt;

    @PropertyName("is_new")
    private boolean isNew;

    @PropertyName("tags_lowercase")
    private List<String> tagsLowercase;



    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public List<String> getImageUrls() { return imageUrls; } // Corrected method name
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    public double getCostPrice() { return costPrice; }
    public void setCostPrice(double costPrice) { this.costPrice = costPrice; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public Map<String, String> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, String> specifications) { this.specifications = specifications; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    @PropertyName("is_new") // Links the isNew field to "is_new" in Firestore
    public boolean isNew() { return isNew; }

    @PropertyName("is_new")
    public void setNew(boolean aNew) { isNew = aNew; }

    @PropertyName("tags_lowercase") // Links tagsLowercase to "tags_lowercase" in Firestore
    public List<String> getTagsLowercase() { return tagsLowercase; }

    private Long expiryDate;

    public
    Long getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Long expiryDate) {
        this.expiryDate = expiryDate;
    }

    @PropertyName("tags_lowercase")
    public void setTagsLowercase(List<String> tagsLowercase) { this.tagsLowercase = tagsLowercase; }

    // --- Getter and Setter for quantityInCart ---

    // Provides a readable string for easy debugging.
    @Override
    public String toString() {
        return "Product{" +
                "productId='" + productId + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }


    public int getClicks() {
        return clicks;
    }

    public void setClicks(int clicks) {
        this.clicks = clicks;
    }
}