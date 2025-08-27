// MainActivity.java

package com.gravo.grava;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private FirebaseAuth mAuth;

    // Google Sign-In UI
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private ActivityResultLauncher<IntentSenderRequest> oneTapLauncher;

    // UI Elements
    private MaterialButton googleLoginButton, loginButton;
    private ProgressBar progressBar;
    private TextInputEditText phoneEditText;

    // Phone Auth
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private String mVerificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        googleLoginButton = findViewById(R.id.googleLoginButton);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);
        phoneEditText = findViewById(R.id.phoneEditText);
        progressBar.setVisibility(View.GONE); // Initially hide progress bar

        // Initialize Firebase and Google Auth
        mAuth = FirebaseAuth.getInstance();
        configureGoogleOneTap();
        initializeGoogleSignInLauncher();

        // Set Click Listeners
        googleLoginButton.setOnClickListener(v -> signInWithGoogle());
        loginButton.setOnClickListener(v -> startPhoneNumberVerification());

        initializePhoneAuthCallbacks();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToHomeActivity();
        }
    }

    // --- Phone Authentication Logic ---

    private void initializePhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices, Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                Log.d(TAG, "onVerificationCompleted:" + credential);
                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Log.w(TAG, "onVerificationFailed", e);
                handleSignInFailure("Phone number verification failed. Please try again.");
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                // The SMS verification code has been sent to the provided phone number.
                // We now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
                Log.d(TAG, "onCodeSent:" + verificationId);
                showLoading(false);

                // Save verification ID and resending token so we can use them later
                mVerificationId = verificationId;

                // Start the OTP activity
                Intent intent = new Intent(MainActivity.this, OtpActivity.class);
                intent.putExtra("verificationId", mVerificationId);
                startActivity(intent);
            }
        };
    }

    private void startPhoneNumberVerification() {
        String phoneNumber = phoneEditText.getText().toString().trim();
        if (phoneNumber.isEmpty() || phoneNumber.length() < 10) {
            phoneEditText.setError("Valid 10-digit phone number is required");
            phoneEditText.requestFocus();
            return;
        }

        showLoading(true);
        // Prepend country code. Make sure to use the correct one.
        String formattedPhoneNumber = "+91" + phoneNumber;

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(formattedPhoneNumber) // Phone number to verify
                        .setTimeout(60L, TimeUnit.SECONDS)    // Timeout and unit
                        .setActivity(this)                    // Activity (for callback binding)
                        .setCallbacks(mCallbacks)             // OnVerificationStateChangedCallbacks
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
                        navigateToHomeActivity();
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        handleSignInFailure("Authentication failed.");
                    }
                });
    }


    // --- Google Sign-In Logic (Mostly Unchanged) ---

    private void initializeGoogleSignInLauncher() {
        oneTapLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        try {
                            SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
                            String idToken = credential.getGoogleIdToken();
                            if (idToken != null) {
                                firebaseAuthWithGoogle(idToken);
                            }
                        } catch (ApiException e) {
                            Log.e(TAG, "Google sign-in failed with ApiException", e);
                            handleSignInFailure("Google sign-in failed. Please try again.");
                        }
                    } else {
                        handleSignInFailure("Sign-in cancelled.");
                    }
                }
        );
    }

    private void configureGoogleOneTap() {
        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .setAutoSelectEnabled(true)
                .build();
    }

    private void signInWithGoogle() {
        showLoading(true);
        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build();
                        oneTapLauncher.launch(intentSenderRequest);
                    } catch (Exception e) {
                        Log.e(TAG, "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                        handleSignInFailure("Could not start sign-in process.");
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Google Sign-In failed: " + e.getLocalizedMessage());
                    handleSignInFailure("Sign-in failed. Check your network connection.");
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();

                        if (isNewUser) {
                            Log.d(TAG, "New user signed up with Google.");
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

    // Yeh function aap MainActivity mein bhi add kar sakte hain
    private void createNewUserDocument(FirebaseUser user) {
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(user.getUid());

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("name", user.getDisplayName()); // Google se mila naam
        userData.put("email", user.getEmail()); // Google se mila email
        userData.put("createdAt", FieldValue.serverTimestamp());

        userRef.set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document created successfully for UID: " + user.getUid()))
                .addOnFailureListener(e -> Log.e(TAG, "Error creating user document", e));
    }


    // --- Helper Methods ---

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            googleLoginButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            googleLoginButton.setEnabled(true);
        }
    }

    private void handleSignInFailure(String errorMessage) {
        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        showLoading(false);
    }

    private void navigateToHomeActivity() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();}
}