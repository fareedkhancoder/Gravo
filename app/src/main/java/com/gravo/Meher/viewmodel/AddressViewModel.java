package com.gravo.Meher.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.gravo.Meher.Address;
import com.gravo.Meher.local.AddressEntity;
import com.gravo.Meher.local.AppDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddressViewModel extends AndroidViewModel {

    private final AppDatabase localDb;
    private final FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LiveData<List<Address>> addressesLiveData;
    private final String currentUserId;

    public AddressViewModel(@NonNull Application application) {
        super(application);
        localDb = AppDatabase.getInstance(application);
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        if (currentUserId != null) {
            // Room DB LiveData observation (Room DB update hone par ye auto-trigger hoga)
            LiveData<List<AddressEntity>> roomLiveData = localDb.addressDao().observeAddressesForUser(currentUserId);

            addressesLiveData = Transformations.map(roomLiveData, entities -> {
                List<Address> list = new ArrayList<>();
                if (entities != null) {
                    for (AddressEntity entity : entities) {
                        list.add(toAddressModel(entity));
                    }
                }
                return list;
            });

            // Room DB khali hone par initial sync trigger karein
            checkAndSyncFromFirestore();
        }
    }

    public LiveData<List<Address>> getAddressesLiveData() {
        return addressesLiveData;
    }

    private void checkAndSyncFromFirestore() {
        if (currentUserId == null) return;

        executor.execute(() -> {
            List<AddressEntity> localEntities = localDb.addressDao().getAddressesForUser(currentUserId);
            if (localEntities == null || localEntities.isEmpty()) {
                fetchFromFirestoreAndSaveLocal();
            }
        });
    }

    public void fetchFromFirestoreAndSaveLocal() {
        if (currentUserId == null) return;

        db.collection("users").document(currentUserId).collection("addresses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<AddressEntity> entitiesToSave = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Address address = document.toObject(Address.class);
                        address.setAddressId(document.getId());
                        entitiesToSave.add(toAddressEntity(address, currentUserId));
                    }

                    executor.execute(() -> {
                        localDb.addressDao().clearUserAddresses(currentUserId);
                        localDb.addressDao().insertAddresses(entitiesToSave);
                    });
                });
    }

    public void makeDefaultAddress(Address address, Runnable onSuccess, Runnable onFailure) {
        if (currentUserId == null) return;
        String targetAddressId = address.getAddressId();

        db.collection("users").document(currentUserId).collection("addresses")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WriteBatch batch = db.batch();
                    List<AddressEntity> updatedEntities = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        boolean isDefault = doc.getId().equals(targetAddressId);
                        batch.update(doc.getReference(), "default", isDefault);

                        Address item = doc.toObject(Address.class);
                        item.setAddressId(doc.getId());
                        item.setDefault(isDefault);
                        updatedEntities.add(toAddressEntity(item, currentUserId));
                    }

                    batch.commit().addOnSuccessListener(aVoid -> {
                        executor.execute(() -> {
                            localDb.addressDao().insertAddresses(updatedEntities);
                            if (onSuccess != null) onSuccess.run();
                        });
                    }).addOnFailureListener(e -> {
                        if (onFailure != null) onFailure.run();
                    });
                });
    }

    public void deleteAddress(Address address, Runnable onSuccess, Runnable onFailure) {
        if (currentUserId == null) return;
        String addressId = address.getAddressId();

        db.collection("users").document(currentUserId)
                .collection("addresses")
                .document(addressId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    executor.execute(() -> {
                        localDb.addressDao().deleteAddressById(addressId);
                        if (onSuccess != null) onSuccess.run();
                    });
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) onFailure.run();
                });
    }

    private AddressEntity toAddressEntity(Address address, String userId) {
        AddressEntity entity = new AddressEntity();
        entity.addressId = address.getAddressId();
        entity.userId = userId;
        entity.fullName = address.getFullName();
        entity.phoneNumber = address.getPhone();
        entity.areaStreet = address.getStreet();
        entity.city = address.getCity();
        entity.state = address.getState();
        entity.pincode = address.getPincode();
        entity.type = address.getType() != null ? address.getType() : "HOME";
        entity.isDefault = address.isDefault();
        return entity;
    }

    private Address toAddressModel(AddressEntity entity) {
        Address address = new Address();
        address.setAddressId(entity.addressId);
        address.setFullName(entity.fullName);
        address.setPhone(entity.phoneNumber);
        address.setStreet(entity.areaStreet);
        address.setCity(entity.city);
        address.setState(entity.state);
        address.setPincode(entity.pincode);
        address.setType(entity.type != null ? entity.type : "HOME");
        address.setDefault(entity.isDefault);
        return address;
    }
}