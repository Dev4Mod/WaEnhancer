package com.wmods.wppenhacer.preference;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.ContactPickerActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ContactPickerPreference extends Preference implements Preference.OnPreferenceClickListener {

    public static final int REQUEST_CONTACT_PICKER = 1;
    public static final int PERMISSIONS_REQUEST_READ_CONTACTS = 2;
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
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ((Activity) getContext()).requestPermissions(
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSIONS_REQUEST_READ_CONTACTS);
        } else {
            startContactPickerActivity();
        }
        return true;
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

    // Método para iniciar a ContactPickerActivity
    public void startContactPickerActivity() {
        Context context = getContext();
        Intent intent = new Intent(context, ContactPickerActivity.class);
        if (mContacts != null) {
            intent.putStringArrayListExtra("selectedNumbers", mContacts);
        }
        intent.putExtra("key", getKey());
        ((Activity) getContext()).startActivityForResult(intent, REQUEST_CONTACT_PICKER);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            mContacts = data.getStringArrayListExtra("selectedNumbers");
            getSharedPreferences().edit().putString(getKey(), mContacts.toString()).apply();
            if (mContacts != null && !mContacts.isEmpty()) {
                setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
            } else {
                setSummary(String.valueOf(summaryOff));
            }
        }
    }
}
