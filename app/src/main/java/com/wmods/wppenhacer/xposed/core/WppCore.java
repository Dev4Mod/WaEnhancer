package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.views.dialog.BottomDialogWpp;
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.bridge.client.BaseClient;
import com.wmods.wppenhacer.xposed.bridge.client.BridgeClient;
import com.wmods.wppenhacer.xposed.bridge.client.ProviderClient;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class WppCore {

    static final HashSet<ActivityChangeState> listenerAcitivity = new HashSet<>();
    @SuppressLint("StaticFieldLeak")
    static Activity mCurrentActivity;
    static LinkedHashSet<Activity> activities = new LinkedHashSet<>();
    private static Class<?> mGenJidClass;
    private static Method mGenJidMethod;
    private static Class bottomDialog;
    private static Field convChatField;
    private static Field chatJidField;
    private static SharedPreferences privPrefs;
    private static Object mStartUpConfig;
    private static Object mActionUser;
    private static SQLiteDatabase mWaDatabase;
    public static BaseClient client;
    private static Object mCachedMessageStore;


    public static void Initialize(ClassLoader loader) throws Exception {
        privPrefs = Utils.getApplication().getSharedPreferences("WaGlobal", Context.MODE_PRIVATE);

        // init UserJID
        var mSendReadClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", loader);
        var subClass = ReflectionUtils.findConstructorUsingFilter(mSendReadClass, (constructor) -> constructor.getParameterCount() == 8).getParameterTypes()[0];
        mGenJidClass = ReflectionUtils.findFieldUsingFilter(subClass, (field) -> Modifier.isStatic(field.getModifiers())).getType();
        mGenJidMethod = ReflectionUtils.findMethodUsingFilter(mGenJidClass, (method) -> method.getParameterCount() == 1 && !Modifier.isStatic(method.getModifiers()));
        // Bottom Dialog
        bottomDialog = Unobfuscator.loadDialogViewClass(loader);

        convChatField = Unobfuscator.loadAntiRevokeConvChatField(loader);
        chatJidField = Unobfuscator.loadAntiRevokeChatJidField(loader);

        // StartUpPrefs
        var startPrefsConfig = Unobfuscator.loadStartPrefsConfig(loader);
        XposedBridge.hookMethod(startPrefsConfig, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mStartUpConfig = param.thisObject;
            }
        });

        // ActionUser
        var actionUser = Unobfuscator.loadActionUser(loader);
        XposedBridge.hookAllConstructors(actionUser, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mActionUser = param.thisObject;
            }
        });

        // CachedMessageStore
        var cachedMessageStore = Unobfuscator.loadCachedMessageStore(loader);
        XposedBridge.hookAllConstructors(cachedMessageStore, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mCachedMessageStore = param.thisObject;
            }
        });

        // Load wa database
        loadWADatabase();
        initBridge(Utils.getApplication());
    }

    public static void initBridge(Context context) throws Exception {
        var prefsCacheHooks = UnobfuscatorCache.getInstance().sPrefsCacheHooks;
        int preferredOrder = prefsCacheHooks.getInt("preferredOrder", 1); // 0 for ProviderClient first, 1 for BridgeClient first

        boolean connected = false;
        if (preferredOrder == 0) {
            if (tryConnectBridge(new ProviderClient(context))) {
                connected = true;
            } else if (tryConnectBridge(new BridgeClient(context))) {
                connected = true;
                preferredOrder = 1; // Update preference to BridgeClient first
            }
        } else {
            if (tryConnectBridge(new BridgeClient(context))) {
                connected = true;
            } else if (tryConnectBridge(new ProviderClient(context))) {
                connected = true;
                preferredOrder = 0; // Update preference to ProviderClient first
            }
        }

        if (!connected) {
            throw new Exception(context.getString(ResId.string.bridge_error));
        }

        // Update the preferred order if it changed
        prefsCacheHooks.edit().putInt("preferredOrder", preferredOrder).apply();
    }


    private static boolean tryConnectBridge(BaseClient baseClient) throws Exception {
        try {
            XposedBridge.log("Trying to connect to " + baseClient.getClass().getSimpleName());
            client = baseClient;
            CompletableFuture<Boolean> canLoadFuture = baseClient.connect();
            Boolean canLoad = canLoadFuture.get();
            if (!canLoad) throw new Exception();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static void sendMessage(String number, String message) {
        try {
            var senderMethod = ReflectionUtils.findMethodUsingFilterIfExists(mActionUser.getClass(), (method) -> List.class.isAssignableFrom(method.getReturnType()) && ReflectionUtils.findIndexOfType(method.getParameterTypes(), String.class) != -1);
            if (senderMethod != null) {
                var userJid = createUserJid(number + "@s.whatsapp.net");
                if (userJid == null) {
                    Utils.showToast("UserJID not found", Toast.LENGTH_SHORT);
                    return;
                }
                var newObject = new Object[senderMethod.getParameterCount()];
                for (int i = 0; i < newObject.length; i++) {
                    var param = senderMethod.getParameterTypes()[i];
                    newObject[i] = ReflectionUtils.getDefaultValue(param);
                }
                var index = ReflectionUtils.findIndexOfType(senderMethod.getParameterTypes(), String.class);
                newObject[index] = message;
                var index2 = ReflectionUtils.findIndexOfType(senderMethod.getParameterTypes(), List.class);
                newObject[index2] = Collections.singletonList(userJid);
                senderMethod.invoke(mActionUser, newObject);
                Utils.showToast("Message sent to " + number, Toast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            Utils.showToast("Error in sending message:" + e.getMessage(), Toast.LENGTH_SHORT);
            XposedBridge.log(e);
        }
    }

    public static void sendReaction(String s, Object objMessage) {
        try {
            var senderMethod = ReflectionUtils.findMethodUsingFilter(mActionUser.getClass(), (method) -> method.getParameterCount() == 3 && Arrays.equals(method.getParameterTypes(), new Class[]{FMessageWpp.TYPE, String.class, boolean.class}));
            senderMethod.invoke(mActionUser, objMessage, s, !TextUtils.isEmpty(s));
        } catch (Exception e) {
            Utils.showToast("Error in sending reaction:" + e.getMessage(), Toast.LENGTH_SHORT);
            XposedBridge.log(e);
        }
    }

    public static void loadWADatabase() {
        if (mWaDatabase != null) return;
        var dataDir = Utils.getApplication().getFilesDir().getParentFile();
        var database = new File(dataDir, "databases/wa.db");
        if (database.exists()) {
            mWaDatabase = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        }
    }


    public static Activity getCurrentActivity() {
        return mCurrentActivity;
    }

//    public static Activity getActivityBySimpleName(String name) {
//        for (var activity : activities) {
//            if (activity.getClass().getSimpleName().equals(name)) {
//                return activity;
//            }
//        }
//        return null;
//    }


    public static int getDefaultTheme() {
        if (mStartUpConfig != null) {
            var result = ReflectionUtils.findMethodUsingFilterIfExists(mStartUpConfig.getClass(), (method) -> method.getParameterCount() == 0 && method.getReturnType() == int.class);
            if (result != null) {
                var value = ReflectionUtils.callMethod(result, mStartUpConfig);
                if (value != null) return (int) value;
            }
        }
        var startup_prefs = Utils.getApplication().getSharedPreferences("startup_prefs", Context.MODE_PRIVATE);
        return startup_prefs.getInt("night_mode", 0);
    }

    @NonNull
    public static String getContactName(Object userJid) {
        loadWADatabase();
        if (mWaDatabase == null || userJid == null) return "";
        String name = getSContactName(userJid, false);
        if (!TextUtils.isEmpty(name)) return name;
        return getWppContactName(userJid);
    }

    @NonNull
    public static String getSContactName(Object userJid, boolean saveOnly) {
        loadWADatabase();
        if (mWaDatabase == null || userJid == null) return "";
        String selection;
        if (saveOnly) {
            selection = "jid = ? AND raw_contact_id > 0";
        } else {
            selection = "jid = ?";
        }
        String name = null;
        var rawJid = getRawString(userJid);
        var cursor = mWaDatabase.query("wa_contacts", new String[]{"display_name"}, selection, new String[]{rawJid}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0);
            cursor.close();
        }
        return name == null ? "" : name;
    }

    @NonNull
    public static String getWppContactName(Object userJid) {
        loadWADatabase();
        if (mWaDatabase == null || userJid == null) return "";
        String name = null;
        var rawJid = getRawString(userJid);
        var cursor2 = mWaDatabase.query("wa_vnames", new String[]{"verified_name"}, "jid = ?", new String[]{rawJid}, null, null, null);
        if (cursor2 != null && cursor2.moveToFirst()) {
            name = cursor2.getString(0);
            cursor2.close();
        }
        return name == null ? "" : name;
    }

    public static Object getFMessageFromKey(Object messageKey) {
        if (messageKey == null) return null;
        try {
            var methodResult = ReflectionUtils.findMethodUsingFilter(mCachedMessageStore.getClass(), (method) -> method.getParameterCount() == 1 && FMessageWpp.Key.TYPE.isAssignableFrom(method.getParameterTypes()[0]));
            return ReflectionUtils.callMethod(methodResult, mCachedMessageStore, messageKey);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }


    public static Object createUserJid(String rawjid) {
        var genInstance = XposedHelpers.newInstance(mGenJidClass);
        try {
            return mGenJidMethod.invoke(genInstance, rawjid);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public static String getRawString(Object userjid) {
        if (userjid == null) return null;
        return (String) XposedHelpers.callMethod(userjid, "getRawString");
    }

    public static boolean isGroup(String str) {
        if (str == null) return false;
        return str.contains("-") || str.contains("@g.us") || (!str.contains("@") && str.length() > 16);
    }

    public static String getCurrentRawJID() {
        var conversation = getCurrentConversation();
        if (conversation == null) return null;
        var chatField = XposedHelpers.getObjectField(conversation, convChatField.getName());
        if (chatField == null) return null;
        var chatJidObj = XposedHelpers.getObjectField(chatField, chatJidField.getName());
        if (chatJidObj == null) return null;
        return getRawString(chatJidObj);
    }

    public static String stripJID(String str) {
        try {
            if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                return str.substring(0, str.indexOf("."));
            } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast")) {
                return str.substring(0, str.indexOf("@"));
            }
            return str;
        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
            return str;
        }
    }

    public static Drawable getContactPhotoDrawable(String jid) {
        var file = getContactPhotoFile(jid);
        if (file == null) return null;
        return Drawable.createFromPath(file.getAbsolutePath());
    }

    public static File getContactPhotoFile(String jid) {
        String datafolder = Utils.getApplication().getCacheDir().getParent() + "/";
        File file = new File(datafolder + "/cache/" + "Profile Pictures" + "/" + stripJID(jid) + ".jpg");
        if (!file.exists())
            file = new File(datafolder + "files" + "/" + "Avatars" + "/" + jid + ".j");
        if (file.exists()) return file;
        return null;
    }

    public static String getMyName() {
        var startup_prefs = Utils.getApplication().getSharedPreferences("startup_prefs", Context.MODE_PRIVATE);
        return startup_prefs.getString("push_name", "WhatsApp");
    }

//    public static String getMyNumber() {
//        var mainPrefs = getMainPrefs();
//        return mainPrefs.getString("registration_jid", "");
//    }

    public static SharedPreferences getMainPrefs() {
        return Utils.getApplication().getSharedPreferences(Utils.getApplication().getPackageName() + "_preferences_light", Context.MODE_PRIVATE);
    }


    public static String getMyBio() {
        var mainPrefs = getMainPrefs();
        return mainPrefs.getString("my_current_status", "");
    }

    public static Drawable getMyPhoto() {
        String datafolder = Utils.getApplication().getCacheDir().getParent() + "/";
        File file = new File(datafolder + "files" + "/" + "me.jpg");
        if (file.exists()) return Drawable.createFromPath(file.getAbsolutePath());
        return null;
    }

    public static BottomDialogWpp createBottomDialog(Context context) {
        return new BottomDialogWpp((Dialog) XposedHelpers.newInstance(bottomDialog, context, 0));
    }

    @Nullable
    public static Activity getCurrentConversation() {
        if (mCurrentActivity == null) return null;
        Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation", mCurrentActivity.getClassLoader());
        if (conversation.isInstance(mCurrentActivity)) return mCurrentActivity;

        // for tablet UI, they're using HomeActivity instead of Conversation
        // TODO: Add more checks for ConversationFragment
        Class<?> home = XposedHelpers.findClass("com.whatsapp.HomeActivity", mCurrentActivity.getClassLoader());
        if (mCurrentActivity.getResources().getConfiguration().smallestScreenWidthDp >= 600 && home.isInstance(mCurrentActivity))
            return mCurrentActivity;
        return null;
    }

    @SuppressLint("ApplySharedPref")
    public static void setPrivString(String key, String value) {
        privPrefs.edit().putString(key, value).commit();
    }

    public static String getPrivString(String key, String defaultValue) {
        return privPrefs.getString(key, defaultValue);
    }

    public static JSONObject getPrivJSON(String key, JSONObject defaultValue) {
        var jsonStr = privPrefs.getString(key, null);
        if (jsonStr == null) return defaultValue;
        try {
            return new JSONObject(jsonStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void setPrivJSON(String key, JSONObject value) {
        privPrefs.edit().putString(key, value == null ? null : value.toString()).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static void removePrivKey(String s) {
        if (s != null && privPrefs.contains(s))
            privPrefs.edit().remove(s).commit();
    }


    @SuppressLint("ApplySharedPref")
    public static void setPrivBoolean(String key, boolean value) {
        privPrefs.edit().putBoolean(key, value).commit();
    }

    public static boolean getPrivBoolean(String key, boolean defaultValue) {
        return privPrefs.getBoolean(key, defaultValue);
    }

    public static void addListenerActivity(ActivityChangeState listener) {
        listenerAcitivity.add(listener);
    }

    public static WaeIIFace getClientBridge() throws Exception {
        if (client == null || client.getService() == null || !client.getService().asBinder().isBinderAlive() || !client.getService().asBinder().pingBinder()) {
            WppCore.getCurrentActivity().runOnUiThread(() -> {
                var dialog = new AlertDialogWpp(WppCore.getCurrentActivity());
                dialog.setTitle("Bridge Error");
                dialog.setMessage("The Connection with WaEnhancer was lost, it is necessary to reconnect with WaEnhancer in order to reestablish the connection.");
                dialog.setPositiveButton("reconnect", (dialog1, which) -> {
                    client.tryReconnect();
                    dialog.dismiss();
                });
                dialog.setNegativeButton("cancel", null);
                dialog.show();
            });
            throw new Exception("Failed connect to Bridge");
        }
        return client.getService();
    }


    public interface ActivityChangeState {

        void onChange(Activity activity, ChangeType type);

        enum ChangeType {
            START, END, RESUME, PAUSE
        }
    }


}
