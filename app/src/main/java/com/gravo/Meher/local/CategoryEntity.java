package com.gravo.Meher.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {
    @NonNull
    @PrimaryKey
    public String categoryId;
    public String name;
    public String imageUrl;
    public long cachedAt;
}