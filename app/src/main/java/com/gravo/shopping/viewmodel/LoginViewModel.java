package com.gravo.shopping.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseUser;
import com.gravo.shopping.data.repository.AuthRepository;

public class LoginViewModel extends AndroidViewModel {

    private final AuthRepository repository;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> authError = new MutableLiveData<>();
    private final MutableLiveData<FirebaseUser> authSuccess = new MutableLiveData<>();

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository();
    }

    // Getters for LiveData
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getAuthError() { return authError; }
    public LiveData<FirebaseUser> getAuthSuccess() { return authSuccess; }

    public void checkUserSession() {
        if (repository.getCurrentUser() != null) {
            authSuccess.setValue(repository.getCurrentUser());
        }
    }

    public void firebaseAuthWithGoogle(String idToken) {
        isLoading.setValue(true);
        repository.signInWithGoogle(idToken).addOnCompleteListener(task -> {
            isLoading.setValue(false);
            if (task.isSuccessful()) {
                FirebaseUser user = task.getResult().getUser();
                boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();

                if (isNewUser) {
                    repository.createUserDocument(user);
                }
                authSuccess.setValue(user);
            } else {
                authError.setValue(task.getException() != null ? task.getException().getMessage() : "Authentication Failed");
            }
        });
    }
}