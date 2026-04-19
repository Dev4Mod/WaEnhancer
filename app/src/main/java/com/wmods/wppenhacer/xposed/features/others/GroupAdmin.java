package com.wmods.wppenhacer.xposed.features.others;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XSharedPreferences;

public class GroupAdmin extends Feature {

    public GroupAdmin(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("admin_grp", false)) return;

        var jidFactory = Unobfuscator.loadJidFactory(classLoader);
        var grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader);

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup view, int position, View convertView) throws Throwable {
                var chatCurrentJid = WppCore.getCurrentUserJid();
                if (chatCurrentJid == null || !chatCurrentJid.isGroup()) return;
                var grpcheckAdminClass = grpcheckAdmin.getDeclaringClass();
                var field = ReflectionUtils.findFieldUsingFilter(view.getClass(), f -> f.getType().isAssignableFrom(grpcheckAdminClass));
                field.setAccessible(true);
                var grpParticipants = field.get(view);
                var context = view.getContext();
                ImageView iconAdmin;
                if ((iconAdmin = view.findViewWithTag("admin_icon")) == null) {
                    var nameGroup = (LinearLayout) view.findViewById(Utils.getID("name_in_group", "id"));
                    if (nameGroup == null) return;
                    var view1 = new LinearLayout(context);
                    view1.setOrientation(LinearLayout.HORIZONTAL);
                    view1.setGravity(Gravity.CENTER_VERTICAL);
                    var nametv = nameGroup.getChildAt(0);
                    iconAdmin = new ImageView(context);
                    var size = Utils.dipToPixels(16);
                    iconAdmin.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                    iconAdmin.setImageResource(ResId.drawable.admin);
                    iconAdmin.setTag("admin_icon");
                    nameGroup.removeView(nametv);
                    view1.addView(nametv);
                    view1.addView(iconAdmin);
                    nameGroup.addView(view1, 0);
                }

                var groupRawJid = chatCurrentJid.getPhoneRawString();
                if (groupRawJid == null) {
                    iconAdmin.setVisibility(View.GONE);
                    return;
                }

                var jidGrp = jidFactory.invoke(null, groupRawJid);
                var participantJid = resolveParticipantJidForAdminCheck(fMessage.getUserJid(), grpcheckAdmin);
                if (participantJid == null) {
                    iconAdmin.setVisibility(View.GONE);
                    return;
                }

                var result = grpcheckAdmin.invoke(grpParticipants, jidGrp, participantJid);
                iconAdmin.setVisibility(result != null && (boolean) result ? View.VISIBLE : View.GONE);
            }
        });

    }

    private Object resolveParticipantJidForAdminCheck(FMessageWpp.UserJid userJid, java.lang.reflect.Method grpcheckAdmin) {
        if (userJid == null) return null;
        var expectedType = grpcheckAdmin.getParameterTypes()[1];
        if (userJid.userJid != null && expectedType.isInstance(userJid.userJid)) {
            return userJid.userJid;
        }
        if (userJid.phoneJid != null && expectedType.isInstance(userJid.phoneJid)) {
            return userJid.phoneJid;
        }
        if (userJid.userJid != null) {
            return userJid.userJid;
        }
        if (userJid.phoneJid != null) {
            return userJid.phoneJid;
        }
        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}