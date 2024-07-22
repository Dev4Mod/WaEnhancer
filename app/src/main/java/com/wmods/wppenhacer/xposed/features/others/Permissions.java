package com.wmods.wppenhacer.xposed.features.others;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class Permissions extends Feature {

    private static final int REQUEST_PERMISSION = 852582;
    private static final int REQUEST_FOLDER = 852583;


    public Permissions(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static void showDialogUriPermission(Activity activity) {
        new AlertDialogWpp(activity).setTitle("Permissions")
                .setMessage(activity.getString(ResId.string.uri_permission))
                .setPositiveButton(activity.getString(ResId.string.allow), (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDownloadsUri());
                    activity.startActivityForResult(intent, REQUEST_FOLDER);
                }).setNegativeButton(activity.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss()).show();
    }

    private static Uri getDownloadsUri() {
        return Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
    }

    @Override
    public void doHook() throws Throwable {
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_PERMISSION);
                    }
                }
                var wae = WppCore.getPrivString("folder_wae", null);
                if (wae == null || !isUriPermissionGranted(activity, Uri.parse(wae))) {
                    showDialogUriPermission(activity);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                var activity = (Activity) param.thisObject;
                if ((int) param.args[0] == REQUEST_FOLDER && (int) param.args[1] == Activity.RESULT_OK) {
                    var uri = (Uri) ((Intent) param.args[2]).getData();
                    if (uri.getPath().endsWith("Download/WaEnhancer")) {
                        WppCore.setPrivString("folder_wae", uri.toString());
                        activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        Utils.doRestart(activity);
                    } else {
                        showDialogUriPermission(activity);
                        Utils.showToast(activity.getString(ResId.string.invalid_folder), Toast.LENGTH_LONG);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onRequestPermissionsResult", int.class, String[].class, int[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                var activity = (Activity) param.thisObject;
                if ((int) param.args[0] == REQUEST_PERMISSION) {
                    var results = (int[]) param.args[2];
                    if (Arrays.stream(results).anyMatch(result -> result != PackageManager.PERMISSION_GRANTED)) {
                        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null)));
                        Utils.showToast(activity.getString(ResId.string.grant_permission), Toast.LENGTH_LONG);
                    }
                }
            }
        });
    }

    private boolean isUriPermissionGranted(Context context, Uri uri) {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        int permissionCheck = context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), takeFlags);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "";
    }
}
