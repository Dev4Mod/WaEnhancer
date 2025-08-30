package com.wmods.wppenhacer.xposed.core.devkit;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.SensorEventListener;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.OpCodeMatchType;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
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

    private static final String TAG = "Unobfuscator";
    private static DexKitBridge dexkit;

    public static final HashMap<String, Class<?>> cacheClasses = new HashMap<>();

    static {
        System.loadLibrary("dexkit");
    }

    public static boolean initWithPath(String path) {
        try {
            dexkit = DexKitBridge.create(path);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // TODO: Functions to find classes and methods
    public synchronized static Method findFirstMethodUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) throws Exception {
        MethodMatcher matcher = new MethodMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        MethodDataList result = dexkit.findMethod(FindMethod.create().matcher(matcher));
        if (result.isEmpty()) return null;
        for (MethodData methodData : result) {
            if (methodData.isMethod()) return methodData.getMethodInstance(classLoader);
        }
        return null;
    }

    public synchronized static Method findFirstMethodUsingStringsFilter(ClassLoader classLoader, String packageFilter, StringMatchType type, String... strings) throws Exception {
        MethodMatcher matcher = new MethodMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        MethodDataList result = dexkit.findMethod(FindMethod.create().searchPackages(packageFilter).matcher(matcher));
        if (result.isEmpty()) return null;

        for (MethodData methodData : result) {
            if (methodData.isMethod()) return methodData.getMethodInstance(classLoader);
        }
        throw new NoSuchMethodException();
    }

    public synchronized static Method[] findAllMethodUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) {
        MethodMatcher matcher = new MethodMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        MethodDataList result = dexkit.findMethod(FindMethod.create().matcher(matcher));
        if (result.isEmpty()) return new Method[0];
        return result.stream().filter(MethodData::isMethod).map(methodData -> convertRealMethod(methodData, classLoader)).filter(Objects::nonNull).toArray(Method[]::new);
    }

    public synchronized static Class<?> findFirstClassUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) throws Exception {
        var matcher = new ClassMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        var result = dexkit.findClass(FindClass.create().matcher(matcher));
        if (result.isEmpty()) return null;
        return result.get(0).getInstance(classLoader);
    }


    public synchronized static Class<?>[] findAllClassUsingStrings(ClassLoader classLoader, StringMatchType type, String... strings) throws Exception {
        var matcher = new ClassMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        var result = dexkit.findClass(FindClass.create().matcher(matcher));
        if (result.isEmpty()) return null;
        return result.stream().map(classData -> convertRealClass(classData, classLoader)).filter(Objects::nonNull).toArray(Class[]::new);
    }


    public synchronized static Class<?> findFirstClassUsingStringsFilter(ClassLoader classLoader, String packageFilter, StringMatchType type, String... strings) throws Exception {
        var matcher = new ClassMatcher();
        for (String string : strings) {
            matcher.addUsingString(string, type);
        }
        var result = dexkit.findClass(FindClass.create().searchPackages(packageFilter).matcher(matcher));
        if (result.isEmpty()) return null;
        return result.get(0).getInstance(classLoader);
    }

    public synchronized static Class<?> findFirstClassUsingName(ClassLoader classLoader, StringMatchType type, String name) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, name, () -> {
            var result = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().className(name, type)));
            if (result.isEmpty())
                throw new ClassNotFoundException("Class not found: " + name);
            return result.get(0).getInstance(classLoader);
        });
    }

    public synchronized static String getMethodDescriptor(Method method) {
        if (method == null) return null;
        return method.getDeclaringClass().getName() + "->" + method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
    }

    public synchronized static String getFieldDescriptor(Field field) {
        return field.getDeclaringClass().getName() + "->" + field.getName() + ":" + field.getType().getName();
    }

    @Nullable
    public synchronized static Method convertRealMethod(MethodData methodData, ClassLoader classLoader) {
        try {
            return methodData.getMethodInstance(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public synchronized static Class<?> convertRealClass(ClassData classData, ClassLoader classLoader) {
        try {
            return classData.getInstance(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    // TODO: Classes and Methods for FreezeSeen
    public synchronized static Method loadFreezeSeenMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "presencestatemanager/setAvailable/new-state")));
    }

    // TODO: Classes and Methods for GhostMode
    public synchronized static Method loadGhostModeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "HandleMeComposing/sendComposing");
            if (method == null) throw new Exception("GhostMode method not found");
            if (method.getParameterTypes().length > 2 && method.getParameterTypes()[2] == int.class)
                return method;
            throw new Exception("GhostMode method not found parameter type");
        });
    }

    // TODO: Classes and Methods for Receipt

    public synchronized static Method loadReceiptMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method[] methods = findAllMethodUsingStrings(classLoader, StringMatchType.Equals, "receipt");
            var deviceJidClass = XposedHelpers.findClass("com.whatsapp.jid.DeviceJid", classLoader);
            Method bestMethod = Arrays.stream(methods).filter(method -> method.getParameterTypes().length > 1 && method.getParameterTypes()[1] == deviceJidClass).findFirst().orElse(null);
            if (bestMethod == null) throw new Exception("Receipt method not found");
            return bestMethod;
        });
    }

    public synchronized static Method loadReceiptOutsideChat(ClassLoader classLoader) throws Exception {
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

    public synchronized static Method loadReceiptInChat(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = loadReceiptMethod(classLoader);
            var methodDataList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("callCreatorJid").addUsingString("reject").addInvoke(DexSignUtil.getMethodDescriptor(method))));
            if (methodDataList.isEmpty()) throw new Exception("Receipt method not found");
            return methodDataList.get(0).getMethodInstance(classLoader);
        });
    }

    // TODO: Classes and Methods for HideForward

    public synchronized static Method loadForwardTagMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> messageInfoClass = loadFMessageClass(classLoader);
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("chatInfo/incrementUnseenImportantMessageCount")));
            if (methodList.isEmpty()) throw new Exception("ForwardTag method support not found");
            var invokes = methodList.get(0).getInvokes();
            for (var invoke : invokes) {
                var method = invoke.getMethodInstance(classLoader);
                if (method.getParameterCount() == 1
                        && (method.getParameterTypes()[0] == int.class
                        || method.getParameterTypes()[0] == long.class)
                        && method.getDeclaringClass() == messageInfoClass
                        && method.getReturnType() == void.class) {
                    return method;
                }
            }
            throw new Exception("ForwardTag method not found");
        });
    }

    public synchronized static Field loadBroadcastTagField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var fmessage = loadFMessageClass(classLoader);
            var clazzData = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addUsingString("UPDATE_MESSAGE_MAIN_BROADCAST_SCAN_SQL")));
            if (clazzData.isEmpty()) throw new Exception("BroadcastTag class not found");
            var methodData = dexkit.findMethod(FindMethod.create().searchInClass(clazzData).matcher(MethodMatcher.create().usingStrings("participant_hash", "view_mode", "broadcast")));

            // 2.25.18.xx, they splitted method and moved to the fmessage
            if (methodData.isEmpty()) {
                methodData = dexkit.findMethod(FindMethod.create().searchInClass(clazzData).matcher(MethodMatcher.create().usingStrings("received_timestamp", "view_mode", "message")));
                if (!methodData.isEmpty()) {
                    var calledMethods = methodData.get(0).getInvokes();
                    for (var cmethod : calledMethods) {
                        if (Modifier.isStatic(cmethod.getModifiers()) && cmethod.getParamCount() == 2 && fmessage.getName().equals(cmethod.getDeclaredClass().getName())) {
                            var pTypes = cmethod.getParamTypes();
                            if (pTypes.get(0).getName().equals(ContentValues.class.getName()) && pTypes.get(1).getName().equals(fmessage.getName())) {
                                methodData.clear();
                                methodData.add(cmethod);
                                break;
                            }
                        }
                    }
                }
            }

            if (methodData.isEmpty()) throw new Exception("BroadcastTag method support not found");
            var usingFields = methodData.get(0).getUsingFields();
            for (var ufield : usingFields) {
                var field = ufield.getField();
                if (field.getDeclaredClass().getName().equals(fmessage.getName()) &&
                        field.getType().getName().equals(boolean.class.getName())
                ) {
                    return field.getFieldInstance(classLoader);
                }
            }
            throw new Exception("BroadcastTag field not found");
        });
    }

    public synchronized static Method loadBroadcastTagMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var field = loadBroadcastTagField(classLoader);
            XposedBridge.log(DexSignUtil.getFieldDescriptor(field));
            var clazzData = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addUsingString("ConversationRow/setUpUserNameInGroupView")));
            if (clazzData.isEmpty())
                throw new Exception("BroadcastTag: ConversationRow Class not found");
            var method = dexkit.findMethod(FindMethod.create().searchInClass(clazzData).matcher(MethodMatcher.create().addUsingField(DexSignUtil.getFieldDescriptor(field))));
            if (method.isEmpty()) {
                var findViewId = View.class.getDeclaredMethod("findViewById", int.class);
                var setImageResource = ImageView.class.getDeclaredMethod("setImageResource", int.class);
                method = dexkit.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create().addUsingField(DexSignUtil.getFieldDescriptor(field))
                                        .addInvoke(DexSignUtil.getMethodDescriptor(findViewId))
                                        .addInvoke(DexSignUtil.getMethodDescriptor(setImageResource))
                        )
                );
            }
            if (method.isEmpty())
                throw new Exception("BroadcastTag: ConversationRow Method not found");
            return method.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Class<?> loadForwardClassMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "UserActions/userActionForwardMessage"));
    }


    // TODO: Classes and Methods for HideView
    public synchronized static Method loadHideViewSendReadJob(ClassLoader classLoader) throws Exception {
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

    public synchronized static Method loadHideViewInChatMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var strings = new String[]{
                    "ReadReceipts/sendDeliveryReadReceipt", "ReadReceipts/acknowledgeMessageIfNeeded", "ReadReceipts/sendDeliveryReceiptIfNotRetry"
            };
            for (var s : strings) {
                var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, s);
                if (method != null) return method;
            }
            throw new Exception("HideViewInChat method not found");
        });
    }

    public synchronized static Class<?> loadFMessageClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var messageClass = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "FMessage/getSenderUserJid/key.id");
            if (messageClass == null) throw new Exception("Message class not found");
            return messageClass;
        });
    }

    // TODO: Classes and Methods for XChatFilter

    public synchronized static Method loadTabListMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var classData = dexkit.findClass(FindClass.create().searchPackages("X.").matcher(ClassMatcher.create().addUsingString("mainContainer")));
            if (classData.isEmpty()) throw new Exception("mainContainer class not found");
            var classMain = classData.get(0).getInstance(classLoader);
            Method method = Arrays.stream(classMain.getDeclaredMethods()).parallel().filter(m -> m.getName().equals("onCreate")).findFirst().orElse(null);
            if (method == null) throw new Exception("onCreate method not found");
            return method;
        });
    }

    public synchronized static Method loadGetTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "No HomeFragment mapping for community tab id:");
            if (result == null) throw new Exception("GetTab method not found");
            return result;
        });
    }

    public synchronized static Method loadTabFragmentMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Class<?> clsFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
            Method result = Arrays.stream(clsFrag.getDeclaredMethods()).parallel().filter(m -> m.getParameterTypes().length == 0 && m.getReturnType().equals(List.class)).findFirst().orElse(null);
            if (result == null) throw new Exception("TabFragment method not found");
            return result;
        });
    }

    public synchronized static Method loadTabNameMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            int id = UnobfuscatorCache.getInstance().getOfuscateIDString("updates");
            if (id < 1) throw new Exception("TabName ID not found");
            MethodDataList result = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().returnType(String.class).usingNumbers(id)));
            if (result.isEmpty()) throw new Exception("TabName method not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Method loadFabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            ClassData classData = dexkit.getClassData("com.whatsapp.conversationslist.ConversationsFragment");
            var result = classData.findMethod(FindMethod.create().matcher(MethodMatcher.create().paramCount(0).usingNumbers(200).returnType(int.class)));
            if (result.isEmpty()) throw new Exception("Fab method not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Method loadIconTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "homeFabManager");
            if (result == null) throw new Exception("IconTab method not found");
            return result;
        });
    }

    public synchronized static Field loadPreIconTabField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> cls = loadIconTabMethod(classLoader).getDeclaringClass();
            Class<?> clsType = findFirstClassUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "Tried to set badge");
            if (clsType == null) throw new Exception("PreIconTabField not found");
            Field result = null;
            for (var field1 : cls.getFields()) {
                Object checkResult = Arrays.stream(field1.getType().getFields()).filter(f -> f.getType().equals(clsType)).findFirst().orElse(null);
                if (checkResult != null) {
                    result = field1;
                    break;
                }
            }
            if (result == null) throw new Exception("PreIconTabField not found 2");
            return result;
        });
    }

    public synchronized static Field loadIconTabField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> cls = loadIconTabMethod(classLoader).getDeclaringClass();
            Class<?> clsType = findFirstClassUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "Tried to set badge");
            if (clsType == null) throw new Exception("IconTabField not found");
            for (var field1 : cls.getFields()) {
                var result = Arrays.stream(field1.getType().getFields()).filter(f -> f.getType().equals(clsType)).findFirst().orElse(null);
                if (result != null) return result;
            }
            throw new Exception("IconTabField not found 2");
        });
    }

    public synchronized static Field loadIconTabLayoutField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> clsType = loadIconTabField(classLoader).getType();
            Class<?> framelayout = findFirstClassUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "android:menu:presenters");
            var result = Arrays.stream(clsType.getFields()).filter(f -> f.getType().equals(framelayout)).findFirst().orElse(null);
            if (result == null) throw new Exception("IconTabLayoutField not found");
            return result;
        });
    }

    public synchronized static Field loadIconMenuField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            Class<?> clsType = loadIconTabLayoutField(classLoader).getType();
            Class<?> menuClass = findFirstClassUsingStringsFilter(classLoader, "X.", StringMatchType.Contains, "Maximum number of items");
            return Arrays.stream(clsType.getFields()).filter(f -> f.getType().equals(menuClass)).findFirst().orElse(null);
        });
    }

    public synchronized static Method loadTabCountMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            Method result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "required free space should be > 0");
            if (result == null) throw new Exception("TabCount method not found");
            return result;
        });
    }


    public synchronized static Method loadEnableCountTabMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Tried to set badge for invalid");
            if (result == null) throw new Exception("EnableCountTab method not found");
            return result;
        });
    }

    public synchronized static Constructor loadEnableCountTabConstructor1(ClassLoader classLoader) throws Exception {
        var countMethod = loadEnableCountTabMethod(classLoader);
        var indiceClass = countMethod.getParameterTypes()[1];
        var result = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().superClass(indiceClass.getName()).addMethod(MethodMatcher.create().paramCount(1))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }

    public synchronized static Constructor loadEnableCountTabConstructor2(ClassLoader classLoader) throws Exception {
        var countTabConstructor1 = loadEnableCountTabConstructor1(classLoader);
        var indiceClass = countTabConstructor1.getParameterTypes()[0];
        var result = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().superClass(indiceClass.getName()).addMethod(MethodMatcher.create().paramCount(1).addParamType(int.class))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }

    public synchronized static Constructor loadEnableCountTabConstructor3(ClassLoader classLoader) throws Exception {
        var countTabConstructor1 = loadEnableCountTabConstructor1(classLoader);
        var indiceClass = countTabConstructor1.getParameterTypes()[0];
        var result = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().superClass(indiceClass.getName()).addMethod(MethodMatcher.create().paramCount(0))));
        if (result.isEmpty()) throw new Exception("EnableCountTab method not found");
        return result.get(0).getInstance(classLoader).getConstructors()[0];
    }
    // TODO: Classes and methods to TimeToSeconds

    public synchronized static Method loadTimeToSecondsMethod(ClassLoader classLoader) throws Exception {
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

    public synchronized static Method loadDndModeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Equals, "MessageHandler/start");
            if (method == null) throw new Exception("DndMode method not found");
            return method;
        });
    }

    // TODO: Classes and methods to MediaQuality
    public synchronized static Method loadMediaQualityVideoMethod2(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "getCorrectedResolution");
            if (method == null) throw new Exception("MediaQualityVideo method not found");
            return method;
        });
    }

    public synchronized static HashMap<String, Field> loadMediaQualityVideoFields(ClassLoader classLoader) throws Exception {
        var method = loadMediaQualityVideoMethod2(classLoader);
        var methodString = method.getReturnType().getDeclaredMethod("toString");
        var methodData = dexkit.getMethodData(methodString);
        var usingFields = Objects.requireNonNull(methodData).getUsingFields();
        var usingStrings = Objects.requireNonNull(methodData).getUsingStrings();
        var result = new HashMap<String, Field>();
        for (int i = 0; i < usingStrings.size(); i++) {
            if (i == usingFields.size()) break;
            var field = usingFields.get(i).getField().getFieldInstance(classLoader);
            result.put(usingStrings.get(i), field);
        }
        return result;
    }

    public synchronized static HashMap<String, Field> loadMediaQualityOriginalVideoFields(ClassLoader classLoader) throws Exception {
        var method = loadMediaQualityVideoMethod2(classLoader);
        var methodString = method.getParameterTypes()[0].getDeclaredMethod("toString");
        var methodData = dexkit.getMethodData(methodString);
        var usingFields = Objects.requireNonNull(methodData).getUsingFields();
        var usingStrings = Objects.requireNonNull(methodData).getUsingStrings();
        var result = new HashMap<String, Field>();
        for (int i = 0; i < usingStrings.size(); i++) {
            if (i == usingFields.size()) break;
            var field = usingFields.get(i).getField().getFieldInstance(classLoader);
            result.put(usingStrings.get(i), field);
        }
        return result;
    }

    // TODO: Classes and methods to ShareLimit


    public synchronized static Method loadShareLimitMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "send_max_video_duration");
            if (method == null) throw new Exception("ShareLimit method not found");
            return method;
        });
    }

    public synchronized static Field loadShareMapItemField(ClassLoader classLoader) throws Exception {
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

    public synchronized static Method loadStatusActivePage(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "playbackFragment/setPageActive");
            if (method == null) throw new Exception("StatusActivePage method not found");
            return method;
        });
    }


    public synchronized static Class<?> loadMenuManagerClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var methods = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "MenuPopupHelper cannot be used without an anchor");
            for (var method : methods) {
                if (method.getReturnType() == void.class) return method.getDeclaringClass();
            }
            throw new Exception("MenuManager class not found");
        });
    }

    public synchronized static Method loadMenuStatusMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var id = Utils.getID("menuitem_conversations_message_contact", "id");
            var methods = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(id)));
            if (methods.isEmpty()) throw new Exception("MenuStatus method not found");
            return methods.get(0).getMethodInstance(loader);
        });
    }

    // TODO: Classes and methods to ViewOnce

    public synchronized static Method[] loadViewOnceMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethods(classLoader, () -> {
            var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("INSERT_VIEW_ONCE_SQL", StringMatchType.Contains)));
            if (method.isEmpty()) throw new Exception("ViewOnce method not found");
            var methodData = method.get(0);
            var listMethods = methodData.getInvokes();
            var list = new ArrayList<Method>();
            for (MethodData m : listMethods) {
                var mInstance = m.getMethodInstance(classLoader);
                if (mInstance.getDeclaringClass().isInterface() && mInstance.getDeclaringClass().getMethods().length == 2) {
                    ClassDataList listClasses = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addInterface(mInstance.getDeclaringClass().getName())));
                    for (ClassData c : listClasses) {
                        Class<?> clazz = c.getInstance(classLoader);
                        for (Method m2 : clazz.getDeclaredMethods()) {
                            if (m2.getParameterCount() != 1 || m2.getParameterTypes()[0] != int.class || m2.getReturnType() != void.class)
                                continue;
                            list.add(m2);
                        }
                    }
                    if (list.isEmpty()) throw new Exception("ViewOnce method not found");
                    return list.toArray(new Method[0]);
                }
            }
            throw new Exception("ViewOnce method not found");
        });
    }


    /**
     * @noinspection SimplifyOptionalCallChains
     */
    public synchronized static Method loadViewOnceDownloadMenuMethod(ClassLoader classLoader) throws Exception {
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


    // TODO: Methods and Classes for Change Colors

    public synchronized static Class<?> loadExpandableWidgetClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "expandableWidgetHelper");
            if (clazz == null) throw new Exception("ExpandableWidgetHelper class not found");
            return clazz;
        });
    }

    public synchronized static Class<?> loadMaterialShapeDrawableClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "Compatibility shadow requested");
            if (clazz == null) throw new Exception("MaterialShapeDrawable class not found");
            return clazz;
        });
    }

    public synchronized static Method loadPropsBooleanMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown BooleanField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }

    public synchronized static Method loadPropsIntegerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown IntField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }

    public synchronized static Method loadPropsJsonMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown JsonField");
            if (method == null) throw new Exception("Props method not found");
            return method;
        });
    }


    private static ClassData loadAntiRevokeImplClass() throws Exception {
        var classes = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addUsingString("smb_eu_tos_update_url")));
        if (classes.isEmpty()) throw new Exception("AntiRevokeImpl class not found");
        return classes.get(0);
    }

    public synchronized static Method loadAntiRevokeOnStartMethod(ClassLoader loader) throws Exception {
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

    public synchronized static Method loadHomeConversationFragmentMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var homeClass = WppCore.getHomeActivityClass(loader);
            var convFragment = XposedHelpers.findClass("com.whatsapp.ConversationFragment", loader);
            MethodData method = dexkit.findMethod(FindMethod.create()
                    .searchInClass(
                            Collections.singletonList(
                                    dexkit.getClassData(homeClass)))
                    .matcher(MethodMatcher.create().returnType(convFragment))).singleOrNull();
            if (method == null) throw new Exception("HomeConversationFragmentMethod not found");
            return method.getMethodInstance(loader);
        });
    }

    public synchronized static Field loadAntiRevokeConvFragmentField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> chatClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversation/createconversation");
            Class<?> conversation = XposedHelpers.findClass("com.whatsapp.ConversationFragment", loader);
            Field field = ReflectionUtils.getFieldByType(conversation, chatClass);
            if (field == null) throw new Exception("AntiRevokeConvChat field not found");
            return field;
        });
    }

    public synchronized static Field loadAntiRevokeConvChatField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> chatClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversation/createconversation");
            Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation", loader);
            Field field = ReflectionUtils.getFieldByType(conversation, chatClass);
            if (field == null) throw new Exception("AntiRevokeConvChat field not found");
            return field;
        });
    }

    public synchronized static Field loadAntiRevokeChatJidField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> chatClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversation/createconversation");
            Class<?> jidClass = XposedHelpers.findClass("com.whatsapp.jid.Jid", loader);
            Field field = ReflectionUtils.getFieldByExtendType(chatClass, jidClass);
            if (field == null) throw new Exception("AntiRevokeChatJid field not found");
            return field;
        });
    }

    public synchronized static Method loadAntiRevokeMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Method method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "msgstore/edit/revoke");
            if (method == null) throw new Exception("AntiRevokeMessage method not found");
            return method;
        });
    }

    public synchronized static Field loadMessageKeyField(ClassLoader loader) throws Exception {
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

    public synchronized static Method loadAntiRevokeBubbleMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Class<?> bubbleClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "ConversationRow/setUpUserNameInGroupView");
            if (bubbleClass == null) throw new Exception("AntiRevokeBubble method not found");
            var result = Arrays.stream(bubbleClass.getMethods()).filter(m -> m.getParameterCount() > 1 && m.getParameterTypes()[0] == ViewGroup.class && m.getParameterTypes()[1] == TextView.class).findFirst().orElse(null);
            if (result == null) throw new Exception("AntiRevokeBubble method not found");
            return result;
        });
    }

    public synchronized static Method loadUnknownStatusPlaybackMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var statusPlaybackClass = XposedHelpers.findClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment", loader);
            var refreshCurrentPage = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("playbackFragment/refreshCurrentPageSubTitle message is empty"))).get(0);
            var invokes = refreshCurrentPage.getInvokes();
            for (var invoke : invokes) {
                var method = invoke.getMethodInstance(loader);
                if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() > 1 && List.of(method.getParameterTypes()).contains(statusPlaybackClass) && method.getDeclaringClass() == statusPlaybackClass) {
                    return method;
                }
            }
            throw new Exception("UnknownStatusPlayback method not found");
        });
    }

    public synchronized static Class loadStatusPlaybackViewClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var ids = List.of(Utils.getID("status_header", "id"), Utils.getID("menu", "id"));
            var clazz = dexkit.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().addMethod(
                                    MethodMatcher.create().usingNumbers(ids)
                            )
                    )
            );
            if (clazz.isEmpty()) throw new Exception("Not Found StatusPlaybackViewClass");
            return clazz.get(0).getInstance(loader);
        });
    }


    public synchronized static Method loadBlueOnReplayMessageJobMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "SendE2EMessageJob/onRun");
            if (result == null) throw new Exception("BlueOnReplayMessageJob method not found");
            return result;
        });
    }

    public synchronized static Method loadBlueOnReplayWaJobManagerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstClassUsingStrings(loader, StringMatchType.Contains, "WaJobManager/start");
            var job = XposedHelpers.findClass("org.whispersystems.jobqueue.Job", loader);
            if (result == null) throw new Exception("BlueOnReplayWaJobManager method not found");
            var method = Arrays.stream(result.getMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == job).findFirst().orElse(null);
            if (method == null) throw new Exception("BlueOnReplayWaJobManager method not found");
            return method;
        });
    }

    public synchronized static Class loadArchiveChatClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "archive/set-content-indicator-to-empty");
            if (clazz == null)
                clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "archive/Unsupported mode in ArchivePreviewView:");
            if (clazz == null) throw new Exception("ArchiveHideView method not found");
            return clazz;
        });
    }


    public synchronized static Method loadAntiRevokeOnCallReceivedMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "voip/callStateChangedOnUIThread");
            if (method == null) throw new Exception("OnCallReceiver method not found");
            return method;
        });
    }

    public synchronized static Method loadOnChangeStatus(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            Method method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "ConversationViewFiller/setParentGroupProfilePhoto");
            if (method == null) throw new Exception("OnChangeStatus method not found");

            // for 19.xx, the current implementation returns wrong method
            if (method.getParameterCount() < 6) {
                ClassData declaringClassData = dexkit.getClassData(method.getDeclaringClass());
                if (declaringClassData == null)
                    throw new Exception("OnChangeStatus method not found");

                Class<?> arg1Class = findFirstClassUsingStrings(loader, StringMatchType.Contains, "problematic contact:");
                MethodDataList methodData = declaringClassData.findMethod(
                        FindMethod.create().matcher(MethodMatcher.create().paramCount(6, 8)));

                for (var methodItem : methodData) {
                    var paramTypes = methodItem.getParamTypes();

                    if (paramTypes.get(0).getInstance(loader) == arg1Class &&
                            paramTypes.get(1).getInstance(loader) == arg1Class) {
                        method = methodItem.getMethodInstance(loader);
                        break;
                    }
                }
            }

            return method;
        });
    }

    public synchronized static Class<?> loadViewHolder(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            Method method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "conversations/click/jid ");
            if (method == null || method.getParameterCount() == 0)
                throw new RuntimeException("ViewHolder not found!");
            return method.getParameterTypes()[0];
        });
    }

    public synchronized static Field loadViewHolderField1(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            Class<?> class1 = loadOnChangeStatus(loader).getDeclaringClass().getSuperclass();
            return ReflectionUtils.getFieldByType(class1, loadViewHolder(loader));
        });
    }

    public synchronized static Method loadStatusUserMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var id = UnobfuscatorCache.getInstance().getOfuscateIDString("lastseensun%s");
            if (id < 1) throw new Exception("GetStatusUser ID not found");
            var result = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingNumber(id).returnType(String.class)));
            if (result.isEmpty()) throw new Exception("GetStatusUser method not found");
            return result.get(result.size() - 1).getMethodInstance(loader);
        });
    }

    public synchronized static Method loadSendPresenceMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var methodData = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("app/send-presence-subscription jid=")));
            if (methodData.isEmpty()) throw new Exception("SendPresence method not found");
            var methodCallers = methodData.get(0).getCallers();
            if (methodCallers.isEmpty()) {
                var method = methodData.get(0);
                var superMethodInterfaces = method.getDeclaredClass().getInterfaces();
                if (superMethodInterfaces.isEmpty())
                    throw new Exception("SendPresence method interface list empty");
                var superMethod = superMethodInterfaces.get(0).findMethod(FindMethod.create().matcher(MethodMatcher.create().name(method.getName()))).firstOrNull();
                if (superMethod == null)
                    throw new Exception("SendPresence method interface method not found");
                methodCallers = superMethod.getCallers();
            }
            var newMethod = methodCallers.firstOrNull(method1 -> method1.getParamCount() == 4);
            if (newMethod == null) throw new Exception("SendPresence method not found 2");
            return newMethod.getMethodInstance(loader);
        });
    }


    public synchronized static Method loadPinnedHashSetMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "getPinnedJids/QUERY_CHAT_SETTINGS");
            if (method == null) throw new Exception("PinnedHashSet method not found");
            return method;
        });
    }

    public synchronized static Method loadGetFiltersMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazzFilters = findFirstClassUsingStrings(loader, StringMatchType.Contains, "conversations/filter/performFiltering");
            if (clazzFilters == null) throw new RuntimeException("Filters class not found");
            return Arrays.stream(clazzFilters.getDeclaredMethods()).parallel().filter(m -> m.getName().equals("publishResults")).findFirst().orElse(null);
        });
    }

    public synchronized static Method loadPinnedInChatMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(3732).returnType(int.class)));
            if (method.isEmpty()) throw new RuntimeException("PinnedInChat method not found");
            return method.get(0).getMethodInstance(loader);
        });
    }

    public synchronized static Method loadBlueOnReplayCreateMenuConversationMethod(ClassLoader loader) throws Exception {
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

    public synchronized static Method loadBlueOnReplayViewButtonMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "PLAYBACK_PAGE_ITEM_ON_CREATE_VIEW_END");
            if (method == null)
                throw new RuntimeException("BlueOnReplayViewButton method not found");
            return method;
        });
    }

    public synchronized static Field loadBlueOnReplayViewButtonOutSideField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = loadBlueOnReplayViewButtonMethod(loader);
            var clazz = method.getDeclaringClass();
            var methodData = dexkit.getMethodData(method);
            var fields = methodData.getUsingFields();
            for (var ufield : fields) {
                var field = ufield.getField().getFieldInstance(loader);
                if (clazz.isAssignableFrom(field.getDeclaringClass()) && clazz != field.getDeclaringClass())
                    return field;
            }
            throw new Exception("BlueOnReplayViewButtonOutSide not found!");
        });
    }

    public synchronized static Method loadBlueOnReplayStatusViewMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "StatusPlaybackPage/onViewCreated");
            if (method == null)
                throw new RuntimeException("BlueOnReplayViewButton method not found");
            return method;
        });
    }

    public synchronized static Method loadChatLimitDeleteMethod(ClassLoader loader) throws Exception {
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

    public synchronized static Method loadChatLimitDelete2Method(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "pref_revoke_admin_nux", "dialog/delete no messages");
            if (method == null) throw new RuntimeException("ChatLimitDelete2 method not found");
            return method;
        });
    }

    public synchronized static Method loadNewMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazzMessageName = loadFMessageClass(loader).getName();
            var listMethods = dexkit.findMethod(FindMethod.create().searchPackages("com.whatsapp").matcher(MethodMatcher.create().addUsingString("extra_payment_note", StringMatchType.Equals)));
            if (listMethods.isEmpty()) throw new Exception("NewMessage method not found");
            var invokes = listMethods.get(0).getInvokes();
            var method = invokes.parallelStream().filter(invoke -> clazzMessageName.equals(invoke.getDeclaredClass().getName()) && invoke.getReturnType() != null && invoke.getReturnType().getName().equals("java.lang.String")).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("NewMessage method not found");
            return method.getMethodInstance(loader);
        });
    }

    public synchronized static Method loadOriginalMessageKey(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "FMessageUtil/getOriginalMessageKeyIfEdited");
            if (method == null) throw new RuntimeException("MessageEdit method not found");
            return method;
        });
    }

    public synchronized static Method loadNewMessageWithMediaMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var methodList = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("INSERT_TABLE_MESSAGE_QUOTED", StringMatchType.Equals)));
            if (methodList.isEmpty()) throw new Exception("NewMessageWithMedia method not found");
            var methodData = methodList.get(0);
            var invokes = methodData.getInvokes();
            var clazzMessageName = loadFMessageClass(loader).getName();
            var method = invokes.parallelStream().filter(invoke -> clazzMessageName.equals(invoke.getDeclaredClass().getName()) && invoke.getReturnType() != null && invoke.getReturnType().getName().equals("java.lang.String")).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("NewMessageWithMedia method not found");
            return method.getMethodInstance(loader);
        });
    }

    public synchronized static Method loadMessageEditMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageEditInfoStore/insertEditInfo/missing");
            if (method == null) throw new RuntimeException("MessageEdit method not found");
            return method;
        });
    }

    public synchronized static Method loadCallerMessageEditMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var methodData1 = dexkit.getMethodData(loadMessageEditMethod(loader));
            var FMessage = loadFMessageClass(loader);
            var invokes = methodData1.getInvokes();
            for (var methodData : invokes) {
                if (methodData.isConstructor()) continue;
                var method = methodData.getMethodInstance(loader);
                if (Modifier.isStatic(method.getModifiers()) &&
                        method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(FMessage) &&
                        !method.getReturnType().isPrimitive()
                ) {
                    return methodData.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("CallerMessageEdit method not found");
        });
    }


    public synchronized static Method loadGetEditMessageMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageEditInfoStore/insertEditInfo/missing");
            if (method == null) throw new RuntimeException("GetEditMessage method not found");
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            if (methodData == null) throw new RuntimeException("GetEditMessage method not found");
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                // pre 21.xx method
                if (invoke.getParamTypes().isEmpty() && Objects.equals(invoke.getDeclaredClass(), methodData.getParamTypes().get(0))) {
                    return invoke.getMethodInstance(loader);
                }

                // 21.xx+ method (static)
                // 25.xx+ added additional type check
                if (Modifier.isStatic(invoke.getMethodInstance(loader).getModifiers()) && Objects.equals(invoke.getParamTypes().get(0), methodData.getParamTypes().get(0)) && !Objects.equals(invoke.getParamTypes().get(0), invoke.getDeclaredClass())) {
                    return invoke.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("GetEditMessage method not found");
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public synchronized static Field loadSetEditMessageField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "CoreMessageStore/updateCheckoutMessageWithTransactionInfo");
            if (method == null)
                method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "UPDATE_MESSAGE_ADD_ON_FLAGS_MAIN_SQL");
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
    public synchronized static Method loadEditMessageShowMethod(ClassLoader loader) throws Exception {
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
    public synchronized static Field loadEditMessageViewField(ClassLoader loader) throws Exception {
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
    public synchronized static Class loadDialogViewClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var id = Utils.getID("touch_outside", "id");
            var result = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingNumber(id).returnType(FrameLayout.class)));
            if (result.isEmpty()) throw new RuntimeException("DialogView class not found");
            return result.get(0).getDeclaredClass().getInstance(loader);
        });
    }

    public synchronized static Constructor loadRecreateFragmentConstructor(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(loader, () -> {
            var data = dexkit.findMethod(FindMethod.create().searchPackages("X.").matcher(MethodMatcher.create().addUsingString("Instantiated fragment")));
            if (data.isEmpty()) throw new RuntimeException("RecreateFragment method not found");
            if (!data.single().isConstructor())
                throw new RuntimeException("RecreateFragment method not found");
            return data.single().getConstructorInstance(loader);
        });
    }


    public synchronized static Method loadOnTabItemAddMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var result = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Maximum number of items supported by");
            if (result == null) throw new RuntimeException("OnTabItemAdd method not found");
            return result;
        });
    }


    public synchronized static Method loadGetViewConversationMethod(ClassLoader loader) throws Exception {
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
    public synchronized static Method loadOnMenuItemSelected(ClassLoader loader) throws Exception {
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

    public synchronized static Method loadOnUpdateStatusChanged(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var clazz = getClassByName("UpdatesViewModel", loader);
            var clazzData = dexkit.getClassData(clazz);
            var methodSeduleche = XposedHelpers.findMethodBestMatch(Timer.class, "schedule", TimerTask.class, long.class, long.class);
            var result = dexkit.findMethod(new FindMethod().searchInClass(List.of(clazzData)).matcher(new MethodMatcher().addInvoke(DexSignUtil.getMethodDescriptor(methodSeduleche))));
            if (result.isEmpty())
                result = dexkit.findMethod(new FindMethod().searchInClass(List.of(clazzData)).matcher(new MethodMatcher().addUsingString("UpdatesViewModel/Scheduled updates list refresh")));
            if (result.isEmpty())
                throw new RuntimeException("OnUpdateStatusChanged method not found");
            return result.get(0).getMethodInstance(loader);
        });
    }

    /**
     * @noinspection DataFlowIssue
     */
    public synchronized static Field loadGetInvokeField(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(loader, () -> {
            var method = loadOnUpdateStatusChanged(loader);
            var methodData = dexkit.getMethodData(DexSignUtil.getMethodDescriptor(method));
            var fields = methodData.getUsingFields();
            var field = fields.stream().map(UsingFieldData::getField).filter(f -> f.getDeclaredClass().equals(methodData.getDeclaredClass())).findFirst().orElse(null);
            if (field == null) throw new RuntimeException("GetInvokeField method not found");
            return field.getFieldInstance(loader);
        });
    }

    public synchronized static Class<?> loadStatusInfoClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "ContactStatusDataItem");
            if (clazz == null) throw new RuntimeException("StatusInfo class not found");
            return clazz;
        });
    }

    public synchronized static Class loadStatusListUpdatesClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "StatusListUpdates");
            if (clazz == null) throw new RuntimeException("StatusListUpdates class not found");
            return clazz;
        });
    }

    public synchronized static Class loadTabFrameClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "android:menu:presenters");
            if (clazz == null) throw new RuntimeException("TabFrame class not found");
            return clazz;
        });
    }

    public synchronized static Class loadRemoveChannelRecClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "hasNewsletterSubscriptions");
            if (clazz == null) throw new RuntimeException("RemoveChannelRec class not found");
            return clazz;
        });
    }

    public synchronized static Class loadFilterAdaperClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingString("CONTACTS_FILTER").paramCount(1).addParamType(int.class))));
            if (clazzList.isEmpty()) throw new RuntimeException("FilterAdapter class not found");
            return clazzList.get(0).getInstance(loader);
        });
    }

    public synchronized static Constructor loadSeeMoreConstructor(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(loader, () -> {
            var classList = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .addMethod(MethodMatcher.create().addUsingNumber(16384).addUsingNumber(512).addUsingNumber(64).addUsingNumber(16))
                    .addMethod(MethodMatcher.create().paramCount(2).paramTypes(int.class, boolean.class))
                    .addMethod(MethodMatcher.create().paramCount(2, 3).paramTypes(int.class, int.class, int.class))
            ));

            if (classList.isEmpty()) throw new RuntimeException("SeeMore constructor 1 not found");
            var clazzData = classList.get(0);
            for (var method : clazzData.getMethods()) {
                if (method.getParamCount() > 1 && method.isConstructor() && method.getParamTypes().stream().allMatch(c -> c.getName().equals(int.class.getName()))) {
                    return method.getConstructorInstance(loader);
                }
            }
            throw new RuntimeException("SeeMore constructor 2 not found");
        });
    }

    public synchronized static Method loadSendStickerMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "StickerGridViewItem.StickerLocal");
            if (method == null) throw new RuntimeException("SendSticker method not found");
            return method;
        });

    }

    public synchronized static Method loadMaterialAlertDialog(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var callConfirmationFragment = XposedHelpers.findClass("com.whatsapp.calling.fragment.CallConfirmationFragment", loader);
            var method = ReflectionUtils.findMethodUsingFilter(callConfirmationFragment, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(android.os.Bundle.class));
            var methodData = dexkit.getMethodData(method);
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                if (invoke.isMethod() && Modifier.isStatic(invoke.getModifiers()) && invoke.getParamCount() == 1 && invoke.getParamTypes().get(0).getName().equals(Context.class.getName())) {
                    return invoke.getMethodInstance(loader);
                }

                // for 22.xx, MaterialAlertDialog method is not static
                if (invoke.isMethod() && invoke.getParamCount() == 1 && invoke.getParamTypes().get(0).getName().equals(Context.class.getName())) {
                    return invoke.getMethodInstance(loader);
                }
            }
            throw new RuntimeException("MaterialAlertDialog not found");
        });
    }

    public synchronized static Method loadGetIntPreferences(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().paramCount(2).addParamType(SharedPreferences.class).addParamType(String.class).modifiers(Modifier.STATIC | Modifier.PUBLIC).returnType(int.class)));
            if (methodList.isEmpty())
                throw new RuntimeException("CallConfirmationLimit method not found");
            return methodList.get(0).getMethodInstance(loader);
        });
    }

    public synchronized static Field loadProfileInfoField(ClassLoader loader) throws Exception {
        var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "[obfuscated]@%s");
        if (clazz == null) throw new RuntimeException("ProfileInfo class not found");
        var fieldList = ReflectionUtils.getFieldsByExtendType(clazz, XposedHelpers.findClass("com.whatsapp.jid.Jid", loader));
        if (fieldList.isEmpty()) throw new RuntimeException("ProfileInfo field not found");
        return fieldList.get(0);
    }

    public synchronized static Method loadAudioProximitySensorMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "messageaudioplayer/onearproximity");
            if (method == null) throw new RuntimeException("ProximitySensor method not found");
            return method;
        });
    }

    public synchronized static Method loadGroupAdminMethod(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "P Message");
        if (method == null)
            method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "ConversationRow/setUpUsernameInGroupViewContainer/not allowed state");
        if (method == null) throw new RuntimeException("GroupAdmin method not found");
        return method;
    }

    public synchronized static Method loadJidFactory(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "lid_me", "status_me", "s.whatsapp.net");
        if (method == null) throw new RuntimeException("JidFactory method not found");
        return method;
    }

    public synchronized static Method loadGroupCheckAdminMethod(ClassLoader loader) throws Exception {
        var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "saveGroupParticipants/INSERT_GROUP_PARTICIPANT_USER");
        var userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", loader);
        var methods = ReflectionUtils.findAllMethodsUsingFilter(clazz, m -> m.getParameterCount() == 2 && m.getParameterTypes()[1].equals(userJidClass) && m.getReturnType().equals(boolean.class));
        if (methods == null || methods.length == 0)
            throw new RuntimeException("GroupCheckAdmin method not found");
        return methods[methods.length - 1];
    }

    public synchronized static Constructor loadStartPrefsConfig(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getConstructor(loader, () -> {
            var results = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("startup_migrated_version")));
            if (results.isEmpty())
                throw new RuntimeException("StartPrefsConfig constructor not found");
            return results.get(0).getConstructorInstance(loader);
        });
    }

    public synchronized static Method loadCheckOnlineMethod(ClassLoader loader) throws Exception {
        var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "MessageHandler/handleConnectionThreadReady connectionready");
        if (method == null)
            method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "app/xmpp/recv/handle_available");
        if (method == null) throw new RuntimeException("CheckOnline method not found");
        return method;
    }

    public synchronized static Method loadEphemeralInsertdb(ClassLoader loader) throws Exception {
        var method = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("expire_timestamp").addUsingString("ephemeral_initiated_by_me").addUsingString("ephemeral_trigger").returnType(ContentValues.class)));
        if (method.isEmpty()) throw new RuntimeException("FieldExpireTime method not found");
        var methodData = method.get(0);
        return methodData.getMethodInstance(loader);
    }

    public synchronized static Method loadDefEmojiClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, "emojis.oba");
            if (method == null) throw new RuntimeException("DefEmoji class not found");
            return method;
        });
    }

    public synchronized static Class loadVideoViewContainerClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, "frame_visibility_serial_worker");
            if (clazz == null) throw new RuntimeException("VideoViewContainer class not found");
            return clazz;
        });
    }

    public synchronized static Class loadImageVewContainerClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().addUsingNumber(Utils.getID("hd_invisible_touch", "id")).addUsingNumber(Utils.getID("control_btn", "id")))));
            if (clazzList.isEmpty())
                throw new RuntimeException("ImageViewContainer class not found");
            return clazzList.get(0).getInstance(loader);
        });
    }


    public synchronized static Method getFilterInitMethod(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(loader, () -> {
            var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(loader);
            var constructor = filterAdaperClass.getConstructors()[0];
            var methods = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addInvoke(DexSignUtil.getMethodDescriptor(constructor))));
            if (methods.isEmpty()) throw new RuntimeException("FilterInit method not found");
            var cFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", loader);
            var method = methods.stream().filter(m -> Arrays.asList(1, 2).contains(m.getParamCount()) && m.getParamTypes().get(0).getName().equals(cFrag.getName())).findFirst().orElse(null);
            if (method == null) throw new RuntimeException("FilterInit method not found 2");

            // for 20.xx, it returned with 2 parameter count
            if (method.getParamCount() == 2) {
                var callers = method.getCallers();
                method = callers.stream().filter(methodData -> methodData.isMethod() && methodData.getDeclaredClassName().equals(cFrag.getName())).findAny().orElse(null);
                if (method == null)
                    throw new RuntimeException("FilterInit method not found 3");
            }
            return method.getMethodInstance(loader);
        });
    }

    public synchronized static Class getFilterView(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var filter_id = Utils.getID("conversations_swipe_to_reveal_filters_stub", "id");
            var results = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addMethod(MethodMatcher.create().addUsingNumber(filter_id))));
            if (results.isEmpty()) throw new RuntimeException("FilterView class not found");
            return results.get(0).getInstance(loader);
        });
    }

    public synchronized static Class loadActionUser(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            for (String s : List.of("UserActions/reportIfBadTime: time=", "UserActions/createFMessageTextFromUserInputs", "UserActions/userActionKeepInChat")) {
                var clazz = findFirstClassUsingStrings(loader, StringMatchType.Contains, s);
                if (clazz != null)
                    return clazz;
            }
            throw new ClassNotFoundException("ActionUser class not found");
        });
    }

    public synchronized static Method loadOnPlaybackFinished(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "playbackPage/onPlaybackContentFinished");
            if (method == null) throw new RuntimeException("OnPlaybackFinished method not found");
            return method;
        });
    }

    public synchronized static Method loadNextStatusRunMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methodList = dexkit.findMethod(new FindMethod().matcher(new MethodMatcher().addUsingString("playMiddleTone").name("run")));
            if (methodList.isEmpty()) throw new RuntimeException("RunNextStatus method not found");
            return methodList.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Method loadOnInsertReceipt(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methods = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("INSERT_RECEIPT_USER")));
            for (var method : methods) {
                var params = method.getParamTypeNames();
                if (!params.isEmpty() && "com.whatsapp.jid.UserJid".equals(params.get(0))) {
                    return method.getMethodInstance(classLoader);
                }
            }
            throw new RuntimeException("OnInsertReceipt method not found");
        });

    }

    public synchronized static Method loadSendAudioTypeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var classMsgReplyAct = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "MessageReplyActivity");
            if (classMsgReplyAct == null)
                throw new ClassNotFoundException("Class MessageReplyActivity not found");
            var method = classMsgReplyAct.getMethod("onActivityResult", int.class, int.class, android.content.Intent.class);
            var methodData = Objects.requireNonNull(dexkit.getMethodData(method));
            var invokes = methodData.getInvokes();
            for (var invoke : invokes) {
                if (!invoke.isMethod()) continue;
                var m1 = invoke.getMethodInstance(classLoader);
                var params = Arrays.asList(m1.getParameterTypes());
                if (params.contains(List.class) && params.contains(int.class) && params.contains(boolean.class) && params.contains(Uri.class)) {
                    return m1;
                }
            }
            throw new NoSuchMethodException("SendAudioType method not found");
        });
    }

    public synchronized static Field loadOriginFMessageField(ClassLoader classLoader) throws Exception {
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

    public synchronized static Method loadForwardAudioTypeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var results = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "FMessageFactory/newFMessageForForward/thumbnail");
            if (results == null || results.length < 1)
                throw new RuntimeException("ForwardAudioType method not found");
            Method result;
            if (results.length > 1) {
                result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "forwardable", "FMessageFactory/newFMessageForForward/thumbnail");
            } else {
                // 2.24.18.xx method is changed
                result = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Non-forwardable message(");
            }
            return result;
        });
    }

    public synchronized static Class loadFragmentLoader(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "not associated with a fragment manager.");
            if (clazz == null) throw new RuntimeException("FragmentLoader class not found");
            return clazz;
        });
    }

    public synchronized static Method loadShowDialogStatusMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadFragmentLoader(classLoader);
            var frag = classLoader.loadClass("androidx.fragment.app.DialogFragment");
            var result = dexkit.findMethod(FindMethod.create().matcher(
                            MethodMatcher.create().paramCount(2).addParamType(frag).addParamType(clazz)
                                    .returnType(void.class).modifiers(Modifier.PUBLIC | Modifier.STATIC)
                                    .opNames(List.of("iget-boolean", "if-nez"), OpCodeMatchType.Contains)
                    )
            );
            if (result.isEmpty()) throw new RuntimeException("showDialogStatus not found");
            return result.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Method loadPlaybackSpeed(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "heroaudioplayer/setPlaybackSpeed");
            if (method == null) throw new RuntimeException("PlaybackSpeed method not found");
            return method;
        });
    }

