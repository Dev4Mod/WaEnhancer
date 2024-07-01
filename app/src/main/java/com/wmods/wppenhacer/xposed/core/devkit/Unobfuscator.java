package com.wmods.wppenhacer.xposed.core.devkit;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.UsingFieldData;
import org.luckypray.dexkit.util.DexSignUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Unobfuscator {

    private static DexKitBridge dexkit;

    public static final String BUBBLE_COLORS_BALLOON_INCOMING_NORMAL = "balloon_incoming_normal";
    public static final String BUBBLE_COLORS_BALLOON_INCOMING_NORMAL_EXT = "balloon_incoming_normal_ext";
    public static final String BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL = "balloon_outgoing_normal";
    public static final String BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL_EXT = "balloon_outgoing_normal_ext";
    public static final HashMap<String, Object> cache = new HashMap<>();

    static {
        System.loadLibrary("dexkit");
    }

    public static boolean initDexKit(String path) {
        try {
            dexkit = DexKitBridge.create(path);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // TODO: Functions to find classes and methods
    public static Method findFirstMethodUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) throws Exception {
        MethodMatcher matcher = new MethodMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        MethodDataList result = dexkit.findMethod(new FindMethod().matcher(matcher));
        if (result.isEmpty()) return null;
        for (MethodData methodData : result) {
            if (methodData.isMethod()) return methodData.getMethodInstance(classLoader);
        }
        throw new NoSuchMethodException();
    }

    public static Method[] findAllMethodUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) {
        MethodMatcher matcher = new MethodMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        MethodDataList result = dexkit.findMethod(new FindMethod().matcher(matcher));
        if (result.isEmpty()) return new Method[0];
        return result.stream().filter(MethodData::isMethod).map(methodData -> {
            try {
                return methodData.getMethodInstance(classLoader);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }).filter(Objects::nonNull).toArray(Method[]::new);
    }

    public static Class<?> findFirstClassUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) throws Exception {
        var matcher = new ClassMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        var result = dexkit.findClass(new FindClass().matcher(matcher));
        if (result.isEmpty()) return null;
        return result.get(0).getInstance(classLoader);
    }

    public static Field getFieldByType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getDeclaredFields()).filter(f -> f.getType().equals(type)).findFirst().orElse(null);
    }

    public static Field getFieldByExtendType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getFields()).filter(f -> type.isAssignableFrom(f.getType())).findFirst().orElse(null);

    }

    public static String getMethodDescriptor(Method method) {
        if (method == null) return null;
        return method.getDeclaringClass().getName() + "->" + method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
    }


    public static String getConstructorDescriptor(Constructor constructor) {
        if (constructor == null) return null;
        return constructor.getDeclaringClass().getName() + "->" + constructor.getName() + "(" + Arrays.stream(constructor.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
    }

    public static String getFieldDescriptor(Field field) {
        return field.getDeclaringClass().getName() + "->" + field.getName() + ":" + field.getType().getName();
    }

    public static boolean isCalledFromClass(Class<?> cls) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(cls.getName()))
                return true;
        }
        return false;
    }

    public static boolean isCalledFromMethod(Method method) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(method.getDeclaringClass().getName()) && stackTraceElement.getMethodName().equals(method.getName()))
                return true;
        }
        return false;
    }


    // TODO: Classes and Methods for FreezeSeen
    public static Method loadFreezeSeenMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "presencestatemanager/setAvailable/new-state")));
    }

    // TODO: Classes and Methods for GhostMode
    public static Method loadGhostModeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "HandleMeComposing/sendComposing");
            if (method == null) throw new Exception("GhostMode method not found");
            if (method.getParameterTypes().length > 2 && method.getParameterTypes()[2] == int.class)
                return method;
            throw new Exception("GhostMode method not found parameter type");
        });
    }

    // TODO: Classes and Methods for Receipt

    public static Method loadReceiptMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method[] methods = findAllMethodUsingStrings(classLoader, StringMatchType.Equals, "privacy_token", "false", "receipt");
            var deviceJidClass = XposedHelpers.findClass("com.whatsapp.jid.DeviceJid", classLoader);
            Method bestMethod = Arrays.stream(methods).filter(method -> method.getParameterTypes().length > 1 && method.getParameterTypes()[1] == deviceJidClass).findFirst().orElse(null);
            if (bestMethod == null) throw new Exception("Receipt method not found");
            return bestMethod;
        });
    }

    public static Method loadReceiptOutsideChat(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = loadReceiptMethod(classLoader);
            if (method == null) throw new Exception("Receipt method not found");
            var classData = dexkit.getClassData(method.getDeclaringClass());
            if (classData == null) throw new Exception("Receipt method not found");
            var methodResult = classData.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("sender")));
            if (methodResult.isEmpty()) throw new Exception("Receipt method not found");
            return methodResult.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadReceiptInChat(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = loadReceiptMethod(classLoader);
            var methodDataList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("callCreatorJid").addUsingString("reject").addUsingNumber(6175).addInvoke(DexSignUtil.getMethodDescriptor(method))));
            if (methodDataList.isEmpty()) throw new Exception("Receipt method not found");
            return methodDataList.get(0).getMethodInstance(classLoader);
        });
    }

    // TODO: Classes and Methods for HideForward

    public static Method loadForwardTagMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> messageInfoClass = loadFMessageClass(classLoader);
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("chatInfo/incrementUnseenImportantMessageCount")));
            if (methodList.isEmpty()) throw new Exception("ForwardTag method support not found");
            var invokes = methodList.get(0).getInvokes();
            for (var invoke : invokes) {
                var method = invoke.getMethodInstance(classLoader);
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == int.class
                        && method.getDeclaringClass() == messageInfoClass
                        && method.getReturnType() == void.class) {
                    return method;
                }
            }
            throw new Exception("ForwardTag method not found");
        });
    }

    public static Class<?> loadForwardClassMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "UserActions/userActionForwardMessage"));
    }


    // TODO: Classes and Methods for HideView

    public static Method loadHideViewSendReadJob(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var classData = dexkit.getClassData(XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", classLoader));
            var methodResult = classData.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("receipt", StringMatchType.Equals)));
            if (methodResult.isEmpty()) {
                methodResult = classData.getSuperClass().findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("receipt", StringMatchType.Equals)));
            }
            if (methodResult.isEmpty()) throw new Exception("HideViewSendReadJob method not found");
            return methodResult.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadHideViewInChatMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "ReadReceipts/PrivacyTokenDecisionNotComputed");
            if (method == null) throw new Exception("HideViewInChat method not found");
            return method;
        });
    }

    public static Method loadHideViewMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "privacy_token", "false", "recipient");
            if (method == null) throw new Exception("HideViewMethod method not found");
            return method;
        });
    }

    public static Method loadHideViewAudioMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageStatusStore/update/nosuchmessage");
            if (result == null) throw new Exception("HideViewAudio method not found");
            return result;
        });
    }

    public static Class<?> loadFMessageClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var messageClass = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "FMessage/getSenderUserJid/key.id");
            if (messageClass == null) throw new Exception("Message class not found");
            return messageClass;
        });
    }

    // TODO: Classes and Methods for XChatFilter

    public static Method loadTabListMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> classMain = findFirstClassUsingStrings(classLoader, StringMatchType.Equals, "mainContainer");
            if (classMain == null) throw new Exception("mainContainer class not found");
            Method method = Arrays.stream(classMain.getMethods()).filter(m -> m.getName().equals("onCreate")).findFirst().orElse(null);
            if (method == null) throw new Exception("onCreate method not found");
            return method;
        });
    }

    public static Method loadGetTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Invalid tab id: 600");
            if (result == null) throw new Exception("GetTab method not found");
            return result;
        });
    }

    public static Method loadTabFragmentMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> clsFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
            Method result = Arrays.stream(clsFrag.getDeclaredMethods()).filter(m -> m.getParameterTypes().length == 0 && m.getReturnType().equals(List.class)).findFirst().orElse(null);
            if (result == null) throw new Exception("TabFragment method not found");
            return result;
        });
    }

    public static Method loadTabNameMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method tabListMethod = loadGetTabMethod(classLoader);
            Class<?> cls = tabListMethod.getDeclaringClass();
            if (Modifier.isAbstract(cls.getModifiers())) {
                var findClass = dexkit.findClass(new FindClass().matcher(new ClassMatcher().superClass(cls.getName()).addUsingString("The item position should be less")));
                cls = findClass.get(0).getInstance(classLoader);
            }
            Method result = Arrays.stream(cls.getMethods()).filter(m -> m.getParameterTypes().length == 1 && m.getReturnType().equals(String.class)).findFirst().orElse(null);
            if (result == null) throw new Exception("TabName method not found");
            return result;
        });
    }

    public static Method loadFabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> cls = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
            List<ClassData> classes = List.of(dexkit.getClassData(cls));
            var result = dexkit.findMethod(new FindMethod().searchInClass(classes).matcher(new MethodMatcher().paramCount(0).usingNumbers(200).returnType(int.class)));
            if (result.isEmpty()) throw new Exception("Fab method not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadIconTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "homeFabManager");
            if (result == null) throw new Exception("IconTab method not found");
            return result;
        });
    }

    public static Field loadIconTabField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> cls = loadIconTabMethod(classLoader).getDeclaringClass();
            Class<?> clsType = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "Tried to set badge");
            var result = Arrays.stream(cls.getFields()).filter(f -> f.getType().equals(clsType)).findFirst().orElse(null);
            if (result == null) throw new Exception("IconTabField not found");
            return result;
        });
    }

    public static Field loadIconTabLayoutField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> clsType = loadIconTabField(classLoader).getType();
            Class<?> framelayout = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "android:menu:presenters");
            var result = Arrays.stream(clsType.getFields()).filter(f -> f.getType().equals(framelayout)).findFirst().orElse(null);
            if (result == null) throw new Exception("IconTabLayoutField not found");
            return result;
        });
    }

    public static Field loadIconMenuField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> clsType = loadIconTabLayoutField(classLoader).getType();
            Class<?> menuClass = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "Maximum number of items");
            return Arrays.stream(clsType.getFields()).filter(f -> f.getType().equals(menuClass)).findFirst().orElse(null);
        });
    }

    public static Method loadTabCountMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "required free space should be > 0");
            if (result == null) throw new Exception("TabCount method not found");
            return result;
        });
    }

