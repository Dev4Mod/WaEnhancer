package com.wmods.wppenhacer.xposed.permission;

import android.Manifest;
import android.os.Build;

import com.wmods.wppenhacer.xposed.core.FeatureLoader;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AndroidPermissions {

    private static final List<String> newPermissions = List.of(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    private static final List<String> sdk30Permissions = List.of(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.MANAGE_EXTERNAL_STORAGE);
    private static final int sdk = Build.VERSION.SDK_INT;

    public static void hook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!"android".equals(loadPackageParam.packageName)) return;
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            hookPermissionsSdk33(loadPackageParam);
        } else if (sdk >= Build.VERSION_CODES.R) {
            hookPermissionsSdk30(loadPackageParam);
        } else if (sdk == Build.VERSION_CODES.Q) {
            hookPermissions(loadPackageParam, "restorePermissionState");
        } else if (sdk == Build.VERSION_CODES.P) {
            hookPermissions(loadPackageParam, "grantPermissions");
        }
    }

    private static void hookPermissionsSdk30(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        var clazz = XposedHelpers.findClass("com.android.server.pm.permission.PermissionManagerService", loadPackageParam.classLoader);
        XposedBridge.hookAllMethods(clazz, "restorePermissionState", new XC_MethodHook() {

            /**
             * @noinspection unchecked
             */
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object pkg = param.args[0];
                String packageName = (String) XposedHelpers.callMethod(pkg, "getPackageName");
                List<String> permissions = (List<String>) XposedHelpers.getObjectField(param.args[0], "requestedPermissions");
                if (packageName.equals(FeatureLoader.PACKAGE_WPP) || packageName.equals(FeatureLoader.PACKAGE_BUSINESS)) {
                    for (String newPermission : sdk30Permissions) {
                        if (!permissions.contains(newPermission)) {
                            permissions.add(newPermission);
                        }
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object pkg = param.args[0];
                Object mPackageManagerInt = XposedHelpers.getObjectField(param.thisObject, "mPackageManagerInt");
                String packageName = (String) XposedHelpers.callMethod(pkg, "getPackageName");
                Object ps = XposedHelpers.callMethod(mPackageManagerInt, "getPackageSetting", packageName);
                if (ps == null)
                    return;
                Object mSettings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                Object mState = XposedHelpers.callMethod(ps, "getPermissionsState");
                List<String> permissions = (List<String>) XposedHelpers.getObjectField(param.args[0], "requestedPermissions");

                int[] getAllUserIds = (int[]) XposedHelpers.callMethod(param.thisObject, "getAllUserIds");

                for (int userId : getAllUserIds) {
                    if (packageName.equals(FeatureLoader.PACKAGE_WPP) || packageName.equals(FeatureLoader.PACKAGE_BUSINESS)) {

                        for (String newPermission : sdk30Permissions) {
                            if (!permissions.contains(newPermission)) {
                                Object bp = XposedHelpers.callMethod(mSettings, "getPermission", newPermission);
                                XposedHelpers.callMethod(mState, "grantRuntimePermission", bp, userId);
//                                XposedHelpers.callMethod(mState, "grantInstallPermission", bp);
                                XposedBridge.log("Added " + newPermission + " permission to " + packageName);
                            }
                        }
                    }
                }
            }
        });
    }

    private static void hookPermissionsSdk33(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> PermissionManagerService = XposedHelpers.findClass("com.android.server.pm.permission.PermissionManagerServiceImpl", lpparam.classLoader);
        Class<?> AndroidPackage = XposedHelpers.findClass("com.android.server.pm.parsing.pkg.AndroidPackage", lpparam.classLoader);
        Class<?> PermissionCallback = XposedHelpers.findClass("com.android.server.pm.permission.PermissionManagerServiceImpl$PermissionCallback", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(PermissionManagerService, "restorePermissionState",
                AndroidPackage, boolean.class, String.class, PermissionCallback, int.class, new XC_MethodHook() {

                    @SuppressWarnings("unchecked")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                        // params
                        Object pkg = param.args[0];
                        int filterUserId = (int) param.args[4];

                        // obtém os campos
                        Object mState = XposedHelpers.getObjectField(param.thisObject, "mState");
                        Object mRegistry = XposedHelpers.getObjectField(param.thisObject, "mRegistry");
                        Object mPackageManagerInt = XposedHelpers.getObjectField(param.thisObject, "mPackageManagerInt");

                        // Continua ?
                        String packageName = (String) XposedHelpers.callMethod(pkg, "getPackageName");
                        Object ps = XposedHelpers.callMethod(mPackageManagerInt, "getPackageStateInternal", packageName);
                        if (ps == null)
                            return;

                        int[] getAllUserIds = (int[]) XposedHelpers.callMethod(param.thisObject, "getAllUserIds");
                        int userHandle_USER_ALL = XposedHelpers.getStaticIntField(Class.forName("android.os.UserHandle"), "USER_ALL");
                        final int[] userIds = filterUserId == userHandle_USER_ALL ? getAllUserIds : new int[]{filterUserId};

                        for (int userId : userIds) {

                            List<String> requestedPermissions;
                            Object userState = XposedHelpers.callMethod(mState, "getOrCreateUserState", userId);
                            int appId = (int) XposedHelpers.callMethod(ps, "getAppId");
                            Object uidState = XposedHelpers.callMethod(userState, "getOrCreateUidState", appId);

                            // package 1
                            if (packageName.equals(FeatureLoader.PACKAGE_WPP) || packageName.equals(FeatureLoader.PACKAGE_BUSINESS)) {

                                requestedPermissions = (List<String>) XposedHelpers.callMethod(pkg, "getRequestedPermissions");
                                for (String newPermission : sdk30Permissions) {
                                    grantInstallOrRuntimePermission(requestedPermissions, uidState, mRegistry, newPermission);
                                }
                            }
                        }
                    }
                });
    }

    private static void hookPermissions(XC_LoadPackage.LoadPackageParam loadPackageParam, String method) {
        var clazz = XposedHelpers.findClass("com.android.server.pm.permission.PermissionManagerService", loadPackageParam.classLoader);
        XposedBridge.hookAllMethods(clazz, method, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
                if (pkgName.equals(FeatureLoader.PACKAGE_WPP) || pkgName.equals(FeatureLoader.PACKAGE_BUSINESS)) {
                    List<String> permissions = (List<String>) XposedHelpers.getObjectField(param.args[0], "requestedPermissions");
                    if (sdk >= Build.VERSION_CODES.R) {
                        for (String newPermission : sdk30Permissions) {
                            if (!permissions.contains(newPermission)) {
                                permissions.add(newPermission);
                                XposedBridge.log("Added " + newPermission + " permission to " + pkgName);
                            }
                        }
                    } else {
                        for (String newPermission : newPermissions) {
                            if (!permissions.contains(newPermission)) {
                                permissions.add(newPermission);
                                XposedBridge.log("Added " + newPermission + " permission to " + pkgName);
                            }
                        }
                    }
                }
            }
        });
    }


    private static void grantInstallOrRuntimePermission
            (List<String> requestedPermissions, Object uidState,
             Object registry, String permission) {

        if (!requestedPermissions.contains(permission))
            XposedHelpers.callMethod(uidState, "grantPermission",
                    XposedHelpers.callMethod(registry, "getPermission", permission));

    }

}