//    public synchronized static Method loadArchiveCheckLockedChatsMethod(ClassLoader classLoader) throws Exception {
//        var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "conversationsfragment/verticalswipetorevealbehavior");
//        if (method == null) throw new RuntimeException("ArchiveCheckLockedChats method not found");
//        return method;
//    }
//
//    public synchronized static Method loadArchiveCheckLockedChatsMethod2(ClassLoader classLoader) throws Exception {
//        var methods = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "registration_device_id");
//        if (methods.length == 0)
//            throw new RuntimeException("ArchiveCheckLockedChats method not found");
//        return Arrays.stream(methods).filter(m -> m.getReturnType().equals(boolean.class) && m.getParameterTypes().length == 0).findFirst().orElse(null);
//    }
//
//    public synchronized static Class<?> loadArchiveLockedChatClass(ClassLoader classLoader) throws Exception {
//        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
//            var clazzList = dexkit.findClass(new FindClass().matcher(new ClassMatcher().addMethod(new MethodMatcher().name("setLockedRowVisibility")).addMethod(new MethodMatcher().name("setEnableStateForChatLock"))));
//            if (clazzList.isEmpty())
//                throw new RuntimeException("ArchiveLockedChatFrame class not found");
//            return clazzList.get(0).getInstance(classLoader);
//        });
//    }

    public synchronized static Method loadListUpdateItems(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("Running diff util, updates list size", StringMatchType.Contains)));
            if (method.isEmpty())
                throw new RuntimeException("ListUpdateItems method not found");
            return method.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Class loadHeaderChannelItemClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "statusTilesEnabled");
            if (clazz == null) throw new RuntimeException("HeaderChannelItem class not found");
            return clazz;
        });
    }

    public synchronized static Class loadListChannelItemClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "isMuteIndicatorEnabled");
            if (clazz == null) throw new RuntimeException("NewsletterDataItem class not found");
            return clazz;
        });
    }


    public synchronized static Method[] loadTextStatusData(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethods(classLoader, () -> {
            var methods = dexkit.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().addParamType("com.whatsapp.TextData")
                    )
            );
            if (methods.isEmpty())
                throw new RuntimeException("loadTextStatusData method not found");

            return methods.stream().filter(MethodData::isMethod).map(methodData -> convertRealMethod(methodData, classLoader)).toArray(Method[]::new);
        });
    }

    public synchronized static Class<?> loadExpirationClass(ClassLoader classLoader) {
        var methods = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "software_forced_expiration");
        var expirationMethod = Arrays.stream(methods).filter(methodData -> methodData.getReturnType().equals(Date.class)).findFirst().orElse(null);
        if (expirationMethod == null) throw new RuntimeException("Expiration class not found");
        return expirationMethod.getDeclaringClass();
    }


    public synchronized static Class<?> loadAbsViewHolder(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "not recyclable");
            if (clazz == null) throw new RuntimeException("AbsViewHolder class not found");
            return clazz;
        });
    }

    public synchronized static Method loadFragmentViewMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "this was called before onCreateView()");
            if (method == null) throw new RuntimeException("FragmentView method not found");
            return method;
        });
    }

    public synchronized static Method loadCopiedMessageMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "conversation/copymessage");
            if (method == null) throw new RuntimeException("CopiedMessage method not found");
            return method;
        });
    }

    public synchronized static Class<?> loadSenderPlayedClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "sendmethods/sendClearDirty");
            if (clazz == null) throw new RuntimeException("SenderPlayed class not found");
            return clazz;
        });
    }

    public synchronized static Method loadSenderPlayedMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadSenderPlayedClass(classLoader);
            var abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
            var interfaces = abstractMediaMessageClass.getInterfaces();

            ArrayList<Class> interfacesList = new ArrayList<>();
            interfacesList.add(abstractMediaMessageClass);
            interfacesList.addAll(Arrays.asList(interfaces));

            Method methodResult = null;
            main_loop:
            for (var method : clazz.getMethods()) {
                if (method.getParameterCount() != 1) continue;
                var parameterType = method.getParameterTypes()[0];
                for (var interfaceClass : interfacesList) {
                    if (interfaceClass.isAssignableFrom(parameterType)) {
                        methodResult = method;
                        break main_loop;
                    }
                }
            }

            // 2.25.19.xx, they refactored the SenderPlayed class
            var fmessageClass = Unobfuscator.loadFMessageClass(classLoader);
            if (methodResult == null) {
                var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "mediaHash and fileType not both present for upload URL generation");
                if (method != null) {
                    var cMethods = dexkit.getMethodData(method).getInvokes();
                    Collections.reverse(cMethods);
                    for (var cmethod : cMethods) {
                        if (cmethod.isMethod() && cmethod.getParamCount() == 1) {
                            var cParamType = cmethod.getParamTypes().get(0).getInstance(classLoader);
                            if (fmessageClass.isAssignableFrom(cParamType)) {
                                methodResult = cmethod.getMethodInstance(classLoader);
                                break;
                            }
                        }
                    }
                }
            }

            if (methodResult == null) throw new RuntimeException("SenderPlayed method not found 2");
            return methodResult;
        });
    }

    public synchronized static Method loadSenderPlayedBusiness(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var loadSenderPlayed = loadSenderPlayedClass(classLoader);
            var foundMethod = ReflectionUtils.findMethodUsingFilter(loadSenderPlayed, method -> method.getParameterCount() > 0 && method.getParameterTypes()[0] == Set.class);
            if (foundMethod == null)
                throw new RuntimeException("SenderPlayedBusiness method not found");
            return foundMethod;
        });
    }

    public synchronized static Field loadMediaTypeField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var methodData = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("conversation/refresh")));
            if (methodData.isEmpty()) throw new RuntimeException("MediaType: aux method not found");
            var fclass = dexkit.getClassData(loadFMessageClass(classLoader));
            var usingFields = methodData.get(0).getUsingFields();
            for (var f : usingFields) {
                var field = f.getField();
                if (field.getDeclaredClass().equals(fclass) && field.getType().getName().equals(int.class.getName())) {
                    return field.getFieldInstance(classLoader);
                }
            }
            throw new RuntimeException("MediaType field not found");
        });

    }

    public synchronized static Method loadBubbleDrawableMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methodData = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("Unreachable code: direction=").returnType(Drawable.class)));
            if (methodData.isEmpty()) throw new Exception("BubbleDrawable method not found");
            return methodData.get(0).getMethodInstance(classLoader);
        });
    }

    public synchronized static Method loadBallonDateDrawable(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methodData = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString("Unreachable code: direction=").returnType(Rect.class)));
            if (methodData.isEmpty()) throw new Exception("LoadDateWrapper method not found");
            var clazz = methodData.get(0).getMethodInstance(classLoader).getDeclaringClass();
            var method = ReflectionUtils.findMethodUsingFilterIfExists(clazz, m -> List.of(1, 2).contains(m.getParameterCount()) && m.getParameterTypes()[0].equals(int.class) && m.getReturnType().equals(Drawable.class));
            if (method == null) throw new RuntimeException("DateWrapper method not found");
            return method;
        });
    }

    public synchronized static Method loadBallonBorderDrawable(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var clazz = loadBallonDateDrawable(classLoader).getDeclaringClass();
            var method = ReflectionUtils.findMethodUsingFilterIfExists(clazz, m -> m.getParameterCount() == 3 && m.getReturnType().equals(Drawable.class));
            if (method == null) throw new RuntimeException("Ballon Border method not found");
            return method;
        });
    }

    public static synchronized Method[] loadRootDetector(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethods(classLoader, () -> {
            var methods = findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "/system/bin/su");
            if (methods.length == 0) throw new RuntimeException("RootDetector method not found");
            return methods;
        });
    }

    public static synchronized Method loadCheckEmulator(ClassLoader classLoader) throws Exception {
        var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Android SDK built for x86");
        if (method == null) throw new RuntimeException("CheckEmulator method not found");
        return method;
    }

    public static synchronized Method loadCheckCustomRom(ClassLoader classLoader) throws Exception {
        var method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "cyanogen");
        if (method == null) throw new RuntimeException("CheckCustomRom method not found");
        return method;
    }

    public static synchronized Method loadTranscribeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "transcribe: starting transcription"));
    }

    public static synchronized Method loadCheckSupportLanguage(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "Unsupported language"));
    }

    public static synchronized Class loadUnkTranscript(ClassLoader classLoader) throws Exception {
        var loadTranscribe = loadTranscribeMethod(classLoader);
        var callbackClass = loadTranscribe.getParameterTypes()[1];
        var onComplete = ReflectionUtils.findMethodUsingFilter(callbackClass, method -> method.getParameterCount() == 4);
        var resultTypeClass = onComplete.getParameterTypes()[0].getName();
        Log.i(TAG, resultTypeClass);
        var classDataList = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addUsingString("Unknown").superClass(resultTypeClass)));
        if (classDataList.isEmpty())
            return null;
        return classDataList.get(0).getInstance(classLoader);
    }

    public static synchronized Class loadTranscriptSegment(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "TranscriptionSegment("));
    }

    public static synchronized Method loadStateChangeMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "presencestatemanager/startTransitionToUnavailable/new-state"));
    }

    public static synchronized Class loadCachedMessageStore(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var cacheMsClass = findFirstClassUsingStrings(loader, StringMatchType.Contains, "CachedMessageStore/getMessage/key");
            if (cacheMsClass == null)
                throw new RuntimeException("CachedMessageStore class not found");
            return cacheMsClass;
        });
    }

    public static synchronized Class loadAbstractMediaMessageClass(ClassLoader loader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(loader, () -> {
            var fmessage = loadFMessageClass(loader);
            var classList = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().addUsingString("first_viewed_timestamp").superClass(fmessage.getName())));
            if (classList.isEmpty())
                throw new RuntimeException("AbstractMediaMessage class not found");
            return classList.get(0).getInstance(loader);
        });
    }

    public static Class<?> loadFragmentClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "mFragmentId=#");
            if (clazz == null) throw new RuntimeException("Fragment class not found");
            return clazz;
        });
    }

    public static Method loadMediaQualitySelectionMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> {
            var methodData = dexkit.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().addUsingString("enable_media_quality_tool").
                            returnType(boolean.class)
            ));

            if (methodData.isEmpty()) {
                methodData = dexkit.findMethod(FindMethod.create().matcher(
                        MethodMatcher.create().addUsingString("show_media_quality_toggle").
                                returnType(boolean.class)
                ));
            }

            if (methodData.isEmpty())
                throw new RuntimeException("MediaQualitySelection method not found");
            return methodData.get(0).getMethodInstance(classLoader);
        });
    }

    public static Field loadFmessageTimestampField(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getField(classLoader, () -> {
            var fmessageClass = loadFMessageClass(classLoader);
            var chatLimitDelete2Method = Unobfuscator.loadChatLimitDelete2Method(classLoader);
            var usingFields = dexkit.getMethodData(chatLimitDelete2Method).getUsingFields();
            for (var uField : usingFields) {
                var field = uField.getField();
                if (field.getDeclaredClass().getName().equals(fmessageClass.getName())
                        && field.getType().getName().equals(long.class.getName())) {
                    return field.getFieldInstance(classLoader);
                }
            }
            throw new RuntimeException("FMessage Timestamp method not found");
        });
    }

    public static Class<?> loadStatusDistributionClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var clazz = findFirstClassUsingStrings(classLoader, StringMatchType.Equals, "Only set a valid status distribution mode");
            if (clazz == null) throw new RuntimeException("StatusDistribution not found!");
            return clazz;
        });
    }


    public static Class<?> loadFilterItemClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var methodList = dexkit.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().addUsingNumber(Utils.getID("invisible_height_placeholder", "id"))
                            .addUsingNumber(Utils.getID("container_view", "id"))
            ));
            if (!methodList.isEmpty())
                return methodList.get(0).getClassInstance(classLoader);

            for (var s : List.of("ConversationsFilter/selectFilter", "has_seen_detected_outcomes_nux")) {
                var applyClazz = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, s);
                if (applyClazz == null) continue;
                methodList = dexkit.findMethod(FindMethod.create().matcher(
                        MethodMatcher.create().paramTypes(View.class, applyClazz)
                ));
                if (!methodList.isEmpty()) return methodList.get(0).getClassInstance(classLoader);
            }
            throw new RuntimeException("FilterItemClass Not Found");
        });
    }

    public static Class[] loadProximitySensorListenerClasses(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClasses(classLoader, () -> {
            var classDataList = dexkit.findClass(
                    FindClass.create().matcher(ClassMatcher.create().addInterface(SensorEventListener.class.getName())));
            if (classDataList.isEmpty()) throw new Exception("Class SensorEventListener not found");
            return classDataList.stream().map(classData -> convertRealClass(classData, classLoader)).filter(Objects::nonNull).toArray(Class[]::new);
        });
    }

    public static Class<?> loadRefreshStatusClass(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getClass(classLoader, () -> {
            var strings = new String[]{"liveStatusUpdatesActive", "Statuses refreshed"};
            for (var s : strings) {
                MethodDataList methods = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingString(s, StringMatchType.Contains)));
                if (methods.isEmpty())
                    continue;
                return methods.get(0).getClassInstance(classLoader);
            }
            // Let's look for forcibly on WhatsApp Web (very boring this)
            var opcodes = List.of(
                    "invoke-static",
                    "move-result",
                    "if-eqz",
                    "iget-object",
                    "invoke-static",
                    "move-result-object",
                    "check-cast",
                    "invoke-virtual",
                    "move-result",
                    "xor-int/lit8",
                    "if-eqz",
                    "if-eqz",
                    "return",
                    "const/4",
                    "goto",
                    "const/4",
                    "return"
            );

            var constant = 0x3684;
            MethodDataList methods = dexkit.findMethod(FindMethod.create().matcher(MethodMatcher.create().addUsingNumber(constant).opCodes(
                    OpCodesMatcher.create().opNames(opcodes).matchType(OpCodeMatchType.Contains)
            )));
            if (methods.size() == 1)
                return methods.get(0).getClassInstance(classLoader);
            throw new Exception("Refresh Status Class Not Found!");
        });
    }

    public static Method loadTcTokenMethod(ClassLoader classLoader) throws Exception {
        return UnobfuscatorCache.getInstance().getMethod(classLoader, () -> findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "GET_RECEIVED_TOKEN_AND_TIMESTAMP_BY_JID"));
    }

    public static Class<?> getClassByName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (cacheClasses.containsKey(className))
            return cacheClasses.get(className);
        var classDataList = dexkit.findClass(FindClass.create().matcher(ClassMatcher.create().className(className, StringMatchType.EndsWith)));
        if (classDataList.isEmpty())
            throw new RuntimeException("Class " + className + " not found!");
        var clazz = classDataList.get(0).getInstance(classLoader);
        cacheClasses.put(className, clazz);
        return clazz;
    }

}
