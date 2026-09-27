package com.wmods.wppenhacer.ui.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.preference.ContactPickerPreference
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

class PrivacyFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.fragment_privacy, rootKey)
    }

    override fun onResume() {
        super.onResume()
        setDisplayHomeAsUpEnabled(false)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        println("onActivityResult: $requestCode $resultCode $data")
        if (requestCode == ContactPickerPreference.REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            val key = data!!.getStringExtra("key") ?: return
            val preference = findPreference<Preference>(key) as? ContactPickerPreference
            preference?.handleActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activityIntent = activity?.intent
        val scrollToKey = activityIntent?.getStringExtra("scroll_to_preference")
        if (scrollToKey != null) {
            scrollToPreference(scrollToKey)
            activityIntent.removeExtra("scroll_to_preference")
        }
    }
}
