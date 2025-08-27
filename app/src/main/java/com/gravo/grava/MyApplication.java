package com.gravo.grava;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Firebase ko poore app ke liye yahan initialize karein
        FirebaseApp.initializeApp(this);
    }
}