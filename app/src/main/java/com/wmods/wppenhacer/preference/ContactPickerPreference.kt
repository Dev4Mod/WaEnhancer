package com.wmods.wppenhacer.preference

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
import com.wmods.wppenhacer.xposed.utils.Utils

class ContactPickerPreference : Preference, Preference.OnPreferenceClickListener {
    private var summaryOff: CharSequence? = null
    private var summaryOn: CharSequence? = null
    private var contacts: ArrayList<String>? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val preferenceKey = key ?: return true
        val selectedContacts = contacts?.let(::ArrayList) ?: arrayListOf()
        val installedPackages = WhatsAppContactPickerLauncher.getInstalledWhatsAppPackages(context)
        when (installedPackages.size) {
            1 -> startSelectContacts(installedPackages[0], preferenceKey, selectedContacts)
            in 2..Int.MAX_VALUE -> showPackageSelectionDialog(
                installedPackages,
                preferenceKey,
                selectedContacts
            )
        }
        return true
    }

    private fun showPackageSelectionDialog(
        installedPackages: ArrayList<String>,
        preferenceKey: String,
        selectedContacts: ArrayList<String>
    ) {
        val items = Array<CharSequence?>(installedPackages.size) { index ->
            WhatsAppContactPickerLauncher.getPackageLabel(installedPackages[index])
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Select WhatsApp app")
            .setItems(items) { _, which ->
                startSelectContacts(
                    installedPackages[which],
                    preferenceKey,
                    ArrayList(selectedContacts)
                )
            }
            .show()
    }

    private fun startSelectContacts(
        packageName: String,
        preferenceKey: String,
        selectedContacts: ArrayList<String>
    ) {
        try {
            val intent = WhatsAppContactPickerLauncher.createPickerIntent(
                context,
                packageName,
                preferenceKey,
                selectedContacts
            )
            (context as Activity).startActivityForResult(intent, REQUEST_CONTACT_PICKER)
        } catch (exception: Exception) {
            Utils.showToast(exception.message, 1)
        }
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        onPreferenceClickListener = this
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ContactPickerPreference,
            0,
            0
        )
        summaryOff = typedArray.getText(R.styleable.ContactPickerPreference_summaryOff)
        summaryOn = typedArray.getText(R.styleable.ContactPickerPreference_summaryOn)
        typedArray.recycle()

        val preferenceKey = key
        val namesString = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(preferenceKey, "").orEmpty()
        if (namesString.length > 2) {
            contacts = ArrayList(
                namesString.substring(1, namesString.length - 1).split(", ").map(String::trim)
            )
        }
        updateSummary()
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            contacts = data.getStringArrayListExtra("contacts")
            getSharedPreferences()!!.edit { putString(key, contacts.toString()) }
            updateSummary()
        }
    }

    private fun updateSummary() {
        val selected = contacts
        summary = if (!selected.isNullOrEmpty()) {
            String.format(summaryOn.toString(), selected.size)
        } else {
            summaryOff.toString()
        }
    }

    companion object {
        const val REQUEST_CONTACT_PICKER = 0xff2515
    }
}
