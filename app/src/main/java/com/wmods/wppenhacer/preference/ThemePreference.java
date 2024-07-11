package com.wmods.wppenhacer.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.TextEditorActivity;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import kotlin.io.FilesKt;

public class ThemePreference extends Preference {


    public static File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
    private androidx.appcompat.app.AlertDialog mainDialog;

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        super.onClick();
        showFolderDialog();
    }

    private void showFolderDialog() {
        final Context context = getContext();
        List<String> folders = getFolders();

        var folder_name = getSharedPreferences().getString(getKey(), null);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.preference_theme, null);
        builder.setView(dialogView);

        LinearLayout folderListContainer = dialogView.findViewById(R.id.folder_list_container);
        Button addButton = dialogView.findViewById(R.id.add_button);

        addButton.setOnClickListener(v -> showCreateNewFolderDialog());

        for (String folder : folders) {
            View itemView = LayoutInflater.from(context).inflate(R.layout.item_folder, null, false);
            TextView folderNameView = itemView.findViewById(R.id.folder_name);
            folderNameView.setText(folder);

            if (folder.equals(folder_name)) {
                folderNameView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_material_green_dark_onPrimaryContainer));
            }
            itemView.setOnClickListener(v -> {
                var cssFile = new File(rootDirectory, folder + "/style.css");
                if (cssFile.exists()) {
                    var code = FilesKt.readText(cssFile, Charset.defaultCharset());
                    var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                    sharedPreferences.edit().putString(getKey(), folder).commit();
                    sharedPreferences.edit().putString("custom_css", code).commit();
                }
                mainDialog.dismiss();
            });
            ImageButton editButton = itemView.findViewById(R.id.edit_button);
            editButton.setOnClickListener(v -> {
                Intent intent = new Intent(context, TextEditorActivity.class);
                intent.putExtra("folder_name", folder);
                intent.putExtra("key", getKey());
                ContextCompat.startActivity(context, intent, null);
            });
            folderListContainer.addView(itemView);
        }

        builder.setNegativeButton("Cancel", null);
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

    private void showCreateNewFolderDialog() {
        final Context context = getContext();
        final EditText input = new EditText(context);
        new AlertDialog.Builder(context)
                .setTitle("New Theme Name")
                .setView(input)
                .setPositiveButton("Create", (dialog, whichButton) -> {
                    String folderName = input.getText().toString();
                    if (!TextUtils.isEmpty(folderName)) {
                        createNewFolder(folderName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewFolder(String folderName) {
        File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
        File newFolder = new File(rootDirectory, folderName);

        if (!newFolder.exists()) {
            if (newFolder.mkdirs()) {
                Toast.makeText(getContext(), "Folder created successfully", Toast.LENGTH_SHORT).show();
                mainDialog.dismiss();
                showFolderDialog();
            } else {
                Toast.makeText(getContext(), "Failed to create folder", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Folder already exists", Toast.LENGTH_SHORT).show();
        }
    }
}