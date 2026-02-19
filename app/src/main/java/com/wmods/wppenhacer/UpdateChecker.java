package com.wmods.wppenhacer;

import android.app.Activity;
import android.util.Log;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import okhttp3.OkHttpClient;

import io.noties.markwon.Markwon;

public class UpdateChecker implements Runnable {

    private static final String TAG = "WAE_UpdateChecker";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/Dev4Mod/WaEnhancer/releases/latest";
    private static final String RELEASE_TAG_PREFIX = "debug-";
    private static final String TELEGRAM_UPDATE_URL = "https://github.com/Dev4Mod/WaEnhancer/releases";

    // Singleton OkHttpClient - expensive to create, reuse across all checks
    private static OkHttpClient httpClient;

    private final Activity mActivity;

    public UpdateChecker(Activity activity) {
        this.mActivity = activity;
    }

    /**
     * Get or create the singleton OkHttpClient with proper timeout configuration
     */
    private static synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public void run() {
        XposedBridge.log("[" + TAG + "] UpdateChecker.run() started");
        try {
            XposedBridge.log("[" + TAG + "] Starting update check...");
            
            var request = new okhttp3.Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .build();
            
            String hash;
            String changelog;
            String publishedAt;
            
            try (var response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    XposedBridge.log("[" + TAG + "] Update check failed: HTTP " + response.code());
                    return;
                }
                
                var body = response.body();
                if (body == null) {
                    XposedBridge.log("[" + TAG + "] Update check failed: Empty response body");
                    return;
                }
                
                var content = body.string();
                var release = new JSONObject(content);
                var tagName = release.optString("tag_name", "");
                
                XposedBridge.log("[" + TAG + "] Latest release tag: " + tagName);
                
                if (tagName.isBlank() || !tagName.startsWith(RELEASE_TAG_PREFIX)) {
                    XposedBridge.log("[" + TAG + "] Invalid or non-debug release tag");
                    return;
                }
                
                hash = tagName.substring(RELEASE_TAG_PREFIX.length()).trim();
                changelog = release.optString("body", "No changelog available.").trim();
                publishedAt = release.optString("published_at", "");
                
                XposedBridge.log("[" + TAG + "] Release hash: " + hash + ", published: " + publishedAt);
            }

            if (hash.isBlank()) {
                XposedBridge.log("[" + TAG + "] Empty hash, skipping");
                return;
            }
            
            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            boolean isNewVersion = !appInfo.versionName.toLowerCase().contains(hash.toLowerCase().trim());
            boolean isIgnored = Objects.equals(WppCore.getPrivString("ignored_version", ""), hash);
            
            if (isNewVersion && !isIgnored) {
                XposedBridge.log("[" + TAG + "] New version available, showing dialog");
                
                final String finalHash = hash;
                final String finalChangelog = changelog;
                final String finalPublishedAt = publishedAt;
                
                mActivity.runOnUiThread(() -> {
                    showUpdateDialog(finalHash, finalChangelog, finalPublishedAt);
                });
            } else {
                XposedBridge.log("[" + TAG + "] No update needed (isNew=" + isNewVersion + ", isIgnored=" + isIgnored + ")");
            }
        } catch (java.net.SocketTimeoutException e) {
            XposedBridge.log("[" + TAG + "] Update check timeout: " + e.getMessage());
        } catch (java.io.IOException e) {
            XposedBridge.log("[" + TAG + "] Network error during update check: " + e.getMessage());
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Unexpected error during update check: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private void showUpdateDialog(String hash, String changelog, String publishedAt) {
        XposedBridge.log("[" + TAG + "] Attempting to show update dialog");
        try {
            var markwon = Markwon.create(mActivity);
            var dialog = new AlertDialogWpp(mActivity);
            
            // Format the published date
            String formattedDate = formatPublishedDate(publishedAt);
            
            // Build simple message with version and date
            StringBuilder message = new StringBuilder();
            message.append("📦 **Version:** `").append(hash).append("`\n");
            if (!formattedDate.isEmpty()) {
                message.append("📅 **Released:** ").append(formattedDate).append("\n");
            }
            message.append("\n### What's New\n\n").append(changelog);
            
            dialog.setTitle("🎉 New Update Available!");
            dialog.setMessage(markwon.toMarkdown(message.toString()));
            dialog.setNegativeButton("Ignore", (dialog1, which) -> {
                WppCore.setPrivString("ignored_version", hash);
                dialog1.dismiss();
            });
            dialog.setPositiveButton("Update Now", (dialog1, which) -> {
                Utils.openLink(mActivity, TELEGRAM_UPDATE_URL);
                dialog1.dismiss();
            });
            dialog.show();
            
            XposedBridge.log("[" + TAG + "] Update dialog shown successfully");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error showing update dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Format ISO 8601 date to human-readable format
     * @param isoDate ISO 8601 date string (e.g., "2024-02-14T12:34:56Z")
     * @return Formatted date (e.g., "Feb 14, 2024" or "February 14, 2024")
     */
    private String formatPublishedDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }
        
        try {
            // Parse ISO 8601 date
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = isoFormat.parse(isoDate);
            
            if (date != null) {
                // Format to readable date
                SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                return displayFormat.format(date);
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error parsing date: " + e.getMessage());
        }
        
        return "";
    }
}
