package com.wmods.wppenhacer.xposed.features.media;

import static com.wmods.wppenhacer.xposed.features.general.MenuStatus.menuStatuses;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.MenuStatus;
import com.wmods.wppenhacer.xposed.utils.MimeTypeUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class StatusDownload extends Feature {

    public StatusDownload(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false)) return;


        var downloadStatus = new MenuStatus.MenuItemStatus() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                if (menu.findItem(ResId.string.download) != null) return null;
                if (fMessage.getKey().isFromMe) return null;
                if (!fMessage.isMediaFile()) return null;
                return menu.add(0, ResId.string.download, 0, ResId.string.download);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                downloadFile(fMessageWpp);
            }
        };
        menuStatuses.add(downloadStatus);


        var sharedMenu = new MenuStatus.MenuItemStatus() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                if (fMessage.getKey().isFromMe) return null;
                if (menu.findItem(ResId.string.share_as_status) != null) return null;
                return menu.add(0, ResId.string.share_as_status, 0, ResId.string.share_as_status);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                sharedStatus(fMessageWpp);
            }
        };
        menuStatuses.add(sharedMenu);
    }

    private void sharedStatus(FMessageWpp fMessageWpp) {
        try {
            if (!fMessageWpp.isMediaFile()) {
                Intent intent = new Intent();
                Class clazz;
                try {
                    clazz = Unobfuscator.getClassByName("TextStatusComposerActivity", classLoader);
                } catch (Exception ignored) {
                    clazz = Unobfuscator.getClassByName("ConsolidatedStatusComposerActivity", classLoader);
                    intent.putExtra("status_composer_mode", 2);
                }
                intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }
            var file = fMessageWpp.getMediaFile();
            Intent intent = new Intent();
            var clazz = Unobfuscator.getClassByName("MediaComposerActivity", classLoader);
            intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton("status@broadcast")));
            intent.putExtra("android.intent.extra.STREAM", new ArrayList<>(Collections.singleton(Uri.fromFile(file))));
            intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
            WppCore.getCurrentActivity().startActivity(intent);
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void downloadFile(FMessageWpp fMessage) {
        try {
            var file = fMessage.getMediaFile();
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var error = Utils.copyFile(file, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(Utils.getApplication().getString(ResId.string.saved_to) + destination, Toast.LENGTH_SHORT);
            } else {

                Utils.showToast(Utils.getApplication().getString(ResId.string.error_when_saving_try_again) + ": " + error, Toast.LENGTH_SHORT);
            }
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Status";
    }


    @NonNull
    private String getStatusDestination(@NonNull File f) throws Exception {
        var fileName = f.getName().toLowerCase();
        var mimeType = MimeTypeUtils.getMimeTypeFromExtension(fileName);
        var folderPath = "";
        if (mimeType.contains("video")) {
            folderPath = "Status Videos";
        } else if (mimeType.contains("image")) {
            folderPath = "Status Images";
        } else if (mimeType.contains("audio")) {
            folderPath = "Status Sounds";
        } else {
            folderPath = "Status Media";
        }
        return Utils.getDestination(folderPath);
    }

}