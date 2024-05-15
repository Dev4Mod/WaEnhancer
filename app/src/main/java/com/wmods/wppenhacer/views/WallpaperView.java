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
    private float mAlpha = 1.0f;
    private ImageView bgView;

    public WallpaperView(@NonNull Context context, XSharedPreferences preferences) {
        super(context);
        this.prefs = preferences;
        init(context);
    }

    private void init(Context context) {
        bgView = new ImageView(context);
        bgView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgView.setAdjustViewBounds(false);
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(prefs.getString("wallpaper_file", ""));
            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
            bgView.setImageDrawable(drawable);
            mAlpha = (100 - prefs.getInt("wallpaper_alpha", 30)) / 100.0f;
            addView(bgView);
        } catch (Exception e) {
            log(e.toString());
        }
    }

    @Override
    public void addView(View child) {
        if (child != bgView){
            child.setAlpha(mAlpha);
        }
        super.addView(child);
    }
}