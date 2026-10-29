package com.gravo.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gravo.shopping.adapters.GridSpacingItemDecoration;
import com.gravo.shopping.adapters.TimeSpentProductAdapter;
import com.gravo.shopping.data.repository.ProductRepository;

import java.util.List;

public class AccountFragment extends Fragment {

    private static final String TAG = "AccountFragment";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView userNameTextView;
    private TextView userEmailTextView;

    private TimeSpentProductAdapter adapter;
    private ProductRepository productRepository;

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 10;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        productRepository = new ProductRepository(requireContext());

        userNameTextView = view.findViewById(R.id.tvName);
        userEmailTextView = view.findViewById(R.id.GmailText);
        ImageView logoutButton = view.findViewById(R.id.btnMenu);
        TextView myOrdersOption = view.findViewById(R.id.btnEdit);
        LinearLayout myAddressesOption = view.findViewById(R.id.address);
        RecyclerView recyclerView = view.findViewById(R.id.gridRecycler);

        setupRecyclerView(recyclerView);
        loadUserProfile();
        loadFirstPage();   // ← yahan se data load hoga

        myOrdersOption.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new Intent(getActivity(), MyOrdersActivity.class));
            }
        });

        logoutButton.setOnClickListener(v -> logoutUser());

        myAddressesOption.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new Intent(getActivity(), MyAddressesActivity.class));
            }
        });
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerView.setLayoutManager(layoutManager);

        // Spacing (aapke package ke hisaab se)
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(2, 16, true));

        adapter = new TimeSpentProductAdapter(requireContext(), product -> {
            // Product click – yahan ProductDetail open karo
            // Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            // intent.putExtra("productId", product.getProductId());
            // startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;

                int visibleItemCount = lm.getChildCount();
                int totalItemCount = lm.getItemCount();
                int firstVisibleItemPosition = lm.findFirstVisibleItemPosition();

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0) {
                        loadNextPage();
                    }
                }
            }
        });
    }

    private void loadFirstPage() {
        currentPage = 0;
        isLastPage = false;
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoading || isLastPage) return;
        isLoading = true;

        productRepository.getTopTimeSpentProducts(currentPage, PAGE_SIZE,
                new ProductRepository.TimeSpendProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products, boolean isEnd) {
                        isLoading = false;
                        isLastPage = isEnd;

                        if (currentPage == 0) {
                            adapter.setProducts(products);          // first page
                        } else {
                            adapter.addProducts(products);          // next pages
                        }
                        currentPage++;
                    }

                    @Override
                    public void onFailure(Exception e) {
                        isLoading = false;
                        Log.e(TAG, "Failed to load time spent products", e);
                    }
                });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            goToLoginActivity();
            return;
        }

        DocumentReference userRef = db.collection("users").document(currentUser.getUid());
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && getContext() != null) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    userNameTextView.setText(document.getString("name"));
                    userEmailTextView.setText("Email: " + document.getString("email"));
                } else {
                    userNameTextView.setText(currentUser.getDisplayName());
                    userEmailTextView.setText("Email: " + currentUser.getEmail());
                }
            }
        });
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(getContext(), "Logged Out", Toast.LENGTH_SHORT).show();
        goToLoginActivity();
    }

    private void goToLoginActivity() {
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }
}