//    public static Field loadTabCountField(ClassLoader classLoader) throws Exception {
//        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
//            Class<?> homeActivity = XposedHelpers.findClass("com.whatsapp.HomeActivity", classLoader);
//            Class<?> pager = loadGetTabMethod(classLoader).getDeclaringClass();
//            return getFieldByExtendType(homeActivity, pager);
//        });
//    }

    public static Method loadEnableCountTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Tried to set badge for invalid");
            if (result == null) throw new Exception("EnableCountTab method not found");
            return result;
        });
    }

    public static Constructor loadEnableCountTabConstructor1(ClassLoader classLoader) throws Exception {
        var countMethod = loadEnableCountTabMethod(classLoader);
        var indiceClass = countMethod.getParameterTypes()[1];
        var result = dexkit.findClass(new FindClass().matcher(new ClassMatcher().superClass(indiceClass.getName()).addMethod(new MethodMatcher().paramCount(1))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }

    public static Constructor loadEnableCountTabConstructor2(ClassLoader classLoader) throws Exception {
        var countTabConstructor1 = loadEnableCountTabConstructor1(classLoader);
        var indiceClass = countTabConstructor1.getParameterTypes()[0];
        var result = dexkit.findClass(new FindClass().matcher(new ClassMatcher().superClass(indiceClass.getName()).addMethod(new MethodMatcher().paramCount(1).addParamType(int.class))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }

    public static Constructor loadEnableCountTabConstructor3(ClassLoader classLoader) throws Exception {
        var countTabConstructor1 = loadEnableCountTabConstructor1(classLoader);
        var indiceClass = countTabConstructor1.getParameterTypes()[0];
        var result = dexkit.findClass(new FindClass().matcher(new ClassMatcher().superClass(indiceClass.getName()).addMethod(new MethodMatcher().paramCount(0))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }
    // TODO: Classes and methods to TimeToSeconds

    public static Method loadTimeToSecondsMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> cls = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "aBhHKm");
            if (cls == null) throw new Exception("TimeToSeconds class not found");
            var clsData = dexkit.getClassData(cls);
            var method = XposedHelpers.findMethodBestMatch(Calendar.class, "setTimeInMillis", long.class);
            var result = clsData.findMethod(new FindMethod().matcher(new MethodMatcher().addInvoke(DexSignUtil.getMethodDescriptor(method)).returnType(String.class).paramCount(2)));
            if (result.isEmpty()) throw new Exception("TimeToSeconds method not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    // TODO: Classes and methods to DndMode

    public static Method loadDndModeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Equals, "MessageHandler/start");
            if (method == null) throw new Exception("DndMode method not found");
            return method;
        });
    }

    // TODO: Classes and methods to MediaQuality

    private static Class<?> loadMediaQualityClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazzMediaClass = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "getCorrectedResolution");
            if (clazzMediaClass == null) throw new Exception("MediaQuality class not found");
            return clazzMediaClass;
        });
    }

    public static Method loadMediaQualityResolutionMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadMediaQualityClass(classLoader);
            return Arrays.stream(clazz.getDeclaredMethods()).filter(
                    m -> m.getParameterTypes().length == 3 &&
                            m.getParameterTypes()[0].equals(int.class) &&
                            m.getParameterTypes()[1].equals(int.class) &&
                            m.getParameterTypes()[2].equals(int.class)
            ).findFirst().orElse(null);
        });
    }

    public static Method loadMediaQualityBitrateMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadMediaQualityClass(classLoader);
            return Arrays.stream(clazz.getDeclaredMethods()).filter(
                    m -> m.getParameterTypes().length == 1 &&
                            m.getParameterTypes()[0].equals(int.class) &&
                            m.getReturnType().equals(int.class)
            ).findFirst().orElse(null);
        });
    }

    public static Method loadMediaQualityVideoMethod2(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "getCorrectedResolution");
            if (method == null) throw new Exception("MediaQualityVideo method not found");
            return method;
        });
    }

    public static Class loadMediaQualityVideoLimitClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "videoLimitMb=");
            if (clazz == null) throw new Exception("MediaQualityVideoLimit method not found");
            return clazz;
        });
    }

    // TODO: Classes and methods to ShareLimit


    public static Method loadShareLimitMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "send_max_video_duration");
            if (method == null) throw new Exception("ShareLimit method not found");
            return method;
        });
    }

    public static Field loadShareMapItemField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var shareLimitMethod = loadShareLimitMethod(classLoader);
            var methodData = dexkit.getMethodData(shareLimitMethod);
            var usingFields = Objects.requireNonNull(methodData).getUsingFields();
            for (var ufield : usingFields) {
                var field = ufield.getField().getFieldInstance(classLoader);
                if (field.getType() == Map.class) return field;
            }
            throw new Exception("ShareItem field not found");
        });
    }

    // TODO: Classes and methods to StatusDownload

    public static Method loadStatusActivePage(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "playbackFragment/setPageActive");
            if (method == null) throw new Exception("StatusActivePage method not found");
            return method;
        });
    }

    public static Class<?> loadStatusDownloadMediaClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "FMessageVideo/Cloned");
            if (clazz == null) throw new Exception("StatusDownloadMedia class not found");
            return clazz;
        });
    }

    public static Class loadMenuStatusClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var classList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingString("chatSettingsStore", StringMatchType.Equals).name("onClick"))));
            if (classList.isEmpty()) throw new Exception("MenuStatus class not found");
            return classList.get(0).getInstance(loader);
        });
    }

    public static Field loadStatusDownloadFileField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var clazz = loadStatusDownloadMediaClass(classLoader);
            var clazz2 = clazz.getField("A01").getType();
            var field = getFieldByType(clazz2, File.class);
            if (field == null) throw new Exception("StatusDownloadFile field not found");
            return field;
        });
    }

    public static Class<?> loadStatusDownloadSubMenuClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var classes = dexkit.findClass(
                    new FindClass().matcher(
                            new ClassMatcher().addMethod(
                                    new MethodMatcher()
                                            .addUsingString("MenuPopupHelper", StringMatchType.Contains)
                                            .returnType(void.class)
                            )
                    )
            );
            if (classes.isEmpty()) throw new Exception("StatusDownloadSubMenu method not found");
            return classes.get(0).getInstance(classLoader);
        });
    }

    public static Class<?> loadStatusDownloadMenuClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "android:menu:expandedactionview");
            if (clazz == null) throw new Exception("StatusDownloadMenu class not found");
            return clazz;
        });
    }

    // TODO: Classes and methods to ViewOnce

    public static Method[] loadViewOnceMethod(ClassLoader classLoader) throws Exception {
        var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("INSERT_VIEW_ONCE_SQL", StringMatchType.Contains)));
        if (method.isEmpty()) throw new Exception("ViewOnce method not found");
        var methodData = method.get(0);
        var listMethods = methodData.getInvokes();
        var list = new ArrayList<Method>();
        for (MethodData m : listMethods) {
            var mInstance = m.getMethodInstance(classLoader);
            if (mInstance.getDeclaringClass().isInterface() && mInstance.getDeclaringClass().getMethods().length == 2) {
                ClassDataList listClasses = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addInterface(mInstance.getDeclaringClass().getName())));
                for (ClassData c : listClasses) {
                    Class<?> clazz = c.getInstance(classLoader);
                    var resultMethod = Arrays.stream(clazz.getDeclaredMethods()).filter(m1 -> m1.getParameterCount() == 0 && m1.getReturnType().equals(int.class)).findFirst().orElse(null);
                    if (resultMethod == null) continue;
                    list.add(resultMethod);
                }
                return list.toArray(new Method[0]);
            }
        }
        throw new Exception("ViewOnce method not found");

    }

    public static Class loadViewOnceClass(ClassLoader loader) throws Exception {
        var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversation/row/viewOnce/no file");
        if (clazz == null) throw new Exception("ViewOnce class not found");
        return clazz;
    }

    public static Method loadViewOnceStoreMethod(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "INSERT_VIEW_ONCE_SQL");
        if (method == null) throw new Exception("ViewOnce class not found");
        return method;
    }


    /**
     * @noinspection SimplifyOptionalCallChains
     */
    public static Method loadViewOnceDownloadMenuMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = XposedHelpers.findClass("com.whatsapp.mediaview.MediaViewFragment", classLoader);
            var method = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getParameterCount() == 2 &&
                    Objects.equals(m.getParameterTypes()[0], Menu.class) &&
                    Objects.equals(m.getParameterTypes()[1], MenuInflater.class) &&
                    m.getDeclaringClass() == clazz
            ).findFirst();
            if (!method.isPresent()) throw new Exception("ViewOnceDownloadMenu method not found");
            return method.get();
        });
    }

    public static Field loadViewOnceDownloadMenuField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var method = loadViewOnceDownloadMenuMethod(classLoader);
            var clazz = XposedHelpers.findClass("com.whatsapp.mediaview.MediaViewFragment", classLoader);
            var methodData = dexkit.getMethodData(method);
            var fields = methodData.getUsingFields();
            for (UsingFieldData field : fields) {
                Field field1 = field.getField().getFieldInstance(classLoader);
                if (field1.getType() == int.class && field1.getDeclaringClass() == clazz) {
                    return field1;
                }
            }
            throw new Exception("ViewOnceDownloadMenu field not found");
        });
    }

    public static Field loadViewOnceDownloadMenuField2(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var methodData = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("photo_progress_fragment"))).get(0);
            var clazz = methodData.getMethodInstance(classLoader).getDeclaringClass();
            var fields = methodData.getUsingFields();
            for (UsingFieldData field : fields) {
                Field field1 = field.getField().getFieldInstance(classLoader);
                if (field1.getType() == int.class && field1.getDeclaringClass() == clazz) {
                    return field1;
                }
            }
            throw new Exception("ViewOnceDownloadMenu field 2 not found");
        });
    }

    /**
     * @noinspection SimplifyOptionalCallChains
     */
    public static Method loadViewOnceDownloadMenuCallMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = XposedHelpers.findClass("com.whatsapp.mediaview.MediaViewFragment", loader);
            var method = Arrays.stream(clazz.getDeclaredMethods()).filter(m ->
                    ((m.getParameterCount() == 2 && Objects.equals(m.getParameterTypes()[1], int.class) && Objects.equals(m.getParameterTypes()[0], clazz))
                            || (m.getParameterCount() == 1 && Objects.equals(m.getParameterTypes()[0], int.class))) &&
                            Modifier.isPublic(m.getModifiers()) && Object.class.isAssignableFrom(m.getReturnType())
            ).findFirst();
            if (!method.isPresent())
                throw new Exception("ViewOnceDownloadMenuCall method not found");
            return method.get();
        });
    }

    // TODO: Methods and Classes for Change Colors

    public static Class<?> loadExpandableWidgetClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "expandableWidgetHelper");
            if (clazz == null) throw new Exception("ExpandableWidgetHelper class not found");
            return clazz;
        });
    }

    public static Class<?> loadMaterialShapeDrawableClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "Compatibility shadow requested");
            if (clazz == null) throw new Exception("MaterialShapeDrawable class not found");
            return clazz;
        });
    }

    public static Class<?> loadCustomDrawableClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "closeIconEnabled");
            if (clazz == null) throw new Exception("CustomDrawable class not found");
            return clazz;
        });
    }

