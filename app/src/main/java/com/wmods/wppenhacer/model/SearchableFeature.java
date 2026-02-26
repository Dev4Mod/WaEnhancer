package com.wmods.wppenhacer.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a searchable feature in the WaEnhancer app.
 * Each feature corresponds to a preference item that can be found via search.
 */
public class SearchableFeature {

    public enum Category {
        GENERAL("General"),
        GENERAL_HOME("General"),
        GENERAL_HOMESCREEN("General"),
        GENERAL_CONVERSATION("General"),
        PRIVACY("Privacy"),
        MEDIA("Media"),
        CUSTOMIZATION("Customization"),
        RECORDINGS("Recordings"),
        HOME_ACTIONS("Home");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum FragmentType {
        GENERAL(0),
        PRIVACY(1),
        HOME(2),
        MEDIA(3),
        CUSTOMIZATION(4),
        RECORDINGS(5),
        ACTIVITY(99);

        private final int position;

        FragmentType(int position) {
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }

    private final String key;
    private final String title;
    private final String summary;
    private final Category category;
    private final FragmentType fragmentType;
    private final String parentKey; // For nested preferences
    private final List<String> searchTags;

    public SearchableFeature(String key, String title, String summary,
            Category category, FragmentType fragmentType) {
        this(key, title, summary, category, fragmentType, null, new ArrayList<>());
    }

    public SearchableFeature(String key, String title, String summary,
            Category category, FragmentType fragmentType,
            String parentKey, List<String> searchTags) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.fragmentType = fragmentType;
        this.parentKey = parentKey;
        this.searchTags = searchTags != null ? searchTags : new ArrayList<>();
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Category getCategory() {
        return category;
    }

    public FragmentType getFragmentType() {
        return fragmentType;
    }

    public String getParentKey() {
        return parentKey;
    }

    public List<String> getSearchTags() {
        return searchTags;
    }

    /**
     * Check if this feature matches the search query.
     * Performs case-insensitive matching against title, summary, and tags.
     */
    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lowerQuery = query.toLowerCase().trim();

        // Check title
        if (title != null && title.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check summary
        if (summary != null && summary.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check tags
        for (String tag : searchTags) {
            if (tag.toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }

        // Check category
        if (category.getDisplayName().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchableFeature{" +
                "key='" + key + '\'' +
                ", title='" + title + '\'' +
                ", category=" + category +
                '}';
    }
}
