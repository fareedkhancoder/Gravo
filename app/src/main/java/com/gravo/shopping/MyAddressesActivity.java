package com.gravo.shopping;

import static android.view.View.GONE;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gravo.shopping.viewmodel.AddressViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MyAddressesActivity extends AppCompatActivity implements AddressAdapter.OnAddressActionsListener {

    public enum AddressMode {
        NORMAL,
        DEFAULT_SETTER,
        SELECT,
    }

    private RecyclerView addressesRecyclerView;
    private AddressAdapter addressAdapter;
    private ImageView back_btn;
    private List<Address> addressList;
    private TextView bottomButton, title;
    private Address currentlySelectedAddress = null;
    private AddressMode mode = AddressMode.NORMAL;
    private AddressViewModel addressViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_addresses);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        getWindow().setStatusBarColor(Color.parseColor("#E543B9F0"));
        back_btn = findViewById(R.id.back_btn);

        back_btn.setOnClickListener(view -> {
            finish();
        });
        title = findViewById(R.id.title);



        if (getIntent().hasExtra("SELECT_ADDRESS_MODE")) {
            mode = AddressMode.SELECT;
        } else if (getIntent().hasExtra("DEFAULT_SETTER_MODE")) {
            mode = AddressMode.DEFAULT_SETTER;
        } else {
            mode = AddressMode.NORMAL;
        }

        addressList = new ArrayList<>();
        addressesRecyclerView = findViewById(R.id.addressesRecyclerView);
        addressesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        addressAdapter = new AddressAdapter(addressList, this, mode);
        addressesRecyclerView.setAdapter(addressAdapter);

        bottomButton = findViewById(R.id.bottomButton);

        switch (mode) {
            case SELECT:
                title.setText("Select Address");
                bottomButton.setText("Deliver Here");
                break;

            case DEFAULT_SETTER:
                title.setText("Set Default Address");
                bottomButton.setVisibility(GONE);
                break;

            case NORMAL:
            default:
                title.setText("My Addresses");
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
                finish();
            } else {
                Intent intent = new Intent(this, AddEditAddressActivity.class);
                startActivity(intent);
            }
        });

        // ViewModel initialization and LiveData observer
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);
        setupAddressObserver();
    }

    private void setupAddressObserver() {
        if (addressViewModel.getAddressesLiveData() != null) {
            addressViewModel.getAddressesLiveData().observe(this, addresses -> {
                addressList.clear();
                if (addresses != null) {
                    addressList.addAll(addresses);
                }
                addressAdapter.notifyDataSetChanged();

                // Check: List khali hai aur activity SELECT mode me hai toh NORMAL mode me switch karein
                if (addressList.isEmpty() && mode == AddressMode.SELECT) {
                    switchToNormalMode();
                }
            });
        }
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
            addressViewModel.makeDefaultAddress(address, () -> runOnUiThread(() -> {
                Toast.makeText(this, "Default address updated", Toast.LENGTH_SHORT).show();
                Intent resultIntent = new Intent();
                resultIntent.putExtra("SELECTED_ADDRESS_ID", address.getAddressId());
                setResult(RESULT_OK, resultIntent);
                finish();
            }), () -> runOnUiThread(() -> {
                Toast.makeText(this, "Failed to update default address", Toast.LENGTH_SHORT).show();
            }));
        }
    }

    @Override
    public void onDeleteClicked(Address address) {
        if (mode == AddressMode.NORMAL) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Address")
                    .setMessage("Are you sure you want to delete this address?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        addressViewModel.deleteAddress(address, () -> runOnUiThread(() -> {
                            Toast.makeText(this, "Address deleted successfully", Toast.LENGTH_SHORT).show();
                        }), () -> runOnUiThread(() -> {
                            Toast.makeText(this, "Failed to delete address", Toast.LENGTH_SHORT).show();
                        }));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void switchToNormalMode() {
        this.mode = AddressMode.NORMAL;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Addresses");
        }

        if (bottomButton != null) {
            bottomButton.setVisibility(View.VISIBLE);
            bottomButton.setText("Add a New Address");
        }

        addressAdapter = new AddressAdapter(addressList, this, mode);
        addressesRecyclerView.setAdapter(addressAdapter);
    }
}