package com.wmods.wppenhacer.xposed.features.general;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeenTick extends Feature {

    private static final ArraySet<MessageInfo> messages = new ArraySet<>();
    private static Object mWaJobManager;
    private static Class<?> mSendReadClass;
    private static Method WaJobManagerMethod;
    private static String currentJid;
    private static String currentScreen = "none";
    private static final HashMap<String, ImageView> messageMap = new HashMap<>();

    public SeenTick(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public static void setSeenButton(ImageView buttonImage, boolean b) {
        Drawable originalDrawable = DesignUtils.getDrawableByName("ic_notif_mark_read");
        Drawable clonedDrawable;

        if (originalDrawable instanceof BitmapDrawable bitmapDrawable) {
            Bitmap bitmap = bitmapDrawable.getBitmap();
            Bitmap clonedBitmap = bitmap.copy(bitmap.getConfig(), true);
            clonedDrawable = new BitmapDrawable(buttonImage.getResources(), clonedBitmap);
        } else {
            clonedDrawable = Objects.requireNonNull(originalDrawable.getConstantState()).newDrawable().mutate();
        }
        if (b) {
            clonedDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
        }
        buttonImage.setImageDrawable(clonedDrawable);
        buttonImage.postInvalidate();
    }

    @Override
    public void doHook() throws Throwable {


        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));


        WaJobManagerMethod = Unobfuscator.loadBlueOnReplayWaJobManagerMethod(classLoader);

        mSendReadClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", classLoader);

        // hook instance of WaJobManager;

        XposedBridge.hookAllConstructors(WaJobManagerMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mWaJobManager = param.thisObject;
            }
        });

        // hook conversation screen

        WppCore.addListenerActivity((activity, type) -> {
            if (activity.getClass().getSimpleName().equals("Conversation") && (type == WppCore.ActivityChangeState.ChangeType.START || type == WppCore.ActivityChangeState.ChangeType.RESUME)) {
                var jid = WppCore.getCurrentRawJID();
                if (!Objects.equals(jid, currentJid)) {
                    currentJid = jid;
                    messages.clear();
                }
                currentScreen = "conversation";
            }
        });

        // hook messages

        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var objMessage = param.args[2];
                var fMessage = new FMessageWpp(objMessage);
                var key = fMessage.getKey();
                if (key.isFromMe) return;
                messages.add(new MessageInfo(fMessage, key.messageID, fMessage.getUserJid()));
            }
        });

        hookOnSendMessages();

        // Send Seen functions
        var ticktype = Integer.parseInt(prefs.getString("seentick", "0"));
        if (ticktype == 0) return;

        // hook current status

        var setPageActiveMethod = Unobfuscator.loadStatusActivePage(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(setPageActiveMethod));
        var fieldList = ReflectionUtils.getFieldByType(setPageActiveMethod.getDeclaringClass(), List.class);

        XposedBridge.hookMethod(setPageActiveMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var position = (int) param.args[1];
                var list = (List<?>) XposedHelpers.getObjectField(param.args[0], fieldList.getName());
                var fMessage = new FMessageWpp(list.get(position));
                var messageKey = (String) fMessage.getKey().messageID;
                var jid = WppCore.getRawString(fMessage.getUserJid());
                messages.clear();
                messages.add(new MessageInfo(fMessage, messageKey, null));
                currentJid = jid;
                currentScreen = "status";
            }
        });

        // Add button to send Seen in conversation
        hookConversationScreen(ticktype);

        /// Add button to send View Once to target
        hookViewOnceScreen(ticktype);

        // Add button to send Seen in status
        hookStatusScreen(ticktype);

    }

    private void hookStatusScreen(int ticktype) throws Exception {
        var viewButtonMethod = Unobfuscator.loadBlueOnReplayViewButtonMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(viewButtonMethod));

        if (ticktype == 1) {
            XposedBridge.hookMethod(viewButtonMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean("hidestatusview", false)) return;
                    var fMessageField = ReflectionUtils.getFieldByExtendType(param.thisObject.getClass(), FMessageWpp.TYPE);
                    var fMessage = new FMessageWpp(ReflectionUtils.getObjectField(fMessageField, param.thisObject));
                    var key = fMessage.getKey();
                    if (key.isFromMe) return;
                    var view = (View) param.getResult();
                    var contentView = (LinearLayout) view.findViewById(Utils.getID("bottom_sheet", "id"));
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
                    contentView.setOrientation(LinearLayout.HORIZONTAL);
                    contentView.addView(buttonImage, 0);
                    messageMap.put(key.messageID, buttonImage);
                    buttonImage.setOnClickListener(v -> CompletableFuture.runAsync(() -> {
                        Utils.showToast(view.getContext().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                        sendBlueTickStatus(currentJid);
                        setSeenButton(buttonImage, true);
                    }));
                    CompletableFuture.runAsync(() -> {
                        var seen = MessageStore.getInstance().isReadMessageStatus(key.messageID);
                        setSeenButton(buttonImage, seen);
                    });
                }
            });
        } else {

            MenuStatus.menuStatuses.add(
                    new MenuStatus.MenuItemStatus() {
                        @Override
                        public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                            if (menu.findItem(ResId.string.send_blue_tick) != null) return null;
                            if (fMessage.getKey().isFromMe) return null;
                            return menu.add(0, ResId.string.send_blue_tick, 0, ResId.string.send_blue_tick);
                        }

                        @Override
                        public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                            sendBlueTickStatus(currentJid);
                            Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                        }
                    });
        }
    }

    private void hookConversationScreen(int ticktype) throws Exception {
        var onCreateMenuConversationMethod = Unobfuscator.loadBlueOnReplayCreateMenuConversationMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateMenuConversationMethod));
        XposedBridge.hookMethod(onCreateMenuConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var menuItem = menu.add(0, 0, 0, ResId.string.send_blue_tick);
                if (ticktype == 1) menuItem.setShowAsAction(2);
                menuItem.setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                menuItem.setOnMenuItemClickListener(item -> {
                    Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                    sendBlueTick(currentJid);
                    return true;
                });
            }
        });

        MenuStatus.menuStatuses.add(
                new MenuStatus.MenuItemStatus() {
                    @Override
                    public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                        if (menu.findItem(ResId.string.read_all_mark_as_read) != null) return null;
                        if (fMessage.getKey().isFromMe) return null;
                        return menu.add(0, ResId.string.read_all_mark_as_read, 0, ResId.string.read_all_mark_as_read);
                    }

                    @Override
                    public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                        try {
                            messages.clear();
                            var listStatusField = ReflectionUtils.getFieldByExtendType(fragmentInstance.getClass(), List.class);
                            var listStatus = (List) listStatusField.get(fragmentInstance);
                            for (int i = 0; i < listStatus.size(); i++) {
                                var fMessage = new FMessageWpp(listStatus.get(i));
                                var messageId = fMessage.getKey().messageID;
                                if (!fMessage.getKey().isFromMe) {
                                    messages.add(new MessageInfo(fMessage, messageId, null));
                                }
                                var view = messageMap.get(messageId);
                                if (view != null) {
                                    view.post(() -> setSeenButton(view, true));
                                }
                            }
                        } catch (Exception e) {
                            log(e);
                        }
                        sendBlueTickStatus(currentJid);
                        Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                    }
                });
    }

    private void hookViewOnceScreen(int ticktype) throws Exception {
        var menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(menuMethod));

        XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
            @Override
            @SuppressLint("DiscouragedApi")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var messageField = ReflectionUtils.getFieldByExtendType(menuMethod.getDeclaringClass(), FMessageWpp.TYPE);
                if (messageField == null) return;
                var fMessage = new FMessageWpp(messageField.get(param.thisObject));
                var id = fMessage.getMediaType();
                // check media is view once
                if (id != 42 && id != 43) return;
                Menu menu = (Menu) param.args[0];
                MenuItem item = menu.add(0, 0, 0, ResId.string.send_blue_tick).setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                item.setOnMenuItemClickListener(item1 -> {
                    sendBlueTickMedia(fMessage.getObject(), true);
                    Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                    return true;
                });
            }
        });

        XposedHelpers.findAndHookMethod("com.whatsapp.messaging.ViewOnceViewerActivity", classLoader, "onCreateOptionsMenu", classLoader.loadClass("android.view.Menu"),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Menu menu = (Menu) param.args[0];
                        MenuItem item = menu.add(0, 0, 0, ResId.string.send_blue_tick).setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                        if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                        item.setOnMenuItemClickListener(item1 -> {
                            CompletableFuture.runAsync(() -> {
                                var keyClass = FMessageWpp.Key.TYPE;
                                var fieldType = ReflectionUtils.getFieldByType(param.thisObject.getClass(), keyClass);
                                var keyMessage = ReflectionUtils.getObjectField(fieldType, param.thisObject);
                                var fMessage = WppCore.getFMessageFromKey(keyMessage);
                                if (fMessage == null) return;
                                sendBlueTickMedia(fMessage, true);
                                Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                            });
                            return true;
                        });

                    }
                });


    }

    private void hookOnSendMessages() throws Exception {
        var messageJobMethod = Unobfuscator.loadBlueOnReplayMessageJobMethod(classLoader);
        var messageSendClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendE2EMessageJob", classLoader);

        XposedBridge.hookMethod(messageJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("blueonreply", false)) return;
                var obj = messageSendClass.cast(param.thisObject);
                var rawJid = (String) XposedHelpers.getObjectField(obj, "jid");

                var handler = new Handler(Looper.getMainLooper());
                if (Objects.equals(currentScreen, "status") && !rawJid.contains("status")) {
                    if (messages.isEmpty()) return;
                    MessageStore.getInstance().storeMessageRead(messages.valueAt(0).messageId);
                    var view = messageMap.get(messages.valueAt(0).messageId);
                    if (view != null) view.post(() -> setSeenButton(view, true));
                    handler.post(() -> sendBlueTickStatus(currentJid));
                } else handler.post(() -> sendBlueTick(rawJid));
            }
        });
    }


    private void sendBlueTick(String currentJid) {
        logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
        if (messages.isEmpty() || currentJid == null || currentJid.contains(Utils.getMyNumber()))
            return;
        var messagekeys = messages.stream().map(item -> item.messageId).collect(Collectors.toList());
        var listAudios = MessageStore.getInstance().getAudioListByMessageList(messagekeys);
        logDebug("listAudios: " + listAudios);
        for (var messageKey : listAudios) {
            var mInfo = messages.stream().filter(messageInfo -> messageInfo.messageId.equals(messageKey)).findAny();
            if (mInfo.isPresent()) {
                messages.remove(mInfo.get());
                sendBlueTickMedia(mInfo.get().fMessage.getObject(), false);
            }
        }
        sendBlueTickMsg(currentJid);
    }

    private void sendBlueTickMsg(String currentJid) {
        CompletableFuture.runAsync(() -> {
            logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
            if (messages.isEmpty() || currentJid == null || currentJid.contains(Utils.getMyNumber()))
                return;
            try {
                logDebug("Blue on Reply: " + currentJid);
                HashMap<Object, List<String>> map = new HashMap<>();
                for (var messageInfo : messages) {
                    map.computeIfAbsent(messageInfo.userJid, k -> new ArrayList<>());
                    Objects.requireNonNull(map.get(messageInfo.userJid)).add(messageInfo.messageId);
                }
                var userJidTarget = WppCore.createUserJid(currentJid);
                for (var userjid : map.keySet()) {
                    var messages = Objects.requireNonNull(map.get(userjid)).toArray(new String[0]);
                    WppCore.setPrivBoolean(messages[0] + "_rpass", true);
                    var participant = WppCore.isGroup(currentJid) ? userjid : null;
                    var sendJob = XposedHelpers.newInstance(mSendReadClass, userJidTarget, participant, null, null, messages, -1, 1L, false);
                    WaJobManagerMethod.invoke(mWaJobManager, sendJob);
                }
                messages.clear();
            } catch (Throwable e) {
                XposedBridge.log("Error: " + e.getMessage());
            }
        });
    }

    private void sendBlueTickStatus(String currentJid) {
        CompletableFuture.runAsync(() -> {
            logDebug("messages: " + Arrays.toString(messages.toArray(new MessageInfo[0])));
            if (messages.isEmpty() || currentJid == null || currentJid.equals("status_me")) return;
            try {
                logDebug("sendBlue: " + currentJid);
                var arr_s = messages.stream().map(item -> item.messageId).toArray(String[]::new);
                Arrays.stream(arr_s).forEach(s -> MessageStore.getInstance().storeMessageRead(s));
                var userJidSender = WppCore.createUserJid("status@broadcast");
                var userJid = WppCore.createUserJid(currentJid);
                WppCore.setPrivBoolean(arr_s[0] + "_rpass", true);
                var sendJob = XposedHelpers.newInstance(mSendReadClass, userJidSender, userJid, null, null, arr_s, -1, 0L, false);
                WaJobManagerMethod.invoke(mWaJobManager, sendJob);
                messages.clear();
            } catch (Throwable e) {
                XposedBridge.log("Error: " + e.getMessage());
            }
        });
    }

    private void sendBlueTickMedia(Object messageObject, boolean clear) {
        CompletableFuture.runAsync(() -> {
            try {
                var fMessage = new FMessageWpp(messageObject);
                logDebug("sendBlueTickMedia: " + WppCore.getRawString(fMessage.getKey().remoteJid));
                var sendPlayerClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendPlayedReceiptJobV2", classLoader);
                var constructor = sendPlayerClass.getDeclaredConstructors()[0];
                var classParticipantInfo = constructor.getParameterTypes()[0];
                var rowsId = new Long[]{fMessage.getRowId()};
                var remoteJid = fMessage.getKey().remoteJid;
                var messageId = fMessage.getKey().messageID;
                constructor = classParticipantInfo.getDeclaredConstructors()[0];
                var participantInfo = constructor.newInstance(remoteJid, null, rowsId, new String[]{messageId});
                var sendJob = XposedHelpers.newInstance(sendPlayerClass, participantInfo, false);
                WaJobManagerMethod.invoke(mWaJobManager, sendJob);
                if (clear) messages.clear();
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Blue Tick";
    }


    static class MessageInfo {
        public Object userJid;
        public String messageId;
        public FMessageWpp fMessage;

        public MessageInfo(FMessageWpp fMessage, String messageId, Object userJid) {
            this.messageId = messageId;
            this.fMessage = fMessage;
            this.userJid = userJid;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof MessageInfo messageInfo) {
                return Objects.equals(messageId, messageInfo.messageId) && Objects.equals(fMessage, messageInfo.fMessage) && Objects.equals(userJid, messageInfo.userJid);
            }
            return false;
        }

        @NonNull
        @Override
        public String toString() {
            return messageId;
        }
    }


}
