package com.gravo.shopping;

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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gravo.shopping.data.repository.ProductRepository;
import com.gravo.shopping.MyApplication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity implements ProductAdapter.OnProductClickListener {

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

        sharedPreferences = getSharedPreferences(SEARCH_HISTORY, MODE_PRIVATE);

        ImageView back_btn = findViewById(R.id.back_btn);
        back_btn.setOnClickListener(v -> {
            finish();
        });

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

        MyApplication.getProductRepository().searchProducts(searchKeywords, new ProductRepository.ProductsCallback() {
                    @Override public void onSuccess(List<Product> products) {
                        List<SearchResult> rankedResults = new ArrayList<>();
                        for (Product product : products) {
                            int score = 0;
                            List<String> tags = product.getTagsLowercase();
                            if (tags != null) {
                                for (String keyword : searchKeywords) {
                                    if (tags.contains(keyword)) {
                                        score++;
                                    }
                                }
                            }
                            rankedResults.add(new SearchResult(product, score));
                        }

                        Collections.sort(rankedResults, (o1, o2) -> Integer.compare(o2.score, o1.score));
                        searchResultsList.clear();
                        for (SearchResult result : rankedResults) {
                            searchResultsList.add(result.product);
                        }

                        productAdapter.notifyDataSetChanged();
                        if (searchResultsList.isEmpty()) {
                        }
                        searchProgressBar.setVisibility(View.GONE);
                    }
                    @Override public void onFailure(Exception error) {
                        searchProgressBar.setVisibility(View.GONE);
                        Log.w(TAG, "Error searching products", error);
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
