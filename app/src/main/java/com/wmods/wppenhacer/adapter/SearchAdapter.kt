package com.wmods.wppenhacer.adapter

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.model.SearchableFeature
import java.util.LinkedHashMap
import java.util.Locale
import androidx.core.graphics.toColorInt

/** Adapter for search results grouped under category headers. */
class SearchAdapter(private val listener: OnFeatureClickListener?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private var searchQuery = ""

    fun setFeatures(newFeatures: List<SearchableFeature>) {
        items.clear()
        val groupedFeatures = LinkedHashMap<SearchableFeature.Category, MutableList<SearchableFeature>>()
        newFeatures.forEach { feature ->
            groupedFeatures.getOrPut(feature.category) { mutableListOf() }.add(feature)
        }
        groupedFeatures.forEach { (category, features) ->
            items.add(category.displayName)
            items.addAll(features)
        }
        notifyDataSetChanged()
    }

    fun setSearchQuery(query: String?) {
        searchQuery = query ?: ""
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == VIEW_TYPE_HEADER) {
            R.layout.item_search_section_header
        } else {
            R.layout.item_search_result
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == VIEW_TYPE_HEADER) SectionHeaderViewHolder(view) else SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionHeaderViewHolder -> holder.bind(items[position] as String)
            is SearchResultViewHolder -> holder.bind(items[position] as SearchableFeature, searchQuery, listener)
        }
    }

    override fun getItemCount(): Int = items.size

    fun interface OnFeatureClickListener {
        fun onFeatureClick(feature: SearchableFeature)
    }

    private class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sectionTitle: TextView = itemView.findViewById(R.id.sectionTitle)

        fun bind(title: String) {
            sectionTitle.text = title
        }
    }

    private class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.featureTitle)
        private val summaryTextView: TextView = itemView.findViewById(R.id.featureSummary)
        private val categoryBadge: TextView = itemView.findViewById(R.id.categoryBadge)

        fun bind(feature: SearchableFeature, query: String, listener: OnFeatureClickListener?) {
            titleTextView.text = highlightText(feature.title, query)
            val summary = feature.summary
            if (!summary.isNullOrEmpty()) {
                summaryTextView.text = highlightText(summary, query)
                summaryTextView.visibility = View.VISIBLE
            } else {
                summaryTextView.visibility = View.GONE
            }

            categoryBadge.text = feature.category.displayName.uppercase(Locale.ROOT)
            categoryBadge.setBackgroundColor(getCategoryColor(feature.category))
            itemView.setOnClickListener { listener?.onFeatureClick(feature) }
        }

        private fun highlightText(text: String?, query: String?): CharSequence? {
            if (text == null || query.isNullOrEmpty()) return text

            val spannable = SpannableString(text)
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            val start = lowerText.indexOf(lowerQuery)
            if (start >= 0) {
                spannable.setSpan(
                    BackgroundColorSpan("#4DFFD700".toColorInt()),
                    start,
                    start + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannable
        }

        private fun getCategoryColor(category: SearchableFeature.Category): Int = when (category) {
            SearchableFeature.Category.GENERAL,
            SearchableFeature.Category.GENERAL_HOME,
            SearchableFeature.Category.GENERAL_HOMESCREEN,
            SearchableFeature.Category.GENERAL_CONVERSATION -> "#4CAF50".toColorInt()
            SearchableFeature.Category.PRIVACY -> "#2196F3".toColorInt()
            SearchableFeature.Category.MEDIA -> "#FF9800".toColorInt()
            SearchableFeature.Category.CUSTOMIZATION -> "#9C27B0".toColorInt()
            SearchableFeature.Category.RECORDINGS -> "#F44336".toColorInt()
            SearchableFeature.Category.HOME_ACTIONS -> "#607D8B".toColorInt()
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
    }
}