//    public static Method loadDeprecatedMethod(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
//            var methods = findAllMethodUsingStrings(loader, StringMatchType.Contains, "software_forced_expiration");
//            if (methods == null || methods.length == 0)
//                throw new Exception("Deprecated method not found");
//            var result = Arrays.stream(methods).filter(method -> method.getReturnType().equals(Date.class)).findFirst().orElse(null);
//            if (result == null) throw new Exception("Deprecated method not found");
//            return result;
//        });
//    }

    public static Method loadPropsBooleanMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown BooleanField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }

    public static Method loadPropsIntegerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown IntField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }

    public static Method loadPropsJsonMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown JsonField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }

//    public static Method loadPropsStringMethod(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
//            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown StringField");
//            if (method == null) throw new Exception("Props method not found");
//            return method;
//        });
//    }


    private static ClassData loadAntiRevokeImplClass() throws Exception {
        var classes = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addUsingString("smb_eu_tos_update_url")));
        if (classes.isEmpty()) throw new Exception("AntiRevokeImpl class not found");
        return classes.get(0);
    }

    public static Method loadAntiRevokeOnStartMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation", loader);
            var classData = loadAntiRevokeImplClass();
            MethodDataList mdOnStart = dexkit.findMethod(
                    FindMethod.create().searchInClass(List.of(dexkit.getClassData(conversation)))
                            .matcher(MethodMatcher.create().addInvoke(Objects.requireNonNull(classData).getDescriptor() + "->onStart()V"))
            );
            if (mdOnStart.isEmpty()) throw new Exception("AntiRevokeOnStart method not found");
            return mdOnStart.get(0).getMethodInstance(loader);
        });
    }


    public static Field loadAntiRevokeConvChatField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> chatClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "payment_chat_composer_entry_nux_shown");
            Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation", loader);
            Field field = getFieldByType(conversation, chatClass);
            if (field == null) throw new Exception("AntiRevokeConvChat field not found");
            return field;
        });
    }

    public static Field loadAntiRevokeChatJidField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> chatClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "payment_chat_composer_entry_nux_shown");
            Class<?> jidClass = XposedHelpers.findClass("com.whatsapp.jid.Jid", loader);
            Field field = getFieldByExtendType(chatClass, jidClass);
            if (field == null) throw new Exception("AntiRevokeChatJid field not found");
            return field;
        });
    }

    public static Method loadAntiRevokeMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Method method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "msgstore/edit/revoke");
            if (method == null) throw new Exception("AntiRevokeMessage method not found");
            return method;
        });
    }

    public static Field loadMessageKeyField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var classList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().fieldCount(3).addMethod(new MethodMatcher().addUsingString("Key").name("toString"))));
            if (classList.isEmpty()) throw new Exception("MessageKey class not found");
            for (ClassData classData : classList) {
                Class<?> keyMessageClass = classData.getInstance(loader);
                var classMessage = loadFMessageClass(loader);
                var fields = ReflectionUtils.getFieldsByExtendType(classMessage, keyMessageClass);
                if (fields.isEmpty()) continue;
                return fields.get(fields.size() - 1);
            }
            throw new Exception("MessageKey field not found");
        });
    }

    public static Method loadAntiRevokeBubbleMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Class<?> bubbleClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "ConversationRow/setUpUserNameInGroupView");
            if (bubbleClass == null) throw new Exception("AntiRevokeBubble method not found");
            var result = Arrays.stream(bubbleClass.getMethods()).filter(m -> m.getParameterCount() > 1 && m.getParameterTypes()[0] == ViewGroup.class && m.getParameterTypes()[1] == TextView.class).findFirst().orElse(null);
            if (result == null) throw new Exception("AntiRevokeBubble method not found");
            return result;
        });
    }

    public static Method loadUnknownStatusPlaybackMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var statusPlaybackClass = XposedHelpers.findClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment", loader);
            var classData = List.of(dexkit.getClassData(statusPlaybackClass));
            var result = dexkit.findMethod(new FindMethod().
                    searchInClass(classData).
                    matcher(new MethodMatcher()
                            .addUsingString("xFamilyGating").
                            addUsingString("xFamilyCrosspostManager")));
            if (result.isEmpty()) throw new Exception("UnknownStatusPlayback method not found");
            return result.get(0).getMethodInstance(loader);
        });
    }

    public static Field loadStatusPlaybackViewField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> class1 = XposedHelpers.findClass("com.whatsapp.status.playback.widget.StatusPlaybackProgressView", loader);
            ClassDataList classView = dexkit.findClass(FindClass.create().matcher(
                    ClassMatcher.create().methodCount(1).addFieldForType(class1)
            ));
            if (classView.isEmpty()) throw new Exception("StatusPlaybackView field not found");
            Class<?> clsViewStatus = classView.get(0).getInstance(loader);
            Class<?> class2 = XposedHelpers.findClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment", loader);
            return Arrays.stream(class2.getDeclaredFields()).filter(f -> f.getType() == clsViewStatus).findFirst().orElse(null);
        });
    }

    public static Class<?> loadMessageStoreClass2(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var result = findFirstClassUsingStrings(loader, StringMatchType.Contains, "databasehelper/createDatabaseTables");
            if (result == null) throw new Exception("MessageStore class not found");
            return result;
        });
    }

