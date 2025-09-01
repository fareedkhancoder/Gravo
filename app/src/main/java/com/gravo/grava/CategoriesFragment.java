package com.gravo.grava;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CategoriesFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CategoriesFragment extends Fragment {

    private static final String ARG_CATEGORY_ID = "category_id";
    private String mCategoryId;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

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
        // 3. Retrieve the ID from the arguments Bundle
        if (getArguments() != null) {
            mCategoryId = getArguments().getString(ARG_CATEGORY_ID);
            Log.d(TAG, "onCreate: received categoryId" + mCategoryId);
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_categories, container, false);

        // Now you can use mCategoryId to fetch products for this specific category
        // For example:
        // TextView title = view.findViewById(R.id.category_title);
        // title.setText("Showing items for category: " + mCategoryId);

        return view;
    }
}