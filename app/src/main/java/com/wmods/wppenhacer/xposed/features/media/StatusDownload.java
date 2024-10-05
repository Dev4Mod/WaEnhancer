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
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class StatusDownload extends Feature {

    private Field fieldFile;

    public StatusDownload(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false)) return;

        fieldFile = Unobfuscator.loadStatusDownloadFileField(classLoader);

        var downloadStatus = new MenuStatus.MenuItemStatus() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                if (menu.findItem(ResId.string.download) != null) return null;
                if (fMessage.getKey().isFromMe) return null;
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
            var fileData = XposedHelpers.getObjectField(fMessageWpp.getObject(), "A01");
            if (!fieldFile.getDeclaringClass().isInstance(fileData)) {
                Intent intent = new Intent();
                intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.textstatuscomposer.TextStatusComposerActivity");
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().finish();
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }
            var file = (File) ReflectionUtils.getField(fieldFile, fileData);
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.mediacomposer.MediaComposerActivity");
            intent.putExtra("jids", new ArrayList<>(Collections.singleton("status@broadcast")));
            intent.putExtra("android.intent.extra.STREAM", new ArrayList<>(Collections.singleton(Uri.fromFile(file))));
            intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
            WppCore.getCurrentActivity().finish();
            WppCore.getCurrentActivity().startActivity(intent);
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void downloadFile(FMessageWpp fMessage) {
        try {
            var fileData = XposedHelpers.getObjectField(fMessage.getObject(), "A01");
            if (!fieldFile.getDeclaringClass().isInstance(fileData)) {
                Utils.showToast(Utils.getApplication().getString(ResId.string.msg_text_status_not_downloadable), Toast.LENGTH_SHORT);
                return;
            }
            var file = (File) ReflectionUtils.getField(fieldFile, fileData);
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getPathDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var destinationFile = new File(destination, name);
            var error = Utils.copyFile(file, destinationFile);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(Utils.getApplication().getString(ResId.string.saved_to) + destinationFile.getAbsolutePath(), Toast.LENGTH_SHORT);
                log("Saved to: " + destinationFile.getAbsolutePath());
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
    private String getPathDestination(@NonNull File f) {
        var fileName = f.getName().toLowerCase();
        var mediaPath = getStatusFolderPath(MimeTypeUtils.getMimeTypeFromExtension(fileName));
        return mediaPath + "/";
    }

    @NonNull
    private File getStatusFolderPath(@NonNull String mimeType) {
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
        var folder = new File(Utils.getDestination(folderPath));
        if (!folder.exists())
            folder.mkdirs();
        return folder;
    }
}