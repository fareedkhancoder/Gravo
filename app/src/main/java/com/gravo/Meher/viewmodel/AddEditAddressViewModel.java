package com.gravo.Meher.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.gravo.Meher.Address;
import com.gravo.Meher.data.repository.ProductRepository;
import com.gravo.Meher.local.AddressEntity;
import com.gravo.Meher.local.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEditAddressViewModel extends AndroidViewModel {

    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;
    private final AppDatabase localDb;
    private final ProductRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public AddEditAddressViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        localDb = AppDatabase.getInstance(application);
        repository = new ProductRepository(application);
    }

    public LiveData<Boolean> getSaveSuccess() { return saveSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void checkServiceabilityAndSave(Address address, boolean isEditMode) {
        repository.checkPincodeServiceability(address.getPincode(), new ProductRepository.ServiceabilityCallback() {
            @Override
            public void onServiceable() {
                saveAddressToFirebaseAndRoom(address, isEditMode);
            }
            @Override
            public void onNotServiceable(String message) {
                errorMessage.postValue(message);
            }
            @Override
            public void onError(Exception e) {
                errorMessage.postValue("Failed to verify pincode: " + e.getMessage());
            }
        });
    }

    private void saveAddressToFirebaseAndRoom(Address address, boolean isEditMode) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.postValue("User not logged in");
            return;
        }

        String userId = currentUser.getUid();
        CollectionReference addressesRef = db.collection("users").document(userId).collection("addresses");
        WriteBatch batch = db.batch();

        DocumentReference addressRef;
        if (isEditMode && address.getAddressId() != null) {
            addressRef = addressesRef.document(address.getAddressId());
        } else {
            addressRef = addressesRef.document();
            address.setAddressId(addressRef.getId());
        }

        batch.set(addressRef, address);

        batch.commit().addOnSuccessListener(aVoid -> {
            executor.execute(() -> {
                AddressEntity entity = new AddressEntity();
                entity.addressId = address.getAddressId();
                entity.userId = userId;
                entity.fullName = address.getFullName();
                entity.phoneNumber = address.getPhone();
                entity.alternatePhone = address.getAlternatePhone();
                entity.houseNo = address.getHouseNo();
                entity.areaStreet = address.getStreet();
                entity.landmark = address.getLandmark();
                entity.city = address.getCity();
                entity.state = address.getState();
                entity.pincode = address.getPincode();
                entity.type = address.getType();
                entity.isDefault = address.isDefault();
                entity.latitude = address.getLatitude();
                entity.longitude = address.getLongitude();

                localDb.addressDao().insertAddress(entity);
                saveSuccess.postValue(true);
            });
        }).addOnFailureListener(e -> errorMessage.postValue(e.getMessage()));
    }
}