//    public static Class<?> loadAxolotlClass(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
//            var result = findFirstClassUsingStrings(loader, StringMatchType.Contains, "failed to open axolotl store");
//            if (result == null) throw new Exception("Axolotl class not found");
//            return result;
//        });
//    }

    public static Method loadBlueOnReplayMessageJobMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "SendE2EMessageJob/onRun");
            if (result == null) throw new Exception("BlueOnReplayMessageJob method not found");
            return result;
        });
    }

    public static Method loadBlueOnReplayWaJobManagerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstClassUsingStrings(loader, StringMatchType.Contains, "WaJobManager/start");
            var job = XposedHelpers.findClass("org.whispersystems.jobqueue.Job", loader);
            if (result == null) throw new Exception("BlueOnReplayWaJobManager method not found");
            var method = Arrays.stream(result.getMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == job).findFirst().orElse(null);
            if (method == null) throw new Exception("BlueOnReplayWaJobManager method not found");
            return method;
        });
    }

    public static Method[] loadArchiveHideViewMethod(ClassLoader loader) throws Exception {
        if (cache.containsKey("ArchiveHideView")) return (Method[]) cache.get("ArchiveHideView");
        var methods = findAllMethodUsingStrings(loader, StringMatchType.Contains, "archive/set-content-indicator-to-empty");
        if (methods.length == 0) throw new Exception("ArchiveHideView method not found");
        ArrayList<Method> result = new ArrayList<>();
        for (var m : methods) {
            result.add(m.getDeclaringClass().getMethod("setVisibility", boolean.class));
        }
        var resultArray = result.toArray(new Method[0]);
        cache.put("ArchiveHideView", resultArray);
        return resultArray;
    }


    public static Method loadAntiRevokeOnCallReceivedMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "VoiceService:callStateChangedOnUiThread");
            if (method == null) throw new Exception("OnCallReceiver method not found");
            return method;
        });
    }


    public static Method loadGetContactInfoMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Class<?> class1 = findFirstClassUsingStrings(loader, StringMatchType.Contains, "GET_CONTACTS_BY_JID_PATTERN");
            if (class1 == null) throw new Exception("ContactManager class not found");
            var jidClass = XposedHelpers.findClass("com.whatsapp.jid.Jid", loader);
            var result = ReflectionUtils.findMethodUsingFilter(class1, m -> m.getParameterCount() == 1 && jidClass.isAssignableFrom(m.getParameterTypes()[0]) && m.getReturnType() != void.class);
            if (result == null) throw new Exception("GetContactInfo method not found");
            return result;
        });
    }

    public static Method loadOnChangeStatus(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Method[] methods = findAllMethodUsingStrings(loader, StringMatchType.Contains, "setParentGroupProfilePhoto");
            var method = Arrays.stream(methods).filter(m -> m.getParameterCount() == 5).findFirst().orElse(null);
            if (method == null) throw new Exception("OnChangeStatus method not found");
            return method;
        });
    }

    public static Field loadViewHolderField1(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> class1 = loadOnChangeStatus(loader).getDeclaringClass().getSuperclass();
            Class<?> classViewHolder = XposedHelpers.findClass("com.whatsapp.conversationslist.ViewHolder", loader);
            return getFieldByType(class1, classViewHolder);
        });
    }

    public static Method loadGetStatusUserMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var id = UnobfuscatorCache.getInstance().getOfuscateIDString("last seen sun %s");
            var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(id)));
            if (result.isEmpty()) throw new Exception("GetStatusUser method not found");
            return result.get(0).getMethodInstance(loader);
        });
    }

    public static Method loadSendPresenceMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "app/send-presence-subscription jid=");
            if (method == null) throw new Exception("SendPresence method not found");
            return method;
        });
    }

    public static Method loadPinnedLimitMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "count_progress");
            if (method == null) throw new Exception("PinnedLimit method not found");
            return method;
        });
    }

