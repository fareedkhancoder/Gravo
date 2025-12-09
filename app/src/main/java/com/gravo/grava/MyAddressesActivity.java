package com.gravo.grava;

import static android.view.View.GONE;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MyAddressesActivity extends AppCompatActivity implements AddressAdapter.OnAddressActionsListener {

    private FirebaseFirestore db;

    public enum AddressMode {
        NORMAL,
        DEFAULT_SETTER,
        SELECT
    }
    private RecyclerView addressesRecyclerView;
    private AddressAdapter addressAdapter;
    private List<Address> addressList;
    private Button bottomButton;
    private Address currentlySelectedAddress = null;
    private AddressMode mode = AddressMode.NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_addresses);
        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbarMyAddresses);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (getIntent().hasExtra("SELECT_ADDRESS_MODE")) {
            mode = AddressMode.SELECT;
        } else if (getIntent().hasExtra("DEFAULT_SETTER_MODE")) {
            mode = AddressMode.DEFAULT_SETTER;
        } else {
            mode = AddressMode.NORMAL;
        }

        db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        addressList = new ArrayList<>();
        addressesRecyclerView = findViewById(R.id.addressesRecyclerView);
        addressesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        addressAdapter = new AddressAdapter(addressList, this, mode);
        addressesRecyclerView.setAdapter(addressAdapter);


        bottomButton = findViewById(R.id.bottomButton); // Make sure this ID is in activity_my_addresses.xml

        // --- Setup toolbar title + bottom button based on mode ---
        switch (mode) {
            case SELECT:
                getSupportActionBar().setTitle("Select a Delivery Address");
                bottomButton.setText("Deliver Here");
                break;

            case DEFAULT_SETTER:
                getSupportActionBar().setTitle("Set Default Address");
                bottomButton.setVisibility(GONE);
                break;

            case NORMAL:
            default:
                getSupportActionBar().setTitle("My Addresses");
                bottomButton.setText("Add a New Address");
                break;
        }

        bottomButton.setOnClickListener(v -> {
            if (mode == AddressMode.SELECT) {
                if (currentlySelectedAddress != null) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("SELECTED_ADDRESS_ID", currentlySelectedAddress.getAddressId());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    Toast.makeText(this, "Please select an address", Toast.LENGTH_SHORT).show();
                }
            } else if (mode == AddressMode.DEFAULT_SETTER) {
                // Just close after user tapped an address
                finish();
            } else { // NORMAL
                Intent intent = new Intent(this, AddEditAddressActivity.class);
                startActivity(intent);
                finish();
            }
        });


        fetchUserAddresses(mAuth.getCurrentUser());
    }

    private void fetchUserAddresses(FirebaseUser currentUser) {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid()).collection("addresses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    addressList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Address address = document.toObject(Address.class);
                        address.setAddressId(document.getId());
                        addressList.add(address);
                    }
                    addressAdapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onAddressSelected(Address address) {
        currentlySelectedAddress = address;
    }
    @Override
    public void onEditClicked(Address address) {
        if (mode == AddressMode.NORMAL) {
            Intent intent = new Intent(this, AddEditAddressActivity.class);
            intent.putExtra("ADDRESS_ID", address.getAddressId());
            startActivity(intent);
        }
    }

    @Override
    public void onMakeDefault(Address address) {
        if (mode == AddressMode.DEFAULT_SETTER) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) return;

            db.collection("users").document(currentUser.getUid())
                    .collection("addresses")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            boolean isSelected = doc.getId().equals(address.getAddressId());
                            doc.getReference().update("default", isSelected);
                        }

                        Toast.makeText(this, "Default address updated", Toast.LENGTH_SHORT).show();

                        // ✅ Return result back to HomeFragment instead of starting a new activity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("SELECTED_ADDRESS_ID", address.getAddressId());
                        setResult(RESULT_OK, resultIntent);
                        finish(); // close MyAddressesActivity
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update default address", Toast.LENGTH_SHORT).show();
                        addressAdapter.notifyDataSetChanged();
                    });
        }
    }


    @Override
    public void onDeleteClicked(Address address) {
        if (mode == AddressMode.NORMAL) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Address")
                    .setMessage("Are you sure you want to delete this address?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null) return;

                        // Show a loading indicator here if you have one (optional)

                        db.collection("users").document(currentUser.getUid())
                                .collection("addresses")
                                .document(address.getAddressId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // 1. Success Feedback
                                    Toast.makeText(this, "Address deleted successfully", Toast.LENGTH_SHORT).show();

                                    // 2. Update the UI (Remove from RecyclerView)
                                    // If you are NOT using FirestoreRecyclerAdapter, you must remove the item manually:
                                    if (addressList != null && addressAdapter != null) {
                                        int position = addressList.indexOf(address);
                                        if (position != -1) {
                                            addressList.remove(position);
                                            addressAdapter.notifyItemRemoved(position);
                                            // Optional: update range if needed
                                            // addressAdapter.notifyItemRangeChanged(position, addressList.size());
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // 3. Failure Feedback
                                    Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
}
