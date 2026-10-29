package com.gravo.shopping;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.gravo.shopping.viewmodel.AddressViewModel;

import java.util.ArrayList;
import java.util.List;

public class AddressBottomSheetFragment extends BottomSheetDialogFragment implements AddressAdapter.OnAddressActionsListener {

    private AddressViewModel addressViewModel;
    private AddressAdapter addressAdapter;
    private List<Address> fullAddressList = new ArrayList<>();
    private List<Address> filteredAddressList = new ArrayList<>();

    // Bottom Sheet ko puri unchai tak kholne aur behavior theek karne ke liye
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED); // Taki list theek se dikhe
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Aapne jo nayi XML sheet provide ki hai uska naam yahan pass karein
        return inflater.inflate(R.layout.bottom_sheet_select_delivery_address, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ViewModel Initialize kar rahe hain (RequireActivity karke activity ka shared ViewModel lein ya apna nya lein)
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);

        // UI Elements ko XML ke sath map karein
        RecyclerView rvAddresses = view.findViewById(R.id.rvAddresses);
        EditText etSearch = view.findViewById(R.id.etSearch);
        ImageButton btnClose = view.findViewById(R.id.btnClose);
        View cardAddNewAddress = view.findViewById(R.id.cardAddNewAddress);

        // RecyclerView Set Karein
        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        ViewGroup.LayoutParams params = rvAddresses.getLayoutParams();
        params.height = (int) (screenHeight * 0.25f); // 0.5f means half of the screen
        rvAddresses.setLayoutParams(params);
        addressAdapter = new AddressAdapter(filteredAddressList, this, MyAddressesActivity.AddressMode.SELECT);
        rvAddresses.setAdapter(addressAdapter);

        // ViewModel LiveData se addresses fetch karein
        if (addressViewModel.getAddressesLiveData() != null) {
            addressViewModel.getAddressesLiveData().observe(getViewLifecycleOwner(), addresses -> {
                if (addresses != null) {
                    fullAddressList.clear();
                    fullAddressList.addAll(addresses);
                    filterAddresses(etSearch.getText().toString()); // Refresh pe bhi filter apply rahe
                }
            });
        }

        // Search ka logic
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAddresses(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Click listeners setup
        btnClose.setOnClickListener(v -> dismiss());

        cardAddNewAddress.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddEditAddressActivity.class);
            startActivity(intent);
            dismiss(); // Add pe jane se pehle ye sheet band kardo
        });
    }

    // Search bar ke hisaab se addresses filter karne wala function
    private void filterAddresses(String query) {
        filteredAddressList.clear();
        if (query.isEmpty()) {
            filteredAddressList.addAll(fullAddressList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Address address : fullAddressList) {
                // Address me Name ya Street me bhi match kre toh result me dikhayega
                boolean matchName = address.getFullName() != null && address.getFullName().toLowerCase().contains(lowerCaseQuery);
                boolean matchStreet = address.getStreet() != null && address.getStreet().toLowerCase().contains(lowerCaseQuery);
                boolean matchPin = address.getPincode() != null && address.getPincode().toLowerCase().contains(lowerCaseQuery);

                if (matchName || matchStreet || matchPin) {
                    filteredAddressList.add(address);
                }
            }
        }
        addressAdapter.notifyDataSetChanged();
    }

    // ---------- Adapter Listeners ke liye Methods ----------

    @Override
    public void onAddressSelected(Address address) {
        // 1. Activity ka reference pehle hi save kar lein taaki sheet band hone ke baad NullPointerException na aaye
        androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity == null) return;

        // 2. Sheet ko turant band (close) kar dein taaki user ko wait na karna pade
        dismiss();

        // 3. Database operation ko ek naye Background Thread par execute karein
        new Thread(() -> {
            // Firebase me is address ko default banayein
            addressViewModel.makeDefaultAddress(address, () -> {

                // UI thread par wapas aakar Toast aur Signal bhejein
                activity.runOnUiThread(() -> {
                    // Yahan activity.getApplicationContext() use karna safe hai
                    Toast.makeText(activity.getApplicationContext(), "Delivery address updated", Toast.LENGTH_SHORT).show();

                    // HomeFragment ko refresh hone ka signal bhejein
                    activity.getSupportFragmentManager().setFragmentResult("ADDRESS_UPDATED_KEY", new Bundle());
                });

            }, () -> {

                // Error aane par
                activity.runOnUiThread(() ->
                        Toast.makeText(activity.getApplicationContext(), "Failed to update address", Toast.LENGTH_SHORT).show()
                );

            });
        }).start();
    }

    @Override
    public void onEditClicked(Address address) {
        // Edit button par click pe intent fire karein
        Intent intent = new Intent(requireContext(), AddEditAddressActivity.class);
        intent.putExtra("ADDRESS_ID", address.getAddressId());
        startActivity(intent);
        dismiss();
    }

    @Override
    public void onMakeDefault(Address address) {
        // Is scenario me AddressSelected wale se hi kam ho raha hai
    }

    @Override
    public void onDeleteClicked(Address address) {
        // Is sheet me delete ka option na hi dein toh behtar, fir bhi blank chor dein ya implement kar dein
    }
}