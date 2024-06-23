package com.wmods.wppenhacer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.FilePicker;
import com.wmods.wppenhacer.R;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class FileSelectPreference extends Preference implements Preference.OnPreferenceClickListener, FilePicker.OnFilePickedListener {

    private String[] mineTypes;
    private boolean selectDirectory;

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        FilePicker.setOnFilePickedListener(this);
        if (selectDirectory) {
            FilePicker.directoryCapture.launch(null);
            return true;
        }
        FilePicker.fileCapture.launch(mineTypes);
        return false;
    }

    @Override
    public void onFilePicked(File file) {
        if (file.isDirectory()) {
            try {
                var tmpFile = Files.write(new File(file, "tmp.file").toPath(), new byte[0]).toFile();
                boolean delete = tmpFile.delete();
            } catch (Exception ignored) {
                Toast.makeText(this.getContext(), R.string.failed_save_directory, Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (!file.canRead()) {
            Toast.makeText(this.getContext(), R.string.unable_to_read_this_file, Toast.LENGTH_SHORT).show();
            return;

        }
        getSharedPreferences().edit().putString(getKey(), file.getAbsolutePath()).apply();
        setSummary(file.getAbsolutePath());
    }

    public void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);
        var typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.FileSelectPreference,
                0, 0
        );
        var attrsArray = typedArray.getTextArray(R.styleable.FileSelectPreference_android_entryValues);
        if (attrsArray != null) {
            mineTypes = Arrays.stream(attrsArray).map(String::valueOf).toArray(String[]::new);
        } else {
            mineTypes = new String[]{"*/*"};
        }
        selectDirectory = typedArray.getBoolean(R.styleable.FileSelectPreference_directory, false);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var keyValue = prefs.getString(this.getKey(), null);
        setSummary(keyValue);
    }
}
