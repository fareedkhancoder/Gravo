package com.gravo.shopping;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CategoriesFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategorySideAdapter.OnCategorySideClickListener {

    // Arguments
    private static final String ARG_CATEGORY_ID = "category_id";
    private String mCategoryId; // The ID received from HomeFragment, can be null

    // Views
    private RecyclerView rvCategories;
    private RecyclerView rvProducts;

    // Adapters
    private CategorySideAdapter categorySideAdapter;
    private ProductAdapter productAdapter;

    // Data
    private List<Category> categoryList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private FirebaseFirestore db;

    // State
    private int selectedCategoryPosition = 0; // Default to the first item

    public CategoriesFragment() {
        // Required empty public constructor
    }

    public static CategoriesFragment newInstance(String categoryId) {
        CategoriesFragment fragment = new CategoriesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_ID, categoryId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        if (getArguments() != null) {
            mCategoryId = getArguments().getString(ARG_CATEGORY_ID);
            Log.d(TAG, "CategoriesFragment onCreate: received categoryId: " + mCategoryId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        rvCategories = view.findViewById(R.id.rv_categories);
        rvProducts = view.findViewById(R.id.rv_products);

        setupRecyclerViews();
        fetchCategories();
    }

    private void setupRecyclerViews() {
        // 1. Setup for the left-side category list
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext()));
        categorySideAdapter = new CategorySideAdapter(getContext(), categoryList, this);
        rvCategories.setAdapter(categorySideAdapter);

        // 2. Setup for the right-side product grid
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2)); // 2 columns in the grid
        productAdapter = new ProductAdapter(getContext(), productList, R.layout.item_product_grid, this);
        rvProducts.setAdapter(productAdapter);
    }

    /**
     * Fetches the complete list of categories from Firestore.
     * After fetching, it determines which category to select and then fetches its products.
     */
    private void fetchCategories() {
        db.collection("categories")
                .orderBy("name") // Optional: sort categories alphabetically
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    categoryList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        categoryList.add(document.toObject(Category.class));
                    }

                    if (categoryList.isEmpty()) {
                        Log.w(TAG, "No categories found in Firestore.");
                        return;
                    }

                    // First, update the adapter with the full list of categories
                    categorySideAdapter.notifyDataSetChanged();

                    // Determine which category should be selected initially
                    int initialPosition = 0; // Default to the first item
                    if (mCategoryId != null) {
                        for (int i = 0; i < categoryList.size(); i++) {
                            if (categoryList.get(i).getId().equals(mCategoryId)) {
                                initialPosition = i;
                                break;
                            }
                        }
                    }

                    // Now, set the selected position in the adapter to trigger the visual highlight
                    this.selectedCategoryPosition = initialPosition;
                    categorySideAdapter.setSelectedPosition(initialPosition);

                    // For reliability, post the scroll command to run after the layout is complete
                    int finalInitialPosition = initialPosition;
                    rvCategories.post(() -> rvCategories.scrollToPosition(finalInitialPosition));

                    // Finally, fetch products for the initially selected category
                    String initialCategoryId = categoryList.get(initialPosition).getId();
                    fetchProductsForCategory(initialCategoryId);

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching categories", e);
                    Toast.makeText(getContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fetches all products that belong to a specific category ID.
     * @param categoryId The unique ID of the category to fetch products for.
     */
    private void fetchProductsForCategory(String categoryId) {
        // TODO: Show a progress bar or shimmer effect here
        db.collection("products")
                .whereEqualTo("categoryId", categoryId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    productList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        productList.add(document.toObject(Product.class));
                    }
                    // Update the product adapter with the new list
                    productAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Found " + productList.size() + " products for category " + categoryId);
                    // TODO: Hide progress bar here
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching products for category " + categoryId, e);
                    // TODO: Hide progress bar here and show an error message
                });
    }


    /**
     * This is called when a user clicks a category in the left-side list.
     */
    @Override
    public void onCategorySideClick(int position) {
        // Update the adapter to highlight the new selection
        categorySideAdapter.setSelectedPosition(position);
        Log.d(TAG, "onCategorySideClick: position" + position);
        this.selectedCategoryPosition = position; // Keep the fragment's state in sync

        Log.d(TAG, "onCategorySideClick: " + selectedCategoryPosition);

        // Get the ID of the newly clicked category
        Category selectedCategory = categoryList.get(position);
        Log.d(TAG, "onCategorySideClick: " + selectedCategory.getId());

        // Fetch products for this new category
        fetchProductsForCategory(selectedCategory.getId());
    }


    /**
     * This is called when a user clicks a product in the right-side grid.
     */
    @Override
    public void onProductClick(Product product) {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        startActivity(intent);
    }
}