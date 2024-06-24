package com.wmods.wppenhacer.xposed.features.media;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class StatusDownload extends Feature {

    private Field fieldFile;

    public StatusDownload(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false)) return;
        var mediaClass = Unobfuscator.loadStatusDownloadMediaClass(classLoader);
        logDebug("Media class: " + mediaClass.getName());
        var menuStatusClass = Unobfuscator.loadMenuStatusClass(classLoader);
        logDebug("MenuStatus class: " + menuStatusClass.getName());
        fieldFile = Unobfuscator.loadStatusDownloadFileField(classLoader);
        logDebug("File field: " + fieldFile.getName());
        var clazzSubMenu = Unobfuscator.loadStatusDownloadSubMenuClass(classLoader);
        logDebug("SubMenu class: " + clazzSubMenu.getName());
        var clazzMenu = Unobfuscator.loadStatusDownloadMenuClass(classLoader);
        logDebug("Menu class: " + clazzMenu.getName());
        var menuField = Unobfuscator.getFieldByType(clazzSubMenu, clazzMenu);
        logDebug("Menu field: " + menuField.getName());


        Class<?> StatusPlaybackBaseFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        Class<?> StatusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        var listStatusField = ReflectionUtils.getFieldsByExtendType(StatusPlaybackContactFragmentClass, List.class).get(0);

        XposedHelpers.findAndHookMethod(menuStatusClass, "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Field subMenuField = Arrays.stream(param.thisObject.getClass().getDeclaredFields()).filter(f -> f.getType() == Object.class && clazzSubMenu.isInstance(XposedHelpers.getObjectField(param.thisObject, f.getName()))).findFirst().orElse(null);
                Object submenu = XposedHelpers.getObjectField(param.thisObject, subMenuField.getName());

                var fragment = Arrays.stream(param.thisObject.getClass().getDeclaredFields()).filter(f -> StatusPlaybackBaseFragmentClass.isInstance(XposedHelpers.getObjectField(param.thisObject, f.getName()))).findFirst().orElse(null);
                if (fragment == null) {
                    logDebug("Fragment not found");
                    return;
                }
                var fragmentInstance = fragment.get(param.thisObject);
                var index = (int) XposedHelpers.getObjectField(fragmentInstance, "A00");
                var listStatus = (List) listStatusField.get(fragmentInstance);
                var fMessage = (Object) listStatus.get(index);
                var menu = (Menu) XposedHelpers.getObjectField(submenu, menuField.getName());
                if (menu.findItem(ResId.string.download) != null) return;
                menu.add(0, ResId.string.download, 0, ResId.string.download).setOnMenuItemClickListener(item -> downloadFile(fMessage));
                menu.add(0, 0, 0, ResId.string.share_as_status).setOnMenuItemClickListener(item -> sharedStatus(fMessage));
            }
        });
    }

    private boolean sharedStatus(Object fMessage) {
        try {
            var fMessageWpp = new FMessageWpp(fMessage);
            var fileData = XposedHelpers.getObjectField(fMessage, "A01");
            if (!fieldFile.getDeclaringClass().isInstance(fileData)) {
                Intent intent = new Intent();
                intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.textstatuscomposer.TextStatusComposerActivity");
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().startActivity(intent);
                return false;
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
        return true;
    }

    private boolean downloadFile(Object fMessage) {
        if (fMessage == null) return true;
        try {
            var fileData = XposedHelpers.getObjectField(fMessage, "A01");
            if (!fieldFile.getDeclaringClass().isInstance(fileData)) {
                Utils.showToast(Utils.getApplication().getString(ResId.string.msg_text_status_not_downloadable), Toast.LENGTH_SHORT);
                return false;
            }
            var file = (File) ReflectionUtils.getField(fieldFile, fileData);
            var userJid = new FMessageWpp(fMessage).getUserJid();
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
        return true;
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