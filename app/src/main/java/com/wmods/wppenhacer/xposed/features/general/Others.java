package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.BaseBundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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


        // Removido pois as não há necessidade de ficar em uma versão obsoleta.

//        var deprecatedMethod = Unobfuscator.loadDeprecatedMethod(loader);
//        logDebug(Unobfuscator.getMethodDescriptor(deprecatedMethod));
//
//        XposedBridge.hookMethod(deprecatedMethod, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) {
//                Date date = new Date(10554803081056L);
//                param.setResult(date);
//            }
//        });

        var novoTema = prefs.getBoolean("novotema", false);
        var menuWIcons = prefs.getBoolean("menuwicon", false);
        var newSettings = prefs.getBoolean("novaconfig", false);
        var filterChats = prefs.getString("chatfilter", null);
        var strokeButtons = prefs.getBoolean("strokebuttons", false);
        var outlinedIcons = prefs.getBoolean("outlinedicons", false);
        var showDnd = prefs.getBoolean("show_dndmode", false);
        var showFreezeLastSeen = prefs.getBoolean("show_freezeLastSeen", false);
        var removechannelRec = prefs.getBoolean("removechannel_rec", false);
        var separateGroups = prefs.getBoolean("separategroups", false);
        var filterSeen = prefs.getBoolean("filterseen", false);
        var fbstyle = prefs.getBoolean("fbstyle", false);
        var alertSticker = prefs.getBoolean("alertsticker", false);
        var channels = prefs.getBoolean("channels", false);
        var igstatus = prefs.getBoolean("igstatus", false);
        var metaai = prefs.getBoolean("metaai", false);
        var topnav = prefs.getBoolean("topnav", false);
        var proximity = prefs.getBoolean("proximity_audios", false);
        var adminGrp = prefs.getBoolean("admin_grp", false);
        var showOnline = prefs.getBoolean("showonline", false);
        var floatingMenu = prefs.getBoolean("floatingmenu", false);
        var filter_itens = prefs.getString("filter_itens", null);
        var disable_defemojis = prefs.getBoolean("disable_defemojis", false);

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

        if (metaai) {
            propsBoolean.put(8025, false);
            propsBoolean.put(6251, false);
            propsBoolean.put(7639, false);

        }

        propsInteger.put(8522, fbstyle ? 1 : 0);
        propsInteger.put(8521, fbstyle ? 1 : 0);
        propsInteger.put(3877, channels ? igstatus ? 2 : 0 : 2);


        hookProps();
        hookViewProfile();
        hookMenuOptions(newSettings, showFreezeLastSeen, showDnd, filterChats);

        if (removechannelRec) {
            hookChannels();
        }

        if (separateGroups) {
            hookChatFilters();
        }

        if (alertSticker) {
            hookStickers();
        }

        if (proximity) {
            var proximitySensorMethod = Unobfuscator.loadProximitySensorMethod(loader);
            XposedBridge.hookMethod(proximitySensorMethod, XC_MethodReplacement.DO_NOTHING);
        }

        if (adminGrp) {
            addgrpAdminIcon();
        }

        if (showOnline) {
            showOnline();
        }

        if (filter_itens != null) {
            filterItens(filter_itens);
        }

        if (disable_defemojis) {
            disable_defEmojis();
        }


    }


    private void disable_defEmojis() throws Exception {
        var defEmojiClass = Unobfuscator.loadDefEmojiClass(loader);
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

    private void showOnline() throws Exception {
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(loader);
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
                Utils.showToast(String.format(Utils.getApplication().getString(ResId.string.toast_online), name), Toast.LENGTH_SHORT);
            }
        });
    }

    private void addgrpAdminIcon() throws Exception {
        var jidFactory = Unobfuscator.loadJidFactory(loader);
        var grpAdmin1 = Unobfuscator.loadGroupAdminMethod(loader);
        var grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(loader);
        var hooked = new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = XposedHelpers.callMethod(param.thisObject, "getFMessage");
                var userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", loader);
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

    private void hookViewProfile() throws Exception {
        var loadProfileInfoField = Unobfuscator.loadProfileInfoField(loader);
        XposedHelpers.findAndHookMethod("com.whatsapp.profile.ViewProfilePhoto", loader, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var item = menu.add(0, 0, 0, "Save");
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                var icon = DesignUtils.getDrawableByName("ic_action_download_wds");
                icon.setTint(Color.WHITE);
                item.setIcon(icon);
                item.setOnMenuItemClickListener(menuItem -> {
                    var subCls = param.thisObject.getClass().getSuperclass();
                    var field = Unobfuscator.getFieldByType(subCls, loadProfileInfoField.getDeclaringClass());
                    var jidObj = ReflectionUtils.getField(loadProfileInfoField, ReflectionUtils.getField(field, param.thisObject));
                    var jid = WppCore.stripJID(WppCore.getRawString(jidObj));
                    var file = WppCore.getContactPhotoFile(jid);
                    var destPath = Utils.getDestination(prefs, file, "Profile Photo");
                    destPath = destPath.endsWith(".jpg") ? destPath : destPath + "pg";
                    if (Utils.copyFile(file, new File(destPath))) {
                        Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.saved_to) + destPath, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.error_when_saving_try_again), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }
        });
    }

    private void hookStickers() throws Exception {
        var sendStickerMethod = Unobfuscator.loadSendStickerMethod(loader);
        XposedBridge.hookMethod(sendStickerMethod, new XC_MethodHook() {
            private Unhook unhooked;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                unhooked = XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", View.OnClickListener.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View.OnClickListener mCaptureOnClickListener = (View.OnClickListener) param.args[0];
                        if (mCaptureOnClickListener == null) return;
                        if (!(param.thisObject instanceof ViewGroup)) return;
                        param.args[0] = (View.OnClickListener) view -> {
                            var context = view.getContext();
                            var dialog = new AlertDialogWpp(view.getContext());
                            dialog.setTitle(context.getString(ResId.string.send_sticker));

                            var stickerView = (ImageView) ((ViewGroup) view).getChildAt(0);
                            LinearLayout linearLayout = new LinearLayout(context);
                            linearLayout.setOrientation(LinearLayout.VERTICAL);
                            linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                            var padding = Utils.dipToPixels(16);
                            linearLayout.setPadding(padding, padding, padding, padding);
                            var image = new ImageView(context);
                            var size = Utils.dipToPixels(72);
                            var params = new LinearLayout.LayoutParams(size, size);
                            params.bottomMargin = padding;
                            image.setLayoutParams(params);
                            image.setImageDrawable(stickerView.getDrawable());
                            linearLayout.addView(image);

                            TextView text = new TextView(context);
                            text.setText(context.getString(ResId.string.do_you_want_to_send_sticker));
                            text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                            linearLayout.addView(text);


                            dialog.setView(linearLayout);
                            dialog.setPositiveButton(context.getString(ResId.string.send), (dialog1, which) -> mCaptureOnClickListener.onClick(view));
                            dialog.setNegativeButton(context.getString(ResId.string.cancel), null);
                            dialog.show();
                        };
                    }
                });
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                unhooked.unhook();
            }

        });
    }

    private void hookChatFilters() throws Exception {
        var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(loader);
        XposedBridge.hookAllConstructors(filterAdaperClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var argResult = IntStream.range(0, param.args.length).mapToObj(i -> new Pair<>(i, param.args[i])).filter(p -> p.second instanceof List).findFirst().orElse(null);
                if (argResult != null) {
                    var newList = new ArrayList<Object>((List) argResult.second);
                    newList.removeIf(item -> {
                        var name = XposedHelpers.getObjectField(item, "A01");
                        return name == null || name == "CONTACTS_FILTER" || name == "GROUP_FILTER";
                    });
                    param.args[argResult.first] = newList;
                }
            }
        });
        var methodSetFilter = Arrays.stream(filterAdaperClass.getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(int.class)).findFirst().orElse(null);

        XposedBridge.hookMethod(methodSetFilter, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var index = (int) param.args[0];
                var field = Unobfuscator.getFieldByType(methodSetFilter.getDeclaringClass(), List.class);
                var list = (List) field.get(param.thisObject);
                if (list == null || index >= list.size()) {
                    param.setResult(null);
                }
            }
        });
    }

    private void hookChannels() throws Exception {
        var removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(loader);
        XposedBridge.hookAllConstructors(removeChannelRecClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length > 0 && param.args[0] instanceof List list) {
                    if (list.isEmpty()) return;
                    list.clear();
                }
            }
        });
    }

    private void hookMenuOptions(boolean newSettings, boolean showFreezeLastSeen, boolean showDnd, String filterChats) {
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", loader, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @SuppressLint("ApplySharedPref")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Menu menu = (Menu) param.args[0];
                Activity home = (Activity) param.thisObject;
                if (prefs.getBoolean("restartbutton", true)) {
                    var iconDraw = DesignUtils.getDrawableByName("vec_account_switcher");
                    iconDraw.setTint(0xff8696a0);
                    var itemMenu = menu.add(0, 0, 0, ResId.string.restart_whatsapp).setIcon(iconDraw).setOnMenuItemClickListener(item -> {
                        Utils.doRestart(home);
                        return true;
                    });
                    if (newSettings) {
                        itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    }
                }
                if (showFreezeLastSeen) {
                    InsertFreezeLastSeenOption(menu, home);
                }
                if (showDnd) {
                    InsertDNDOption(menu, home);
                } else {
                    Utils.getApplication().getSharedPreferences(Utils.getApplication().getPackageName() + "_mdgwa_preferences", Context.MODE_PRIVATE).edit().putBoolean("dndmode", false).commit();
                }
            }
        });


        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", loader, "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
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

    private void hookProps() throws Exception {
        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean));
        var dataUsageActivityClass = XposedHelpers.findClass("com.whatsapp.settings.SettingsDataUsageActivity", loader);
        var workManagerClass = Unobfuscator.loadWorkManagerClass(loader);
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

        var methodPropsInteger = Unobfuscator.loadPropsIntegerMethod(loader);

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

//    private static void restartApp(Activity home) {
//        Intent intent = Utils.getApplication().getPackageManager().getLaunchIntentForPackage(Utils.getApplication().getPackageName());
//        if (intent != null) {
//            home.finishAffinity();
//            Utils.getApplication().startActivity(intent);
//        }
//        Runtime.getRuntime().exit(0);
//    }

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
    private static void InsertDNDOption(Menu menu, Activity home) {
        var dndmode = WppCore.getPrivBoolean("dndmode", false);
        int iconDraw;
        iconDraw = Utils.getID(dndmode ? "ic_location_nearby_disabled" : "ic_location_nearby", "drawable");
        var item = menu.add(0, 0, 0, "Dnd Mode " + dndmode);
        item.setIcon(iconDraw);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
    private static void InsertFreezeLastSeenOption(Menu menu, Activity home) {
        final boolean freezelastseen = WppCore.getPrivBoolean("freezelastseen", false);
        MenuItem item = menu.add(0, 0, 0, "Freeze Last Seen");
        item.setIcon(freezelastseen ? ResId.drawable.eye_disabled : ResId.drawable.eye_enabled);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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

    @NonNull
    @Override
    public String getPluginName() {
        return "Others";
    }
}
