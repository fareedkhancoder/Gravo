package com.gravo.Meher.model;

public class OrderProduct {
    private final String productId;
    private String name;
    private String imageUrl;
    private final String variant;
    private final int quantity;
    private final double unitPrice;
    private final double lineTotal;

    public OrderProduct(String productId, String name, String imageUrl, String variant,
                        int quantity, double unitPrice, double lineTotal) {
        this.productId = productId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.variant = variant;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getVariant() { return variant; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getLineTotal() { return lineTotal; }
}
