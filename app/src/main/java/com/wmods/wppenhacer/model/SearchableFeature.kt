package com.wmods.wppenhacer.model

import java.util.Locale

/** Data model representing a searchable feature in the WaEnhancer app. */
class SearchableFeature @JvmOverloads constructor(
    val key: String,
    val title: String,
    val summary: String?,
    val category: Category,
    val fragmentType: FragmentType,
    val parentKey: String? = null,
    searchTags: List<String>? = null
) {
    val searchTags: List<String> = searchTags ?: mutableListOf()

    enum class Category(val displayName: String) {
        GENERAL("General"),
        GENERAL_HOME("General"),
        GENERAL_HOMESCREEN("General"),
        GENERAL_CONVERSATION("General"),
        PRIVACY("Privacy"),
        MEDIA("Media"),
        CUSTOMIZATION("Customization"),
        RECORDINGS("Recordings"),
        HOME_ACTIONS("Home")
    }

    enum class FragmentType(val position: Int) {
        HOME(0),
        GENERAL(1),
        PRIVACY(2),
        MEDIA(3),
        CUSTOMIZATION(4),
        RECORDINGS(5),
        ACTIVITY(99)
    }

    fun matches(query: String?): Boolean {
        if (query.isNullOrBlank()) return false

        val locale = Locale.getDefault()
        val lowerQuery = query.lowercase(locale).trim()
        if (title.lowercase(locale).contains(lowerQuery)) return true
        if (summary?.lowercase(locale)?.contains(lowerQuery) == true) return true
        if (searchTags.any { it.lowercase(locale).contains(lowerQuery) }) return true
        return category.displayName.lowercase(locale).contains(lowerQuery)
    }

    override fun toString(): String =
        "SearchableFeature{key='$key', title='$title', category=$category}"
}
