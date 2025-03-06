package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.os.BaseBundle;
import android.os.Message;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.listeners.OnMultiClickListener;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.AnimationUtil;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.OkHttpClient;

public class Others extends Feature {

    public static HashMap<Integer, Boolean> propsBoolean = new HashMap<>();
    public static HashMap<Integer, Integer> propsInteger = new HashMap<>();
    private Properties properties;

    public Others(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        // receivedIncomingTimestamp

        properties = Utils.getProperties(prefs, "custom_css", "custom_filters");

        var menuWIcons = prefs.getBoolean("menuwicon", false);
        var newSettings = prefs.getBoolean("novaconfig", false);
        var filterChats = prefs.getString("chatfilter", null);
        var filterSeen = prefs.getBoolean("filterseen", false);
        var status_style = Integer.parseInt(prefs.getString("status_style", "0"));
        var metaai = prefs.getBoolean("metaai", false);
        var proximity = prefs.getBoolean("proximity_audios", false);
        var showOnline = prefs.getBoolean("showonline", false);
        var floatingMenu = prefs.getBoolean("floatingmenu", false);
        var filter_items = prefs.getString("filter_items", null);
        var disable_defemojis = prefs.getBoolean("disable_defemojis", false);
        var autonext_status = prefs.getBoolean("autonext_status", false);
        var audio_type = Integer.parseInt(prefs.getString("audio_type", "0"));
        var audio_transcription = prefs.getBoolean("audio_transcription", false);
        var oldStatus = prefs.getBoolean("oldstatus", false);
        var igstatus = prefs.getBoolean("igstatus", false);
        var animationEmojis = prefs.getBoolean("animation_emojis", false);

        propsInteger.put(3877, oldStatus ? igstatus ? 2 : 0 : 2);
        propsBoolean.put(5171, filterSeen); // filtros de chat e grupos
        propsBoolean.put(4497, menuWIcons);
        propsBoolean.put(4023, newSettings);
        propsBoolean.put(8013, Objects.equals(filterChats, "2")); // lupa sera removida e sera adicionado uma barra no lugar.
        propsBoolean.put(2358, false);
        propsBoolean.put(2889, floatingMenu);
        propsBoolean.put(7769, false);

        // disable new Media Picker
        propsBoolean.put(9286, false);

        // Instant Video
        propsBoolean.put(3354, true);
        propsBoolean.put(5418, true);
        propsBoolean.put(9051, true);

        // disable new toolbar
        propsBoolean.put(11824, false);
        propsBoolean.put(6481, false);

        // Enable music in Stories
        //propsBoolean.put(13280, true);

        propsBoolean.put(6798, true);  // show all status
        propsBoolean.put(3575, animationEmojis);  // auto play emojis settings
        propsBoolean.put(9757, animationEmojis);  // auto play emojis settings
        propsBoolean.put(10639, animationEmojis);  // emojis map 1
        propsBoolean.put(12495, animationEmojis);  // emojis map 2
        propsBoolean.put(11066, animationEmojis);  // emojis map 3

        propsBoolean.put(7589, true);  // Media select quality
        propsBoolean.put(6972, false); // Media select quality
        propsBoolean.put(5625, true);  // Enable option to autodelete channels media

        propsBoolean.put(8643, true);  // Enable TextStatusComposerActivityV2
//        propsBoolean.put(3403, true);  // Enable Sticker Suggestion
        propsBoolean.put(8607, true);  // Enable Dialer keyboard
        propsBoolean.put(9578, true);  // Enable Privacy Checkup
        propsInteger.put(8135, 2);  // Call Filters
        propsBoolean.put(9141, true);  // Enable Translate Message
        propsBoolean.put(8925, true);  // Enable Translate Message

        propsBoolean.put(10380, false); // fix crash bug in Settings/Archived

        propsBoolean.put(10379, true);
        propsBoolean.put(10388, true);


        if (metaai) {
            propsBoolean.put(8025, false);
            propsBoolean.put(6251, false);
            propsBoolean.put(7639, false);
        }

        if (audio_transcription) {
            Others.propsBoolean.put(8632, true);
            Others.propsBoolean.put(2890, true);
            Others.propsBoolean.put(9215, false);
            Others.propsBoolean.put(9216, true);
            Others.propsBoolean.put(6808, true);
            Others.propsBoolean.put(10286, true);
            Others.propsBoolean.put(11596, true);
        }

        // Whatsapp Status Style
        status_style = oldStatus ? 0 : status_style;
        propsInteger.put(9973, 1);
        propsBoolean.put(6285, true);
        propsInteger.put(8522, status_style);
        propsInteger.put(8521, status_style);


        hookProps();
        hookMenuOptions(filterChats);

        if (proximity) {
            var proximitySensorMethod = Unobfuscator.loadProximitySensorMethod(classLoader);
            XposedBridge.hookMethod(proximitySensorMethod, XC_MethodReplacement.DO_NOTHING);
        }

        if (filter_items != null && prefs.getBoolean("custom_filters", true)) {
            filterItems(filter_items);
        }

        if (disable_defemojis) {
            disable_defEmojis();
        }

        if (autonext_status) {
            autoNextStatus();
        }

        if (audio_type > 0) {
            sendAudioType(audio_type);
        }

        customPlayBackSpeed();

        showOnline(showOnline);

        animationList();

        stampCopiedMessage();

        doubleTapReaction();

        alwaysOnline();

        callInfo();

    }

