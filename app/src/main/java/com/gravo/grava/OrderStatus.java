package com.gravo.grava;

public class OrderStatus {
    private String status;
    private boolean isCompleted;

    public OrderStatus(String status, boolean isCompleted) {
        this.status = status;
        this.isCompleted = isCompleted;
    }

    public String getStatus() {
        return status;
    }

    public boolean isCompleted() {
        return isCompleted;
    }
}
