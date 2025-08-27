package com.gravo.grava;

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
}
