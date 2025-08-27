package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class OtpActivity extends AppCompatActivity {

    private static final String TAG = "OtpActivity";

    private FirebaseAuth mAuth;
    private String mVerificationId;

    private TextInputEditText otpEditText;
    private MaterialButton verifyButton;
    private ProgressBar otpProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        mAuth = FirebaseAuth.getInstance();

        // Get the verification ID from the intent
        mVerificationId = getIntent().getStringExtra("verificationId");

        otpEditText = findViewById(R.id.otpEditText);
        verifyButton = findViewById(R.id.verifyButton);
        otpProgressBar = findViewById(R.id.otpProgressBar);
        otpProgressBar.setVisibility(View.GONE);

        verifyButton.setOnClickListener(v -> {
            String otp = otpEditText.getText().toString().trim();
            if (otp.isEmpty() || otp.length() < 6) {
                otpEditText.setError("Enter the 6-digit OTP");
                otpEditText.requestFocus();
                return;
            }
            showLoading(true);
            verifyCode(otp);
        });
    }

    private void verifyCode(String code) {
        try {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
            signInWithPhoneAuthCredential(credential);
        } catch (Exception e) {
            Toast.makeText(this, "Verification failed. Please try again.", Toast.LENGTH_SHORT).show();
            showLoading(false);
        }
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        // Check karein ki user naya hai ya purana
                        boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();

                        if (isNewUser) {
                            Log.d(TAG, "New user signed up with phone number.");
                            // Naye user ke liye Firestore mein document banayein
                            createNewUserDocument(task.getResult().getUser());
                        } else {
                            Log.d(TAG, "Existing user logged in.");
                        }

                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        navigateToHomeActivity();

                    } else {
                        // ... (error handling)
                    }
                });
    }

    private void createNewUserDocument(FirebaseUser user) {
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(user.getUid());

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("phone", user.getPhoneNumber()); // Phone number save karein
        userData.put("createdAt", FieldValue.serverTimestamp());
        // Shuru mein naam aur email khaali chhod sakte hain

        userRef.set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document created successfully for UID: " + user.getUid()))
                .addOnFailureListener(e -> Log.e(TAG, "Error creating user document", e));
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            otpProgressBar.setVisibility(View.VISIBLE);
            verifyButton.setEnabled(false);
        } else {
            otpProgressBar.setVisibility(View.GONE);
            verifyButton.setEnabled(true);
        }
    }

    private void navigateToHomeActivity() {
        Intent intent = new Intent(OtpActivity.this, MainActivity.class); // Or your HomeActivity
        // Clear the back stack so the user cannot navigate back to the OTP or login screens
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}