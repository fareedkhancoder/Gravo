package com.gravo.shopping.data.repository;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private static final String TAG = "AuthRepository";

    public AuthRepository() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public Task<AuthResult> signInWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        return mAuth.signInWithCredential(credential);
    }

    public void createUserDocument(FirebaseUser user) {
        if (user == null) return;
        DocumentReference userRef = db.collection("users").document(user.getUid());

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("name", user.getDisplayName());
        userData.put("email", user.getEmail());
        userData.put("createdAt", FieldValue.serverTimestamp());

        userRef.set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document created"))
                .addOnFailureListener(e -> Log.e(TAG, "Error creating user document", e));
    }
}