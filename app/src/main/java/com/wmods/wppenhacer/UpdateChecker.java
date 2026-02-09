package com.wmods.wppenhacer;

import android.app.Activity;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import okhttp3.OkHttpClient;

public class UpdateChecker implements Runnable {

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/mubashardev/WaEnhancer/releases/latest";
    private static final String RELEASE_TAG_PREFIX = "debug-";
    private static final String TELEGRAM_UPDATE_URL = "https://github.com/mubashardev/WaEnhancer/releases";

    private final Activity mActivity;

    public UpdateChecker(Activity activity) {
        this.mActivity = activity;
    }


    @Override
    public void run() {
        try {
            var client = new OkHttpClient();
            var request = new okhttp3.Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .build();
            String hash;
            String changelog;
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;
                var body = response.body();
                var content = body.string();
                var release = new JSONObject(content);
                var tagName = release.optString("tag_name", "");
                XposedBridge.log("[UPDATE]" +tagName);
                if (tagName.isBlank() || !tagName.startsWith(RELEASE_TAG_PREFIX)) return;
                hash = tagName.substring(RELEASE_TAG_PREFIX.length()).trim();
                changelog = release.optString("body", "No changelog available.").trim();
            }

            if (hash.isBlank()) return;
            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            if (!appInfo.versionName.toLowerCase().contains(hash.toLowerCase().trim()) && !Objects.equals(WppCore.getPrivString("ignored_version", ""), hash)) {
                mActivity.runOnUiThread(() -> {
                    var dialog = new AlertDialogWpp(mActivity);
                    dialog.setTitle("WAE - New version available!");
                    dialog.setMessage("Changelog:\n\n" + changelog);
                    dialog.setNegativeButton("Ignore", (dialog1, which) -> {
                        WppCore.setPrivString("ignored_version", hash);
                        dialog1.dismiss();
                    });
                    dialog.setPositiveButton("Update", (dialog1, which) -> {
                        Utils.openLink(mActivity, TELEGRAM_UPDATE_URL);
                        dialog1.dismiss();
                    });
                    dialog.show();
                });
            }
        } catch (Exception ignored) {
        }
    }
}
