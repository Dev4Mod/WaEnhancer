package com.wmods.wppenhacer.ui.fragments;

import static com.wmods.wppenhacer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.preference.ContactPickerPreference;
import com.wmods.wppenhacer.preference.FileSelectPreference;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;
import com.wmods.wppenhacer.xposed.features.general.LiteMode;

public class PrivacyFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_privacy, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("onActivityResult: " + requestCode + " " + resultCode + " " + data);
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            ContactPickerPreference contactPickerPref = findPreference(data.getStringExtra("key"));
            if (contactPickerPref != null) {
                contactPickerPref.handleActivityResult(requestCode, resultCode, data);
            }
        } else if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
            FileSelectPreference fileSelectPreference = findPreference(data.getStringExtra("key"));
            if (fileSelectPreference != null) {
                fileSelectPreference.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }

}
