package com.gravo.shopping;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.gravo.shopping.data.repository.ProductRepository;
import com.gravo.shopping.local.AppDatabase;

public class MyApplication extends Application {
    private static AppDatabase database;
    private static ProductRepository productRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
        database = AppDatabase.getInstance(this);
        productRepository = new ProductRepository(this);
        productRepository.startSync();
    }

    public static AppDatabase getDatabase() { return database; }
    public static ProductRepository getProductRepository() { return productRepository; }
}