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





                        // 1. Try to find the existing "Back up" button directly (e.g. by text)
                        android.view.View backupButton = findBackupButton(rootView);
                        
                        // 2. If not found, look for "Google Account" anchor to guess location
                        if (backupButton == null) {
                            android.view.View anchor = findViewByText(rootView, "Google Account", "Google Drive settings");
                            if (anchor != null) {
                                // Walk up to find the main list item container
                                android.view.View listItem = anchor;
                                while (listItem.getParent() != null && 
                                       listItem.getParent() instanceof ViewGroup && 
                                       !(((ViewGroup)listItem.getParent()) instanceof android.widget.ScrollView)) {
                                    
                                    ViewGroup parent = (ViewGroup) listItem.getParent();
                                    // If we found the specific LinearLayout that holds list items
                                    if (parent instanceof LinearLayout && ((LinearLayout)parent).getOrientation() == LinearLayout.VERTICAL) {
                                        // Check if this parent has many children (likely the main list)
                                        if (parent.getChildCount() > 3) {
                                            // The item *before* the Google Account item is usually the Backup Button directly
                                            int index = parent.indexOfChild(listItem);
                                            if (index > 0) {
                                                android.view.View prev = parent.getChildAt(index - 1);
                                                // Verify if it looks like a button (clickable)
                                                if (prev.isClickable() || (prev instanceof ViewGroup && ((ViewGroup)prev).getChildCount() > 0 && prev.findViewById(android.R.id.button1) != null)) { 
                                                     // heuristic
                                                     backupButton = prev;
                                                } else {
                                                    // Even if we aren't sure, let's target this position
                                                    XposedBridge.log("BackupRestore: Approximated Backup Button position at index " + (index - 1));
                                                    backupButton = prev; 
                                                }
                                            } else {
                                                // If Google Account is first (unlikely), inject before it
                                                backupButton = null; // Can't merge
                                            }
                                        }
                                        break; // Found the container
                                    }
                                    listItem = (android.view.View) parent;
                                }
                            }
                        }

                        if (backupButton != null) {
                            XposedBridge.log("BackupRestore: Targeting View as Backup Button: " + backupButton.getClass().getName());
                            
                            
                            ViewGroup parent = (ViewGroup) backupButton.getParent();
                            if (parent != null) {
                                int index = parent.indexOfChild(backupButton);
                                
                                // Ensure strict LinearLayout logic to prevent overlapping
                                if (parent instanceof LinearLayout || parent.getClass().getName().contains("LinearLayout")) {
                                    // Save original params and ID
                                    ViewGroup.LayoutParams originalParams = backupButton.getLayoutParams();
                                    int originalId = backupButton.getId();
                                    
                                    parent.removeView(backupButton);
                                    
                                    // Helper container
                                    LinearLayout container = new LinearLayout(activity);
                                    container.setId(originalId); // CRITICAL: Inherit ID for Relative/ConstraintLayout
                                    container.setOrientation(LinearLayout.HORIZONTAL);
                                    container.setLayoutParams(originalParams);
                                    
                                    // p1
                                    LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                                    p1.setMargins(0, 0, 8, 0);
                                    backupButton.setLayoutParams(p1);
                                    backupButton.setId(android.view.View.NO_ID); // Clear ID from button so container takes precedence? Or keep it?
                                    // Actually, it's safer to keep ID on container if parent looks for it.
                                    
                                    // p2
                                    android.view.View ourButton = createRestoreButton(activity);
                                    LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                                    p2.setMargins(8, 0, 0, 0);
                                    ourButton.setLayoutParams(p2);
                                    
                                    container.addView(backupButton);
                                    container.addView(ourButton);
                                    parent.addView(container, index);
                                    
                                    XposedBridge.log("BackupRestore: Successfully injected button!");
                                    return;
                                } else {
                                    XposedBridge.log("BackupRestore: Parent is NOT LinearLayout (" + parent.getClass().getSimpleName() + "). Aborting merge to avoid overlap.");
                                }
                            }
                        }
                        
                        // Fallback: Just inject at the bottom of the main scroll container
                        XposedBridge.log("BackupRestore: Attempting Fallback Injection (Bottom of Screen)...");
                        
                        android.widget.ScrollView scrollView = findScrollView(rootView);
                        if (scrollView != null) {
                             XposedBridge.log("BackupRestore: Found ScrollView for fallback injection.");
                             if (scrollView.getChildCount() > 0) {
                                 android.view.View content = scrollView.getChildAt(0);
                                 if (content instanceof LinearLayout && ((LinearLayout)content).getOrientation() == LinearLayout.VERTICAL) {
                                      ViewGroup contentVg = (ViewGroup) content;
                                      
                                      // Create button
                                      android.view.View fallbackButton = createRestoreButton(activity);
                                      
                                      // Layout Params with margins
                                      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                          LinearLayout.LayoutParams.MATCH_PARENT, 
                                          LinearLayout.LayoutParams.WRAP_CONTENT
                                      );
                                      int margin = dpToPx(activity, 16);
                                      lp.setMargins(margin, margin, margin, margin);
                                      fallbackButton.setLayoutParams(lp);
                                      
                                      contentVg.addView(fallbackButton);
                                      XposedBridge.log("BackupRestore: Injected Fallback Button at bottom.");
                                 } else {
                                     XposedBridge.log("BackupRestore: ScrollView child is NOT Vertical LinearLayout. Aborting fallback to avoid overlap.");
                                 }
                             }
                        } else {
                            XposedBridge.log("BackupRestore: Could not find ScrollView for fallback.");
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
    
    private android.widget.ScrollView findScrollView(android.view.View view) {
        if (view instanceof android.widget.ScrollView) return (android.widget.ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.widget.ScrollView sv = findScrollView(vg.getChildAt(i));
                if (sv != null) return sv;
            }
        }
        return null;
    }

    private int dpToPx(android.content.Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
    


    private android.view.View findViewByText(android.view.View view, String... variations) {
        if (view == null) return null;
        
        if (view instanceof android.widget.TextView) {
            CharSequence textCs = ((android.widget.TextView) view).getText();
            if (textCs != null) {
                String text = textCs.toString().trim();
                for (String v : variations) {
                    if (text.equalsIgnoreCase(v) || text.contains(v)) {
                        return view;
                    }
                }
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View found = findViewByText(vg.getChildAt(i), variations);
                if (found != null) return found;
            }
        }
        return null;
    }



    private void showWarningDialog(Activity activity) {
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Warning [Experimental Feature]")
            .setMessage("This feature is experimental and is under development. This may cause all your chat history to be wiped out.\n\nDo it on your own risk.")
            .setPositiveButton("Proceed", (dialog, which) -> showRestoreDialog(activity))
            .setNegativeButton("Cancel", null)
            .show();
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

    private android.view.View findBackupButton(android.view.View view) {
        if (view == null) return null;

        // 1. Check by ID (most reliable)
        try {
            int id = view.getId();
            if (id != android.view.View.NO_ID) {
                String resName = view.getResources().getResourceEntryName(id);
                if (resName != null && (
                    resName.equals("backup_button") || 
                    resName.equals("db_backup_button") || 
                    resName.equals("google_drive_backup_now_btn")
                )) {
                    return view;
                }
            }
        } catch (Throwable t) {
            // Ignore resource errors
        }

        // 2. Check by Text (if TextView)
        if (view instanceof android.widget.TextView) {
            CharSequence textCs = ((android.widget.TextView) view).getText();
            if (textCs != null) {
                String text = textCs.toString().trim();
                // Strict check for "Back up"
                if (text.equalsIgnoreCase("Back up") || 
                    text.equalsIgnoreCase("Back Up") || 
                    text.equalsIgnoreCase("Backup") ||
                    text.equalsIgnoreCase("Fazer backup") || 
                    text.equalsIgnoreCase("Copia de seguridad")
                    ) {
                    
                    // Case A: The TextView itself is the button
                    if (view.isClickable()) {
                         return view;
                    }
                    
                    // Case B: The TextView is inside a button (e.g. WDSButton layout)
                    // Check parent
                    if (view.getParent() instanceof android.view.View) {
                        android.view.View parent = (android.view.View) view.getParent();
                        if (parent.isClickable() || parent.getClass().getName().contains("Button")) {
                            return parent;
                        }
                    }
                }
            }
        }
        
        // 3. Recursive search
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View found = findBackupButton(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private android.view.View createRestoreButton(Activity activity) {
        try {
            Class<?> WDSButtonClass = XposedHelpers.findClass("com.whatsapp.ui.wds.components.button.WDSButton", classLoader);
            Object wdsButton = XposedHelpers.newInstance(WDSButtonClass, activity, null);
            XposedHelpers.callMethod(wdsButton, "setText", "Force Restore");
            XposedHelpers.callMethod(wdsButton, "setOnClickListener", new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    showWarningDialog(activity);
                }
            });
            return (android.view.View) wdsButton;
        } catch (Throwable t) {}
        
        android.widget.Button button = new android.widget.Button(activity);
        button.setText("Force Restore");
        button.setAllCaps(false);
        button.setOnClickListener(v -> showWarningDialog(activity));
        return button;
    }
}