//    public static Method loadPinnedLimit2Method(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
//            var id = UnobfuscatorCache.getInstance().getOfuscateIdString("Unpin All");
//            MethodDataList result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(id)));
//            if (result.isEmpty()) throw new Exception("PinnedLimit2 method not found");
//            var clazz = result.get(0).getDeclaredClass().getInstance(loader);
//            return Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getReturnType().equals(boolean.class)).findFirst().orElse(null);
//        });
//    }

    public static Method loadPinnedHashSetMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "SELECT jid, pinned_time FROM settings");
            if (clazz == null) throw new Exception("PinnedList class not found");
            var method = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getReturnType().equals(Set.class)).findFirst().orElse(null);
            if (method == null) throw new Exception("PinnedHashSet method not found");
            return method;
        });
    }

    public static Method loadGetFiltersMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazzFilters = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversations/filter/performFiltering");
            if (clazzFilters == null) throw new RuntimeException("Filters class not found");
            return Arrays.stream(clazzFilters.getDeclaredMethods()).filter(m -> m.getName().equals("publishResults")).findFirst().orElse(null);
        });
    }

    public static Method loadPinnedInChatMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(3732).returnType(int.class)));
            if (method.isEmpty()) throw new RuntimeException("PinnedInChat method not found");
            return method.get(0).getMethodInstance(loader);
        });
    }

    public static Method loadBlueOnReplayCreateMenuConversationMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var conversationClass = XposedHelpers.findClass("com.whatsapp.Conversation", loader);
            if (conversationClass == null)
                throw new RuntimeException("BlueOnReplayCreateMenuConversation class not found");
            var method = Arrays.stream(conversationClass.getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(Menu.class)).findFirst().orElse(null);
            if (method == null)
                throw new RuntimeException("BlueOnReplayCreateMenuConversation method not found");
            return method;
        });
    }

    public static Method loadBlueOnReplayViewButtonMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "PLAYBACK_PAGE_ITEM_ON_CREATE_VIEW_END");
            if (method == null)
                throw new RuntimeException("BlueOnReplayViewButton method not found");
            return method;
        });
    }

    public static Method loadBlueOnReplayStatusViewMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "StatusPlaybackPage/onViewCreated");
            if (method == null)
                throw new RuntimeException("BlueOnReplayViewButton method not found");
            return method;
        });
    }

    public static Method loadChatLimitDeleteMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "app/time server update processed");
            if (clazz == null) throw new RuntimeException("ChatLimitDelete class not found");
            var method = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getReturnType().equals(long.class) && Modifier.isStatic(m.getModifiers())).findFirst().orElse(null);
            if (method == null) {
                var methodList = Objects.requireNonNull(dexkit.getClassData(clazz)).findMethod(new FindMethod().matcher(new MethodMatcher().opCodes(new OpCodesMatcher().opNames(
                        List.of("invoke-static",
                                "move-result-wide", "iget-wide", "const-wide/16", "cmp-long",
                                "if-eqz", "iget-wide", "add-long/2addr", "return-wide",
                                "iget-wide", "cmp-long", "if-eqz", "iget-wide",
                                "goto", "invoke-static", "move-result-wide", "iget-wide",
                                "sub-long/2addr", "return-wide")))));
                if (methodList.isEmpty())
                    throw new RuntimeException("ChatLimitDelete method not found");
                method = methodList.get(0).getMethodInstance(loader);
            }
            return method;
        });
    }

    public static Method loadChatLimitDelete2Method(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "pref_revoke_admin_nux", "dialog/delete no messages");
            if (method == null) throw new RuntimeException("ChatLimitDelete2 method not found");
            return method;
        });
    }

//    public static Class<?> loadChatLimitEditClass(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
//          var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingNumber(5884).addUsingNumber(2890))));
//          if (clazzList.isEmpty()) throw new RuntimeException("ChatLimitEdit class not found");
//          return clazzList.get(0).getInstance(loader);
//        });
//    }


