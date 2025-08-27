// Category.java
package com.gravo.grava;

public class Category {
    private String name;
    private String iconUrl;

    // A public no-argument constructor is required for Firestore
    public Category() {}

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }
}