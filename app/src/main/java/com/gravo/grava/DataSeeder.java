package com.gravo.grava;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataSeeder {

    private static final String TAG = "DataSeeder";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Yeh function har product ke 'name' se words nikal kar
     * unhe lowercase mein 'tags_lowercase' naam ke ek naye array field mein save karta hai.
     */
    public void addLowercaseTagsFromName() {
        Log.d(TAG, "Starting to add lowercase tags from product names...");

        db.collection("products").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // WriteBatch ka istemal karein taaki saare badlav ek saath ho
                WriteBatch batch = db.batch();
                int productsUpdated = 0;

                for (QueryDocumentSnapshot document : task.getResult()) {
                    String productName = document.getString("name");

                    if (productName != null && !productName.isEmpty()) {
                        // Product ke naam ko chote shabdon mein todein
                        String[] words = productName.toLowerCase().split("\\s+");
                        List<String> lowercaseTags = new ArrayList<>(Arrays.asList(words));

                        // Naye field ko document mein update karein
                        batch.update(document.getReference(), "tags_lowercase", lowercaseTags);
                        productsUpdated++;
                    }
                }

                // Saare badlav ek saath save karein
                int finalProductsUpdated = productsUpdated;
                batch.commit().addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SUCCESS: Successfully added lowercase tags to " + finalProductsUpdated + " products.");
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "FAILURE: Error committing batch update for tags.", e);
                });

            } else {
                Log.e(TAG, "FAILURE: Error fetching products to create tags.", task.getException());
            }
        });
    }

    // ... (Aapke purane DataSeeder ke functions yahan ho sakte hain)
}
