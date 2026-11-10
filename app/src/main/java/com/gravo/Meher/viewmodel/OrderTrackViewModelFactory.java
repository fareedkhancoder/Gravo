package com.gravo.Meher.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class OrderTrackViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;

    public OrderTrackViewModelFactory(@NonNull Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(OrderTrackViewModel.class)) {
            return (T) new OrderTrackViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
