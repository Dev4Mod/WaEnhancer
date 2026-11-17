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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.preference.PasswordEditTextPreference
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.utils.StickerRestore
import com.wmods.wppenhacer.utils.StickerRestore.StickerTarget
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
import com.wmods.wppenhacer.xposed.features.others.translation.OpenAITranslationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.material.preference.MaterialSwitchPreference
import kotlin.concurrent.thread

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
            findPreference<Preference>("restore_stickers")?.setOnPreferenceClickListener {
                startStickerRestore()
                true
            }
            
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

        private fun startStickerRestore() {
            runStickerTask(R.string.restore_stickers_loading, {
                if (!StickerRestore.isRootAvailable) null else StickerRestore.findTargets()
            }) { targets ->
                when {
                    targets == null -> Toast.makeText(requireContext(), R.string.restore_stickers_no_root, Toast.LENGTH_LONG).show()
                    targets.isEmpty() -> Toast.makeText(requireContext(), R.string.restore_stickers_not_found, Toast.LENGTH_LONG).show()
                    else -> chooseStickerApp(targets)
                }
            }
        }

        private fun chooseStickerApp(targets: List<StickerTarget>) {
            val apps = targets.groupBy { it.packageName to it.userId }.values.toList()
            if (apps.size == 1) {
                chooseStickerAccount(apps.first())
                return
            }
            val labels = apps.map { accounts ->
                val target = accounts.first()
                val label = WhatsAppContactPickerLauncher.getPackageLabel(target.packageName)
                if (target.userId == 0) label
                else "$label ${getString(R.string.restore_stickers_user_suffix, target.userId)}"
            }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_whatsapp_app)
                .setItems(labels) { _, index -> chooseStickerAccount(apps[index]) }
                .show()
        }

        private fun chooseStickerAccount(accounts: List<StickerTarget>) {
            if (accounts.size == 1) {
                previewStickerRestore(accounts.first())
                return
            }
            val labels = accounts.map { target ->
                val name = target.name
                    ?: target.accountId?.let { getString(R.string.restore_stickers_account, it) }
                    ?: getString(R.string.restore_stickers_main_account)
                buildString {
                    append(name)
                    target.phone?.let { append(" · ").append(it) }
                    if (target.isActive) append(' ').append(getString(R.string.restore_stickers_active))
                }
            }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_stickers_choose_account)
                .setItems(labels) { _, index -> previewStickerRestore(accounts[index]) }
                .show()
        }

        private fun previewStickerRestore(target: StickerTarget) {
            val context = requireContext().applicationContext
            runStickerTask(R.string.restore_stickers_loading, {
                StickerRestore.preview(context, target)
            }) { preview ->
                if (preview.toRestore == 0) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.restore_stickers)
                        .setMessage(R.string.restore_stickers_nothing)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@runStickerTask
                }
                val message = getString(
                    R.string.restore_stickers_preview,
                    preview.toRestore,
                    preview.total,
                    preview.starred
                ) + "\n\n" + getString(R.string.restore_stickers_warning)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.restore_stickers)
                    .setMessage(message)
                    .setPositiveButton(R.string.restore_stickers_confirm) { _, _ -> applyStickerRestore(target) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        private fun applyStickerRestore(target: StickerTarget) {
            val context = requireContext().applicationContext
            runStickerTask(R.string.restore_stickers, {
                StickerRestore.restore(context, target)
            }) { restored ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.restore_stickers)
                    .setMessage(getString(R.string.restore_stickers_success, restored))
                    .setPositiveButton(R.string.restore_stickers_open_whatsapp) { _, _ ->
                        requireContext().packageManager.getLaunchIntentForPackage(target.packageName)
                            ?.let(::startActivity)
                    }
                    .setNegativeButton(android.R.string.ok, null)
                    .show()
            }
        }

        private fun <T> runStickerTask(messageRes: Int, task: () -> T, onResult: (T) -> Unit) {
            val activity = requireActivity()
            val progress = MaterialAlertDialogBuilder(activity)
                .setMessage(messageRes)
                .setCancelable(false)
                .show()
            thread {
                val result = runCatching(task)
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!isAdded || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    result.onSuccess(onResult).onFailure { error ->
                        Toast.makeText(
                            activity,
                            getString(R.string.restore_stickers_failed, error.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
