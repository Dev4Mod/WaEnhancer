package com.wmods.wppenhacer.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WhatsAppContactPickerLauncher {

    public static final String EXTRA_PICKER_MODE = "picker_mode";
    public static final String EXTRA_CONTACT_MODE = "contact_mode";
    private static final List<String> WHATSAPP_PACKAGES = List.of("com.whatsapp", "com.whatsapp.w4b");
    private static final List<String> ABOUT_ACTIVITY_CANDIDATES = List.of(
            "com.whatsapp.settings.About",
            "com.whatsapp.settings.ui.About"
    );
    private static final List<String> SETTINGS_NOTIFICATIONS_CANDIDATES = List.of(
            "com.whatsapp.SettingsNotifications",
            "com.whatsapp.settings.SettingsNotifications",
            "com.whatsapp.settings.ui.SettingsNotifications"
    );

    private WhatsAppContactPickerLauncher() {
    }

    @NonNull
    public static ArrayList<String> getInstalledWhatsAppPackages(@NonNull Context context) {
        ArrayList<String> installedPackages = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : WHATSAPP_PACKAGES) {
            try {
                packageManager.getPackageInfo(packageName, 0);
                installedPackages.add(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return installedPackages;
    }

    @NonNull
    public static CharSequence getPackageLabel(@NonNull String packageName) {
        return "com.whatsapp.w4b".equals(packageName)
                ? "WhatsApp Business"
                : "WhatsApp";
    }

    @NonNull
    public static Intent createPickerIntent(@NonNull Context context,
                                            @NonNull String packageName,
                                            @NonNull String key,
                                            @Nullable ArrayList<String> selectedJids) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(packageName, resolveSettingsNotificationsClassName(context, packageName));
        intent.putExtra(EXTRA_CONTACT_MODE, true);
        intent.putExtra("key", key);
        intent.putStringArrayListExtra("contacts", selectedJids == null ? new ArrayList<>() : new ArrayList<>(selectedJids));
        return intent;
    }

    @NonNull
    public static Intent createAboutPickerIntent(@NonNull Context context,
                                                 @NonNull String packageName,
                                                 @NonNull String key,
                                                 @Nullable ArrayList<String> selectedJids) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(packageName, resolveAboutActivityClassName(context, packageName));
        intent.putExtra(EXTRA_PICKER_MODE, true);
        intent.putExtra("key", key);
        intent.putStringArrayListExtra("contacts", selectedJids == null ? new ArrayList<>() : new ArrayList<>(selectedJids));
        return intent;
    }

    @NonNull
    private static String resolveAboutActivityClassName(@NonNull Context context, @NonNull String packageName) throws Exception {
        PackageManager packageManager = context.getPackageManager();
        for (String candidate : ABOUT_ACTIVITY_CANDIDATES) {
            try {
                packageManager.getActivityInfo(new ComponentName(packageName, candidate), 0);
                return candidate;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
        if (packageInfo.activities != null) {
            for (var activityInfo : packageInfo.activities) {
                String name = activityInfo.name;
                if (name == null) {
                    continue;
                }
                if (name.endsWith(".settings.About") || name.endsWith(".settings.ui.About") || name.endsWith(".About")) {
                    return name;
                }
            }
        }

        throw new Exception("Class About not found");
    }

    @NonNull
    private static String resolveSettingsNotificationsClassName(@NonNull Context context, @NonNull String packageName) throws Exception {
        PackageManager packageManager = context.getPackageManager();
        for (String candidate : SETTINGS_NOTIFICATIONS_CANDIDATES) {
            try {
                packageManager.getActivityInfo(new ComponentName(packageName, candidate), 0);
                return candidate;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
        if (packageInfo.activities != null) {
            for (var activityInfo : packageInfo.activities) {
                String name = activityInfo.name;
                if (name != null && name.endsWith("SettingsNotifications")) {
                    return name;
                }
            }
        }

        throw new Exception("Class SettingsNotifications not found");
    }
}