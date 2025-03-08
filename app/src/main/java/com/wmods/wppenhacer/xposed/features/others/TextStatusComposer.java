package com.wmods.wppenhacer.xposed.features.others;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.views.dialog.SimpleColorPickerDialog;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class TextStatusComposer extends Feature {
    private static final AtomicReference<ColorData> colorData = new AtomicReference<>();
    private static final AtomicReference<Object> textComposerModel = new AtomicReference<>();

    public TextStatusComposer(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("statuscomposer", false)) return;

        var setColorTextComposer = Unobfuscator.loadTextStatusComposer(classLoader);
        log("setColorTextComposer: " + Unobfuscator.getMethodDescriptor(setColorTextComposer));

        var textModelClass = XposedHelpers.findClassIfExists("com.whatsapp.statuscomposer.composer.TextStatusComposerViewModel", classLoader);

        if (textModelClass != null) {
            XposedBridge.hookAllConstructors(textModelClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    textComposerModel.set(param.thisObject);
                }
            });
            var arrMethod = ReflectionUtils.findMethodUsingFilter(textModelClass, method -> method.getParameterCount() == 1 && method.getParameterTypes()[0] == int.class && method.getReturnType() == int.class);
            XposedBridge.hookMethod(arrMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        param.setResult(XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args));
                    } catch (Exception e) {
                        param.setResult(ResId.string.app_name);
                    }
                }
            });
        }

        var clazz = XposedHelpers.findClass("com.whatsapp.statuscomposer.composer.TextStatusComposerFragment", classLoader);
        var methodOnCreate = ReflectionUtils.findMethodUsingFilter(clazz, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0] == Bundle.class && method.getParameterTypes()[1] == View.class);
        XposedBridge.hookMethod(methodOnCreate,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var activity = WppCore.getCurrentActivity();
                        var viewRoot = (View) param.args[1];
                        var pickerColor = viewRoot.findViewById(Utils.getID("color_picker_btn", "id"));
                        var entry = (EditText) viewRoot.findViewById(Utils.getID("entry", "id"));

                        pickerColor.setOnLongClickListener(v -> {
                            var dialog = new SimpleColorPickerDialog(activity, color -> {
                                try {
                                    if (textModelClass != null) {
                                        var textModel = textComposerModel.get();
                                        var mField = ReflectionUtils.getFieldsByType(textModel.getClass(), setColorTextComposer.getDeclaringClass()).get(0);
                                        var auxInstance = ReflectionUtils.getObjectField(mField, textModel);
                                        ReflectionUtils.callMethod(setColorTextComposer, auxInstance, "background_color_key", color);
                                    } else {
                                        Field fieldInt = ReflectionUtils.getFieldByType(param.thisObject.getClass(), int.class);
                                        fieldInt.setInt(param.thisObject, color);
                                    }
                                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(color));
                                    var controls = viewRoot.findViewById(Utils.getID("controls", "id"));
                                    controls.setBackgroundColor(color);

                                } catch (Exception e) {
                                    log(e);
                                }
                            });
                            dialog.create().setCanceledOnTouchOutside(false);
                            dialog.show();
                            return true;
                        });

                        var textColor = viewRoot.findViewById(Utils.getID("font_picker_btn", "id"));
                        textColor.setOnLongClickListener(v -> {
                            var dialog = new SimpleColorPickerDialog(activity, color -> {
                                var colorData = new ColorData();
                                colorData.instance = param.thisObject;
                                colorData.color = color;
                                TextStatusComposer.colorData.set(colorData);
                                entry.setTextColor(color);
                            });
                            dialog.create().setCanceledOnTouchOutside(false);
                            dialog.show();
                            return true;
                        });


                    }
                });

        var setColorTextComposer2 = Unobfuscator.loadTextStatusComposer2(classLoader);
        log("setColorTextComposer2: " + Unobfuscator.getMethodDescriptor(setColorTextComposer2));
        XposedBridge.hookMethod(setColorTextComposer2, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (colorData.get() != null) {
                    var textData = param.args[0];
                    XposedHelpers.setObjectField(textData, "textColor", colorData.get().color);
                    colorData.set(null);
                }
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Text Status Composer";
    }

    public static class ColorData {
        public Object instance;
        public int color;
    }
}