//    public static Method loadOriginalMessageMethod(ClassLoader loader) throws Exception {
//        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
//            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "fmessage-clone-comparison-failed");
//            if (method == null) throw new RuntimeException("OriginalMessage method not found");
//            return method;
//        });
//    }

    public static Method loadNewMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazzMessage = loadFMessageClass(loader);
            var clazzData = Objects.requireNonNull(dexkit.getClassData(clazzMessage));
            var methodData = clazzData.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("\n").returnType(String.class)));
            if (methodData.isEmpty()) {
                var field = clazzMessage.getDeclaredField("A02");
                methodData = clazzData.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingField(DexSignUtil.getFieldDescriptor(field)).returnType(String.class)));
            }
            if (methodData.isEmpty()) throw new RuntimeException("NewMessage method not found");
            return methodData.get(0).getMethodInstance(loader);
        });
    }

    public static Method loadNewMessageWithMediaMethod(ClassLoader loader) throws Exception {
        var clazzMessage = Objects.requireNonNull(dexkit.getClassData(loadFMessageClass(loader)));
        var methodData = clazzMessage.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(0x200000).returnType(String.class)));
        if (methodData.isEmpty()) {
            methodData = clazzMessage.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("video").returnType(String.class)));
            if (methodData.isEmpty()) return null;
        }
        return methodData.get(0).getMethodInstance(loader);
    }

    public static Method loadMessageEditMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageEditInfoStore/insertEditInfo/missing");
            if (method == null) throw new RuntimeException("MessageEdit method not found");
            return method;
        });
    }

    public static Method loadGetEditMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageEditInfoStore/insertEditInfo/missing");
            if (method == null) throw new RuntimeException("GetEditMessage method not found");
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            if (methodData == null) throw new RuntimeException("GetEditMessage method not found");
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                if (invoke.getParamTypes().isEmpty() && Objects.equals(invoke.getDeclaredClass(), methodData.getParamTypes().get(0))) {
                    return invoke.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("GetEditMessage method not found");
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public static Field loadSetEditMessageField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "CoreMessageStore/updateCheckoutMessageWithTransactionInfo");
            var classData = dexkit.getClassData(loadFMessageClass(loader));
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            var usingFields = methodData.getUsingFields();
            for (var f : usingFields) {
                var field = f.getField();
                if (field.getDeclaredClass().equals(classData) && field.getType().getName().equals(long.class.getName())) {
                    return field.getFieldInstance(loader);
                }
            }
            throw new RuntimeException("SetEditMessage method not found");
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public static Method loadEditMessageShowMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "newsletter_reaction_sheet");
            var fields = Arrays.stream(clazz.getDeclaredFields()).filter(f -> f.getType().equals(TextView.class)).toArray(Field[]::new);
            var classData = dexkit.getClassData(clazz);
            if (fields.length == 0) throw new RuntimeException("EditMessageShow method not found");
            for (var field : fields) {
                var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingField(DexSignUtil.getFieldDescriptor(field)).paramCount(1)).searchInClass(List.of(classData)));
                if (!result.isEmpty()) return result.get(0).getMethodInstance(loader);
            }
            throw new RuntimeException("EditMessageShow method not found");
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public static Field loadEditMessageViewField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = loadEditMessageShowMethod(loader);
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            var fields = methodData.getUsingFields();
            for (var ufield : fields) {
                var field = ufield.getField();
                if (field.getType().getName().equals(TextView.class.getName())) {
                    return field.getFieldInstance(loader);
                }
            }
            throw new RuntimeException("EditMessageView method not found");
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public static Class loadDialogViewClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var id = Utils.getID("touch_outside", "id");
            var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(id).returnType(FrameLayout.class)));
            if (result.isEmpty()) throw new RuntimeException("DialogView class not found");
            return result.get(0).getDeclaredClass().getInstance(loader);
        });
    }

    public static Constructor loadRecreateFragmentConstructor(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(loader, () -> {
            var data = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("Instantiated fragment")));
            if (data.isEmpty()) throw new RuntimeException("RecreateFragment method not found");
            if (!data.single().isConstructor())
                throw new RuntimeException("RecreateFragment method not found");
            return data.single().getConstructorInstance(loader);
        });
    }


    public static Method loadOnTabItemAddMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Maximum number of items supported by");
            if (result == null) throw new RuntimeException("OnTabItemAdd method not found");
            return result;
        });
    }

    public static Method loadScrollPagerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findAllMethodUsingStrings(loader, StringMatchType.Contains, "search_fragment");
            if (result == null) throw new RuntimeException("ScrollPager methods not found");
            var method = Arrays.stream(result).filter(m -> m.getName().equals("onScroll")).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("ScrollPager method not found");
            return method;
        });
    }

    public static Method loadGetViewConversationMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", loader);
            var method = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getParameterCount() == 3 && m.getReturnType().equals(View.class) && m.getParameterTypes()[1].equals(LayoutInflater.class)).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("GetViewConversation method not found");
            return method;
        });
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    public static Method loadOnMenuItemSelected(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var aClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", loader);
            var result = Arrays.stream(aClass.getDeclaredMethods()).
                    filter(m -> m.getParameterCount() == 4 &&
                            m.getParameterTypes()[0].equals(int.class) &&
                            m.getParameterTypes()[1].equals(int.class) &&
                            m.getParameterTypes()[2].equals(boolean.class) &&
                            m.getParameterTypes()[3].equals(boolean.class)
                    ).collect(Collectors.toList());
            if (result.isEmpty()) throw new RuntimeException("OnMenuItemSelected method not found");
            return result.get(1);
        });
    }

    public static Method loadOnUpdateStatusChanged(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = XposedHelpers.findClass("com.whatsapp.updates.viewmodels.UpdatesViewModel", loader);
            var clazzData = dexkit.getClassData(clazz);
            var methodSeduleche = XposedHelpers.findMethodBestMatch(Timer.class, "schedule", TimerTask.class, long.class, long.class);
            var result = dexkit.findMethod(new FindMethod().searchInClass(List.of(clazzData)).matcher(new MethodMatcher().addInvoke(DexSignUtil.getMethodDescriptor(methodSeduleche))));
            if (result.isEmpty())
                throw new RuntimeException("OnUpdateStatusChanged method not found");
            return result.get(0).getMethodInstance(loader);
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public static Field loadGetInvokeField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = loadOnUpdateStatusChanged(loader);
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            var fields = methodData.getUsingFields();
            var field = fields.stream().map(UsingFieldData::getField).filter(f -> f.getDeclaredClass().equals(methodData.getDeclaredClass())).findFirst().orElse(null);
            if (field == null) throw new RuntimeException("GetInvokeField method not found");
            return field.getFieldInstance(loader);
        });
    }

    public static Class<?> loadStatusInfoClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "ContactStatusDataItem");
            if (clazz == null) throw new RuntimeException("StatusInfo class not found");
            return clazz;
        });
    }

    public static Class loadStatusListUpdatesClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "StatusListUpdates");
            if (clazz == null) throw new RuntimeException("StatusListUpdates class not found");
            return clazz;
        });
    }

    public static Class loadTabFrameClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "android:menu:presenters");
            if (clazz == null) throw new RuntimeException("TabFrame class not found");
            return clazz;
        });
    }


    public static List<Method> loadNineDrawableMethods(ClassLoader loader) throws Exception {
        var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().returnType(NinePatchDrawable.class).paramCount(4)));
        if (result.isEmpty()) return Collections.emptyList();
        var arr = new ArrayList<Method>();
        for (var m : result) {
            if (m.isMethod()) arr.add(m.getMethodInstance(loader));
        }
        return arr;
    }

    public static Class loadOnMenuItemClickClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "android:menu:expandedactionview");
            if (clazz == null) throw new RuntimeException("OnMenuItemClick class not found");
            return clazz;
        });
    }

    public static Class loadOnMenuItemClickClass2(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "flash_call_retry_dialog");
            if (clazz == null) throw new RuntimeException("OnMenuItemClick class not found");
            return clazz;
        });
    }


    public static Class loadRemoveChannelRecClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "RecommendedNewslettersListDataItem(recommendedNewsletters=");
            if (clazz == null) throw new RuntimeException("RemoveChannelRec class not found");
            return clazz;
        });
    }

    public static Class loadFilterAdaperClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingString("CONTACTS_FILTER").paramCount(1).addParamType(int.class))));
            if (clazzList.isEmpty()) throw new RuntimeException("FilterAdapter class not found");
            return clazzList.get(0).getInstance(loader);
        });
    }

    public static Method loadSeeMoreMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var classList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().
                    addMethod(new MethodMatcher().addUsingNumber(16384).addUsingNumber(512).addUsingNumber(64).addUsingNumber(16))
                    .addMethod(new MethodMatcher().paramCount(2).addParamType(int.class).addParamType(boolean.class))));
            if (classList.isEmpty()) throw new RuntimeException("SeeMore method 1 not found");
            var clazzData = classList.get(0);
            XposedBridge.log(clazzData.toString());
            for (var method : clazzData.getMethods()) {
                if (method.getParamCount() == 2 && method.getParamTypes().get(0).getName().equals(int.class.getName()) && method.getParamTypes().get(1).getName().equals(boolean.class.getName())) {
                    return method.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("SeeMore method 2 not found");
        });
    }

    public static Method loadSendStickerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "StickerGridViewItem.StickerLocal");
            if (method == null) throw new RuntimeException("SendSticker method not found");
            return method;
        });

    }

    public static Method loadMaterialAlertDialog(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var callConfirmationFragment = XposedHelpers.findClass("com.whatsapp.calling.fragment.CallConfirmationFragment", loader);
            var method = ReflectionUtils.findMethodUsingFilter(callConfirmationFragment, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(android.os.Bundle.class));
            var methodData = dexkit.getMethodData(method);
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                if (invoke.isMethod() && Modifier.isStatic(invoke.getModifiers()) && invoke.getParamCount() == 1 && invoke.getParamTypes().get(0).getName().equals(Context.class.getName())) {
                    return invoke.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("MaterialAlertDialog not found");
        });
    }

    public static Method loadGetIntPreferences(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().paramCount(2).addParamType(SharedPreferences.class).addParamType(String.class).modifiers(Modifier.STATIC | Modifier.PUBLIC).returnType(int.class)));
            if (methodList.isEmpty())
                throw new RuntimeException("CallConfirmationLimit method not found");
            return methodList.get(0).getMethodInstance(loader);
        });
    }

    public static Field loadProfileInfoField(ClassLoader loader) throws Exception {
        var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "[obfuscated]@%s");
        if (clazz == null) throw new RuntimeException("ProfileInfo class not found");
        var fieldList = ReflectionUtils.getFieldsByExtendType(clazz, XposedHelpers.findClass("com.whatsapp.jid.Jid", loader));
        if (fieldList.isEmpty()) throw new RuntimeException("ProfileInfo field not found");
        return fieldList.get(0);
    }

    public static Method loadProximitySensorMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "messageaudioplayer/onearproximity");
            if (method == null) throw new RuntimeException("ProximitySensor method not found");
            return method;
        });
    }

    public static Method loadGroupAdminMethod(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "P Message");
        if (method == null) throw new RuntimeException("GroupAdmin method not found");
        return method;
    }

    public static Method loadJidFactory(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "lid_me", "status_me", "s.whatsapp.net");
        if (method == null) throw new RuntimeException("JidFactory method not found");
        return method;
    }

    public static Method loadGroupCheckAdminMethod(ClassLoader loader) throws Exception {
        var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "[LidGroup]GroupParticipantsManager");
        var userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", loader);
        var methods = ReflectionUtils.findAllMethodsUsingFilter(clazz, m -> m.getParameterCount() == 2 && m.getParameterTypes()[1].equals(userJidClass) && m.getReturnType().equals(boolean.class));
        if (methods == null || methods.length == 0)
            throw new RuntimeException("GroupCheckAdmin method not found");
        return methods[methods.length - 1];
    }

    public static Constructor loadStartPrefsConfig(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(loader, () -> {
            var results = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("startup_migrated_version")));
            if (results.isEmpty())
                throw new RuntimeException("StartPrefsConfig constructor not found");
            return results.get(0).getConstructorInstance(loader);
        });
    }

    public static Method loadCheckOnlineMethod(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "app/xmpp/recv/handle_available");
        if (method == null) throw new RuntimeException("CheckOnline method not found");
        return method;
    }

    public static Method loadEphemeralInsertdb(ClassLoader loader) throws Exception {
        var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("expire_timestamp").addUsingString("ephemeral_initiated_by_me").addUsingString("ephemeral_trigger").returnType(ContentValues.class)));
        if (method.isEmpty()) throw new RuntimeException("FieldExpireTime method not found");
        var methodData = method.get(0);
        return methodData.getMethodInstance(loader);
    }

    public static Method loadDefEmojiClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "emojis.oba");
            if (method == null) throw new RuntimeException("DefEmoji class not found");
            return method;
        });
    }

    public static Class loadVideoViewContainerClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "frame_visibility_serial_worker");
            if (clazz == null) throw new RuntimeException("VideoViewContainer class not found");
            return clazz;
        });
    }

    public static Class loadImageVewContainerClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingNumber(Utils.getID("hd_invisible_touch", "id")).addUsingNumber(Utils.getID("control_btn", "id")))));
            if (clazzList.isEmpty())
                throw new RuntimeException("ImageViewContainer class not found");
            return clazzList.get(0).getInstance(loader);
        });
    }

    public static Class loadMediaQualityProcessor(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "{maxKb=");
            if (clazz == null) throw new RuntimeException("MediaQualityProcessor class not found");
            return clazz;
        });
    }

    public static Method getFilterInitMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(loader);
            var constructor = filterAdaperClass.getConstructors()[0];
            var methods = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addInvoke(DexSignUtil.getMethodDescriptor(constructor))));
            if (methods.isEmpty()) throw new RuntimeException("FilterInit method not found");
            var cFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", loader);
            var method = methods.stream().filter(m -> m.getParamCount() == 1 && m.getParamTypes().get(0).getName().equals(cFrag.getName())).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("FilterInit method not found 2");
            return method.getMethodInstance(loader);
        });
    }

    public static Class getFilterView(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var results = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().name("setInboxFilterHelper"))));
            if (results.isEmpty()) throw new RuntimeException("FilterView class not found");
            return results.get(0).getInstance(loader);
        });
    }

    public static Class loadActionUser(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var results = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addUsingString("UserActions/reportIfBadTime: time=")));
            if (results.isEmpty()) throw new RuntimeException("ActionUser class not found");
            return results.get(0).getInstance(loader);
        });
    }

    public static Method loadOnPlaybackFinished(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "playbackPage/onPlaybackContentFinished");
            if (method == null) throw new RuntimeException("OnPlaybackFinished method not found");
            return method;
        });
    }

    public static Method loadNextStatusRunMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("SequentialVoiceMemoPlayer/playMiddleTone").name("run")));
            if (methodList.isEmpty()) throw new RuntimeException("RunNextStatus method not found");
            return methodList.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadOnInsertReceipt(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "ReceiptUserStore/insertOrUpdateUserReceiptForMessage");
            if (method == null) throw new RuntimeException("OnInsertReceipt method not found");
            return method;
        });

    }

    public static Method loadSendAudioTypeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = classLoader.loadClass("com.whatsapp.status.playback.MessageReplyActivity").getMethod("onActivityResult", int.class, int.class, android.content.Intent.class);
            var methodData = dexkit.getMethodData(method);
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                if (!invoke.isMethod()) continue;
                var m1 = invoke.getMethodInstance(classLoader);
                var params = Arrays.asList(m1.getParameterTypes());
                if (params.contains(List.class) && params.contains(int.class) && params.contains(boolean.class) && params.contains(Uri.class)) {
                    return m1;
                }
            }
            throw new RuntimeException("SendAudioType method not found");
        });
    }

    public static Field loadOriginFMessageField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("audio/ogg; codecs=opu").paramCount(0).returnType(boolean.class)));
            var clazz = loadFMessageClass(classLoader);
            if (result.isEmpty()) throw new RuntimeException("OriginFMessageField not found");
            var fields = result.get(0).getUsingFields();
            for (var field : fields) {
                var f = field.getField().getFieldInstance(classLoader);
                if (f.getDeclaringClass().equals(clazz)) {
                    return f;
                }
            }
            throw new RuntimeException("OriginFMessageField not found");
        });
    }

    public static Method loadForwardAudioTypeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "forwardable", "FMessageFactory/newFMessageForForward/thumbnail");
            if (result == null) throw new RuntimeException("ForwardAudioType method not found");
            return result;
        });
    }

    public static Class loadFragmentLoader(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "not associated with a fragment manager.");
            if (clazz == null) throw new RuntimeException("FragmentLoader class not found");
            return clazz;
        });
    }

    public static Method loadShowDialogStatusMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadFragmentLoader(classLoader);
            var frag = classLoader.loadClass("androidx.fragment.app.DialogFragment");
            var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().paramCount(2).addParamType(frag).addParamType(clazz).returnType(void.class).modifiers(Modifier.PUBLIC | Modifier.STATIC)));
            if (result.isEmpty()) throw new RuntimeException("showDialogStatus not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadPlaybackSpeed(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "heroaudioplayer/setPlaybackSpeed");
            if (method == null) throw new RuntimeException("PlaybackSpeed method not found");
            return method;
        });
    }

