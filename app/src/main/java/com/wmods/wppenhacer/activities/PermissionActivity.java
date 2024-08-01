package com.wmods.wppenhacer.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.databinding.ActivityPermissionsBinding;

public class PermissionActivity extends AppCompatActivity {

    private ActivityPermissionsBinding binding;

    private BatteryPermissionHelper batteryPermissionHelper = BatteryPermissionHelper.Companion.getInstance();

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        getTheme().applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true);
        getTheme().applyStyle(R.style.ThemeOverlay, true);
        getTheme().applyStyle(R.style.ThemeOverlay_MaterialGreen, true);
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnContacts.setOnClickListener((view) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 0));
        binding.btnArmaz.setOnClickListener((view) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.btnMedia.setOnClickListener((view) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 0));
        }
        var packageName = getPackageName();

        binding.btnOptimization.setOnClickListener((view) -> {
            if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                batteryPermissionHelper.getPermission(this, true, true);
            } else {
                var intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivityForResult(intent, 0);
            }
        });
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        boolean allPermissions = true;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allPermissions = false;
        } else {
            binding.btnMedia.setEnabled(false);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                allPermissions = false;
            } else {
                binding.btnArmaz.setEnabled(false);
            }
        } else {
            if (!Environment.isExternalStorageManager()) {
                allPermissions = false;
            } else {
                binding.btnArmaz.setEnabled(false);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            allPermissions = false;
        } else {
            binding.btnContacts.setEnabled(false);
        }

        var powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            allPermissions = false;
        }

        if (allPermissions) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        checkPermissions();
    }
}
