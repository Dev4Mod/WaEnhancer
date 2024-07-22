package com.wmods.wppenhacer.preference;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.utils.FilePicker;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class FileSelectPreference extends Preference implements Preference.OnPreferenceClickListener, FilePicker.OnFilePickedListener, FilePicker.OnUriPickedListener {

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

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void showAlertPermission() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(R.string.storage_permission);
        builder.setMessage(R.string.permission_storage);
        builder.setPositiveButton(R.string.allow, (dialog, which) -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
            getContext().startActivity(intent);
        });
        builder.setNegativeButton(R.string.deny, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showAlertPermission();
            return true;
        }

        FilePicker.setOnFilePickedListener(this);
        if (selectDirectory) {
            FilePicker.directoryCapture.launch(null);
            return true;
        }
        if (mineTypes.length == 1 && mineTypes[0].contains("image")) {
            FilePicker.setOnUriPickedListener(this);
            FilePicker.imageCapture.launch(new PickVisualMediaRequest.Builder().setMediaType(new ActivityResultContracts.PickVisualMedia.SingleMimeType(mineTypes[0])).build());
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

    @Override
    public void onUriPicked(Uri uri) {
        ContentResolver contentResolver = getContext().getContentResolver();
        var type = contentResolver.getType(uri);
        var extension = type.split("/")[1];
        var folder = new File(App.getWaEnhancerFolder(), "files");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        var outFile = new File(folder, this.getKey() + "." + extension);
        getSharedPreferences().edit().putString(getKey(), outFile.getAbsolutePath()).apply();
        setSummary(outFile.getAbsolutePath());
        CompletableFuture.runAsync(() -> {
            try (var inputStream = contentResolver.openInputStream(uri)) {
                Files.copy(inputStream, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Utils.showToast("Failed to save file: " + e, Toast.LENGTH_SHORT);
            }
        });

    }
}
