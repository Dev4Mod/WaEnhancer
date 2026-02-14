package com.wmods.wppenhacer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.SearchAdapter;
import com.wmods.wppenhacer.databinding.ActivitySearchBinding;
import com.wmods.wppenhacer.model.SearchableFeature;
import com.wmods.wppenhacer.utils.FeatureCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for searching and navigating to app features.
 */
public class SearchActivity extends BaseActivity implements SearchAdapter.OnFeatureClickListener {
    
    private ActivitySearchBinding binding;
    private SearchAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.search_features_title);
        }
        
        // Setup RecyclerView
        adapter = new SearchAdapter(this);
        binding.searchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.searchResults.setAdapter(adapter);
        
        // Setup search input
        setupSearchInput();
        
        // Show all features by default (grouped by category)
        loadAllFeatures();
        
        // Focus on search input
        binding.searchInput.requestFocus();
    }
    
    private void setupSearchInput() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
    
    private void loadAllFeatures() {
        List<SearchableFeature> allFeatures = FeatureCatalog.getAllFeatures(this);
        adapter.setFeatures(allFeatures);
        adapter.setSearchQuery("");
        updateEmptyState(false, "");
    }
    
    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            // Show all features when search is empty
            loadAllFeatures();
            return;
        }
        
        // Search features
        List<SearchableFeature> results = FeatureCatalog.search(this, query);
        
        // Update adapter
        adapter.setFeatures(results);
        adapter.setSearchQuery(query);
        
        // Update empty state
        if (results.isEmpty()) {
            updateEmptyState(true, getString(R.string.search_no_results));
        } else {
            updateEmptyState(false, "");
        }
    }
    
    private void updateEmptyState(boolean show, String message) {
        if (show) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.searchResults.setVisibility(View.GONE);
            binding.emptyStateText.setText(message);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.searchResults.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    public void onFeatureClick(SearchableFeature feature) {
        // Navigate back to MainActivity with feature information
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to_fragment", feature.getFragmentType().getPosition());
        intent.putExtra("scroll_to_preference", feature.getKey());
        intent.putExtra("parent_preference", feature.getParentKey());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
