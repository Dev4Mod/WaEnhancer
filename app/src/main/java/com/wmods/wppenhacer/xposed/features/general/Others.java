package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.BaseBundle;
import android.os.Message;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Others extends Feature {

    public static HashMap<Integer, Boolean> propsBoolean = new HashMap<>();
    public static HashMap<Integer, Integer> propsInteger = new HashMap<>();

    public Others(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        // receivedIncomingTimestamp

        var novoTema = prefs.getBoolean("novotema", false);
        var menuWIcons = prefs.getBoolean("menuwicon", false);
        var newSettings = prefs.getBoolean("novaconfig", false);
        var filterChats = prefs.getString("chatfilter", null);
        var strokeButtons = prefs.getBoolean("strokebuttons", false);
        var outlinedIcons = prefs.getBoolean("outlinedicons", false);
        var showDnd = prefs.getBoolean("show_dndmode", false);
        var showFreezeLastSeen = prefs.getBoolean("show_freezeLastSeen", false);
        var filterSeen = prefs.getBoolean("filterseen", false);
        var fbstyle = prefs.getBoolean("fbstyle", false);
        var metaai = prefs.getBoolean("metaai", false);
        var topnav = prefs.getBoolean("topnav", false);
        var proximity = prefs.getBoolean("proximity_audios", false);
        var showOnline = prefs.getBoolean("showonline", false);
        var floatingMenu = prefs.getBoolean("floatingmenu", false);
        var filter_itens = prefs.getString("filter_itens", null);
        var disable_defemojis = prefs.getBoolean("disable_defemojis", false);
        var autonext_status = prefs.getBoolean("autonext_status", false);
        var toast_viewed_status = prefs.getBoolean("toast_viewed_status", false);
        var toast_viewed_message = prefs.getBoolean("toast_viewed_message", false);
        var audio_type = Integer.parseInt(prefs.getString("audio_type", "0"));
        var audio_transcription = prefs.getBoolean("audio_transcription", false);

        propsBoolean.put(5171, filterSeen); // filtros de chat e grupos
        propsBoolean.put(4524, novoTema);
        propsBoolean.put(4497, menuWIcons);
        propsBoolean.put(4023, newSettings);
        propsBoolean.put(8013, Objects.equals(filterChats, "2")); // lupa sera removida e sera adicionado uma barra no lugar.
        propsBoolean.put(5834, strokeButtons);
        propsBoolean.put(5509, outlinedIcons);
        propsBoolean.put(2358, false);
        propsBoolean.put(7516, fbstyle);
        propsBoolean.put(3289, !topnav);
        propsBoolean.put(4656, !topnav);
        propsBoolean.put(2889, floatingMenu);
        propsBoolean.put(7769, false);

        // Instant Video
        propsBoolean.put(3354, true);
        propsBoolean.put(5418, true);

        propsBoolean.put(9051, true);
        propsBoolean.put(5332, false);

        if (metaai) {
            propsBoolean.put(8025, false);
            propsBoolean.put(6251, false);
            propsBoolean.put(7639, false);
        }

        if (audio_transcription) {
            Others.propsBoolean.put(8632, true);
            Others.propsBoolean.put(2890, true);
        }

        propsInteger.put(8522, fbstyle ? 1 : 0);
        propsInteger.put(8521, fbstyle ? 1 : 0);

        hookProps();

        hookMenuOptions(newSettings, showFreezeLastSeen, showDnd, filterChats);

        if (proximity) {
            var proximitySensorMethod = Unobfuscator.loadProximitySensorMethod(classLoader);
            XposedBridge.hookMethod(proximitySensorMethod, XC_MethodReplacement.DO_NOTHING);
        }

        if (filter_itens != null) {
            filterItens(filter_itens);
        }

        if (disable_defemojis) {
            disable_defEmojis();
        }

        if (autonext_status) {
            autoNextStatus();
        }
        toast_viewed(toast_viewed_status, toast_viewed_message);

        if (audio_type > 0) {
            sendAudioType(audio_type);
        }
        customPlayBackSpeed();
        showOnline(showOnline);

    }

    private void customPlayBackSpeed() throws Exception {
        var voicenote_speed = prefs.getFloat("voicenote_speed", 2.0f);
        var playBackSpeed = Unobfuscator.loadPlaybackSpeed(classLoader);
        XposedBridge.hookMethod(playBackSpeed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if ((float) param.args[1] == 2.0f) {
                    param.args[1] = voicenote_speed;
                }
            }
        });
        var voicenoteClass = classLoader.loadClass("com.whatsapp.search.views.itemviews.VoiceNoteProfileAvatarView");
        var method = ReflectionUtils.findAllMethodUsingFilter(voicenoteClass, method1 -> method1.getParameterCount() == 4 && method1.getParameterTypes()[0] == int.class && method1.getReturnType().equals(void.class));
        XposedBridge.hookMethod(method[method.length - 1], new XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if ((int) param.args[0] == 3) {
                    var view = (View) param.thisObject;
                    var playback = (TextView) view.findViewById(Utils.getID("fast_playback_overlay", "id"));
                    if (playback != null) {
                        playback.setText(String.valueOf(voicenote_speed).replace(".", ",") + "×");
                    }
                }
            }
        });
    }


    private void sendAudioType(int audio_type) throws Exception {
        var sendAudioTypeMethod = Unobfuscator.loadSendAudioTypeMethod(classLoader);
        log(Unobfuscator.getMethodDescriptor(sendAudioTypeMethod));
        XposedBridge.hookMethod(sendAudioTypeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var results = ReflectionUtils.findArrayOfType(param.args, Integer.class);
                if (results.size() < 2) {
                    log("sendAudioTypeMethod size < 2");
                    return;
                }
                var mediaType = results.get(0);
                if ((int) mediaType.second != 2) return;
                var audioType = results.get(1);
                param.args[audioType.first] = audio_type - 1; // 1 = voice notes || 0 = audio voice
            }
        });

        var originFMessageField = Unobfuscator.loadOriginFMessageField(classLoader);
        var forwardAudioTypeMethod = Unobfuscator.loadForwardAudioTypeMethod(classLoader);

        XposedBridge.hookMethod(forwardAudioTypeMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = param.getResult();
                originFMessageField.setAccessible(true);
                originFMessageField.setInt(fMessage, audio_type - 1);
            }
        });
    }

    private void toast_viewed(boolean toast_viewed_status, boolean toast_viewed_message) throws Exception {

        var onInsertReceipt = Unobfuscator.loadOnInsertReceipt(classLoader);
        XposedBridge.hookMethod(onInsertReceipt, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var type = (int) param.args[1];
                var id = (long) param.args[2];
                if (type != 13) return;
                var PhoneUserJid = param.args[0];
                AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    var raw = WppCore.getRawString(PhoneUserJid);
                    var UserJid = WppCore.createUserJid(raw);
                    var contactName = WppCore.getContactName(UserJid);
                    if (TextUtils.isEmpty(contactName)) {
                        contactName = WppCore.stripJID(raw);
                    }
                    var sql = MessageStore.getDatabase();
                    try (var result = sql.query("status", null, "message_table_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
                        if (result.moveToNext()) {
                            if (toast_viewed_status) {
                                Utils.showToast(String.format("%s viewed your status", contactName), Toast.LENGTH_LONG);
                            }
                            Tasker.sendTaskerEvent(contactName, WppCore.stripJID(raw), "viewed_status");
                        } else if (!Objects.equals(WppCore.getCurrentRawJID(), raw)) {
                            try (var result2 = sql.query("message", null, "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
                                if (result2.moveToNext()) {
                                    var chat_id = result2.getLong(result2.getColumnIndexOrThrow("chat_row_id"));
                                    try (var result3 = sql.query("chat", null, "_id = ? AND subject IS NULL", new String[]{String.valueOf(chat_id)}, null, null, null)) {
                                        if (result3.moveToNext()) {
                                            if (toast_viewed_message)
                                                Utils.showToast(String.format("%s viewed your message", contactName), Toast.LENGTH_LONG);
                                            Tasker.sendTaskerEvent(contactName, WppCore.stripJID(raw), "viewed_message");
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    private void autoNextStatus() throws Exception {
        Class<?> StatusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        var runNextStatusMethod = Unobfuscator.loadNextStatusRunMethod(classLoader);
        XposedBridge.hookMethod(runNextStatusMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var obj = XposedHelpers.getObjectField(param.thisObject, "A01");
                if (StatusPlaybackContactFragmentClass.isInstance(obj)) {
                    param.setResult(null);
                }
            }
        });
        var onPlayBackFinished = Unobfuscator.loadOnPlaybackFinished(classLoader);
        XposedBridge.hookMethod(onPlayBackFinished, XC_MethodReplacement.DO_NOTHING);
    }


    private void disable_defEmojis() throws Exception {
        var defEmojiClass = Unobfuscator.loadDefEmojiClass(classLoader);
        XposedBridge.hookMethod(defEmojiClass, XC_MethodReplacement.returnConstant(null));
    }

    private void filterItens(String filterItens) {
        var itens = filterItens.split("\n");
        var idsFilter = new ArrayList<Integer>();
        for (String item : itens) {
            var id = Utils.getID(item, "id");
            if (id > 0) {
                idsFilter.add(id);
            }
        }
        XposedHelpers.findAndHookMethod(View.class, "invalidate", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                var id = view.getId();
                if (id > 0 && idsFilter.contains(id) && view.getVisibility() == View.VISIBLE) {
                    view.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showOnline(boolean showOnline) throws Exception {
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader);
        XposedBridge.hookMethod(checkOnlineMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var message = (Message) param.args[0];
                if (message.arg1 != 5) return;
                BaseBundle baseBundle = (BaseBundle) message.obj;
                var jid = baseBundle.getString("jid");
                if (WppCore.isGroup(jid)) return;
                var name = WppCore.getContactName(WppCore.createUserJid(jid));
                name = TextUtils.isEmpty(name) ? WppCore.stripJID(jid) : name;
                if (showOnline)
                    Utils.showToast(String.format(Utils.getApplication().getString(ResId.string.toast_online), name), Toast.LENGTH_SHORT);
                Tasker.sendTaskerEvent(name, WppCore.stripJID(jid), "contact_online");
            }
        });
    }


    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
    private static void InsertDNDOption(Menu menu, Activity home, boolean newSettings) {
        var dndmode = WppCore.getPrivBoolean("dndmode", false);
        var item = menu.add(0, 0, 0, home.getString(ResId.string.dnd_mode_title));
        var drawable = DesignUtils.getDrawableByName(dndmode ? "ic_location_nearby_disabled" : "ic_location_nearby");
        if (drawable != null) {
            drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
            item.setIcon(drawable);
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            if (!dndmode) {
                new AlertDialogWpp(home)
                        .setTitle(home.getString(ResId.string.dnd_mode_title))
                        .setMessage(home.getString(ResId.string.dnd_message))
                        .setPositiveButton(home.getString(ResId.string.activate), (dialog, which) -> {
                            WppCore.setPrivBoolean("dndmode", true);
                            Utils.doRestart(home);
                        })
                        .setNegativeButton(home.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss())
                        .create().show();
                return true;
            }
            WppCore.setPrivBoolean("dndmode", false);
            Utils.doRestart(home);
            return true;
        });
    }

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
    private static void InsertFreezeLastSeenOption(Menu menu, Activity home, boolean newSettings) {
        final boolean freezelastseen = WppCore.getPrivBoolean("freezelastseen", false);
        MenuItem item = menu.add(0, 0, 0, home.getString(ResId.string.freezelastseen_title));
        var drawable = Utils.getApplication().getDrawable(freezelastseen ? ResId.drawable.eye_disabled : ResId.drawable.eye_enabled);
        if (drawable != null) {
            drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
            item.setIcon(drawable);
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            if (!freezelastseen) {
                new AlertDialogWpp(home)
                        .setTitle(home.getString(ResId.string.freezelastseen_title))
                        .setMessage(home.getString(ResId.string.freezelastseen_message))
                        .setPositiveButton(home.getString(ResId.string.activate), (dialog, which) -> {
                            WppCore.setPrivBoolean("freezelastseen", true);
                            Utils.doRestart(home);
                        })
                        .setNegativeButton(home.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss())
                        .create().show();
                return true;
            }
            WppCore.setPrivBoolean("freezelastseen", false);
            Utils.doRestart(home);
            return true;
        });
    }


    private void hookProps() throws Exception {
        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean));
        var dataUsageActivityClass = XposedHelpers.findClass("com.whatsapp.settings.SettingsDataUsageActivity", classLoader);
        var workManagerClass = Unobfuscator.loadWorkManagerClass(classLoader);
        XposedBridge.hookMethod(methodPropsBoolean, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) param.args[param.args.length - 1];

                var propValue = propsBoolean.get(i);
                if (propValue != null) {
                    // Fix Bug in Settings Data Usage
                    switch (i) {
                        case 4023:
                            if (Unobfuscator.isCalledFromClass(dataUsageActivityClass))
                                return;
                            break;
                        // Fix bug in work manager
                        case 3877:
                            if (!Unobfuscator.isCalledFromClass(workManagerClass))
                                return;
                            break;
                    }
                    param.setResult(propValue);
                }
            }
        });

        var methodPropsInteger = Unobfuscator.loadPropsIntegerMethod(classLoader);

        XposedBridge.hookMethod(methodPropsInteger, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) param.args[param.args.length - 1];
                var propValue = propsInteger.get(i);
                if (propValue == null) return;
                param.setResult(propValue);
            }
        });
    }

    private void hookMenuOptions(boolean newSettings, boolean showFreezeLastSeen, boolean showDnd, String filterChats) {
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var activity = (Activity) param.thisObject;
                if (prefs.getBoolean("restartbutton", true)) {
                    var iconDraw = activity.getDrawable(ResId.drawable.refresh);
                    iconDraw.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
                    var itemMenu = menu.add(0, 0, 0, ResId.string.restart_whatsapp).setIcon(iconDraw).setOnMenuItemClickListener(item -> {
                        Utils.doRestart(activity);
                        return true;
                    });
                    if (newSettings) {
                        itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    }
                }
                if (showFreezeLastSeen) {
                    InsertFreezeLastSeenOption(menu, activity, newSettings);
                }
                if (showDnd) {
                    InsertDNDOption(menu, activity, newSettings);
                } else {
                    var dndmode = WppCore.getPrivBoolean("dndmode", false);
                    if (dndmode) {
                        WppCore.setPrivBoolean("dndmode", false);
                        Utils.doRestart(activity);
                    }
                }
            }
        });


        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var item = menu.findItem(Utils.getID("menuitem_search", "id"));
                if (item != null) {
                    item.setVisible(Objects.equals(filterChats, "1"));
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Others";
    }
}
