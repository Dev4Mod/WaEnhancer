package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.TextUtilsCompat;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShowOnline extends Feature {

    private Object mStatusUser;
    private Object mInstancePresence;

    public ShowOnline(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var showOnlineText = prefs.getBoolean("showonlinetext", false);
        var showOnlineIcon = prefs.getBoolean("dotonline", false);
        if (!showOnlineText && !showOnlineIcon) return;

        var classViewHolder = Unobfuscator.loadViewHolder(classLoader);
        XposedBridge.hookAllConstructors(classViewHolder, new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.args[1];
                var context = (Context) param.args[0];
                LinearLayout content = view.findViewById(Utils.getID("conversations_row_content", "id"));
                if (content == null) {
                    content = view.findViewById(Utils.getID("row_content", "id"));
                }
                if (showOnlineText) {
                    var linearLayout = new LinearLayout(context);
                    linearLayout.setGravity(Gravity.END | Gravity.TOP);
                    content.addView(linearLayout);

                    // Add TextView to show last seen time
                    TextView lastSeenText = new TextView(context);
                    lastSeenText.setId(0x7FFF0002);
                    lastSeenText.setTextSize(12f);
                    lastSeenText.setText("");
                    lastSeenText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
                    lastSeenText.setGravity(Gravity.CENTER_VERTICAL);
                    lastSeenText.setVisibility(View.VISIBLE);
                    linearLayout.addView(lastSeenText);
                }
                if (showOnlineIcon) {
                    var contactView = (FrameLayout) view.findViewById(Utils.getID("contact_selector", "id"));
                    var photoView = (ImageView) contactView.getChildAt(0);
                    contactView.removeView(photoView);

                    var relativeLayout = new RelativeLayout(context);
                    relativeLayout.setId(0x7FFF0003);
                    var params = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.CENTER_IN_PARENT);
                    photoView.setLayoutParams(params);
                    relativeLayout.addView(photoView);
                    contactView.addView(relativeLayout);
                    var isLeftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR;

                    var imageView = new ImageView(context);
                    imageView.setId(0x7FFF0001);
                    var params2 = new RelativeLayout.LayoutParams(Utils.dipToPixels(14), Utils.dipToPixels(14));
                    params2.addRule(RelativeLayout.ALIGN_TOP, contactView.getId());
                    params2.addRule(isLeftToRight ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, photoView.getId());
                    params2.topMargin = Utils.dipToPixels(5);
                    imageView.setLayoutParams(params2);
                    imageView.setImageResource(ResId.drawable.online);
                    imageView.setAdjustViewBounds(true);
                    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                    imageView.setVisibility(View.INVISIBLE);
                    relativeLayout.addView(imageView);
                }
            }
        });

        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus));
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(field1));
        var getStatusUser = Unobfuscator.loadStatusUserMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getStatusUser));
        var sendPresenceMethod = Unobfuscator.loadSendPresenceMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(sendPresenceMethod));
        var tcTokenMethod = Unobfuscator.loadTcTokenMethod(classLoader);
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);


        XposedBridge.hookAllConstructors(getStatusUser.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mStatusUser = param.thisObject;
            }
        });

        XposedBridge.hookAllConstructors(sendPresenceMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mInstancePresence = param.thisObject;
            }
        });

        // load methods
        var tokenClass = sendPresenceMethod.getParameterTypes()[2];
        var fieldTokenDBInstance = ReflectionUtils.getFieldByExtendType(sendPresenceMethod.getDeclaringClass(), tcTokenMethod.getDeclaringClass());


        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            @SuppressLint("ResourceType")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var object = param.args[0];
                var viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
                var view = (View) viewField.get(viewHolder);

                var getAdapterPositionMethod = ReflectionUtils.findMethodUsingFilter(absViewHolderClass, method -> method.getParameterCount() == 0 && method.getReturnType() == int.class);
                var position = (int) ReflectionUtils.callMethod(getAdapterPositionMethod, viewHolder);
                ImageView csDot = showOnlineIcon ? view.findViewById(0x7FFF0003).findViewById(0x7FFF0001) : null;
                if (showOnlineIcon) {
                    csDot.setVisibility(View.INVISIBLE);
                }
                TextView lastSeenText = showOnlineText ? view.findViewById(0x7FFF0002) : null;
                var jidFiled = ReflectionUtils.getFieldByExtendType(object.getClass(), XposedHelpers.findClass("com.whatsapp.jid.Jid", classLoader));
                var jidObject = jidFiled.get(object);
                var jid = WppCore.getRawString(jidObject);
                if (WppCore.isGroup(jid)) return;

                var tokenDBInstance = fieldTokenDBInstance.get(mInstancePresence);
                var tokenData = ReflectionUtils.callMethod(tcTokenMethod, tokenDBInstance, jidObject);
                var tokenObj = tokenClass.getConstructors()[0].newInstance(tokenData == null ? null : XposedHelpers.getObjectField(tokenData, "A01"));
                sendPresenceMethod.invoke(null, jidObject, null, tokenObj, mInstancePresence);

                var status = (String) ReflectionUtils.callMethod(getStatusUser, mStatusUser, object, false);
                var currentPosition = (int) ReflectionUtils.callMethod(getAdapterPositionMethod, viewHolder);
                if (currentPosition != position) return;
                if (!TextUtils.isEmpty(status) && status.trim().equals(UnobfuscatorCache.getInstance().getString("online"))) {
                    if (csDot != null) {
                        csDot.setVisibility(View.VISIBLE);
                    }
                }

                if (lastSeenText != null) {
                    if (!TextUtils.isEmpty(status)) {
                        lastSeenText.setText(status);
                        if (UnobfuscatorCache.getInstance().getString("online").equals(status)) {
                            lastSeenText.setTextColor(Color.GREEN);
                        } else {
                            lastSeenText.setTextColor(0xffcac100);
                        }
                    } else {
                        lastSeenText.setText("");
                        lastSeenText.setTextColor(Color.GRAY);
                    }
                }

            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Conversation";
    }
}
