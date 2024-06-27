package com.wmods.wppenhacer.xposed.features.media;

import static com.wmods.wppenhacer.xposed.features.general.MenuStatus.menuStatuses;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
            public MenuItem addMenu(Menu menu) {
                if (menu.findItem(ResId.string.download) != null) return null;
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
            public MenuItem addMenu(Menu menu) {
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
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }
            var file = (File) ReflectionUtils.getField(fieldFile, fileData);
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.mediacomposer.MediaComposerActivity");
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
            var fileData = XposedHelpers.getObjectField(fMessage.getObject(), "A01");
            if (!fieldFile.getDeclaringClass().isInstance(fileData)) {
                Utils.showToast(Utils.getApplication().getString(ResId.string.msg_text_status_not_downloadable), Toast.LENGTH_SHORT);
                return;
            }
            var file = (File) ReflectionUtils.getField(fieldFile, fileData);
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getPathDestination(prefs, file);
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

    private boolean copyFile(SharedPreferences prefs, File file) throws IOException {
        if (file == null || !file.exists()) throw new IOException("File doesn't exist");

        var destination = getPathDestination(prefs, file);

        try (FileInputStream in = new FileInputStream(file);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] bArr = new byte[1024];
            while (true) {
                int read = in.read(bArr);
                if (read <= 0) {
                    in.close();
                    out.close();

                    MediaScannerConnection.scanFile(Utils.getApplication(),
                            new String[]{destination},
                            new String[]{MimeTypeUtils.getMimeTypeFromExtension(file.getAbsolutePath())},
                            (path, uri) -> {
                            });

                    return true;
                }
                out.write(bArr, 0, read);
            }
        }
    }

    @NonNull
    private String getPathDestination(SharedPreferences sharedPreferences, @NonNull File f) {
        var fileName = f.getName().toLowerCase();

        var mediaPath = getStatusFolderPath(sharedPreferences, MimeTypeUtils.getMimeTypeFromExtension(fileName));
        if (!mediaPath.exists())
            mediaPath.mkdirs();

        return mediaPath + "/";
    }

    @NonNull
    private File getStatusFolderPath(SharedPreferences sharedPreferences, @NonNull String mimeType) {
        String folderPath = sharedPreferences.getString("localdownload", Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download");
        if (mimeType.contains("video")) {
            folderPath += "/WhatsApp/Wa Enhancer/Status Videos/";
        } else if (mimeType.contains("image")) {
            folderPath += "/WhatsApp/Wa Enhancer/Status Images/";
        } else if (mimeType.contains("audio")) {
            folderPath += "/WhatsApp/Wa Enhancer/Status Sounds/";
        } else {
            folderPath += "/WhatsApp/Wa Enhancer/Status Media/";
        }
        return new File(folderPath);
    }
}