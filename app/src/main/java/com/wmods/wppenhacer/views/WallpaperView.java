package com.wmods.wppenhacer.views;

import static de.robv.android.xposed.XposedBridge.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import de.robv.android.xposed.XSharedPreferences;

@SuppressLint("ViewConstructor")
public class WallpaperView extends FrameLayout {
    private final XSharedPreferences prefs;

    public WallpaperView(@NonNull Context context, XSharedPreferences preferences) {
        super(context);
        this.prefs = preferences;
        init(context);
    }

    private void init(Context context) {
        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(false);
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(prefs.getString("wallpaper_file", ""));
            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
            imageView.setImageDrawable(drawable);
            addView(imageView);
        } catch (Exception e) {
            log(e.toString());
        }
    }

}