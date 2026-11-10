package com.gravo.Meher;

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

import com.google.firebase.firestore.DocumentSnapshot;
import com.gravo.Meher.data.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

public class CategoriesFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategorySideAdapter.OnCategorySideClickListener {

    private static final String ARG_CATEGORY_ID = "category_id";
    private String mCategoryId;

    private RecyclerView rvCategories;
    private RecyclerView rvProducts;

    private CategorySideAdapter categorySideAdapter;
    private ProductAdapter productAdapter;

    private List<Category> categoryList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private ProductRepository repository;

    private int selectedCategoryPosition = 0;

    // 🌟 PAGINATION STATE VARIABLES
    private static final int PAGE_SIZE = 10;
    private DocumentSnapshot lastVisibleDoc = null;
    private boolean isLoading = false;
    private boolean isLastPage = false;

    public CategoriesFragment() {}

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
        if (getArguments() != null) {
            mCategoryId = getArguments().getString(ARG_CATEGORY_ID);
        }
        repository = new ProductRepository(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvCategories = view.findViewById(R.id.rv_categories);
        rvProducts = view.findViewById(R.id.rv_products);

        setupRecyclerViews();
        fetchCategories();
    }

    private void setupRecyclerViews() {
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext()));
        categorySideAdapter = new CategorySideAdapter(getContext(), categoryList, this);
        rvCategories.setAdapter(categorySideAdapter);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        rvProducts.setLayoutManager(gridLayoutManager);
        productAdapter = new ProductAdapter(getContext(), productList, R.layout.item_product_grid, this);
        rvProducts.setAdapter(productAdapter);

        // 🌟 INFINITE SCROLL LISTENER FOR PRODUCTS
        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0) { // Sirf down scroll par check karein
                    int visibleItemCount = gridLayoutManager.getChildCount();
                    int totalItemCount = gridLayoutManager.getItemCount();
                    int firstVisibleItemPosition = gridLayoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0
                                && totalItemCount >= PAGE_SIZE) {
                            loadMoreProducts();
                        }
                    }
                }
            }
        });
    }

    private void fetchCategories() {
        repository.getCategories(new ProductRepository.CategoriesCallback() {
            @Override
            public void onSuccess(List<Category> categories) {
                categoryList.clear();
                categoryList.addAll(categories);

                if (categoryList.isEmpty()) {
                    Log.w(TAG, "No categories found.");
                    return;
                }

                categorySideAdapter.notifyDataSetChanged();

                int initialPosition = 0;
                if (mCategoryId != null) {
                    for (int i = 0; i < categoryList.size(); i++) {
                        if (categoryList.get(i).getId().equals(mCategoryId)) {
                            initialPosition = i;
                            break;
                        }
                    }
                }

                selectedCategoryPosition = initialPosition;
                categorySideAdapter.setSelectedPosition(initialPosition);

                int finalInitialPosition = initialPosition;
                rvCategories.post(() -> rvCategories.scrollToPosition(finalInitialPosition));

                String initialCategoryId = categoryList.get(initialPosition).getId();
                fetchProductsForCategory(initialCategoryId);
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "Error fetching categories", error);
                Toast.makeText(getContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 🌟 INITIAL PAGE LOAD (RESET STATE)
    private void fetchProductsForCategory(String categoryId) {
        lastVisibleDoc = null;
        isLastPage = false;
        isLoading = true;
        productList.clear();
        productAdapter.notifyDataSetChanged();

        repository.getProductsByCategoryPaginated(categoryId, null, PAGE_SIZE, new ProductRepository.PaginatedProductsCallback() {
            @Override
            public void onSuccess(List<Product> products, DocumentSnapshot lastDoc, boolean isEnd) {
                isLoading = false;
                productList.addAll(products);
                lastVisibleDoc = lastDoc;
                isLastPage = isEnd;
                productAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Exception e) {
                isLoading = false;
                Log.e(TAG, "Error fetching products for category " + categoryId, e);
            }
        });
    }

    // 🌟 LOAD NEXT 10 PRODUCTS ON SCROLL
    private void loadMoreProducts() {
        if (categoryList.isEmpty() || selectedCategoryPosition >= categoryList.size()) return;

        isLoading = true;
        String currentCategoryId = categoryList.get(selectedCategoryPosition).getId();

        repository.getProductsByCategoryPaginated(currentCategoryId, lastVisibleDoc, PAGE_SIZE, new ProductRepository.PaginatedProductsCallback() {
            @Override
            public void onSuccess(List<Product> products, DocumentSnapshot lastDoc, boolean isEnd) {
                isLoading = false;
                if (!products.isEmpty()) {
                    int startPos = productList.size();
                    productList.addAll(products);
                    lastVisibleDoc = lastDoc;
                    productAdapter.notifyItemRangeInserted(startPos, products.size());
                }
                isLastPage = isEnd;
            }

            @Override
            public void onFailure(Exception e) {
                isLoading = false;
                Log.e(TAG, "Error loading more products", e);
            }
        });
    }

    @Override
    public void onCategorySideClick(int position) {
        categorySideAdapter.setSelectedPosition(position);
        this.selectedCategoryPosition = position;

        Category selectedCategory = categoryList.get(position);
        fetchProductsForCategory(selectedCategory.getId());
    }

    @Override
    public void onProductClick(Product product) {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", product.getProductId());
        repository.recordProductClick(product.getProductId());
        startActivity(intent);
    }
}