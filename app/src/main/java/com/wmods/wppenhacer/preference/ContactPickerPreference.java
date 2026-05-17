package com.wmods.wppenhacer.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ContactPickerPreference extends Preference implements Preference.OnPreferenceClickListener {

    public static final int REQUEST_CONTACT_PICKER = 0xff2515;
    private CharSequence summaryOff;
    private CharSequence summaryOn;
    private ArrayList<String> mContacts;

    public ContactPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }


    public ContactPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public ContactPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String preferenceKey = getKey();
        ArrayList<String> selectedContacts = mContacts == null ? new ArrayList<>() : new ArrayList<>(mContacts);
        var installedPackages = WhatsAppContactPickerLauncher.getInstalledWhatsAppPackages(getContext());
        if (installedPackages.size() == 1) {
            startSelectContacts(installedPackages.get(0), preferenceKey, selectedContacts);
        } else if (installedPackages.size() > 1) {
            showPackageSelectionDialog(installedPackages, preferenceKey, selectedContacts);
        }
        return true;
    }

    private void showPackageSelectionDialog(@NonNull ArrayList<String> installedPackages,
                                            @NonNull String preferenceKey,
                                            @NonNull ArrayList<String> selectedContacts) {
        CharSequence[] items = new CharSequence[installedPackages.size()];
        for (int i = 0; i < installedPackages.size(); i++) {
            items[i] = WhatsAppContactPickerLauncher.getPackageLabel(installedPackages.get(i));
        }

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Select WhatsApp app")
                .setItems(items, (dialog, which) -> startSelectContacts(
                        installedPackages.get(which),
                        preferenceKey,
                        new ArrayList<>(selectedContacts)
                ))
                .show();
    }

    private void startSelectContacts(@NonNull String packageName,
                                     @NonNull String preferenceKey,
                                     @NonNull ArrayList<String> selectedContacts) {
        try {
            Intent intent = WhatsAppContactPickerLauncher.createPickerIntent(getContext(), packageName, preferenceKey, selectedContacts);
            ((Activity) getContext()).startActivityForResult(intent, REQUEST_CONTACT_PICKER);
        } catch (Exception e) {
            Utils.showToast(e.getMessage(), 1);
        }
    }

    private void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);
        var typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ContactPickerPreference,
                0, 0
        );
        summaryOff = typedArray.getText(R.styleable.ContactPickerPreference_summaryOff);
        summaryOn = typedArray.getText(R.styleable.ContactPickerPreference_summaryOn);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String namesString = prefs.getString(getKey(), "");
        if (namesString.length() > 2) {
            mContacts = Arrays.stream(namesString.substring(1, namesString.length() - 1).split(", ")).map(item -> item.trim()).collect(Collectors.toCollection(ArrayList::new));
        }
        if (mContacts != null && !mContacts.isEmpty()) {
            setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
        } else {
            setSummary(String.valueOf(summaryOff));
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            mContacts = data.getStringArrayListExtra("contacts");
            getSharedPreferences().edit().putString(getKey(), mContacts.toString()).apply();
            if (mContacts != null && !mContacts.isEmpty()) {
                setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
            } else {
                setSummary(String.valueOf(summaryOff));
            }
        }
    }
}