package com.wmods.wppenhacer.xposed.features.media;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener;
import com.wmods.wppenhacer.xposed.utils.MimeTypeUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XSharedPreferences;

public class StatusDownload extends Feature {

    public StatusDownload(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false)) return;


        var downloadStatus = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageWppList, int currentIndex) {
                if (menu.findItem(R.string.download) != null) return null;
                var fMessage = fMessageWppList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                if (!fMessage.isMediaFile()) return null;
                return menu.add(0, R.string.download, 0, R.string.download);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageWppList, int currentIndex) {
                var fMessage = fMessageWppList.get(currentIndex);
                downloadFile(fMessage);
            }
        };
        MenuStatusListener.getMenuStatuses().add(downloadStatus);


        var sharedMenu = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageWppList, int currentIndex) {
                var fMessage = fMessageWppList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                if (menu.findItem(R.string.share_as_status) != null) return null;
                return menu.add(0, R.string.share_as_status, 0, R.string.share_as_status);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageWppList, int currentIndex) {
                var fMessageWpp = fMessageWppList.get(currentIndex);
                sharedStatus(fMessageWpp);
            }
        };
        MenuStatusListener.getMenuStatuses().add(sharedMenu);
    }

    private void sharedStatus(FMessageWpp fMessageWpp) {
        try {
            if (!fMessageWpp.isMediaFile()) {
                Intent intent = new Intent();
                Class<?> clazz;
                try {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "TextStatusComposerActivity");
                } catch (Exception ignored) {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ConsolidatedStatusComposerActivity");
                    intent.putExtra("status_composer_mode", 2);
                }
                intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }
            var file = fMessageWpp.getMediaFile();
            if (file == null) {
                Utils.showToast(Utils.getString(R.string.download_not_available), Toast.LENGTH_SHORT);
                return;
            }
            Intent intent = new Intent();
            var clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "MediaComposerActivity");
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
            if (file == null) {
                Utils.showToast(Utils.getString(R.string.download_not_available), Toast.LENGTH_LONG);
                return;
            }
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var error = Utils.copyFile(file, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(Utils.getString(R.string.saved_to) + destination, Toast.LENGTH_SHORT);
            } else {

                Utils.showToast(Utils.getString(R.string.error_when_saving_try_again) + ": " + error, Toast.LENGTH_SHORT);
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