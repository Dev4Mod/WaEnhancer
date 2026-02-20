package com.wmods.wppenhacer.ui.fragments.base;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    requireActivity().finish();
                }
            }
        });
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        chanceStates(null);
        monitorPreference();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(true);
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

    private void monitorPreference() {
        var downloadstatus = (MaterialSwitchPreference) findPreference("downloadstatus");

        if (downloadstatus != null) {
            downloadstatus.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
        }

        var downloadviewonce = (MaterialSwitchPreference) findPreference("downloadviewonce");
        if (downloadviewonce != null) {
            downloadviewonce.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
        }
    }

    private boolean checkStoragePermission(Object newValue) {
        if (newValue instanceof Boolean && (Boolean) newValue) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                App.showRequestStoragePermission(requireActivity());
                return false;
            }
        }
        return true;
    }

    @SuppressLint("ApplySharedPref")
    private void chanceStates(String key) {

        var lite_mode = mPrefs.getBoolean("lite_mode", false);

        if (lite_mode) {
            setPreferenceState("wallpaper", false);
            setPreferenceState("custom_filters", false);
        }

        var changeColorEnabled = mPrefs.getBoolean("changecolor", false);
        var changeColorMode = mPrefs.getString("changecolor_mode", "manual");
        var monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        var useMonetColors = changeColorEnabled && monetAvailable && Objects.equals(changeColorMode, "monet");

        setPreferenceState("changecolor_mode", changeColorEnabled && monetAvailable);
        setPreferenceState("primary_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("background_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("text_color", changeColorEnabled && !useMonetColors);

        if (Objects.equals(key, "thememode")) {
            var mode = Integer.parseInt(mPrefs.getString("thememode", "0"));
            App.setThemeMode(mode);
        }

        var colorMode = mPrefs.getString("wae_color_mode", "preset");
        var useMonet = Objects.equals(colorMode, "monet") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        setPreferenceState("wae_color_preset", !useMonet);

        if (Objects.equals(key, "wae_color_mode") || Objects.equals(key, "wae_color_preset")) {
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }

        if (Objects.equals(key, "force_english")) {
            mPrefs.edit().commit();
            Utils.doRestart(requireContext());
        }

        var igstatus = mPrefs.getBoolean("igstatus", false);
        setPreferenceState("oldstatus", !igstatus);

        var oldstatus = mPrefs.getBoolean("oldstatus", false);
        setPreferenceState("verticalstatus", !oldstatus);
        setPreferenceState("channels", !oldstatus);
        setPreferenceState("removechannel_rec", !oldstatus);
        setPreferenceState("status_style", !oldstatus);
        setPreferenceState("igstatus", !oldstatus);

        var channels = mPrefs.getBoolean("channels", false);
        setPreferenceState("removechannel_rec", !channels && !oldstatus);

        var freezelastseen = mPrefs.getBoolean("freezelastseen", false);
        setPreferenceState("show_freezeLastSeen", !freezelastseen);
        setPreferenceState("showonlinetext", !freezelastseen);
        setPreferenceState("dotonline", !freezelastseen);


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

    public void setDisplayHomeAsUpEnabled(boolean enabled) {
        if (getActivity() == null) return;
        var actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }
    
    /**
     * Scroll to a specific preference by key.
     * This is called when navigating from search results.
     */
    public void scrollToPreference(String preferenceKey) {
        if (preferenceKey == null) return;
        
        // Small delay to ensure preference screen is fully loaded
        getView().postDelayed(() -> {
            var preference = findPreference(preferenceKey);
            if (preference != null) {
                scrollToPreference(preference);
                
                // Highlight the preference for visibility
                highlightPreference(preference);
            }
        }, 100);
    }
    
    /**
     * Highlight a preference with a temporary background color.
     */
    private void highlightPreference(androidx.preference.Preference preference) {
        // Wait longer to ensure RecyclerView has laid out the views after scrolling
        getView().postDelayed(() -> {
            androidx.recyclerview.widget.RecyclerView recyclerView = getListView();
            if (recyclerView == null || preference == null || preference.getKey() == null) return;
            
            // Find the preference view by iterating through visible items
            String targetKey = preference.getKey();
            boolean found = false;
            
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                android.view.View child = recyclerView.getChildAt(i);
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                
                if (holder instanceof androidx.preference.PreferenceViewHolder) {
                    androidx.preference.PreferenceViewHolder prefHolder = (androidx.preference.PreferenceViewHolder) holder;
                    
                    // Try to match by adapter position
                    int position = prefHolder.getBindingAdapterPosition();
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        try {
                            // Get all preferences recursively
                            androidx.preference.Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
                            if (pref != null && pref.getKey() != null && pref.getKey().equals(targetKey)) {
                                animateHighlight(prefHolder.itemView);
                                found = true;
                                break;
                            }
                        } catch (Exception e) {
                            // Continue searching
                        }
                    }
                }
            }
            
            // If not found, try a second time after a longer delay
            if (!found) {
                getView().postDelayed(() -> tryHighlightAgain(targetKey), 500);
            }
        }, 500);
    }
    
    private void tryHighlightAgain(String targetKey) {
        androidx.recyclerview.widget.RecyclerView recyclerView = getListView();
        if (recyclerView == null) return;
        
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            android.view.View child = recyclerView.getChildAt(i);
            
            // Simple approach: check all text views in the item for matching preference
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) child;
                // Get the preference at this position and check key
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof androidx.preference.PreferenceViewHolder) {
                    int position = holder.getBindingAdapterPosition();
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        androidx.preference.Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
                        if (pref != null && pref.getKey() != null && pref.getKey().equals(targetKey)) {
                            animateHighlight(child);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private androidx.preference.Preference findPreferenceAtPosition(androidx.preference.PreferenceGroup group, int targetPosition) {
        if (group == null) return null;
        
        int currentPosition = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference pref = group.getPreference(i);
            if (pref == null) continue;
            
            if (currentPosition == targetPosition) {
                return pref;
            }
            currentPosition++;
            
            // Recursively check groups
            if (pref instanceof androidx.preference.PreferenceGroup) {
                androidx.preference.PreferenceGroup subGroup = (androidx.preference.PreferenceGroup) pref;
                int subCount = countPreferences(subGroup);
                if (targetPosition < currentPosition + subCount) {
                    return findPreferenceAtPosition(subGroup, targetPosition - currentPosition);
                }
                currentPosition += subCount;
            }
        }
        return null;
    }
    
    private int countPreferences(androidx.preference.PreferenceGroup group) {
        int count = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference pref = group.getPreference(i);
            if (pref instanceof androidx.preference.PreferenceGroup) {
                count += countPreferences((androidx.preference.PreferenceGroup) pref);
            } else {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Animate a highlight effect on the view.
     */
    private void animateHighlight(android.view.View view) {
        if (view == null || getContext() == null) return;
        
        // Get primary color using android attribute
        android.util.TypedValue typedValue = new android.util.TypedValue();
        view.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;
        
        // Make it 20% opacity (dim)
        int highlightColor = android.graphics.Color.argb(
            51, // ~20% of 255
            android.graphics.Color.red(primaryColor),
            android.graphics.Color.green(primaryColor),
            android.graphics.Color.blue(primaryColor)
        );
        
        // Save original background
        android.graphics.drawable.Drawable originalBackground = view.getBackground();
        
        // Set highlight background
        view.setBackgroundColor(highlightColor);
        
        // Fade out after 1.5 seconds
        view.postDelayed(() -> {
            if (originalBackground != null) {
                view.setBackground(originalBackground);
            } else {
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }, 1500);
    }
}
