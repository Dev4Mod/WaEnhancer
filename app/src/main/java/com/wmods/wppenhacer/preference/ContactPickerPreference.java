package com.wmods.wppenhacer.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        PackageManager pm = getContext().getPackageManager();
        for (var pkg : List.of("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                if (pm.getPackageInfo(pkg, 0) != null) {
                    startSelectContacts(pkg);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void startSelectContacts(String packageName) {
        try {
            Intent intent = new Intent();
            var pInfo = getContext().getApplicationContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            assert pInfo.activities != null;
            String className = null;
            for (var activity : pInfo.activities) {
                if (activity.name.endsWith("SettingsNotifications")) {
                    className = activity.name;
                    break;
                }
            }
            if (className == null) {
                throw new Exception("Class SettingsNotifications not found");
            }
            intent.setClassName(packageName, className);
            intent.putExtra("key", getKey());
            intent.putExtra("contact_mode", true);
            intent.putStringArrayListExtra("contacts", mContacts);
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
