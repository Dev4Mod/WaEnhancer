package com.wmods.wppenhacer.xposed.features.others;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

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
        var hooked = new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = XposedHelpers.callMethod(param.thisObject, "getFMessage");
                var userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
                var methodResult = ReflectionUtils.findMethodUsingFilter(fMessage.getClass(), method -> method.getReturnType() == userJidClass && method.getParameterCount() == 0);
                var userJid = ReflectionUtils.callMethod(methodResult, fMessage);
                var chatCurrentJid = WppCore.getCurrentRawJID();
                if (!WppCore.isGroup(chatCurrentJid)) return;
                var field = ReflectionUtils.getFieldByType(param.thisObject.getClass(), grpcheckAdmin.getDeclaringClass());
                var grpParticipants = field.get(param.thisObject);
                var jidGrp = jidFactory.invoke(null, chatCurrentJid);
                var result = ReflectionUtils.callMethod(grpcheckAdmin, grpParticipants, jidGrp, userJid);
                var view = (View) param.thisObject;
                var context = view.getContext();
                ImageView iconAdmin;
                if ((iconAdmin = view.findViewById(0x7fff0010)) == null) {
                    var nameGroup = (LinearLayout) view.findViewById(Utils.getID("name_in_group", "id"));
                    var view1 = new LinearLayout(context);
                    view1.setOrientation(LinearLayout.HORIZONTAL);
                    view1.setGravity(Gravity.CENTER_VERTICAL);
                    var nametv = nameGroup.getChildAt(0);
                    iconAdmin = new ImageView(context);
                    var size = Utils.dipToPixels(16);
                    iconAdmin.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                    iconAdmin.setImageResource(ResId.drawable.admin);
                    iconAdmin.setId(0x7fff0010);
                    nameGroup.removeView(nametv);
                    view1.addView(nametv);
                    view1.addView(iconAdmin);
                    nameGroup.addView(view1, 0);
                }
                iconAdmin.setVisibility(result != null && (boolean) result ? View.VISIBLE : View.GONE);
            }
        };
        XposedBridge.hookMethod(grpAdmin1, hooked);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}
