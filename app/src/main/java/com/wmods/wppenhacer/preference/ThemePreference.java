package com.wmods.wppenhacer.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.FilePicker;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.TextEditorActivity;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import kotlin.io.FilesKt;

public class ThemePreference extends Preference implements FilePicker.OnUriPickedListener {


    public static File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
    private androidx.appcompat.app.AlertDialog mainDialog;

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        super.onClick();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showThemeDialog();
            } else {
                showAlertPermission();
            }
        } else {
            showThemeDialog();
        }
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

    @SuppressLint("ApplySharedPref")
    private void showThemeDialog() {
        final Context context = getContext();
        List<String> folders = getFolders();
        folders.add(0, "Default Theme");

        var folder_name = getSharedPreferences().getString(getKey(), null);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.preference_theme, null);
        builder.setView(dialogView);

        LinearLayout folderListContainer = dialogView.findViewById(R.id.folder_list_container);
        Button newTheme = dialogView.findViewById(R.id.create_theme_button);
        newTheme.setOnClickListener(v -> showCreateNewThemeDialog());

        Button importTheme = dialogView.findViewById(R.id.import_theme_button);
        importTheme.setOnClickListener(v -> {
            FilePicker.setOnUriPickedListener(this);
            FilePicker.fileCapture.launch(new String[]{"application/zip"});
        });

        for (String folder : folders) {
            View itemView = LayoutInflater.from(context).inflate(R.layout.item_folder, null, false);
            TextView folderNameView = itemView.findViewById(R.id.folder_name);
            folderNameView.setText(folder);

            if (folder.equals(folder_name)) {
                folderNameView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_material_green_dark_onPrimaryContainer));
            }
            itemView.setOnClickListener(v -> {
                var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                sharedPreferences.edit().putString(getKey(), folder).commit();
                var cssFile = new File(rootDirectory, folder + "/style.css");
                if (cssFile.exists()) {
                    var code = FilesKt.readText(cssFile, Charset.defaultCharset());
                    sharedPreferences.edit().putString("custom_css", code).commit();
                } else {
                    sharedPreferences.edit().putString("custom_css", "").commit();
                }
                mainDialog.dismiss();
            });
            ImageButton editButton = itemView.findViewById(R.id.edit_button);
            if (folder.equals("Default Theme")) {
                editButton.setVisibility(View.INVISIBLE);
            } else {
                editButton.setOnClickListener(v -> {
                    Intent intent = new Intent(context, TextEditorActivity.class);
                    intent.putExtra("folder_name", folder);
                    intent.putExtra("key", getKey());
                    ContextCompat.startActivity(context, intent, null);
                });
            }
            folderListContainer.addView(itemView);
        }
        mainDialog = builder.show();
    }

    private List<String> getFolders() {
        List<String> folderNames = new ArrayList<>();
        File[] folders = rootDirectory.listFiles(File::isDirectory);

        if (folders != null) {
            for (File folder : folders) {
                folderNames.add(folder.getName());
            }
        }

        return folderNames;
    }

    private void showCreateNewThemeDialog() {
        final Context context = getContext();
        final EditText input = new EditText(context);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.new_theme_name)
                .setView(input)
                .setPositiveButton(R.string.create, (dialog, whichButton) -> {
                    String folderName = input.getText().toString();
                    if (!TextUtils.isEmpty(folderName)) {
                        createNewFolder(folderName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createNewFolder(String folderName) {
        File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
        File newFolder = new File(rootDirectory, folderName);
        if (!newFolder.exists()) {
            if (newFolder.mkdirs()) {
                mainDialog.dismiss();
                showThemeDialog();
            }
        }
    }

    @Override
    public void onUriPicked(Uri uri) {
        if (uri == null) return;
        try (var inputStream = getContext().getContentResolver().openInputStream(uri)) {
            var zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            if (zipEntry == null) {
                Utils.showToast(getContext().getString(R.string.invalid_zip_file), 1);
                return;
            }
            do {
                var name = zipEntry.getName();
                if (!name.contains("/")) {
                    continue;
                }
                var folderName = name.substring(0, name.lastIndexOf('/'));
                File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
                File newFolder = new File(rootDirectory, folderName);
                if (!newFolder.exists()) newFolder.mkdirs();
                var file = new File(rootDirectory, name);
                Files.copy(zipInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                zipEntry = zipInputStream.getNextEntry();
            } while (zipEntry != null);
            Utils.showToast(getContext().getString(R.string.theme_imported_successfully), 1);
            mainDialog.dismiss();
            showThemeDialog();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}