package com.gravo.shopping;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.gravo.shopping.viewmodel.LoginViewModel;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // MVVM
    private LoginViewModel viewModel;



    // UI Components (Simple Variables)
    private MaterialButton btnGoogleLogin;
    private MaterialButton btnSkip;
    private TextView btnPhoneLogin; // XML mein ye TextView tha

    // Google Sign In
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private ActivityResultLauncher<IntentSenderRequest> oneTapLauncher;

    // Phone Auth
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private String mVerificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Simple Layout Set
        setContentView(R.layout.activity_main);

        // 1. Initialize Views with findViewById
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnSkip = findViewById(R.id.btnSkip);
        btnPhoneLogin = findViewById(R.id.loginButton); // XML ID match karein

        // 2. Setup ViewModel
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        // Setup Google OneTap
        configureGoogleOneTap();
        initializeGoogleSignInLauncher();

        // Setup Phone Auth Callbacks
        initializePhoneAuthCallbacks();

        // Check if user is already logged in
        viewModel.checkUserSession();

        setupObservers();
        setupListeners();
    }

    private void setupListeners() {
        // 1. Google Login
        btnGoogleLogin.setOnClickListener(v -> startGoogleSignIn());

        // 2. Phone Login
        btnPhoneLogin.setOnClickListener(v -> showPhoneInputDialog());

        // 3. Skip Login
        btnSkip.setOnClickListener(v -> {
            navigateToHomeActivity();
        });
    }

    private void setupObservers() {
        // Observe Loading State
        viewModel.getIsLoading().observe(this, isLoading -> {
            btnGoogleLogin.setEnabled(!isLoading);
            btnPhoneLogin.setEnabled(!isLoading);
            if(isLoading) Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show();
        });

        // Observe Success
        viewModel.getAuthSuccess().observe(this, user -> {
            Toast.makeText(this, "Welcome " + (user.getDisplayName() != null ? user.getDisplayName() : ""), Toast.LENGTH_SHORT).show();
            navigateToHomeActivity();
        });

        // Observe Error
        viewModel.getAuthError().observe(this, error -> {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    // --- Google Logic ---

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

    private void initializeGoogleSignInLauncher() {
        oneTapLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        try {
                            SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
                            String idToken = credential.getGoogleIdToken();
                            if (idToken != null) {
                                viewModel.firebaseAuthWithGoogle(idToken);
                            }
                        } catch (ApiException e) {
                            Log.e(TAG, "Google sign-in failed", e);
                        }
                    }
                }
        );
    }

    private void startGoogleSignIn() {
        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build();
                        oneTapLauncher.launch(intentSenderRequest);
                    } catch (Exception e) {
                        Log.e(TAG, "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Google Sign-In failed: " + e.getLocalizedMessage());
                    Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show();
                });
    }

    // --- Phone Logic ---

    private void showPhoneInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // 1. Inflate the custom layout
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_phone_input, null);
        builder.setView(dialogView);

        // 2. Create the dialog
        AlertDialog dialog = builder.create();

        // 3. Make background transparent so rounded corners show correctly
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // 4. Initialize UI elements from the custom layout
        EditText etPhone = dialogView.findViewById(R.id.etPhoneInput);
        View btnVerify = dialogView.findViewById(R.id.btnVerify);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        // 5. Handle Verify Click
        btnVerify.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            if (!phone.isEmpty() && phone.length() == 10) {
                startPhoneNumberVerification(phone);
                dialog.dismiss(); // Close dialog on success
            } else {
                etPhone.setError("Invalid Number"); // Set error on the input field directly
                // Optional: Shake animation could go here
            }
        });

        // 6. Handle Cancel Click
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        String formattedPhoneNumber = "+91" + phoneNumber;

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(formattedPhoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void initializePhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener(result -> navigateToHomeActivity());
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Toast.makeText(MainActivity.this, "Verification Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                mVerificationId = verificationId;
                Intent intent = new Intent(MainActivity.this, OtpActivity.class);
                intent.putExtra("verificationId", mVerificationId);
                startActivity(intent);
            }
        };
    }

    private void navigateToHomeActivity() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}