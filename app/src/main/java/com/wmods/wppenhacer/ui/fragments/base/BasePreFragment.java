package com.wmods.wppenhacer.ui.fragments.base;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.BuildConfig;

import rikka.material.preference.MaterialSwitchPreference;

public abstract class BasePreFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected SharedPreferences mPrefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        chanceStates();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void chanceStates() {

        var channels = (MaterialSwitchPreference) findPreference("channels");
        if (channels != null) {
            Log.i("channels", String.valueOf(mPrefs.getBoolean("channels", false)));
            var bool = mPrefs.getBoolean("igstatus", false);
            if (bool) channels.setChecked(false);
            channels.setEnabled(!bool);
        }

        var fbstyle = (MaterialSwitchPreference) findPreference("fbstyle");
        if (fbstyle != null) {
            var bool = mPrefs.getBoolean("channels", false);
            if (bool) fbstyle.setChecked(false);
            fbstyle.setEnabled(!bool);
        }
        var showFreeze = (MaterialSwitchPreference) findPreference("show_freezeLastSeen");
        if (showFreeze != null) {
            var bool = mPrefs.getBoolean("freezelastseen", false);
            if (bool) showFreeze.setChecked(false);
            showFreeze.setEnabled(!bool);
        }
        var thememode = (ListPreference) findPreference("thememode");
        if (thememode != null) {
            var mode = Integer.parseInt(mPrefs.getString("thememode", "0"));
            App.setThemeMode(mode);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
        App.getInstance().sendBroadcast(intent);
        chanceStates();
    }
}
