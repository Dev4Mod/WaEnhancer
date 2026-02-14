package com.wmods.wppenhacer.adapter;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.model.SearchableFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter for displaying search results in a RecyclerView with section headers.
 */
public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;
    
    private final List<Object> items; // Can be SearchableFeature or String (section header)
    private String searchQuery = "";
    private final OnFeatureClickListener listener;
    
    public interface OnFeatureClickListener {
        void onFeatureClick(SearchableFeature feature);
    }
    
    public SearchAdapter(OnFeatureClickListener listener) {
        this.items = new ArrayList<>();
        this.listener = listener;
    }
    
    public void setFeatures(List<SearchableFeature> newFeatures) {
        items.clear();
        
        // Group features by category
        Map<SearchableFeature.Category, List<SearchableFeature>> groupedFeatures = new LinkedHashMap<>();
        for (SearchableFeature feature : newFeatures) {
            groupedFeatures.computeIfAbsent(feature.getCategory(), k -> new ArrayList<>()).add(feature);
        }
        
        // Add items with section headers
        for (Map.Entry<SearchableFeature.Category, List<SearchableFeature>> entry : groupedFeatures.entrySet()) {
            items.add(entry.getKey().getDisplayName()); // Add section header
            items.addAll(entry.getValue()); // Add features in that section
        }
        
        notifyDataSetChanged();
    }
    
    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query : "";
    }
    
    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_section_header, parent, false);
            return new SectionHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false);
            return new SearchResultViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SectionHeaderViewHolder) {
            ((SectionHeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof SearchResultViewHolder) {
            ((SearchResultViewHolder) holder).bind((SearchableFeature) items.get(position), searchQuery, listener);
        }
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView sectionTitle;
        
        public SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            sectionTitle = itemView.findViewById(R.id.sectionTitle);
        }
        
        public void bind(String title) {
            sectionTitle.setText(title);
        }
    }
    
    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView summaryTextView;
        private final TextView categoryBadge;
        
        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.featureTitle);
            summaryTextView = itemView.findViewById(R.id.featureSummary);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
        }
        
        public void bind(SearchableFeature feature, String query, OnFeatureClickListener listener) {
            // Set title with highlighting
            titleTextView.setText(highlightText(feature.getTitle(), query));
            
            // Set summary with highlighting
            if (feature.getSummary() != null && !feature.getSummary().isEmpty()) {
                summaryTextView.setText(highlightText(feature.getSummary(), query));
                summaryTextView.setVisibility(View.VISIBLE);
            } else {
                summaryTextView.setVisibility(View.GONE);
            }
            
            // Set category badge
            categoryBadge.setText(feature.getCategory().getDisplayName().toUpperCase(Locale.ROOT));
            categoryBadge.setBackgroundColor(getCategoryColor(feature.getCategory()));
            
            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFeatureClick(feature);
                }
            });
        }
        
        private CharSequence highlightText(String text, String query) {
            if (text == null || query == null || query.isEmpty()) {
                return text;
            }
            
            SpannableString spannable = new SpannableString(text);
            String lowerText = text.toLowerCase();
            String lowerQuery = query.toLowerCase();
            
            int start = lowerText.indexOf(lowerQuery);
            if (start >= 0) {
                int end = start + query.length();
                spannable.setSpan(
                        new BackgroundColorSpan(Color.parseColor("#4DFFD700")),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            
            return spannable;
        }
        
        private int getCategoryColor(SearchableFeature.Category category) {
            switch (category) {
                case GENERAL:
                case GENERAL_HOME:
                case GENERAL_HOMESCREEN:
                case GENERAL_CONVERSATION:
                    return Color.parseColor("#4CAF50"); // Green
                case PRIVACY:
                    return Color.parseColor("#2196F3"); // Blue
                case MEDIA:
                    return Color.parseColor("#FF9800"); // Orange
                case CUSTOMIZATION:
                    return Color.parseColor("#9C27B0"); // Purple
                case RECORDINGS:
                    return Color.parseColor("#F44336"); // Red
                case HOME_ACTIONS:
                    return Color.parseColor("#607D8B"); // Blue Grey
                default:
                    return Color.parseColor("#757575"); // Grey
            }
        }
    }
}
