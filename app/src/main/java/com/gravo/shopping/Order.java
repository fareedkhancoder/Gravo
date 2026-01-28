package com.gravo.shopping;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.util.List;
import java.util.Map;

// This class represents an 'orders' document in Firestore
public class Order {

    @Exclude
    private String orderId; // To store the document ID

    private String orderStatus;
    private double totalAmount;
    private Timestamp orderDate;
    private String paymentMethod; // To store how the user paid
    private Address shippingAddress; // To store the delivery address object
    private List<Map<String, Object>> items; // To store the list of purchased items

    public Order() {
        // Required for Firestore
    }

    // --- Getters ---
    public String getOrderId() {
        return orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public Timestamp getOrderDate() {
        return orderDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }


    // --- Setters ---
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setOrderDate(Timestamp orderDate) {
        this.orderDate = orderDate;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }
}
