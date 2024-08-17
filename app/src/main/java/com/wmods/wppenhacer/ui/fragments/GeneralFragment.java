package com.wmods.wppenhacer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;

public class GeneralFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        var root = super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction().add(R.id.frag_container, new GeneralPreferenceFragment()).commitNow();
        }
        return root;
    }

    public static class GeneralPreferenceFragment extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.fragment_general, rootKey);
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }

    public static class HomeGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_home, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class HomeScreenGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class ConversationGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

}