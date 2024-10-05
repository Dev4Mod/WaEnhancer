package com.wmods.wppenhacer.xposed.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.WppXposed;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;

public class Utils {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static final ExecutorService executorCachedService = Executors.newCachedThreadPool();

    @NonNull
    public static Application getApplication() {
        return FeatureLoader.mApp == null ? App.getInstance() : FeatureLoader.mApp;
    }

    public static ExecutorService getExecutor() {
        return executorService;
    }

    public static ExecutorService getExecutorCachedService() {
        return executorCachedService;
    }

    public static boolean doRestart(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        if (intent == null)
            return false;
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        mainIntent.setPackage(context.getPackageName());
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
        return true;
    }

    @SuppressLint("DiscouragedApi")
    public static int getID(String name, String type) {
        try {
            return getApplication().getApplicationContext().getResources().getIdentifier(name, type, getApplication().getPackageName());
        } catch (Exception e) {
            XposedBridge.log("Error while getting ID: " + name + " " + type + " message:" + e);
            return -1;
        }
    }

    public static int dipToPixels(float dipValue) {
        DisplayMetrics metrics = FeatureLoader.mApp.getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    public static String getMyNumber() {
        return FeatureLoader.mApp.getSharedPreferences(FeatureLoader.mApp.getPackageName() + "_preferences_light", Context.MODE_PRIVATE).getString("ph", "");
    }

    public static String getDateTimeFromMillis(long timestamp) {
        return new SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).format(new Date(timestamp));
    }

    @SuppressLint("SdCardPath")
    public static String getDestination(String name) {
        var folder = WppXposed.getPref().getString("download_local", "/sdcard/Download");
        var waFolder = new File(folder, "WhatsApp");
        var filePath = new File(waFolder, name);
        try {
            WppCore.getClientBridge().createDir(filePath.getAbsolutePath());
        } catch (Exception ignored) {
        }
        return filePath.getAbsolutePath() + "/";
    }

    public static String copyFile(File srcFile, File destFile) {
        if (srcFile == null || !srcFile.exists()) return "File not found or is null";

        try (FileInputStream in = new FileInputStream(srcFile);
             var parcelFileDescriptor = WppCore.getClientBridge().openFile(destFile.getAbsolutePath(), true)) {
            var out = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
            byte[] bArr = new byte[1024];
            while (true) {
                int read = in.read(bArr);
                if (read <= 0) {
                    in.close();
                    out.close();
                    Utils.scanFile(destFile);
                    return "";
                }
                out.write(bArr, 0, read);
            }
        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
            return e.getMessage();
        }
    }

    public static void showToast(String message, int length) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Já estamos na thread principal
            Toast.makeText(Utils.getApplication(), message, length).show();
        } else {
            // Não estamos na thread principal, postamos no Handler
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(Utils.getApplication(), message, length).show()
            );
        }
    }

    public static void setToClipboard(String string) {
        ClipboardManager clipboard = (ClipboardManager) Utils.getApplication().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", string);
        clipboard.setPrimaryClip(clip);
    }

    public static String generateName(Object userJid, String fileFormat) {
        var contactName = WppCore.getContactName(userJid);
        var number = WppCore.stripJID(WppCore.getRawString(userJid));
        return toValidFileName(contactName) + "_" + number + "_" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date()) + "." + fileFormat;
    }


    @NonNull
    public static String toValidFileName(@NonNull String input) {
        return input.replaceAll("[:\\\\/*\"?|<>']", " ");
    }

    public static void scanFile(File file) {
        MediaScannerConnection.scanFile(Utils.getApplication(),
                new String[]{file.getAbsolutePath()},
                new String[]{MimeTypeUtils.getMimeTypeFromExtension(file.getAbsolutePath())},
                (s, uri) -> {
                });
    }

    public static Properties extractProperties(String text) {
        Properties properties = new Properties();
        Pattern pattern = Pattern.compile("^/\\*\\s*(.*?)\\s*\\*/", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String propertiesText = matcher.group(1);
            String[] lines = propertiesText.split("\\s*\\n\\s*");

            for (String line : lines) {
                String[] keyValue = line.split("\\s*=\\s*");
                String key = keyValue[0].strip();
                String value = keyValue[1].strip().replaceAll("^\"|\"$", ""); // Remove quotes, if any
                properties.put(key, value);
            }
        }

        return properties;
    }

    public static int tryParseInt(String wallpaperAlpha, int i) {
        try {
            return Integer.parseInt(wallpaperAlpha.trim());
        } catch (Exception e) {
            return i;
        }
    }

    public static Application getApplicationByReflect() {
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            Object app = activityThread.getMethod("getApplication").invoke(thread);
            if (app == null) {
                throw new NullPointerException("u should init first");
            }
            return (Application) app;
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new NullPointerException("u should init first");
    }

    public static <T> T binderLocalScope(BinderLocalScopeBlock<T> block) {
        long identity = Binder.clearCallingIdentity();
        try {
            return block.execute();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public static String getAuthorFromCss(String code) {
        if (code == null) return null;
        var match = Pattern.compile("author\\s*=\\s*(.*?)\n").matcher(code);
        if (!match.find()) return null;
        return match.group(1);
    }

    @FunctionalInterface
    public interface BinderLocalScopeBlock<T> {
        T execute();
    }
}
