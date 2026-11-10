package com.gravo.Meher;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Product implements Serializable {

    @DocumentId
    private String productId;

    private String name;
    private int clicks;
    private String description;
    private double price;
    private String brand;
    private int stockQuantity;
    private int maxBuyingLimit;
    private List<String> imageUrls;
    private String categoryId;
    private String availabilityStatus; // e.g. "IN_STOCK", "OUT_OF_STOCK", "LOW_STOCK", or numeric "1", "3", "5", "6"

    private double discountPercent;
    @PropertyName("MRP")
    private Double MRP;
    private Double averageRating;
    private Integer ratingCount;
    private Integer reviewCount;
    private Map<String, String> reviewMap;
    private Map<String, Double> ratingMap;
    private String variantTitle;
    private Map<String, Object> variantMap;
    private double costPrice;
    private String sellerId;
    private Map<String, String> specifications;
    private Date createdAt;
    private Date updatedAt;
    private int schemaVersion;

    @PropertyName("isActive")
    private boolean isActive = true;

    @PropertyName("is_new")
    private boolean isNew;

    @PropertyName("tags_lowercase")
    private List<String> tagsLowercase;

    private Long expiryDate;

    public Product() {}

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getClicks() { return clicks; }
    public void setClicks(int clicks) { this.clicks = clicks; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public int getMaxBuyingLimit() { return maxBuyingLimit; }
    public void setMaxBuyingLimit(int maxBuyingLimit) { this.maxBuyingLimit = maxBuyingLimit; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    @PropertyName("MRP")
    public Double getMRP() { return MRP; }
    @PropertyName("MRP")
    public void setMRP(Double MRP) { this.MRP = MRP; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Map<String, String> getReviewMap() { return reviewMap; }
    public void setReviewMap(Map<String, String> reviewMap) { this.reviewMap = reviewMap; }

    public Map<String, Double> getRatingMap() { return ratingMap; }
    public void setRatingMap(Map<String, Double> ratingMap) { this.ratingMap = ratingMap; }

    public String getVariantTitle() { return variantTitle; }
    public void setVariantTitle(String variantTitle) { this.variantTitle = variantTitle; }

    public Map<String, Object> getVariantMap() { return variantMap; }
    public void setVariantMap(Map<String, Object> variantMap) { this.variantMap = variantMap; }

    public double getCostPrice() { return costPrice; }
    public void setCostPrice(double costPrice) { this.costPrice = costPrice; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public Map<String, String> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, String> specifications) { this.specifications = specifications; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    @PropertyName("isActive")
    public boolean isActive() { return isActive; }

    @PropertyName("isActive")
    public void setActive(boolean active) { isActive = active; }

    @PropertyName("is_new")
    public boolean isNew() { return isNew; }

    @PropertyName("is_new")
    public void setNew(boolean aNew) { isNew = aNew; }

    @PropertyName("tags_lowercase")
    public List<String> getTagsLowercase() { return tagsLowercase; }

    @PropertyName("tags_lowercase")
    public void setTagsLowercase(List<String> tagsLowercase) { this.tagsLowercase = tagsLowercase; }

    public Long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Long expiryDate) { this.expiryDate = expiryDate; }

    /**
     * Calculates the effective available stock left, considering both stockQuantity
     * and numeric or status representations in availabilityStatus (e.g., "1", "3", "5", "6", "OUT_OF_STOCK").
     */
    public int getEffectiveStockQuantity() {
        if (!isActive) return 0;
        if (availabilityStatus == null || availabilityStatus.trim().isEmpty()) {
            if (stockQuantity > 0) return stockQuantity;
            if (maxBuyingLimit > 0) return maxBuyingLimit;
            return 999;
        }

        String statusStr = availabilityStatus.trim();
        if ("OUT_OF_STOCK".equalsIgnoreCase(statusStr) || "0".equals(statusStr)) {
            return 0;
        }

        try {
            int parsedStatusStock = Integer.parseInt(statusStr);
            if (parsedStatusStock <= 0) return 0;
            return stockQuantity > 0 ? Math.min(stockQuantity, parsedStatusStock) : parsedStatusStock;
        } catch (NumberFormatException ignored) {
            if ("IN_STOCK".equalsIgnoreCase(statusStr) || "LOW_STOCK".equalsIgnoreCase(statusStr)) {
                if (stockQuantity > 0) {
                    return stockQuantity;
                }
                return maxBuyingLimit > 0 ? maxBuyingLimit : 999;
            }
            return Math.max(0, stockQuantity);
        }
    }

    /**
     * Calculates maximum allowed quantity a user can purchase.
     * If effective stock (or numeric availabilityStatus) is lower than maxBuyingLimit,
     * maxBuyingLimit is bypassed and effective stock takes precedence.
     */
    public int getMaxAllowedQuantity() {
        int effectiveStock = getEffectiveStockQuantity();
        if (effectiveStock <= 0 || !isActive) {
            return 0;
        }
        if (maxBuyingLimit > 0) {
            return Math.min(effectiveStock, maxBuyingLimit);
        }
        return effectiveStock;
    }

    public boolean isAvailableForPurchase() {
        return getMaxAllowedQuantity() > 0;
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId='" + productId + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", stockQuantity=" + stockQuantity +
                ", availabilityStatus='" + availabilityStatus + '\'' +
                ", maxBuyingLimit=" + maxBuyingLimit +
                ", isActive=" + isActive +
                '}';
    }
}
