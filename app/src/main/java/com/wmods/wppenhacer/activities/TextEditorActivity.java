package com.wmods.wppenhacer.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.amrdeveloper.codeview.CodeView;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.syntax.CSSLanguage;
import com.wmods.wppenhacer.preference.ThemePreference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import kotlin.io.FilesKt;

public class TextEditorActivity extends AppCompatActivity {
    private CodeView codeView;
    private String folderName;
    private ActivityResultLauncher<String> mGetContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        codeView = findViewById(R.id.codeView);
        codeView.setHorizontallyScrolling(false);

        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onUriSelected);

        folderName = getIntent().getStringExtra("folder_name");
        if (!TextUtils.isEmpty(folderName)) {
            readFile(folderName);
        }
        // Apply CSS Highlighting with dynamic color highlight
        CSSLanguage.applyCSSHighlighting(this, codeView);
        configAutoComplete();
    }

    public void configAutoComplete() {
        File folderFolder = new File(ThemePreference.rootDirectory, folderName);
        var files = folderFolder.listFiles(item -> item.getName().endsWith(".png"));

        String[] languageKeywords = Arrays.stream(files).map(File::getName).map(s -> "url(\"" + s + "\")").toArray(String[]::new);

        // Custom list item xml layout
        final int layoutId = R.layout.list_item_suggestion;

        // TextView id to put suggestion on it
        final int viewId = R.id.suggestItemTextView;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, layoutId, viewId, languageKeywords);

        // Add the ArrayAdapter to the CodeView
        codeView.setAdapter(adapter);
    }


    private void readFile(String folderName) {
        try {
            File folderFolder = new File(ThemePreference.rootDirectory, folderName);
            File cssCode = new File(folderFolder, "style.css");
            if (cssCode.exists()) {
                var code = FilesKt.readText(cssCode, Charset.defaultCharset());
                codeView.setText(code);
            } else {
                cssCode.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.css_editor_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_save -> {
                String code = codeView.getText().toString();
                try {
                    File folderFolder = new File(ThemePreference.rootDirectory, folderName);
                    File cssCode = new File(folderFolder, "style.css");
                    FilesKt.writeText(cssCode, code, Charset.defaultCharset());
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    var prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    var key = getIntent().getStringExtra("key");
                    if (key != null && prefs.getString(key, "").equals(folderName)) {
                        prefs.edit().putString("custom_css", code).commit();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            case R.id.menuitem_exit -> finish();
            case R.id.menuitem_clear -> codeView.setText("");
            case R.id.menuitem_import_image -> {
                mGetContent.launch("image/*");
            }
        }
        return super.onOptionsItemSelected(item);
    }


    public void onUriSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        var linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        var input = new EditText(this);
        input.setHint("example.png");
        input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        linearLayout.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Enter image file name")
                .setPositiveButton("OK", (dialog, which) -> {
                    var fileName = input.getText().toString();
                    if (fileName.endsWith(".png")) {
                        copyFromUri(fileName, uri);
                    } else {
                        Toast.makeText(this, "Name must end with .png", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setView(linearLayout).show();
    }

    public void copyFromUri(String fileName, Uri uri) {
        var outFolder = new File(ThemePreference.rootDirectory, folderName);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            var outFile = new File(outFolder, fileName);
            FileOutputStream out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
            Toast.makeText(this, "Imported as " + fileName, Toast.LENGTH_LONG).show();
            configAutoComplete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}