    private void callInfo() throws Exception {
        if (!prefs.getBoolean("call_info", false))
            return;

        var clsCallEventCallback = classLoader.loadClass("com.whatsapp.calling.service.VoiceServiceEventCallback");
        Class<?> clsWamCall = classLoader.loadClass("com.whatsapp.fieldstats.events.WamCall");

        XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (clsWamCall.isInstance(param.args[0])) {

                            Object callinfo = XposedHelpers.callMethod(param.thisObject, "getCallInfo");
                            if (callinfo == null)
                                return;
                            var userJid = XposedHelpers.callMethod(callinfo, "getPeerJid");
                            CompletableFuture.runAsync(() -> {
                                try {
                                    showCallInformation(param.args[0], userJid);
                                } catch (Exception e) {
                                    logDebug(e);
                                }
                            });
                        }
                    }
                });
    }

    private void showCallInformation(Object wamCall, Object userJid) throws Exception {
        if (WppCore.isGroup(WppCore.getRawString(userJid)))
            return;
        var sb = new StringBuilder();
        var contact = WppCore.getContactName(userJid);
        var number = WppCore.stripJID(WppCore.getRawString(userJid));
        if (!TextUtils.isEmpty(contact)) sb.append(String.format(Utils.getApplication().getString(ResId.string.contact_s), contact)).append("\n");
        sb.append(String.format(Utils.getApplication().getString(ResId.string.phone_number_s), number)).append("\n");
        var ip = (String) XposedHelpers.getObjectField(wamCall, "callPeerIpStr");
        if (ip != null) {
            var client = new OkHttpClient();
            var url = "http://ip-api.com/json/" + ip;
            var request = new okhttp3.Request.Builder().url(url).build();
            var content = client.newCall(request).execute().body().string();
            var json = new JSONObject(content);
            var country = json.getString("country");
            var city = json.getString("city");
            sb.append(String.format(Utils.getApplication().getString(ResId.string.country_s), country)).append("\n")
            .append(String.format(Utils.getApplication().getString(ResId.string.city_s), city)).append("\n")
            .append(String.format(Utils.getApplication().getString(ResId.string.ip_s), ip)).append("\n");
        }
        var platform = (String) XposedHelpers.getObjectField(wamCall, "callPeerPlatform");
        if (platform != null) sb.append(String.format(Utils.getApplication().getString(ResId.string.platform_s), platform)).append("\n");
        var wppVersion = (String) XposedHelpers.getObjectField(wamCall, "callPeerAppVersion");
        if (wppVersion != null) sb.append(String.format(Utils.getApplication().getString(ResId.string.wpp_version_s), wppVersion)).append("\n");
        Utils.showNotification(Utils.getApplication().getString(ResId.string.call_information), sb.toString());
    }

    private void alwaysOnline() throws Exception {
        if (!prefs.getBoolean("always_online", false)) return;
        var stateChange = Unobfuscator.loadStateChangeMethod(classLoader);
        XposedBridge.hookMethod(stateChange, XC_MethodReplacement.DO_NOTHING);
    }


    private void doubleTapReaction() throws Exception {

        if (!prefs.getBoolean("doubletap2like", false)) return;

        var emoji = prefs.getString("doubletap2like_emoji", "👍");

        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));

        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var viewGroup = (View) param.thisObject;
                if (viewGroup == null) return;

                var onMultiClickListener = new OnMultiClickListener(2, 500) {
                    @Override
                    public void onMultiClick(View view) {
                        var reactionView = (ViewGroup) view.findViewById(Utils.getID("reactions_bubble_layout", "id"));
                        if (reactionView != null && reactionView.getVisibility() == View.VISIBLE) {
                            for (int i = 0; i < reactionView.getChildCount(); i++) {
                                if (reactionView.getChildAt(i) instanceof TextView textView) {
                                    if (textView.getText().toString().contains(emoji)) {
                                        WppCore.sendReaction("", param.args[2]);
                                        return;
                                    }
                                }
                            }
                        }
                        WppCore.sendReaction(emoji, param.args[2]);
                    }
                };
                viewGroup.setOnClickListener(onMultiClickListener);
            }
        });
    }

    private void stampCopiedMessage() throws Exception {
        if (!prefs.getBoolean("stamp_copied_message", false)) return;

        var copiedMessage = Unobfuscator.loadCopiedMessageMethod(classLoader);

        XposedBridge.hookMethod(copiedMessage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var Collection = (java.util.Collection) param.args[param.args.length - 1];
                param.args[param.args.length - 1] = new ArrayList<Object>(Collection) {
                    @Override
                    public int size() {
                        return 1;
                    }
                };
            }
        });
    }

    private void animationList() throws Exception {
        var animation = prefs.getString("animation_list", "default");

        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus));
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(field1));
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            @SuppressLint("ResourceType")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
                var view = (View) viewField.get(viewHolder);
                if (!Objects.equals(animation, "default")) {
                    view.startAnimation(AnimationUtil.getAnimation(animation));
                } else if (properties.containsKey("home_list_animation")) {
                    var animation = AnimationUtil.getAnimation(properties.getProperty("home_list_animation"));
                    if (animation != null) {
                        view.startAnimation(animation);
                    }
                }
            }
        });
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
        var method = ReflectionUtils.findAllMethodsUsingFilter(voicenoteClass, method1 -> method1.getParameterCount() == 4 && method1.getParameterTypes()[0] == int.class && method1.getReturnType().equals(void.class));
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

    private void filterItems(String filterItems) {
        var itens = filterItems.split("\n");
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


    private void hookProps() throws Exception {
        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean));
        var dataUsageActivityClass = XposedHelpers.findClass("com.whatsapp.settings.SettingsDataUsageActivity", classLoader);
        XposedBridge.hookMethod(methodPropsBoolean, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) param.args[param.args.length - 1];

                var propValue = propsBoolean.get(i);
                if (propValue != null) {
                    // Fix Bug in Settings Data Usage
                    switch (i) {
                        case 4023:
                            if (ReflectionUtils.isCalledFromClass(dataUsageActivityClass))
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

    private void hookMenuOptions(String filterChats) {
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
