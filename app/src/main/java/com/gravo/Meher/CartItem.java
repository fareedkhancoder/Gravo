package com.gravo.Meher;

import java.io.Serializable;

/**
 * Yeh ek helper model class hai jo ek product aur uski cart mein quantity
 * ko ek saath hold karti hai.
 */
public class CartItem implements Serializable {
    private Product product;
    private int quantity;

    // Firestore ke liye zaroori
    public CartItem() {}

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    private boolean isOutOfStock = false;
    private boolean isQuantityAdjusted = false;
    private String statusBadgeText;

    public boolean isOutOfStock() {
        return isOutOfStock;
    }

    public void setOutOfStock(boolean outOfStock) {
        isOutOfStock = outOfStock;
    }

    public boolean isQuantityAdjusted() {
        return isQuantityAdjusted;
    }

    public void setQuantityAdjusted(boolean quantityAdjusted) {
        isQuantityAdjusted = quantityAdjusted;
    }

    public String getStatusBadgeText() {
        return statusBadgeText;
    }

    public void setStatusBadgeText(String statusBadgeText) {
        this.statusBadgeText = statusBadgeText;
    }
}
