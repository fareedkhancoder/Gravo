package com.gravo.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class AccountFragment extends Fragment {

    private static final String TAG = "AccountFragment";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // UI Elements
    private TextView profileInitialsTextView;
    private TextView userNameTextView;
    private TextView userEmailTextView;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Find views by their ID
        profileInitialsTextView = view.findViewById(R.id.profileInitialsTextView);
        userNameTextView = view.findViewById(R.id.userNameTextView);
        userEmailTextView = view.findViewById(R.id.userEmailTextView);
        TextView logoutButton = view.findViewById(R.id.logoutButton);
        LinearLayout myOrdersOption = view.findViewById(R.id.myOrdersOption);
        LinearLayout myAddressesOption = view.findViewById(R.id.myAddressesOption);


        // Load user data
        loadUserProfile();

        // Set click listeners for menu options
        myOrdersOption.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new Intent(getActivity(), MyOrdersActivity.class));
            }
        });

        // Set listener for the logout button
        logoutButton.setOnClickListener(v -> {
            logoutUser();
        });

        myAddressesOption.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new Intent(getActivity(), MyAddressesActivity.class));
            }
        });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // Agar user logged in nahin hai, to use login screen par bhejein
            goToLoginActivity();
            return;
        }

        // Firestore se user ka naam aur email prapt karein
        DocumentReference userRef = db.collection("users").document(currentUser.getUid());
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String name = document.getString("name");
                    String email = document.getString("email");

                    userNameTextView.setText(name);
                    userEmailTextView.setText(email);

                    // Naam ka pehla अक्षर (initial) set karein
                    if (name != null && !name.isEmpty()) {
                        profileInitialsTextView.setText(String.valueOf(name.charAt(0)));
                    }

                } else {
                    Log.d(TAG, "No such document in Firestore, using Auth data");
                    // Agar Firestore mein data nahin hai, to Auth se default data lein
                    userNameTextView.setText(currentUser.getDisplayName());
                    userEmailTextView.setText(currentUser.getEmail());
                    if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                        profileInitialsTextView.setText(String.valueOf(currentUser.getDisplayName().charAt(0)));
                    }
                }
            } else {
                Log.d(TAG, "get failed with ", task.getException());
            }
        });
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(getContext(), "Logged Out", Toast.LENGTH_SHORT).show();
        goToLoginActivity();
    }

    private void goToLoginActivity() {
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            // Saari purani activities ko clear kar dein
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }
}
