package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BackupRestore extends Feature {
    public BackupRestore(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Backup Restore";
    }

    // static flag to control the hook
    public static boolean sForceCloudCheck = false;

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("force_restore_backup_feature", false)) return;

        // Plan O: The Golden Path (Discovered via Spy Mode on 2026-02-09)
        // Inject button into Settings -> Chats -> Chat backup screen
        XposedBridge.log("BackupRestore: Plan O (Golden Path) - Button Injection Mode");

        var SettingsGoogleDrive = XposedHelpers.findClass("com.whatsapp.backup.google.SettingsGoogleDrive", classLoader);

        XposedBridge.hookAllMethods(SettingsGoogleDrive, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                
                // Post to UI thread to ensure all views are inflated
                activity.runOnUiThread(() -> {
                    try {
                        XposedBridge.log("BackupRestore: Injecting Force Restore button...");

                        // Find the root layout - try multiple IDs
                        ViewGroup rootView = null;
                        int[] possibleRootIds = {
                            android.R.id.content,
                            activity.getResources().getIdentifier("action_bar_root", "id", "com.whatsapp"),
                            activity.getResources().getIdentifier("content_frame", "id", "com.whatsapp")
                        };
                        
                        for (int id : possibleRootIds) {
                            if (id != 0) {
                                android.view.View view = activity.findViewById(id);
                                if (view instanceof ViewGroup) {
                                    rootView = (ViewGroup) view;
                                    XposedBridge.log("BackupRestore: Found root using ID: " + id);
                                    break;
                                }
                            }
                        }
                        
                        if (rootView == null) {
                            XposedBridge.log("BackupRestore: Failed to find root view");
                            return;
                        }

                        // Find the deepest LinearLayout or ScrollView to add the button to
                        ViewGroup targetContainer = findBestContainer(rootView);
                        XposedBridge.log("BackupRestore: Target container: " + targetContainer.getClass().getSimpleName());

                        // Create the button (try WDSButton first, fallback to standard button)
                        android.view.View button = createRestoreButton(activity);
                        
                        if (button != null) {
                            // Add button to the container
                            targetContainer.addView(button);
                            XposedBridge.log("BackupRestore: Button added successfully to " + targetContainer.getClass().getSimpleName());
                        }
                        
                    } catch (Throwable t) {
                        XposedBridge.log("BackupRestore: Error in UI thread: " + t.getMessage());
                        t.printStackTrace();
                    }
                });
            }
        });

        // Hook File.exists() to hide local backup when forcing cloud mode
        try {
            XposedHelpers.findAndHookMethod(java.io.File.class, "exists", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sForceCloudCheck) {
                        java.io.File file = (java.io.File) param.thisObject;
                        String path = file.getAbsolutePath();
                        // Check if it's looking for the message store
                        if (path.contains("msgstore") && (path.endsWith(".db") || path.contains(".crypt"))) {
                             XposedBridge.log("BackupRestore: Blocking visibility of local backup: " + file.getName());
                             param.setResult(false);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BackupRestore: Failed to hook File.exists: " + t.getMessage());
        }

        // Re-enable Anti-Finish Hook (with loop protection)
        // This is needed because even if we hide the local file, the Activity might try to finish() 
        // if it detects it's not a "fresh install" via other means (shared prefs, etc).
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "finish", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!param.thisObject.getClass().getName().contains("RestoreFromBackupActivity")) {
                        return;
                    }

                    Activity activity = (Activity) param.thisObject;
                    Intent intent = activity.getIntent();
                    
                    // Only block finish if we are in "Force Mode" (is_new_user + sForceCloudCheck)
                    // We check sForceCloudCheck to ensure we only block it during the initial launch phase
                    if (sForceCloudCheck || (intent != null && intent.getBooleanExtra("is_new_user", false))) {
                         XposedBridge.log("BackupRestore: RestoreFromBackupActivity tried to finish(), but we blocked it!");
                         
                         // Debug: Print stack trace to see WHY it's finishing
                         // for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                         //    XposedBridge.log("   " + ste.toString());
                         // }
                         
                         param.setResult(null); // Prevent finish()
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BackupRestore: Failed to hook Activity.finish: " + t.getMessage());
        }
    }
    
    private ViewGroup findBestContainer(ViewGroup root) {
        // Recursively find the best container (deepest LinearLayout or main content container)
        ViewGroup best = root;
        
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                // Prefer LinearLayout with vertical orientation
                if (vg instanceof LinearLayout && 
                    ((LinearLayout) vg).getOrientation() == LinearLayout.VERTICAL &&
                    vg.getChildCount() > 0) {
                    best = vg;
                }
                // Recurse
                ViewGroup deeper = findBestContainer(vg);
                if (deeper != root && deeper.getChildCount() > 0) {
                    best = deeper;
                }
            }
        }
        
        return best;
    }
    
    // ... createRestoreButton ...
    private android.view.View createRestoreButton(Activity activity) {
        // ... (Keep existing implementation)
        // Just correcting the one we viewed earlier
        try {
            Class<?> WDSButtonClass = XposedHelpers.findClass("com.whatsapp.ui.wds.components.button.WDSButton", classLoader);
            Object wdsButton = XposedHelpers.newInstance(WDSButtonClass, activity, null);
            XposedHelpers.callMethod(wdsButton, "setText", "Force Restore Backup (Experimental)");
            
            XposedHelpers.callMethod(wdsButton, "setOnClickListener", new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    showRestoreDialog(activity);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            int dp16 = (int) (16 * activity.getResources().getDisplayMetrics().density);
            params.setMargins(dp16, dp16, dp16, dp16);
            ((android.view.View) wdsButton).setLayoutParams(params);
            return (android.view.View) wdsButton;
            
        } catch (Throwable t) {}
        
        // Fallback
        android.widget.Button button = new android.widget.Button(activity);
        button.setText("Force Restore Backup (Experimental)");
        button.setAllCaps(false);
        button.setOnClickListener(v -> showRestoreDialog(activity));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int dp16 = (int) (16 * activity.getResources().getDisplayMetrics().density);
        params.setMargins(dp16, dp16, dp16, dp16);
        button.setLayoutParams(params);
        return button;
    }

    private void showRestoreDialog(Activity activity) {
        String[] options = {"Local / Auto Restore (Standard)", "Force Google Drive Download (Wizard)"};
        
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Select Restore Method")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    launchLocalRestore(activity);
                } else {
                    launchGoogleDriveRestore(activity);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void launchLocalRestore(Activity activity) {
        // Disable hook
        sForceCloudCheck = false;
        
        try {
            Intent intent = new Intent();
            intent.setClassName(activity.getPackageName(), "com.whatsapp.backup.google.RestoreFromBackupActivity");
            intent.setAction("action_show_restore_one_time_setup");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            XposedBridge.log("BackupRestore: Launched Local/Auto Restore");
        } catch (Throwable t) {
            XposedBridge.log("BackupRestore: Local Restore failed: " + t.getMessage());
            android.widget.Toast.makeText(activity, "Failed to launch local restore", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGoogleDriveRestore(Activity activity) {
        // Enable hook to hide local files!
        sForceCloudCheck = true;
        
        // Schedule disable after 15 seconds (safety net)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            sForceCloudCheck = false;
            XposedBridge.log("BackupRestore: Force Cloud Check disabled (timeout)");
        }, 15000);

        try {
            String packageName = activity.getPackageName();
            Intent intent = new Intent();
            intent.setClassName(packageName, "com.whatsapp.backup.google.RestoreFromBackupActivity");
            intent.setAction("action_show_restore_one_time_setup");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Still pass is_new_user just in case
            intent.putExtra("is_new_user", true);

            activity.startActivity(intent);
            XposedBridge.log("BackupRestore: Launched RestoreFromBackupActivity with BLOCKED local files");
            
            activity.runOnUiThread(() -> 
                android.widget.Toast.makeText(activity, "Forcing Cloud Check (Local Hidden)...", android.widget.Toast.LENGTH_SHORT).show()
            );
            
        } catch (Throwable t) {
            XposedBridge.log("BackupRestore: Force Restore failed: " + t.getMessage());
            sForceCloudCheck = false; // Reset on fail
            android.widget.Toast.makeText(activity, "Failed to launch Force Restore", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
}
