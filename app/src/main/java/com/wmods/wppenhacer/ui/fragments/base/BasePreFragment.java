package com.wmods.wppenhacer.ui.fragments.base;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
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

        var igstatus = mPrefs.getBoolean("igstatus", false);
        setPreferenceState("oldstatus", !igstatus);

        var oldstatus = mPrefs.getBoolean("oldstatus", false);
        setPreferenceState("channels", !oldstatus);
        setPreferenceState("removechannel_rec", !oldstatus);
        setPreferenceState("fbstyle", !oldstatus);
        setPreferenceState("igstatus", !oldstatus);

        var channels = mPrefs.getBoolean("channels", false);
        setPreferenceState("removechannel_rec", !channels && !oldstatus);

        var freezelastseen = mPrefs.getBoolean("freezelastseen", false);
        setPreferenceState("show_freezeLastSeen", !freezelastseen);

        var thememode = (ListPreference) findPreference("thememode");
        if (thememode != null) {
            var mode = Integer.parseInt(mPrefs.getString("thememode", "0"));
            App.setThemeMode(mode);
        }

        var separategroups = mPrefs.getBoolean("separategroups", false);
        setPreferenceState("filtergroups", !separategroups);

        var filtergroups = mPrefs.getBoolean("filtergroups", false);
        setPreferenceState("separategroups", !filtergroups);


        var callBlockContacts = findPreference("call_block_contacts");
        var callWhiteContacts = findPreference("call_white_contacts");
        if (callBlockContacts != null && callWhiteContacts != null) {
            var callType = Integer.parseInt(mPrefs.getString("call_privacy", "0"));
            switch (callType) {
                case 3:
                    callBlockContacts.setEnabled(true);
                    callWhiteContacts.setEnabled(false);
                    break;
                case 4:
                    callWhiteContacts.setEnabled(true);
                    callBlockContacts.setEnabled(false);
                    break;
                default:
                    callWhiteContacts.setEnabled(false);
                    callBlockContacts.setEnabled(false);
                    break;
            }

        }
    }

    private void setPreferenceState(String key, boolean enabled) {
        var pref = findPreference(key);
        if (pref != null) {
            pref.setEnabled(enabled);
            if (pref instanceof MaterialSwitchPreference && !enabled) {
                ((MaterialSwitchPreference) pref).setChecked(false);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
        App.getInstance().sendBroadcast(intent);
        chanceStates();
    }
}
