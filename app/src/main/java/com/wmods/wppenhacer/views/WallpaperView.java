package com.wmods.wppenhacer.views;

import static de.robv.android.xposed.XposedBridge.log;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.io.File;

import de.robv.android.xposed.XSharedPreferences;

public class WallpaperView extends FrameLayout {
    private final XSharedPreferences prefs;
    private ImageView imageView;
    private float mAlpha = 1f;

    public WallpaperView(@NonNull Context context, XSharedPreferences preferences) {
        super(context);
        this.prefs = preferences;
        init(context);
    }

    private void init(Context context) {
        imageView = new android.widget.ImageView(context);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(false);
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(prefs.getString("wallpaper_file", ""));
            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
            imageView.setImageDrawable(drawable);
            this.mAlpha = (100 - prefs.getInt("wallpaper_alpha", 30)) / 100f;
            addView(imageView);
        }catch (Exception e){
            log(e.toString());
        }
    }

    @Override
    public void addView(View child) {
        if (child != imageView)
            child.setAlpha(mAlpha);
        super.addView(child);
    }
}
