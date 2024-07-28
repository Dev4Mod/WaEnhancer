package com.wmods.wppenhacer.activities.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.wmods.wppenhacer.R;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        getTheme().applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true);
        getTheme().applyStyle(R.style.ThemeOverlay, true);
        getTheme().applyStyle(R.style.ThemeOverlay_MaterialGreen, true);
        super.onCreate(savedInstanceState);
    }
}
