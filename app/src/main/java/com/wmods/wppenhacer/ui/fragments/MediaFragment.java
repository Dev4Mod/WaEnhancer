package com.wmods.wppenhacer.ui.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.CallRecordingSettingsActivity;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;

public class MediaFragment extends BasePreferenceFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_media, rootKey);

        // Call Recording Settings preference
        var callRecordingSettings = findPreference("call_recording_settings");
        if (callRecordingSettings != null) {
            callRecordingSettings.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), CallRecordingSettingsActivity.class);
                startActivity(intent);
                return true;
            });
        }

        var videoCallScreenRec = findPreference("video_call_screen_rec");
        if (videoCallScreenRec != null) {
            videoCallScreenRec.setEnabled(true);
            videoCallScreenRec.setOnPreferenceClickListener(preference -> {
                try {
                     var intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mubashardev"));
                     startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            });
            videoCallScreenRec.setOnPreferenceChangeListener((preference, newValue) -> false); // Prevent toggling
        }
    }
}
