package com.gravo.grava;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity implements ProductAdapter.OnProductClickListener {

    private FirebaseFirestore db;
    private EditText searchEditText;
    private RecyclerView searchResultsRecyclerView;
    private ProductAdapter productAdapter;
    private List<Product> searchResultsList;
    private ProgressBar searchProgressBar;

    // For Search Suggestions
    private ListView suggestionsListView;
    private ArrayAdapter<String> suggestionsAdapter;
    private List<String> suggestionsList;
    private SharedPreferences sharedPreferences;
    private static final String SEARCH_HISTORY = "SearchHistory";
    private static final String TAG = "SearchActivity";

    // NAYI CLASS: Search result ko score ke saath store karne ke liye
    private static class SearchResult {
        Product product;
        int score;

        SearchResult(Product product, int score) {
            this.product = product;
            this.score = score;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(SEARCH_HISTORY, MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbarSearch);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        searchEditText = findViewById(R.id.searchEditText);
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView);
        suggestionsListView = findViewById(R.id.suggestionsListView);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        searchResultsList = new ArrayList<>();
        suggestionsList = new ArrayList<>();

        // Setup RecyclerView
        searchResultsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        productAdapter = new ProductAdapter(this, searchResultsList, R.layout.item_product_grid, this);
        searchResultsRecyclerView.setAdapter(productAdapter);

        // Setup Suggestions ListView
        suggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestionsList);
        suggestionsListView.setAdapter(suggestionsAdapter);

        // Auto-focus and open keyboard
        searchEditText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);

        setupSearchListeners();
    }

    private void setupSearchListeners() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = v.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                    saveSearchQuery(query);
                }
                return true;
            }
            return false;
        });

        // Typing ke waqt suggestions dikhayein
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().isEmpty()) {
                    suggestionsListView.setVisibility(View.GONE);
                    searchResultsRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    showSuggestionsFromHistory(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        suggestionsListView.setOnItemClickListener((parent, view, position, id) -> {
            String query = suggestionsAdapter.getItem(position);
            searchEditText.setText(query);
            searchEditText.setSelection(query.length());
            performSearch(query);
        });
    }

    // NAYA: Yeh function ab user ki purani search history se suggestions dikhayega
    private void showSuggestionsFromHistory(String text) {
        Set<String> history = sharedPreferences.getStringSet(SEARCH_HISTORY, new HashSet<>());
        suggestionsList.clear();
        for (String query : history) {
            if (query.toLowerCase().contains(text.toLowerCase())) {
                suggestionsList.add(query);
            }
        }

        if (suggestionsList.isEmpty()) {
            suggestionsListView.setVisibility(View.GONE);
        } else {
            suggestionsListView.setVisibility(View.VISIBLE);
            searchResultsRecyclerView.setVisibility(View.GONE);
            suggestionsAdapter.notifyDataSetChanged();
        }
    }

    private void saveSearchQuery(String query) {
        Set<String> history = new HashSet<>(sharedPreferences.getStringSet(SEARCH_HISTORY, new HashSet<>()));
        history.add(query);
        sharedPreferences.edit().putStringSet(SEARCH_HISTORY, history).apply();
    }

    private void performSearch(String query) {
        suggestionsListView.setVisibility(View.GONE);
        searchResultsRecyclerView.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);

        searchProgressBar.setVisibility(View.VISIBLE);
        searchResultsList.clear();
        productAdapter.notifyDataSetChanged();

        List<String> searchKeywords = Arrays.asList(query.toLowerCase().split("\\s+"));

        db.collection("products")
                .whereArrayContainsAny("tags_lowercase", searchKeywords)
                .get()
                .addOnCompleteListener(task -> {
                    searchProgressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        List<SearchResult> rankedResults = new ArrayList<>();

                        // Step 1: Har product ko score dein
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Product product = document.toObject(Product.class);
                            product.setProductId(document.getId());

                            int score = 0;
                            List<String> tags = (List<String>) document.get("tags_lowercase");
                            if (tags != null) {
                                for (String keyword : searchKeywords) {
                                    if (tags.contains(keyword)) {
                                        score++;
                                    }
                                }
                            }
                            rankedResults.add(new SearchResult(product, score));
                        }

                        // Step 2: Results ko score ke hisab se sort karein
                        Collections.sort(rankedResults, (o1, o2) -> Integer.compare(o2.score, o1.score));

                        // Step 3: Sorted list ko display ke liye taiyar karein
                        searchResultsList.clear();
                        for (SearchResult result : rankedResults) {
                            searchResultsList.add(result.product);
                        }

                        productAdapter.notifyDataSetChanged();
                        if (searchResultsList.isEmpty()) {
                            Toast.makeText(this, "No products found for '" + query + "'", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "Error getting documents: ", task.getException());
                    }
                });
    }

    @Override
    public void onProductClick(Product product) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        startActivity(intent);
    }
}
