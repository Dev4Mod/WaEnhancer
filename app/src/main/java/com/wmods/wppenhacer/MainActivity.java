package com.wmods.wppenhacer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.wmods.wppenhacer.adapter.MainPagerAdapter;
import com.wmods.wppenhacer.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        getTheme().applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true);
        getTheme().applyStyle(R.style.ThemeOverlay, true);
        getTheme().applyStyle(R.style.ThemeOverlay_MaterialGreen, true);
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.mipmap.launcher);
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        binding.navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return switch (item.getItemId()) {
                    case R.id.navigation_home -> {
                        binding.viewPager.setCurrentItem(0);
                        yield true;
                    }
                    case R.id.navigation_chat -> {
                        binding.viewPager.setCurrentItem(1);
                        yield true;
                    }
                    case R.id.navigation_privacy -> {
                        binding.viewPager.setCurrentItem(2);
                        yield true;
                    }
                    case R.id.navigation_media -> {
                        binding.viewPager.setCurrentItem(3);
                        yield true;
                    }
                    case R.id.navigation_colors -> {
                        binding.viewPager.setCurrentItem(4);
                        yield true;
                    }
                    default -> false;
                };
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.navView.getMenu().getItem(position).setChecked(true);
            }
        });

        setupPermissions();
        FilePicker.registerFilePicker(this);
    }

    private void setupPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 0);
        }
    }

    public static boolean isXposedEnabled() {
        return false;
    }

}