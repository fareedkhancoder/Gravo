package com.gravo.grava;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class AddEditAddressActivity extends AppCompatActivity {

    private static final String TAG = "AddEditAddressActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // UI Elements
    private TextInputEditText fullNameEditText, phoneEditText, streetEditText, cityEditText, stateEditText, pincodeEditText;
    private MaterialButtonToggleGroup addressTypeToggleGroup;
    private MaterialCheckBox defaultAddressCheckBox;
    private Button saveAddressButton;
    private Toolbar toolbar;

    // Mode variables
    private boolean isEditMode = false;
    private String addressIdToEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_address);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        toolbar = findViewById(R.id.toolbarAddEditAddress);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        fullNameEditText = findViewById(R.id.fullNameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        streetEditText = findViewById(R.id.streetEditText);
        cityEditText = findViewById(R.id.cityEditText);
        stateEditText = findViewById(R.id.stateEditText);
        pincodeEditText = findViewById(R.id.pincodeEditText);
        addressTypeToggleGroup = findViewById(R.id.addressTypeToggleGroup);
        defaultAddressCheckBox = findViewById(R.id.defaultAddressCheckBox);
        saveAddressButton = findViewById(R.id.saveAddressButton);

        if (getIntent().hasExtra("ADDRESS_ID")) {
            isEditMode = true;
            addressIdToEdit = getIntent().getStringExtra("ADDRESS_ID");
            toolbar.setTitle("Edit Address");
            loadAddressDetails();
        } else {
            isEditMode = false;
            toolbar.setTitle("Add a New Address");
        }

        saveAddressButton.setOnClickListener(v -> {
            saveAddress();
            startActivity(new Intent(this, MyAddressesActivity.class));
        });
    }

    private void loadAddressDetails() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid()).collection("addresses").document(addressIdToEdit)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Address address = documentSnapshot.toObject(Address.class);
                        if (address != null) {
                            fullNameEditText.setText(address.getFullName());
                            phoneEditText.setText(address.getPhone());
                            streetEditText.setText(address.getStreet());
                            cityEditText.setText(address.getCity());
                            stateEditText.setText(address.getState());
                            pincodeEditText.setText(address.getPincode());
                            if ("Home".equalsIgnoreCase(address.getType())) {
                                addressTypeToggleGroup.check(R.id.homeButton);
                            } else {
                                addressTypeToggleGroup.check(R.id.workButton);
                            }
                            defaultAddressCheckBox.setChecked(address.isDefault());
                        }
                    }
                });
    }

    private void saveAddress() {
        String fullName = fullNameEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String street = streetEditText.getText().toString().trim();
        String city = cityEditText.getText().toString().trim();
        String state = stateEditText.getText().toString().trim();
        String pincode = pincodeEditText.getText().toString().trim();
        boolean isDefault = defaultAddressCheckBox.isChecked();
        String type = addressTypeToggleGroup.getCheckedButtonId() == R.id.homeButton ? "Home" : "Work";

        if (fullName.isEmpty() || phone.isEmpty() || street.isEmpty() || city.isEmpty() || state.isEmpty() || pincode.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        CollectionReference addressesRef = db.collection("users").document(currentUser.getUid()).collection("addresses");

        WriteBatch batch = db.batch();

        if (isDefault) {
            addressesRef.whereEqualTo("default", true).get().addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    batch.update(doc.getReference(), "default", false);
                }
                createNewAddress(batch, addressesRef, fullName, phone, street, city, state, pincode, type, isDefault);
            });
        } else {
            createNewAddress(batch, addressesRef, fullName, phone, street, city, state, pincode, type, isDefault);
        }
    }

    private void createNewAddress(WriteBatch batch, CollectionReference addressesRef, String fullName, String phone, String street, String city, String state, String pincode, String type, boolean isDefault) {
        Map<String, Object> addressData = new HashMap<>();
        addressData.put("fullName", fullName);
        addressData.put("phone", phone);
        addressData.put("street", street);
        addressData.put("city", city);
        addressData.put("state", state);
        addressData.put("pincode", pincode);
        addressData.put("type", type);
        addressData.put("default", isDefault); // YEH LINE ZAROORI HAI

        DocumentReference addressRef;
        if (isEditMode) {
            addressRef = addressesRef.document(addressIdToEdit);
        } else {
            addressRef = addressesRef.document();
        }
        batch.set(addressRef, addressData);

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Address saved successfully!", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to save address.", Toast.LENGTH_SHORT).show();
        });
    }
}
