package com.wmods.wppenhacer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.utils.StickerRestore
import com.wmods.wppenhacer.utils.StickerRestore.StickerTarget
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
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
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey)
            setDisplayHomeAsUpEnabled(true)
            findPreference<Preference>("restore_stickers")?.setOnPreferenceClickListener {
                startStickerRestore()
                true
            }
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
