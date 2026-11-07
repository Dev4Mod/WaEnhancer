package com.wmods.wppenhacer.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.preference.PasswordEditTextPreference
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.xposed.features.others.translation.OpenAITranslationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.material.preference.MaterialSwitchPreference

class GeneralFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.frag_container, GeneralPreferenceFragment())
                .commitNow()
        }

        val activityIntent = activity?.intent
        val scrollToKey = activityIntent?.getStringExtra("scroll_to_preference")
        if (scrollToKey != null) {
            root?.postDelayed({
                (childFragmentManager.findFragmentById(R.id.frag_container) as? BasePreferenceFragment)
                    ?.scrollToPreference(scrollToKey)
            }, 300)
            activityIntent.removeExtra("scroll_to_preference")
        }
        return root
    }

    class GeneralPreferenceFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.fragment_general, rootKey)
        }

        override fun onResume() {
            super.onResume()
            setDisplayHomeAsUpEnabled(false)
        }
    }

    class HomeGeneralPreference : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_home, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class HomeScreenGeneralPreference : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    class ConversationGeneralPreference : BasePreferenceFragment() {
        
        private var providerPref: ListPreference? = null
        private var baseUrlPref: EditTextPreference? = null
        private var apiKeyPref: PasswordEditTextPreference? = null
        private var modelPref: ListPreference? = null
        private var fallbackPref: MaterialSwitchPreference? = null
        private var fetchModelsPref: Preference? = null
        
        private companion object {
            const val PREF_FETCHED_MODELS = "translation_fetched_models"
        }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey)
            setDisplayHomeAsUpEnabled(true)
            
            setupTranslationPreferences()
        }
        
        private fun setupTranslationPreferences() {
            providerPref = findPreference("translation_provider")
            baseUrlPref = findPreference("translation_ai_base_url")
            apiKeyPref = findPreference("translation_ai_api_key")
            modelPref = findPreference("translation_ai_model")
            fallbackPref = findPreference("translation_ai_fallback_to_google")
            fetchModelsPref = findPreference("translation_fetch_models")
            
            if (providerPref == null || baseUrlPref == null || apiKeyPref == null || 
                modelPref == null || fallbackPref == null || fetchModelsPref == null) {
                return
            }
            
            // Restore previously fetched models from prefs
            restoreFetchedModels()
            
            // Update visibility based on provider
            updateAiFieldsVisibility()
            
            // Listen to provider changes
            providerPref?.setOnPreferenceChangeListener { _, newValue ->
                // Update visibility after change
                providerPref?.value = newValue.toString()
                updateAiFieldsVisibility()
                true
            }
            
            // Handle "Fetch Models" button
            fetchModelsPref?.setOnPreferenceClickListener {
                fetchModelsFromApi()
                true
            }
            
            // Handle manual model entry selection
            modelPref?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == "__manual__") {
                    showManualModelDialog()
                    false // Don't save __manual__ as the value
                } else {
                    true
                }
            }
            
            // Set model summary provider
            modelPref?.summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                pref.value ?: "gpt-4o-mini"
            }
        }
        
        private fun restoreFetchedModels() {
            val prefs = preferenceManager.sharedPreferences ?: return
            val fetchedModels = prefs.getString(PREF_FETCHED_MODELS, null)
            
            if (!fetchedModels.isNullOrBlank()) {
                try {
                    val models = fetchedModels.split("|").filter { it.isNotBlank() }
                    if (models.isNotEmpty()) {
                        updateModelList(models)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        
        private fun showManualModelDialog() {
            val context = requireContext()
            val editText = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                hint = "Enter model name"
                setText(modelPref?.value ?: "")
            }
            
            AlertDialog.Builder(context)
                .setTitle("Enter Model Name")
                .setMessage("Enter the exact model name (e.g., gpt-4o-mini)")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    val modelName = editText.text.toString().trim()
                    if (modelName.isNotBlank()) {
                        // Add to list if not present and set as value
                        addCustomModelToList(modelName)
                        modelPref?.value = modelName
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun addCustomModelToList(modelName: String) {
            val currentEntries = modelPref?.entries?.toMutableList() ?: mutableListOf()
            val currentValues = modelPref?.entryValues?.toMutableList() ?: mutableListOf()
            
            // Remove "Manual entry..." if present
            val manualIndex = currentValues.indexOf("__manual__")
            if (manualIndex >= 0) {
                currentEntries.removeAt(manualIndex)
                currentValues.removeAt(manualIndex)
            }
            
            // Add custom model if not present
            if (!currentValues.contains(modelName)) {
                currentEntries.add(modelName)
                currentValues.add(modelName)
            }
            
            // Re-add "Manual entry..." at end
            currentEntries.add("Manual entry...")
            currentValues.add("__manual__")
            
            modelPref?.entries = currentEntries.toTypedArray()
            modelPref?.entryValues = currentValues.toTypedArray()
        }
        
        private fun updateAiFieldsVisibility() {
            val isAiSelected = providerPref?.value == "openai"
            
            baseUrlPref?.isVisible = isAiSelected
            apiKeyPref?.isVisible = isAiSelected
            modelPref?.isVisible = isAiSelected
            fallbackPref?.isVisible = isAiSelected
            fetchModelsPref?.isVisible = isAiSelected
        }
        
        private fun fetchModelsFromApi() {
            val context = requireContext()
            val prefs = preferenceManager.sharedPreferences ?: return
            
            // Show loading toast
            Toast.makeText(context, "Fetching models...", Toast.LENGTH_SHORT).show()
            
            // Create provider and fetch models
            val provider = OpenAITranslationProvider(prefs)
            
            provider.fetchModels { result, statusCode, errorDetails ->
                // Callback runs on OkHttp thread, post to main thread for UI updates
                activity?.runOnUiThread {
                    result.fold(
                        onSuccess = { models ->
                            if (models.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "No models found in response",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                updateModelList(models)
                                saveFetchedModels(models)
                                Toast.makeText(
                                    context,
                                    "Fetched ${models.size} models",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = { error ->
                            val message = if (statusCode != null) {
                                val snippet = errorDetails?.take(100) ?: error.message
                                "HTTP $statusCode: $snippet"
                            } else {
                                "Error: ${error.message}"
                            }
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
        
        private fun updateModelList(models: List<String>) {
            val entries = models.toMutableList()
            val values = models.toMutableList()
            
            // Add "Manual entry..." option at the end
            entries.add("Manual entry...")
            values.add("__manual__")
            
            modelPref?.entries = entries.toTypedArray()
            modelPref?.entryValues = values.toTypedArray()
        }
        
        private fun saveFetchedModels(models: List<String>) {
            val prefs = preferenceManager.sharedPreferences ?: return
            val modelsString = models.joinToString("|")
            prefs.edit().putString(PREF_FETCHED_MODELS, modelsString).apply()
        }
    }
}
