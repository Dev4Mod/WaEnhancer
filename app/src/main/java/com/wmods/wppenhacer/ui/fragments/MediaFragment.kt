package com.wmods.wppenhacer.ui.fragments

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.CallRecordingSettingsActivity
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

class MediaFragment : BasePreferenceFragment() {
    override fun onResume() {
        super.onResume()
        setDisplayHomeAsUpEnabled(false)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.fragment_media, rootKey)

        findPreference<Preference>("call_recording_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), CallRecordingSettingsActivity::class.java))
            true
        }
        findPreference<Preference>("video_call_screen_rec")?.isEnabled = false
    }
}
