package com.wmods.wppenhacer.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.wmods.wppenhacer.ui.fragments.CustomizationFragment;
import com.wmods.wppenhacer.ui.fragments.GeneralFragment;
import com.wmods.wppenhacer.ui.fragments.HomeFragment;
import com.wmods.wppenhacer.ui.fragments.MediaFragment;
import com.wmods.wppenhacer.ui.fragments.PrivacyFragment;
import com.wmods.wppenhacer.ui.fragments.RecordingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    private final boolean isRecordingEnabled;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        var prefs = PreferenceManager.getDefaultSharedPreferences(fragmentActivity);
        isRecordingEnabled = prefs.getBoolean("call_recording_enable", false);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 0 -> new GeneralFragment();
            case 1 -> new PrivacyFragment();
            case 3 -> new MediaFragment();
            case 4 -> new CustomizationFragment();
            case 5 -> new RecordingsFragment();
            default -> new HomeFragment();
        };
    }

    @Override
    public int getItemCount() {
        return isRecordingEnabled ? 6 : 5;
    }
}