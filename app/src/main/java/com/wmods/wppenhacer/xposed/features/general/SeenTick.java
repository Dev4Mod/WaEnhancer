package com.wmods.wppenhacer.xposed.features.general;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeenTick extends Feature {

    private final Set<FMessageWpp> statuses = ConcurrentHashMap.newKeySet();
    private static Object mWaJobManager;
    private static Class<?> mSendReadClass;
    private static Method WaJobManagerMethod;
    private static FMessageWpp.UserJid currentJid;
    private static String currentScreen = "none";
    private final ConcurrentHashMap<String, WeakReference<ImageView>> messageMap = new ConcurrentHashMap<>();

    public SeenTick(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public static void setSeenButton(ImageView buttonImage, boolean b) {
        Drawable originalDrawable = DesignUtils.getDrawableByName("ic_notif_mark_read");
        if (originalDrawable == null) {
            buttonImage.setImageResource(Utils.getID("ic_notif_mark_read", "drawable"));
            if (b) buttonImage.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
            return;
        }

        Drawable clonedDrawable;

        if (originalDrawable instanceof BitmapDrawable bitmapDrawable) {
            Bitmap bitmap = bitmapDrawable.getBitmap();
            Bitmap.Config config = bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
            Bitmap clonedBitmap;
            try {
                clonedBitmap = bitmap.copy(config, true);
            } catch (Exception ex) {
                clonedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    android.graphics.Canvas canvas = new android.graphics.Canvas(clonedBitmap);
                    canvas.drawBitmap(bitmap, 0f, 0f, null);
                } catch (Exception ignore) {
                }
            }
            clonedDrawable = new BitmapDrawable(buttonImage.getResources(), clonedBitmap);
        } else {
            var cs = originalDrawable.getConstantState();
            if (cs != null) {
                clonedDrawable = cs.newDrawable().mutate();
            } else {
                clonedDrawable = originalDrawable.mutate();
            }
        }
        if (b) {
            clonedDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
        }
        buttonImage.setImageDrawable(clonedDrawable);
        buttonImage.postInvalidate();
    }

    private void registerMessageView(String messageId, ImageView view) {
        if (messageId == null || view == null) return;
        messageMap.put(messageId, new WeakReference<>(view));
    }

    private ImageView getRegisteredView(String messageId) {
        WeakReference<ImageView> ref = messageMap.get(messageId);
        return ref == null ? null : ref.get();
    }

    @Override
    public void doHook() throws Throwable {


        WaJobManagerMethod = Unobfuscator.loadBlueOnReplayWaJobManagerMethod(classLoader);

        mSendReadClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "SendReadReceiptJob");

        // hook instance of WaJobManager;

        XposedBridge.hookAllConstructors(WaJobManagerMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mWaJobManager = param.thisObject;
            }
        });

        // hook conversation screen

        WppCore.addListenerActivity((activity, type) -> {
            if (activity.getClass().getSimpleName().equals("Conversation") && (type == WppCore.ActivityChangeState.ChangeType.STARTED || type == WppCore.ActivityChangeState.ChangeType.RESUMED)) {
                var jid = WppCore.getCurrentUserJid();
                if (!Objects.equals(jid, currentJid)) {
                    currentJid = jid;
                }
                currentScreen = "conversation";
            }
        });

        // hook messages
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
                var object = list.get(position);
                if (!FMessageWpp.TYPE.isInstance(object)) {
                    var fmessageField = ReflectionUtils.findFieldUsingFilter(object.getClass(), field -> FMessageWpp.TYPE.isAssignableFrom(field.getType()));
                    object = fmessageField.get(object);
                }
                var fMessage = new FMessageWpp(object);
                statuses.clear();
                statuses.add(fMessage);
                currentJid = fMessage.getUserJid();
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
        var viewStatusField = Unobfuscator.loadBlueOnReplayViewButtonOutSideField(classLoader);
        if (ticktype == 1) {
            XposedBridge.hookMethod(viewButtonMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean("hidestatusview", false)) return;
                    var fMessageField = ReflectionUtils.getFieldByExtendType(viewStatusField.getDeclaringClass(), FMessageWpp.TYPE);
                    var fMessageObj = ReflectionUtils.getObjectField(fMessageField, param.thisObject);
                    if (fMessageObj == null) {
                        var instance = ReflectionUtils.getObjectField(viewStatusField, param.thisObject);
                        fMessageField = ReflectionUtils.findFieldUsingFilterIfExists(instance.getClass(), field1 -> FMessageWpp.TYPE.isAssignableFrom(field1.getType()));
                        if (fMessageField != null) {
                            fMessageObj = ReflectionUtils.getObjectField(fMessageField, instance);
                        }
                    }
                    if (fMessageObj == null) {
                        logDebug("Failed to find fMessageField");
                        return;
                    }
                    var fMessage = new FMessageWpp(fMessageObj);
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
                    registerMessageView(key.messageID, buttonImage);
                    buttonImage.setOnClickListener(v -> CompletableFuture.runAsync(() -> {
                        Utils.showToast(view.getContext().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                        sendBlueTickStatus(currentJid);
                        buttonImage.post(() -> setSeenButton(buttonImage, true));
                    }));
                    CompletableFuture.runAsync(() -> {
                        var seen = MessageStore.getInstance().isReadMessageStatus(key.messageID);
                        buttonImage.post(() -> setSeenButton(buttonImage, seen));
                    });
                }
            });
        } else {

            MenuStatusListener.menuStatuses.add(
                    new MenuStatusListener.onMenuItemStatusListener() {
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
                    sendBlueTick(currentJid);
                    Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                    HideSeenView.updateAllBubbleViews();
                    return true;
                });
            }
        });

        MenuStatusListener.menuStatuses.add(
                new MenuStatusListener.onMenuItemStatusListener() {
                    @Override
                    public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                        if (menu.findItem(ResId.string.read_all_mark_as_read) != null) return null;
                        if (fMessage.getKey().isFromMe) return null;
                        return menu.add(0, ResId.string.read_all_mark_as_read, 0, ResId.string.read_all_mark_as_read);
                    }

                    @Override
                    public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                        try {
                            statuses.clear();
                            var listStatusField = ReflectionUtils.getFieldByExtendType(fragmentInstance.getClass(), List.class);
                            var listStatus = (List) listStatusField.get(fragmentInstance);
                            for (int i = 0; i < listStatus.size(); i++) {
                                var obj = listStatus.get(i);
                                if (!FMessageWpp.TYPE.isInstance(obj)) {
                                    var fieldFMessage = ReflectionUtils.getFieldByExtendType(obj.getClass(), FMessageWpp.TYPE);
                                    obj = fieldFMessage.get(obj);
                                }
                                var fMessage = new FMessageWpp(obj);
                                var messageId = fMessage.getKey().messageID;
                                if (!fMessage.getKey().isFromMe) {
                                    statuses.add(fMessage);
                                }
                                var view = getRegisteredView(messageId);
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
                Object fmessageObj = null;
                if (messageField != null) {
                    fmessageObj = messageField.get(param.thisObject);
                }
                if (fmessageObj == null) {
                    var keyField = ReflectionUtils.getFieldByExtendType(param.thisObject.getClass(), FMessageWpp.Key.TYPE);
                    if (keyField != null) {
                        var keyObj = keyField.get(param.thisObject);
                        fmessageObj = WppCore.getFMessageFromKey(keyObj);
                    }
                }
                FMessageWpp fMessage = new FMessageWpp(fmessageObj);
                if (!fMessage.isViewOnce()) return;
                Menu menu = (Menu) param.args[0];
                MenuItem item = menu.add(0, 0, 0, ResId.string.send_blue_tick).setIcon(Utils.getID("ic_notif_mark_read", "drawable"));
                if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                item.setOnMenuItemClickListener(item1 -> {
                    var userJid = fMessage.getKey().remoteJid;
                    var messageID = fMessage.getKey().messageID;
                    MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageID, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                    MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageID, MessageHistory.MessageType.MESSAGE_TYPE, true);
                    sendBlueTickMedia(fMessage);
                    statuses.clear();
                    Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                    HideSeenView.updateAllBubbleViews();
                    return true;
                });
            }
        });

        XposedHelpers.findAndHookMethod(WppCore.getViewOnceViewerActivityClass(classLoader), "onCreateOptionsMenu", classLoader.loadClass("android.view.Menu"),
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
                                var fMessage = new FMessageWpp.Key(keyMessage).getFMessage();
                                var rawJid = fMessage.getKey().remoteJid.getPhoneRawString();
                                var messageID = fMessage.getKey().messageID;
                                MessageHistory.getInstance().updateViewedMessage(rawJid, messageID, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                                MessageHistory.getInstance().updateViewedMessage(rawJid, messageID, MessageHistory.MessageType.MESSAGE_TYPE, true);
                                sendBlueTickMedia(fMessage);
                                statuses.clear();
                                Utils.showToast(Utils.getApplication().getString(ResId.string.sending_read_blue_tick), Toast.LENGTH_SHORT);
                                HideSeenView.updateAllBubbleViews();
                            });
                            return true;
                        });

                    }
                });


    }

    private void hookOnSendMessages() throws Exception {
        var messageJobMethod = Unobfuscator.loadBlueOnReplayMessageJobMethod(classLoader);
        var messageSendClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains, "SendE2EMessageJob");

        XposedBridge.hookMethod(messageJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("blueonreply", false)) return;
                var obj = messageSendClass.cast(param.thisObject);
                var rawJid = (String) XposedHelpers.getObjectField(obj, "jid");
                var userJid = new FMessageWpp.UserJid(WppCore.createUserJid(rawJid));

                if (Objects.equals(currentScreen, "status") && !userJid.isStatus()) {
                    if (statuses.isEmpty()) return;
                    var first = statuses.stream().findFirst().orElse(null);
                    if (first == null) return;
                    MessageStore.getInstance().storeMessageRead(first.getKey().messageID);
                    var view = getRegisteredView(first.getKey().messageID);
                    if (view != null) view.post(() -> setSeenButton(view, true));
                    sendBlueTickStatus(currentJid);
                } else {
                    sendBlueTick(userJid);
                }
                HideSeenView.updateAllBubbleViews();
            }
        });
    }

    private static void updateMessageStatusView(String rawJid, List<FMessageWpp> messages) {
        for (var msg : messages) {
            MessageHistory.getInstance().updateViewedMessage(rawJid, msg.getKey().messageID, MessageHistory.MessageType.MESSAGE_TYPE, true);
        }
        HideSeenView.updateAllBubbleViews();
    }

    private void sendBlueTick(FMessageWpp.UserJid userJid) {
        CompletableFuture.runAsync(() -> {
            if (Objects.equals(userJid.getPhoneNumber(), Utils.getMyNumber()) || Objects.requireNonNullElse(userJid.getUserRawString(), "").contains("lid_me"))
                return;
            var messages = new ArrayList<FMessageWpp>();
            var hideSeenMessagesssages = MessageHistory.getInstance().getHideSeenMessages(userJid.getPhoneRawString(), MessageHistory.MessageType.MESSAGE_TYPE, false);
            for (var message : hideSeenMessagesssages) {
                var fmessage = message.getFMessage();
                if (fmessage == null) continue;
                messages.add(fmessage);
            }
            if (messages.isEmpty())
                return;

            if (prefs.getBoolean("hideaudioseen", false)) {
                for (var m : messages) {
                    if (m.getMediaType() == 2) sendBlueTickMedia(m);
                }
            }
            sendBlueTickMsg(userJid, messages);
            updateMessageStatusView(userJid.getPhoneRawString(), messages);
        }, Utils.getExecutor());
    }

    private void sendBlueTickMsg(FMessageWpp.UserJid userJid, ArrayList<FMessageWpp> messages) {
        if (messages.isEmpty())
            return;
        try {
            HashMap<FMessageWpp.UserJid, List<String>> groupedMap = new HashMap<>();
            for (FMessageWpp message : messages) {
                var userJidMsg = userJid.isGroup() ? message.getUserJid() : message.getKey().remoteJid;
                groupedMap.computeIfAbsent(userJidMsg, k -> new ArrayList<>()).add(message.getKey().messageID);
            }

            for (var entry : groupedMap.entrySet()) {
                var userJidMsg = entry.getKey();
                String[] messageIds = entry.getValue().toArray(new String[0]);
                var participant = userJid.isGroup() ? userJidMsg.userJid : null;


                Object sendJob = XposedHelpers.newInstance(
                        mSendReadClass, userJid.userJid, participant, null, null, messageIds, -1, 1L, false
                );
                XposedHelpers.setAdditionalInstanceField(sendJob, "blue_on_reply", true);
                WaJobManagerMethod.invoke(mWaJobManager, sendJob);
            }
        } catch (Exception e) {
            logDebug(e);
        }
    }

    private void sendBlueTickStatus(FMessageWpp.UserJid currentJid) {
        CompletableFuture.runAsync(() -> {
            if (statuses.isEmpty() || currentJid == null || "status_me".equals(currentJid.getPhoneNumber()))
                return;
            try {
                var arr_s = statuses.stream().map(item -> item.getKey().messageID).toArray(String[]::new);
                Arrays.stream(arr_s).forEach(s -> MessageStore.getInstance().storeMessageRead(s));
                var userJidSender = WppCore.createUserJid("status@broadcast");

                var sendJob2 = XposedHelpers.newInstance(mSendReadClass, userJidSender, currentJid.phoneJid, null, null, arr_s, -1, 0L, false);
                XposedHelpers.setAdditionalInstanceField(sendJob2, "blue_on_reply", true);
                WaJobManagerMethod.invoke(mWaJobManager, sendJob2);

                statuses.clear();
            } catch (Exception e) {
                logDebug(e);
            }
        }, Utils.getExecutor());
    }

    private void sendBlueTickMedia(FMessageWpp fMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                var userJid = fMessage.getKey().remoteJid;
                Object participant = null;
                if (userJid.isGroup()) {
                    participant = fMessage.getUserJid().userJid;
                }
                var sendPlayerClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains, "SendPlayedReceiptJob");
                var constructor = sendPlayerClass.getDeclaredConstructors()[0];
                var classParticipantInfo = constructor.getParameterTypes()[0];
                var rowsId = new Long[]{fMessage.getRowId()};
                var messageId = fMessage.getKey().messageID;
                constructor = classParticipantInfo.getDeclaredConstructors()[0];
                var participantInfo = constructor.newInstance(userJid.userJid, participant, rowsId, new String[]{messageId});
                var sendJob = XposedHelpers.newInstance(sendPlayerClass, participantInfo, false);
                WaJobManagerMethod.invoke(mWaJobManager, sendJob);
            } catch (Throwable e) {
                logDebug(e);
            }
        }, Utils.getExecutor());
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Seen Tick";
    }


}
