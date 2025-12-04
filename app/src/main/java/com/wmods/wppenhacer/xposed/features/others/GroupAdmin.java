package com.wmods.wppenhacer.xposed.features.others;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class GroupAdmin extends Feature {

    public GroupAdmin(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("admin_grp", false)) return;
        var jidFactory = Unobfuscator.loadJidFactory(classLoader);
        var grpAdmin1 = Unobfuscator.loadGroupAdminMethod(classLoader);
        var grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader);
        // WaEnhancer: defensive wrapper added
        var hooked = new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object targetObj = null;
                    try {
                        targetObj = param.thisObject != null ? param.thisObject : (param.args != null && param.args.length > 1 ? param.args[1] : null);
                    } catch (Throwable ignore) { }

                    if (targetObj == null || !(targetObj instanceof View)) {
                        return; // nothing to modify safely
                    }

                    try {
                        // --- BEGIN original logic ---
                        setupUsernameInGroupViewContainer((View) targetObj, jidFactory, grpcheckAdmin);
                        // --- END original logic ---
                    } catch (Throwable t) {
                        XposedBridge.log("WaEnhancer: safeHook inner error in setupUsernameInGroupViewContainer hook: " + t);
                    }

                } catch (Throwable outer) {
                    XposedBridge.log("WaEnhancer: safeHook outer error: " + outer);
                }
            }
        };
        XposedBridge.hookMethod(grpAdmin1, hooked);
    }

    @SuppressLint("ResourceType")
    private void setupUsernameInGroupViewContainer(View container, java.lang.reflect.Method jidFactory, java.lang.reflect.Method grpcheckAdmin) {
        // WaEnhancer: defensive wrapper added
        try {
            if (container == null) {
                XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer skipped: container is null");
                return;
            }

            // Safely extract fMessage
            Object fMessageObj = null;
            try {
                fMessageObj = XposedHelpers.callMethod(container, "getFMessage");
            } catch (Throwable tGetMsg) {
                XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer getFMessage() threw: " + tGetMsg);
                return;
            }

            if (fMessageObj == null) {
                XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer skipped: fMessageObj is null");
                return;
            }

            // --- ORIGINAL METHOD BODY START ---
            try {
                var fMessage = new FMessageWpp(fMessageObj);
                var userJid = fMessage.getUserJid();

                // Defensive: check chatCurrentJid for null
                FMessageWpp.UserJid chatCurrentJid = null;
                try {
                    chatCurrentJid = WppCore.getCurrentUserJid();
                } catch (Throwable tJid) {
                    XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer getCurrentUserJid() threw: " + tJid);
                    return;
                }

                if (chatCurrentJid == null) {
                    XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer skipped: chatCurrentJid is null");
                    return;
                }

                // Defensive: check isGroup() for null
                Boolean isGroup = null;
                try {
                    isGroup = chatCurrentJid.isGroup();
                } catch (Throwable tIsGroup) {
                    XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer isGroup() threw: " + tIsGroup);
                    return;
                }

                if (isGroup == null || !isGroup) {
                    return;
                }

                var field = ReflectionUtils.getFieldByType(container.getClass(), grpcheckAdmin.getDeclaringClass());
                var grpParticipants = field.get(container);
                var jidGrp = jidFactory.invoke(null, chatCurrentJid.getUserRawString());
                var result = grpcheckAdmin.invoke(grpParticipants, jidGrp, userJid.userJid);
                var context = container.getContext();
                ImageView iconAdmin;
                if ((iconAdmin = container.findViewById(0x7fff0010)) == null) {
                    var nameGroup = container.findViewById(Utils.getID("name_in_group", "id"));
                    if (nameGroup == null || !(nameGroup instanceof LinearLayout)) {
                        XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer - nameGroup not found or invalid type");
                        return;
                    }
                    var nameGroupLayout = (LinearLayout) nameGroup;
                    var view1 = new LinearLayout(context);
                    view1.setOrientation(LinearLayout.HORIZONTAL);
                    view1.setGravity(Gravity.CENTER_VERTICAL);
                    var nametv = nameGroupLayout.getChildAt(0);
                    iconAdmin = new ImageView(context);
                    var size = Utils.dipToPixels(16);
                    iconAdmin.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                    iconAdmin.setImageResource(ResId.drawable.admin);
                    iconAdmin.setId(0x7fff0010);
                    nameGroupLayout.removeView(nametv);
                    view1.addView(nametv);
                    view1.addView(iconAdmin);
                    nameGroupLayout.addView(view1, 0);
                }
                iconAdmin.setVisibility(result != null && (boolean) result ? View.VISIBLE : View.GONE);
            } catch (Throwable inner) {
                XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer inner caught: " + inner);
            }
            // --- ORIGINAL METHOD BODY END ---

        } catch (Throwable outer) {
            XposedBridge.log("WaEnhancer: setupUsernameInGroupViewContainer outer caught: " + outer);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}
