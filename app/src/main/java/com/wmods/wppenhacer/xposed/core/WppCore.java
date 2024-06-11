package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class WppCore {

    private static Object mainActivity;
    private static Method getContactMethod;
    private static Class<?> mGenJidClass;
    private static Method mGenJidMethod;
    private static Class bottomDialog;
    @SuppressLint("StaticFieldLeak")
    private static Activity mConversation;
    private static Field convChatField;
    private static Field chatJidField;

    private static final HashSet<ObjectOnChangeListener> listenerChat = new HashSet<>();
    private static final HashMap<Class, List<OnMenuCreate>> listenerMenu = new HashMap<>();
    private static Object mContactManager;
    private static SharedPreferences privPrefs;
    private static Object mStartUpConfig;
    private static Object mActionUser;

    public static void addMenuItemClass(Class<?> aClass, OnMenuCreate listener) {
        var list = listenerMenu.computeIfAbsent(aClass, k -> new ArrayList<>());
        list.add(listener);
    }

    public static void addMenuItemString(String className, OnMenuCreate listener) {
        var classLoader = Utils.getApplication().getClassLoader();
        var aClass = XposedHelpers.findClass(className, classLoader);
        var list = listenerMenu.computeIfAbsent(aClass, k -> new ArrayList<>());
        list.add(listener);
    }

    public static Object getConversation() {
        return mConversation;
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
                var index = ReflectionUtils.findIndexOfType(senderMethod.getParameterTypes(), String.class);
                newObject[index - 1] = 0;
                newObject[index] = message;
                newObject[newObject.length - 1] = false;
                newObject[newObject.length - 2] = false;
                newObject[newObject.length - 3] = false;
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


    public interface ObjectOnChangeListener {
        void onChange(Object object, String type);

    }

    public static void Initialize(ClassLoader loader) throws Exception {
        privPrefs = Utils.getApplication().getSharedPreferences("WaGlobal", Context.MODE_PRIVATE);

        // init Main activity
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.whatsapp.HomeActivity", loader), "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mainActivity = param.thisObject;
            }
        });

        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.whatsapp.HomeActivity", loader), "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mainActivity = param.thisObject;
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (Class<?> aClass : listenerMenu.keySet()) {
                    if (!aClass.isInstance(param.thisObject)) return;
                    for (OnMenuCreate listener : listenerMenu.get(aClass)) {
                        listener.onBeforeCreate((Activity) param.thisObject, (Menu) param.args[0]);
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                for (Class<?> aClass : listenerMenu.keySet()) {
                    if (!aClass.isInstance(param.thisObject)) return;
                    for (OnMenuCreate listener : listenerMenu.get(aClass)) {
                        listener.onAfterCreate((Activity) param.thisObject, (Menu) param.args[0]);
                    }
                }
            }
        });

        // init ContactManager
        getContactMethod = Unobfuscator.loadGetContactInfoMethod(loader);
        XposedBridge.hookAllConstructors(getContactMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mContactManager = param.thisObject;
            }
        });

        // init UserJID
        var mSendReadClass = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", loader);
        var subClass = Arrays.stream(mSendReadClass.getConstructors()).filter(c -> c.getParameterTypes().length == 8).findFirst().orElse(null).getParameterTypes()[0];
        mGenJidClass = Arrays.stream(subClass.getFields()).filter(field -> Modifier.isStatic(field.getModifiers())).findFirst().orElse(null).getType();
        mGenJidMethod = Arrays.stream(mGenJidClass.getMethods()).filter(m -> m.getParameterCount() == 1 && !Modifier.isStatic(m.getModifiers())).findFirst().orElse(null);
        // Bottom Dialog
        bottomDialog = Unobfuscator.loadDialogViewClass(loader);

        // Conversation
        var onStartMethod = Unobfuscator.loadAntiRevokeOnStartMethod(loader);
        var onResumeMethod = Unobfuscator.loadAntiRevokeOnResumeMethod(loader);
        convChatField = Unobfuscator.loadAntiRevokeConvChatField(loader);
        chatJidField = Unobfuscator.loadAntiRevokeChatJidField(loader);
        XposedBridge.hookMethod(onStartMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                mConversation = (Activity) param.thisObject;
                for (ObjectOnChangeListener listener : listenerChat) {
                    listener.onChange(mConversation, "onStartConversation");
                }
            }
        });

        XposedBridge.hookMethod(onResumeMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                mConversation = (Activity) param.thisObject;
                for (ObjectOnChangeListener listener : listenerChat) {
                    listener.onChange(mConversation, "onResumeConversation");
                }
            }
        });

        // StartUpPrefs
        var startPrefsConfig = Unobfuscator.loadStartPrefsConfig(loader);
        XposedBridge.hookMethod(startPrefsConfig, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mStartUpConfig = param.thisObject;
            }
        });

        var actionUser = Unobfuscator.loadActionUser(loader);
        XposedBridge.hookAllConstructors(actionUser, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mActionUser = param.thisObject;
                XposedBridge.log("mActionUser: " + mActionUser);
            }
        });


    }

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

    public static Object getContactManager() {
        return mContactManager;
    }

    @Nullable
    public static String getContactName(Object userJid) {
        try {
            var contact = getContactMethod.invoke(mContactManager, userJid);
            if (contact != null) {
                var stringField = Arrays.stream(contact.getClass().getDeclaredFields()).filter(f -> f.getType().equals(String.class)).toArray(Field[]::new);
                return (String) stringField[3].get(contact);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
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
        if (mConversation == null) return null;
        var chatField = XposedHelpers.getObjectField(mConversation, convChatField.getName());
        var chatJidObj = XposedHelpers.getObjectField(chatField, chatJidField.getName());
        return getRawString(chatJidObj);
    }

    public static String stripJID(String str) {
        try {
            return (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast")) ? str.substring(0, str.indexOf("@")) : str;
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
        var startup_prefs = ((Context) mainActivity).getSharedPreferences("startup_prefs", Context.MODE_PRIVATE);
        return startup_prefs.getString("push_name", "WhatsApp");
    }

    public static String getMyNumber() {
        var mainPrefs = getMainPrefs();
        return mainPrefs.getString("registration_jid", "");
    }

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

    public static Activity getMainActivity() {
        return (Activity) mainActivity;
    }


    public static Dialog createDialog(Context context) {
        return (Dialog) XposedHelpers.newInstance(bottomDialog, context, 0);
    }

    public static Activity getCurrenConversation() {
        return mConversation;
    }

    @SuppressLint("ApplySharedPref")
    public static void setPrivString(String key, String value) {
        privPrefs.edit().putString(key, value).commit();
    }

    public static String getPrivString(String key, String defaultValue) {
        return privPrefs.getString(key, defaultValue);
    }

    public static void removePrivKey(String s) {
        if (s != null && privPrefs.contains(s))
            privPrefs.edit().remove(s).commit();
    }

    public abstract static class OnMenuCreate {

        public void onBeforeCreate(Activity activity, Menu menu) {

        }

        public void onAfterCreate(Activity activity, Menu menu) {

        }

    }

    @SuppressLint("ApplySharedPref")
    public static void setPrivBoolean(String key, boolean value) {
        privPrefs.edit().putBoolean(key, value).commit();
    }

    public static boolean getPrivBoolean(String key, boolean defaultValue) {
        return privPrefs.getBoolean(key, defaultValue);
    }

    public static void addListenerChat(ObjectOnChangeListener listener) {
        listenerChat.add(listener);
    }

}
