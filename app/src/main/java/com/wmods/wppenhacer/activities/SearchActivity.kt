package com.wmods.wppenhacer.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.adapter.SearchAdapter
import com.wmods.wppenhacer.databinding.ActivitySearchBinding
import com.wmods.wppenhacer.model.SearchableFeature
import com.wmods.wppenhacer.utils.FeatureCatalog

/** Activity for searching and navigating to app features. */
class SearchActivity : BaseActivity(), SearchAdapter.OnFeatureClickListener {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.search_features_title)
        }

        adapter = SearchAdapter(this)
        binding.searchResults.layoutManager = LinearLayoutManager(this)
        binding.searchResults.adapter = adapter
        setupSearchInput()
        loadAllFeatures()
        binding.searchInput.requestFocus()
    }

    private fun setupSearchInput() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadAllFeatures() {
        adapter.setFeatures(FeatureCatalog.getAllFeatures(this))
        adapter.setSearchQuery("")
        updateEmptyState(false, "")
    }

    private fun performSearch(query: String) {
        if (query.trim().isEmpty()) {
            loadAllFeatures()
            return
        }

        val results = FeatureCatalog.search(this, query)
        adapter.setFeatures(results)
        adapter.setSearchQuery(query)
        if (results.isEmpty()) {
            updateEmptyState(true, getString(R.string.search_no_results))
        } else {
            updateEmptyState(false, "")
        }
    }

    private fun updateEmptyState(show: Boolean, message: String) {
        if (show) {
            binding.emptyState.visibility = View.VISIBLE
            binding.searchResults.visibility = View.GONE
            binding.emptyStateText.text = message
        } else {
            binding.emptyState.visibility = View.GONE
            binding.searchResults.visibility = View.VISIBLE
        }
    }

    override fun onFeatureClick(feature: SearchableFeature) {
        if (feature.fragmentType == SearchableFeature.FragmentType.ACTIVITY) return

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to_fragment", feature.fragmentType.position)
            putExtra("scroll_to_preference", feature.key)
            putExtra("parent_preference", feature.parentKey)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
