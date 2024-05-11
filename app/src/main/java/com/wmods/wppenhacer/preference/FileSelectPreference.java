package com.wmods.wppenhacer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.FilePicker;
import com.wmods.wppenhacer.R;

import java.io.File;
import java.util.Arrays;

public class FileSelectPreference extends Preference implements Preference.OnPreferenceClickListener, FilePicker.OnFilePickedListener {

    private String[] mineTypes;

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context,attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context,attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    public void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);
        var typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.FileSelectPreference,
                0, 0
        );
        var attrsArray = typedArray.getTextArray(R.styleable.FileSelectPreference_android_entryValues);
        mineTypes = Arrays.stream(attrsArray).map(String::valueOf).toArray(String[]::new);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var keyValue = prefs.getString(this.getKey(),null);
        setSummary(keyValue);
    }


    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        FilePicker.setOnFilePickedListener(this);
        FilePicker.fileCapture.launch(mineTypes);
        return false;
    }

    @Override
    public void onFilePicked(File file) {
        getSharedPreferences().edit().putString(getKey(),file.getAbsolutePath()).apply();
        setSummary(file.getAbsolutePath());
    }
}