//    public static Method loadArchiveCheckLockedChatsMethod(ClassLoader classLoader) throws Exception {
//        var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "conversationsfragment/verticalswipetorevealbehavior");
//        if (method == null) throw new RuntimeException("ArchiveCheckLockedChats method not found");
//        return method;
//    }
//
//    public static Method loadArchiveCheckLockedChatsMethod2(ClassLoader classLoader) throws Exception {
//        var methods = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "registration_device_id");
//        if (methods.length == 0)
//            throw new RuntimeException("ArchiveCheckLockedChats method not found");
//        return Arrays.stream(methods).filter(m -> m.getReturnType().equals(boolean.class) && m.getParameterTypes().length == 0).findFirst().orElse(null);
//    }
//
//    public static Class<?> loadArchiveLockedChatClass(ClassLoader classLoader) throws Exception {
//        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
//            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().name("setLockedRowVisibility")).addMethod(new MethodMatcher().name("setEnableStateForChatLock"))));
//            if (clazzList.isEmpty())
//                throw new RuntimeException("ArchiveLockedChatFrame class not found");
//            return clazzList.get(0).getInstance(classLoader);
//        });
//    }

    public static Constructor loadListUpdateItemsConstructor(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(classLoader, () -> {
            var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().paramCount(1).returnType(void.class).addParamType(Object.class).addUsingNumber(8686)));
            if (method.isEmpty())
                throw new RuntimeException("ListUpdateItems method not found");
            return method.get(0).getClassInstance(classLoader).getConstructors()[0];
        });
    }

    public static Class loadHeaderChannelItemClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "NewsletterHeaderDataItem");
            if (clazz == null) throw new RuntimeException("HeaderChannelItem class not found");
            return clazz;
        });
    }

    public static Class loadListChannelItemClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "NewsletterDataItem", "isMuteIndicatorEnabled");
            if (clazz == null) throw new RuntimeException("HeaderChannelItem class not found");
            return clazz;
        });
    }

    public static Method loadTextStatusComposer(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method1 = Activity.class.getDeclaredMethod("getWindow");
            var method2 = View.class.getDeclaredMethod("setBackground", Drawable.class);
            var clazz = classLoader.loadClass("com.whatsapp.textstatuscomposer.TextStatusComposerActivity");
            var field1 = clazz.getDeclaredField("A02");
            var classData = dexkit.getClassData(clazz);
            if (classData == null) throw new RuntimeException("TextStatusComposer class not found");
            var methods = classData.findMethod(new FindMethod().matcher(new MethodMatcher()
                    .addInvoke(DexSignUtil.getMethodDescriptor(method1))
                    .addInvoke(DexSignUtil.getMethodDescriptor(method2))
                    .addUsingField(DexSignUtil.getFieldDescriptor(field1))
                    .modifiers(Modifier.PUBLIC | Modifier.STATIC)
            ));
            if (methods.isEmpty())
                throw new RuntimeException("TextStatusComposer method not found");
            return methods.get(0).getMethodInstance(classLoader);
        });
    }

    public static Method loadTextStatusComposer2(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> TextDataClass = classLoader.loadClass("com.whatsapp.TextData");
            var clazz = classLoader.loadClass("com.whatsapp.textstatuscomposer.TextStatusComposerActivity");
            var field1 = TextDataClass.getDeclaredField("fontStyle");
            var field2 = TextDataClass.getDeclaredField("textColor");
            var field3 = TextDataClass.getDeclaredField("backgroundColor");
            var classData = dexkit.getClassData(clazz);
            if (classData == null)
                throw new RuntimeException("TextStatusComposer2 class not found");
            var methods = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher()
                    .addUsingField(DexSignUtil.getFieldDescriptor(field1))
                    .addUsingField(DexSignUtil.getFieldDescriptor(field2))
                    .addUsingField(DexSignUtil.getFieldDescriptor(field3))
                    .modifiers(Modifier.PUBLIC)
            ));
            for (var method : methods) {
                var callers = method.getCallers();
                if (callers.stream().anyMatch(methodData -> methodData.getDeclaredClassName().contains("TextStatusComposerActivity"))) {
                    return method.getMethodInstance(classLoader);
                }
            }
            throw new RuntimeException("TextStatusComposer2 method not found");
        });
    }
}
