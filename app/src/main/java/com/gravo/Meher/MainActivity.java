package com.gravo.Meher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.gravo.Meher.viewmodel.LoginViewModel;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PRIVACY_POLICY_URL = "https://yourdomain.com/privacy-policy";
    private static final String TERMS_URL = "https://yourdomain.com/terms";
    private static final long PHONE_AUTH_TIMEOUT_SECONDS = 60L;

    private LoginViewModel viewModel;

    // Main authentication entry points.
    private LinearLayout btnGoogleLogin;
    private LinearLayout btnPhoneLogin;

    // Activity-level blocking loader. It is shown after the phone dialog closes.
    private FrameLayout authLoadingOverlay;
    private TextView tvAuthLoadingMessage;

    // Shared UI state guards. These prevent duplicate requests from rapid taps.
    private boolean isPhoneVerificationInProgress = false;
    private boolean isGoogleSignInInProgress = false;
    private boolean isViewModelLoading = false;
    private boolean isOtpScreenOpened = false;
    private boolean isNavigating = false;

    private String pendingPhoneNumber;

    // Google One Tap.
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private ActivityResultLauncher<IntentSenderRequest> oneTapLauncher;

    // Firebase Phone Auth.
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        configureGoogleOneTap();
        initializeGoogleSignInLauncher();
        initializePhoneAuthCallbacks();

        setupObservers();
        setupListeners();
        setupLegalLinks();

        // Keep the existing session check. Its loading state is observed below.
        viewModel.checkUserSession();
    }

    private void bindViews() {
        btnGoogleLogin = findViewById(R.id.btnContinueGoogle);
        btnPhoneLogin = findViewById(R.id.btnContinuePhone);
        authLoadingOverlay = findViewById(R.id.layoutAuthLoading);
        tvAuthLoadingMessage = findViewById(R.id.tvAuthLoadingMessage);

        if (btnGoogleLogin == null || btnPhoneLogin == null
                || authLoadingOverlay == null || tvAuthLoadingMessage == null) {
            throw new IllegalStateException(
                    "activity_main.xml is missing one or more required authentication views.");
        }
    }

    private void setupListeners() {
        btnGoogleLogin.setOnClickListener(v -> startGoogleSignIn());
        btnPhoneLogin.setOnClickListener(v -> showPhoneInputDialog());
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            isViewModelLoading = Boolean.TRUE.equals(isLoading);
            updateAuthButtonStates();
        });

        viewModel.getAuthSuccess().observe(this, user -> {
            if (user == null || isFinishing() || isDestroyed()) {
                return;
            }

            String displayName = user.getDisplayName();
            String message = (displayName == null || displayName.trim().isEmpty())
                    ? "Welcome to Gravo"
                    : "Welcome " + displayName;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            navigateToHomeActivity();
        });

        viewModel.getAuthError().observe(this, error -> {
            if (error == null || error.trim().isEmpty()) {
                return;
            }

            finishGoogleSignInAttempt();
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    private void updateAuthButtonStates() {
        boolean busy = isPhoneVerificationInProgress
                || isGoogleSignInInProgress
                || isViewModelLoading;

        if (btnPhoneLogin != null) {
            btnPhoneLogin.setEnabled(!busy);
            btnPhoneLogin.setAlpha(busy ? 0.55f : 1.0f);
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setEnabled(!busy);
            btnGoogleLogin.setAlpha(busy ? 0.55f : 1.0f);
        }
    }

    private void showAuthLoading(String message) {
        if (isFinishing() || isDestroyed() || authLoadingOverlay == null) {
            return;
        }

        tvAuthLoadingMessage.setText(message);
        authLoadingOverlay.setVisibility(View.VISIBLE);
        authLoadingOverlay.bringToFront();
        authLoadingOverlay.requestFocus();
    }

    private void hideAuthLoading() {
        if (authLoadingOverlay != null) {
            authLoadingOverlay.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Legal links
    // -------------------------------------------------------------------------

    private void setupLegalLinks() {
        View privacyLink = findViewById(R.id.tvPrivacyPolicy);
        View termsLink = findViewById(R.id.tvTermsConditions);

        if (privacyLink != null) {
            privacyLink.setOnClickListener(v -> openLegalPage(PRIVACY_POLICY_URL));
        }
        if (termsLink != null) {
            termsLink.setOnClickListener(v -> openLegalPage(TERMS_URL));
        }
    }

    private void openLegalPage(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Page link is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = Uri.parse(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                Toast.makeText(this, "Invalid page link", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open legal page", e);
            Toast.makeText(this, "Unable to open page", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------------
    // Google One Tap sign-in
    // -------------------------------------------------------------------------

    private void configureGoogleOneTap() {
        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
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
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        finishGoogleSignInAttempt();
                        return;
                    }

                    try {
                        SignInCredential credential = oneTapClient
                                .getSignInCredentialFromIntent(result.getData());
                        String idToken = credential.getGoogleIdToken();

                        if (idToken == null || idToken.trim().isEmpty()) {
                            finishGoogleSignInAttempt();
                            Toast.makeText(this, "Google did not return an ID token.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Keep the loading state active while the ViewModel authenticates with Firebase.
                        viewModel.firebaseAuthWithGoogle(idToken);
                    } catch (ApiException e) {
                        Log.e(TAG, "Google sign-in result could not be read.", e);
                        finishGoogleSignInAttempt();
                        Toast.makeText(this, "Google sign-in was cancelled or failed.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startGoogleSignIn() {
        if (isAuthBusy()) {
            return;
        }

        isGoogleSignInInProgress = true;
        updateAuthButtonStates();
        showAuthLoading("Connecting to Google...");

        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    if (isFinishing() || isDestroyed()) {
                        finishGoogleSignInAttempt();
                        return;
                    }

                    try {
                        IntentSenderRequest request = new IntentSenderRequest.Builder(
                                result.getPendingIntent().getIntentSender()).build();
                        oneTapLauncher.launch(request);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to launch Google One Tap UI.", e);
                        finishGoogleSignInAttempt();
                        Toast.makeText(this, "Unable to open Google sign-in.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Google One Tap sign-in could not start.", e);
                    finishGoogleSignInAttempt();
                    Toast.makeText(this, "Google sign-in is unavailable. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void finishGoogleSignInAttempt() {
        isGoogleSignInInProgress = false;
        updateAuthButtonStates();
        if (!isPhoneVerificationInProgress) {
            hideAuthLoading();
        }
    }

    // -------------------------------------------------------------------------
    // Phone number entry and Firebase Phone Authentication
    // -------------------------------------------------------------------------

    private void showPhoneInputDialog() {
        if (isAuthBusy()) {
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_phone_input, null, false);
        EditText etPhone = dialogView.findViewById(R.id.etPhoneInput);
        View btnVerify = dialogView.findViewById(R.id.btnVerify);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        if (etPhone == null || btnVerify == null || btnCancel == null) {
            Log.e(TAG, "dialog_phone_input.xml is missing etPhoneInput, btnVerify, or btnCancel.");
            Toast.makeText(this, "Unable to open phone sign-in. Please try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (pendingPhoneNumber != null && pendingPhoneNumber.length() == 10) {
            etPhone.setText(pendingPhoneNumber);
            etPhone.setSelection(etPhone.getText().length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnVerify.setOnClickListener(v -> {
            if (isPhoneVerificationInProgress) {
                return;
            }

            String phone = etPhone.getText() == null
                    ? ""
                    : etPhone.getText().toString().trim();

            // Accept exactly 10 ASCII digits; country code is added below.
            if (!phone.matches("[0-9]{10}")) {
                etPhone.setError("Enter a valid 10-digit mobile number");
                etPhone.requestFocus();
                return;
            }

            // Disable the dialog action immediately; the Activity-level guard is a second layer.
            btnVerify.setEnabled(false);
            startPhoneNumberVerification(phone, dialog);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private boolean isAuthBusy() {
        return isPhoneVerificationInProgress
                || isGoogleSignInInProgress
                || isViewModelLoading
                || isNavigating;
    }

    private void startPhoneNumberVerification(String phoneNumber, AlertDialog phoneDialog) {
        if (isAuthBusy()) {
            return;
        }

        // Set the guard before dismissing the dialog so rapid taps cannot start multiple SMS requests.
        isPhoneVerificationInProgress = true;
        isOtpScreenOpened = false;
        pendingPhoneNumber = phoneNumber;
        updateAuthButtonStates();
        showAuthLoading("Sending verification code...");

        if (phoneDialog != null && phoneDialog.isShowing()) {
            phoneDialog.dismiss();
        }

        try {
            String formattedPhoneNumber = "+91" + phoneNumber;

            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                    .setPhoneNumber(formattedPhoneNumber)
                    .setTimeout(PHONE_AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(mCallbacks)
                    .build();

            PhoneAuthProvider.verifyPhoneNumber(options);
        } catch (Exception e) {
            Log.e(TAG, "Could not start Firebase phone verification for the submitted number.", e);
            handlePhoneVerificationFailure(e);
        }
    }

    private void initializePhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                // Firebase can continue auto-retrieval after onCodeSent(). Once the OTP
                // screen is visible, leave that screen in control of the manual OTP flow.
                if (isOtpScreenOpened) {
                    Log.d(TAG, "Automatic verification callback arrived after the OTP screen opened.");
                    return;
                }

                showAuthLoading("Verifying your phone number...");
                FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener(MainActivity.this, result -> {
                            isPhoneVerificationInProgress = false;
                            hideAuthLoading();
                            updateAuthButtonStates();
                            navigateToHomeActivity();
                        })
                        .addOnFailureListener(MainActivity.this, e -> {
                            Log.e(TAG, "Automatic phone credential sign-in failed.", e);
                            handlePhoneVerificationFailure(e);
                        });
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Log.e(TAG, "Firebase phone verification failed.", e);
                runOnUiThread(() -> handlePhoneVerificationFailure(e));
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                runOnUiThread(() -> openOtpScreen(verificationId));
            }

            @Override
            public void onCodeAutoRetrievalTimeOut(@NonNull String verificationId) {
                // The OTP has already been sent and the OTP screen is open. Do not dismiss that screen.
                Log.d(TAG, "Phone auth auto-retrieval timed out; manual OTP entry remains available.");
            }
        };
    }

    private void openOtpScreen(String verificationId) {
        if (isFinishing() || isDestroyed() || isOtpScreenOpened) {
            return;
        }

        isOtpScreenOpened = true;
        isPhoneVerificationInProgress = false;
        hideAuthLoading();
        updateAuthButtonStates();

        try {
            Intent intent = new Intent(MainActivity.this, OtpActivity.class);
            intent.putExtra("verificationId", verificationId);
            intent.putExtra("phoneNumber", pendingPhoneNumber);
            startActivity(intent);
        } catch (Exception e) {
            isOtpScreenOpened = false;
            Log.e(TAG, "Unable to open OtpActivity. Check its manifest declaration.", e);
            Toast.makeText(this, "Code sent, but the verification screen could not be opened.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handlePhoneVerificationFailure(Exception exception) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        isPhoneVerificationInProgress = false;
        isOtpScreenOpened = false;
        hideAuthLoading();
        updateAuthButtonStates();

        String friendlyMessage = getPhoneAuthErrorMessage(exception);
        Toast.makeText(this, friendlyMessage, Toast.LENGTH_LONG).show();
    }

    private String getPhoneAuthErrorMessage(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "The mobile number is invalid. Please check and try again.";
        }

        if (exception instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait before trying again.";
        }

        if (exception instanceof FirebaseAuthMissingActivityForRecaptchaException) {
            return "Phone verification could not start. Please update the app and try again.";
        }

        if (exception instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) exception).getErrorCode();
            if (code.toUpperCase().contains("OPERATION_NOT_ALLOWED")) {
                return "Phone sign-in is not enabled for this Firebase project. Please contact support.";
            }
        }

        String message = exception.getMessage();
        if (message != null && message.toLowerCase().contains("not allowed")) {
            return "Phone sign-in is not enabled for this Firebase project. Please contact support.";
        }

        return "Couldn't send the verification code. Please check your connection and try again.";
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void navigateToHomeActivity() {
        if (isNavigating || isFinishing() || isDestroyed()) {
            return;
        }

        isNavigating = true;
        isPhoneVerificationInProgress = false;
        isGoogleSignInInProgress = false;
        hideAuthLoading();
        updateAuthButtonStates();

        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
