package com.wmods.wppenhacer.ui.fragments.base;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Objects;

import rikka.material.preference.MaterialSwitchPreference;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected SharedPreferences mPrefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        chanceStates(null);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
        App.getInstance().sendBroadcast(intent);
        chanceStates(s);
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

    @SuppressLint("ApplySharedPref")
    private void chanceStates(String key) {

        if (Objects.equals(key, "thememode")) {
            var mode = Integer.parseInt(mPrefs.getString("thememode", "0"));
            App.setThemeMode(mode);
        }

        if (Objects.equals(key, "force_english")) {
            mPrefs.edit().commit();
            Utils.doRestart(requireContext());
        }

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
}
