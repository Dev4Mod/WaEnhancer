package com.wmods.wppenhacer.views;

import static de.robv.android.xposed.XposedBridge.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.preference.ThemePreference;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import de.robv.android.xposed.XSharedPreferences;

@SuppressLint("ViewConstructor")
public class WallpaperView extends FrameLayout {
    private final XSharedPreferences prefs;
    private final Properties properties;

    public WallpaperView(@NonNull Context context, XSharedPreferences preferences, Properties properties) {
        super(context);
        this.prefs = preferences;
        this.properties = properties;
        init(context);
    }

    private void init(Context context) {
        ImageView bgView = new ImageView(context);
        bgView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgView.setAdjustViewBounds(false);
        try {
            String image = ThemePreference.rootDirectory.getAbsolutePath() + "/" + prefs.getString("folder_theme", "") + "/" + properties.getProperty("wallpaper_file");
            if (prefs.getBoolean("wallpaper", false)) {
                image = prefs.getString("wallpaper_file", "");
            }
            Drawable drawable = getDrawableImage(image);
            bgView.setImageDrawable(drawable);
            addView(bgView);
        } catch (Exception e) {
            log("Error initializing wallpaper view: " + e.getMessage());
        }
    }

    private Drawable getDrawableImage(String imagePath) throws Exception {
        var fileOut = getContext().getFilesDir().getAbsolutePath() + "/" + "wallpaper.jpg";
        var file = new File(imagePath);
        if (!file.exists()) return null;
        var waeFolder = App.getWaEnhancerFolder();
        String filePath = file.getAbsolutePath();
        long lastModified = file.lastModified();
        String cacheKey = filePath + "_" + lastModified;
        Uri fileUri = null;

        if (!file.canRead() && imagePath.startsWith(waeFolder.getAbsolutePath())) {
            var destination = imagePath.replace(waeFolder.getAbsolutePath() + "/", "");
            var split = destination.split("/");
            var waeFolderUriPath = WppCore.getPrivString("folder_wae", null);
            if (waeFolderUriPath == null) return null;
            var waeFolderUri = Uri.parse(waeFolderUriPath);
            var documentFile = DocumentFile.fromTreeUri(getContext(), waeFolderUri);
            for (String s : split) {
                if ((documentFile = documentFile.findFile(s)) == null) {
                    return null;
                }
            }
            fileUri = documentFile.getUri();
        }
        // Recuperar informações armazenadas em cache
        String cachedData = WppCore.getPrivString("wallpaper_data", "");

        // Verificar se o arquivo foi modificado ou se é um novo arquivo
        if (cacheKey.equals(cachedData) && new File(fileOut).exists()) {
            // Carregar imagem do arquivo em cache
            Bitmap bitmap = BitmapFactory.decodeFile(fileOut);
            return new BitmapDrawable(getResources(), bitmap);
        }

        Bitmap bitmap = fileUri != null ? BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(fileUri)) : BitmapFactory.decodeFile(imagePath);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        var windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        var scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        // Salvar a imagem em disco
        try (var outputStream = new FileOutputStream(fileOut)) {
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            outputStream.flush();
        }

        WppCore.setPrivString("wallpaper_data", cacheKey);
        bitmap.recycle();

        return new BitmapDrawable(getResources(), scaledBitmap);
    }

}