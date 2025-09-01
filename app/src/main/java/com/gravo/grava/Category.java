package com.gravo.grava;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.DocumentId;

public class Category {

    /**
     * This field will be AUTOMATICALLY filled with the document's unique ID
     * (e.g., "RlnkHyAZyIpWMLhtMWFh") by Firestore because of the @DocumentId annotation.
     */
    @DocumentId
    private String id;

    /**
     * These are regular data fields that will be filled from the data
     * INSIDE the Firestore document.
     */
    private String name;
    private String iconUrl;

    // A no-argument constructor is required for Firestore
    public Category() {}

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    // --- Setters ---

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "Category{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", iconUrl='" + iconUrl + '\'' +
                '}';
    }
}