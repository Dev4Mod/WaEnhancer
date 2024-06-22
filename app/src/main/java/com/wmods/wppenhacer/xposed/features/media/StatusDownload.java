package com.wmods.wppenhacer.xposed.features.media;

import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.utils.MimeTypeUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class StatusDownload extends Feature {

    public StatusDownload(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false)) return;
        var mediaClass = Unobfuscator.loadStatusDownloadMediaClass(classLoader);
        logDebug("Media class: " + mediaClass.getName());
        var menuStatusClass = Unobfuscator.loadMenuStatusClass(classLoader);
        logDebug("MenuStatus class: " + menuStatusClass.getName());
        var fieldFile = Unobfuscator.loadStatusDownloadFileField(classLoader);
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
                menu.add(0, ResId.string.download, 0, ResId.string.download).setOnMenuItemClickListener(item -> {
                    if (fMessage == null) return true;
                    try {
                        var fileData = XposedHelpers.getObjectField(fMessage, "A01");
                        var file = (File) XposedHelpers.getObjectField(fileData, fieldFile.getName());
                        if (copyFile(prefs, file)) {
                            Utils.showToast(Utils.getApplication().getString(ResId.string.saved_to) + getPathDestination(prefs, file), Toast.LENGTH_SHORT);
                        } else {
                            Utils.showToast(Utils.getApplication().getString(ResId.string.error_when_saving_try_again), Toast.LENGTH_SHORT);
                        }
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
                    }
                    return true;
                });
            }
        });


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

        return mediaPath + "/" + f.getName();
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