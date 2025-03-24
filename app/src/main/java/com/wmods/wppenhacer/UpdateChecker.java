package com.wmods.wppenhacer;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Html;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Objects;

import okhttp3.OkHttpClient;

public class UpdateChecker implements Runnable {

    private final Activity mActivity;

    public UpdateChecker(Activity activity) {
        this.mActivity = activity;
    }


    @Override
    public void run() {
        try {
            var client = new OkHttpClient();
            var request = new okhttp3.Request.Builder()
                    .url("https://t.me/s/waenhancher")
                    .build();
            var response = client.newCall(request).execute();
            var body = response.body();
            if (body == null) return;
            var content = body.string();
            var findText = "WaEnhancer_Business_";
            var indexHash = content.lastIndexOf(findText);
            var lastindexHash = content.indexOf(".apk", indexHash);
            var hash = content.substring(indexHash + findText.length(), lastindexHash);
            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            if (!appInfo.versionName.toLowerCase().contains(hash.toLowerCase().trim()) && !Objects.equals(WppCore.getPrivString("ignored_version", ""), hash)) {
                var changelogIndex = content.indexOf("<div class=\"tgme_widget_message_text js-message_text\" dir=\"auto\">", lastindexHash);
                var closeTag = content.indexOf("</div>", changelogIndex);
                var changelogText = content.substring(changelogIndex, closeTag + 6);
                var changelog = Html.fromHtml(changelogText, Html.FROM_HTML_MODE_COMPACT).toString();
                mActivity.runOnUiThread(() -> {
                    AlertDialog.Builder dialog = new AlertDialog.Builder(mActivity);
                    dialog.setTitle("WAE - New version available!");
                    dialog.setMessage("Changelog:\n\n" + changelog);
                    dialog.setNegativeButton("Ignore", (dialog1, which) -> {
                        WppCore.setPrivString("ignored_version", hash);
                        dialog1.dismiss();
                    });
                    dialog.setPositiveButton("Update", (dialog1, which) -> {
                        Utils.openLink(mActivity, "https://t.me/waenhancher");
                        dialog1.dismiss();
                    });
                    dialog.show();
                });
            }
        } catch (Exception ignored) {
        }
    }
}
