package com.wmods.wppenhacer.xposed.features.general;


import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeenTick extends Feature {

    private static final ArraySet<MessageInfo> messages = new ArraySet<>();
    private static Object mWaJobManager;
    private static Field fieldMessageKey;
    private static Class<?> mSendReadClass;
    private static Method WaJobManagerMethod;
    private static String currentJid;

    public SeenTick(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {


        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));

        fieldMessageKey = Unobfuscator.loadMessageKeyField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(fieldMessageKey));

        var messageSendClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendE2EMessageJob", loader);


        WaJobManagerMethod = Unobfuscator.loadBlueOnReplayWaJobManagerMethod(loader);

        var messageJobMethod = Unobfuscator.loadBlueOnReplayMessageJobMethod(loader);

        mSendReadClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", loader);


        WppCore.addListenerChat((conv, type) -> {
            var jid = WppCore.getCurrentRawJID();
            if (!Objects.equals(jid, currentJid)) {
                currentJid = jid;
                XposedBridge.log("Changed Start");
                messages.clear();
            }
        });

        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var objMessage = param.args[2];
                var fieldMessageDetails = XposedHelpers.getObjectField(objMessage, fieldMessageKey.getName());
                String messageKey = (String) XposedHelpers.getObjectField(fieldMessageDetails, "A01");
                if (XposedHelpers.getBooleanField(fieldMessageDetails, "A02")) return;
                messages.add(new MessageInfo(objMessage, messageKey));
            }
        });

        XposedBridge.hookMethod(messageJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!messageSendClass.isInstance(param.thisObject)) return;
                if (!prefs.getBoolean("blueonreply", false)) return;
                new Handler(Looper.getMainLooper()).post(() -> sendBlueTick((String) XposedHelpers.getObjectField(messageSendClass.cast(param.thisObject), "jid")));
            }
        });

        XposedBridge.hookAllConstructors(WaJobManagerMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mWaJobManager = param.thisObject;
            }
        });

        // Send Seen functions

        var ticktype = Integer.parseInt(prefs.getString("seentick", "0"));
        if (ticktype == 0) return;

        var onCreateMenuConversationMethod = Unobfuscator.loadBlueOnReplayCreateMenuConversationMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateMenuConversationMethod));
        XposedBridge.hookMethod(onCreateMenuConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("hideread", false))
                    return;

                var menu = (Menu) param.args[0];
                var menuItem = menu.add(0, 0, 0, ResId.string.send_blue_tick);
                if (ticktype == 1) menuItem.setShowAsAction(2);
                menuItem.setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                menuItem.setOnMenuItemClickListener(item -> {
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(Utils.getApplication(), "Sending read blue tick..", Toast.LENGTH_SHORT).show());
                    sendBlueTick(currentJid);
                    return true;
                });
            }
        });

        var setPageActiveMethod = Unobfuscator.loadStatusActivePage(loader);
        logDebug(Unobfuscator.getMethodDescriptor(setPageActiveMethod));
        var fieldList = Unobfuscator.getFieldByType(setPageActiveMethod.getDeclaringClass(), List.class);
        XposedBridge.hookMethod(setPageActiveMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var position = (int) param.args[1];
                var list = (List<?>) XposedHelpers.getObjectField(param.args[0], fieldList.getName());
                var message = list.get(position);
                var messageKeyObject = XposedHelpers.getObjectField(message, fieldMessageKey.getName());
                var messageKey = (String) XposedHelpers.getObjectField(messageKeyObject, "A01");
                var userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", loader);
                var userJidMethod = Arrays.stream(fieldMessageKey.getDeclaringClass().getDeclaredMethods()).filter(m -> m.getReturnType().equals(userJidClass)).findFirst().orElse(null);
                var userJid = XposedHelpers.callMethod(message, userJidMethod.getName());
                var jid = WppCore.getRawString(userJid);
                messages.clear();
                messages.add(new MessageInfo(message, messageKey));
                currentJid = jid;
            }
        });



        var viewButtonMethod = Unobfuscator.loadBlueOnReplayViewButtonMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(viewButtonMethod));

        if (ticktype == 1) {
            XposedBridge.hookMethod(viewButtonMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean("hidestatusview", false)) return;
                    var view = (View) param.getResult();
                    var contentView = (LinearLayout) view.findViewById(Utils.getID("bottom_sheet", "id"));
                    var infoBar = contentView.findViewById(Utils.getID("info", "id"));
                    var buttonImage = new ImageView(view.getContext());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Utils.dipToPixels(32), Utils.dipToPixels(32));
                    params.gravity = Gravity.CENTER_VERTICAL;
                    params.setMargins(Utils.dipToPixels(5), Utils.dipToPixels(5), 0, 0);
                    buttonImage.setLayoutParams(params);
                    buttonImage.setImageResource(Utils.getID("ic_notif_mark_read", "drawable"));
                    GradientDrawable border = new GradientDrawable();
                    border.setShape(GradientDrawable.RECTANGLE);
                    border.setStroke(1, Color.WHITE);
                    border.setCornerRadius(20);
                    border.setColor(Color.parseColor("#80000000"));
                    buttonImage.setBackground(border);
                    view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        if (infoBar.getVisibility() != View.VISIBLE) {
                            if (contentView.getChildAt(0) == buttonImage) return;
                            contentView.setOrientation(LinearLayout.HORIZONTAL);
                            contentView.addView(buttonImage, 0);
                        }
                    });
                    buttonImage.setOnClickListener(v -> {
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(view.getContext(), ResId.string.sending_read_blue_tick, Toast.LENGTH_SHORT).show());
                        sendBlueTickStatus(currentJid);
                    });
                }
            });

        } else {
            var mediaClass = Unobfuscator.loadStatusDownloadMediaClass(loader);
            logDebug("Media class: " + mediaClass.getName());
            var menuStatusClass = Unobfuscator.loadMenuStatusClass(loader);
            logDebug("MenuStatus class: " + menuStatusClass.getName());
            var clazzSubMenu = Unobfuscator.loadStatusDownloadSubMenuClass(loader);
            logDebug("SubMenu class: " + clazzSubMenu.getName());
            var clazzMenu = Unobfuscator.loadStatusDownloadMenuClass(loader);
            logDebug("Menu class: " + clazzMenu.getName());
            var menuField = Unobfuscator.getFieldByType(clazzSubMenu, clazzMenu);
            logDebug("Menu field: " + menuField.getName());
            XposedHelpers.findAndHookMethod(menuStatusClass, "onClick", View.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Field subMenuField = Arrays.stream(param.thisObject.getClass().getDeclaredFields()).filter(f -> f.getType() == Object.class && clazzSubMenu.isInstance(XposedHelpers.getObjectField(param.thisObject, f.getName()))).findFirst().orElse(null);
                    Object submenu = XposedHelpers.getObjectField(param.thisObject, subMenuField.getName());
                    var menu = (Menu) XposedHelpers.getObjectField(submenu, menuField.getName());
                    if (menu.findItem(ResId.string.send_blue_tick) != null) return;
                    MenuItem item = menu.add(0, ResId.string.send_blue_tick, 0, ResId.string.send_blue_tick);
                    item.setOnMenuItemClickListener(item1 -> {
                        sendBlueTickStatus(currentJid);
                        Toast.makeText(Utils.getApplication(), ResId.string.sending_read_blue_tick, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                }
            });

        }

        /// Add button to send View Once to Target
        var menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(menuMethod));
        var menuIntField = Unobfuscator.loadViewOnceDownloadMenuField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(menuIntField));
        var classThreadMessage = Unobfuscator.loadThreadMessageClass(loader);

        XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
            @Override
            @SuppressLint("DiscouragedApi")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (XposedHelpers.getIntField(param.thisObject, menuIntField.getName()) == 3) {
                    Menu menu = (Menu) param.args[0];
                    MenuItem item = menu.add(0, 0, 0, ResId.string.send_blue_tick).setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                    if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    item.setOnMenuItemClickListener(item1 -> {
                        var messageField = Unobfuscator.getFieldByExtendType(menuMethod.getDeclaringClass(), classThreadMessage);
                        var messageObject = XposedHelpers.getObjectField(param.thisObject, messageField.getName());
                        sendBlueTickMedia(messageObject,true);
                        Toast.makeText(Utils.getApplication(), ResId.string.sending_read_blue_tick, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                }

            }
        });
    }


    private void sendBlueTick(String currentJid) {
        logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
        if (messages.isEmpty() || currentJid == null || currentJid.contains(Utils.getMyNumber()))
            return;
        var messagekeys = messages.stream().map(item->item.messageKey).collect(Collectors.toList());
        var listAudios = MessageStore.getAudioListByMessageList(messagekeys);
        logDebug("listAudios: " + listAudios);
        for (var messageKey : listAudios) {
            var mInfo = messages.stream().filter(messageInfo -> messageInfo.messageKey.equals(messageKey)).findAny();
            if (mInfo.isPresent()) {
                messages.remove(mInfo.get());
                sendBlueTickMedia(mInfo.get().fMessage,false);
            }
        }
        sendBlueTickMsg(currentJid);
    }

    private void sendBlueTickMsg(String currentJid) {
        logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
        if (messages.isEmpty() || currentJid == null || currentJid.contains(Utils.getMyNumber()))
            return;
        try {
            logDebug("Blue on Reply: " + currentJid);
            var arr_s = messages.stream().map(item->item.messageKey).toArray(String[]::new);
            var userJid = WppCore.createUserJid(currentJid);
            var sendJob = XposedHelpers.newInstance(mSendReadClass, userJid, null, null, null, arr_s, -1, 0L, false);
            WaJobManagerMethod.invoke(mWaJobManager, sendJob);
            messages.clear();
        } catch (Throwable e) {
            XposedBridge.log("Error: " + e.getMessage());
        }
    }

    private void sendBlueTickStatus(String currentJid) {
        logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
        if (messages.isEmpty() || currentJid == null || currentJid.equals("status_me")) return;
        try {
            logDebug("sendBlue: " + currentJid);
            var arr_s = messages.stream().map(item->item.messageKey).toArray(String[]::new);
            var userJidSender = WppCore.createUserJid("status@broadcast");
            var userJid = WppCore.createUserJid(currentJid);
            var sendJob = XposedHelpers.newInstance(mSendReadClass, userJidSender, userJid, null, null, arr_s, -1, 0L, false);
            WaJobManagerMethod.invoke(mWaJobManager, sendJob);
            messages.clear();
        } catch (Throwable e) {
            XposedBridge.log("Error: " + e.getMessage());
        }
    }

    private void sendBlueTickMedia(Object messageObject, boolean clear) {
        try {
            logDebug("sendBlue: " + WppCore.getCurrentRawJID());
            var sendPlayerClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendPlayedReceiptJob", loader);
            var sendJob = XposedHelpers.newInstance(sendPlayerClass, messageObject);
            WaJobManagerMethod.invoke(mWaJobManager, sendJob);
            if (clear) messages.clear();
        } catch (Throwable e) {
            XposedBridge.log("Error: " + e.getMessage());
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Blue Tick";
    }


    static class MessageInfo {
        public String messageKey;
        public Object fMessage;

        public MessageInfo(Object fMessage, String messageKey) {
            this.messageKey = messageKey;
            this.fMessage = fMessage;
        }

        @NonNull
        @Override
        public String toString() {
            return messageKey;
        }
    }


}
