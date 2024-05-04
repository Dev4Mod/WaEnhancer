package com.wmods.wppenhacer.xposed.core.components;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AlertDialogWpp {


    private static Method getAlertDialog;
    private static Class<?> alertDialogClass;
    private static Method setItemsMethod;
    private static boolean isAvailable;
    private static Method setMessageMethod;
    private static Method setNegativeButtonMethod;
    private static Method setPositiveButtonMethod;
    private AlertDialog.Builder mAlertDialog;
    private Object mAlertDialogWpp;

    public static void initDialog(ClassLoader loader) {
        try {
            getAlertDialog = Unobfuscator.loadMaterialAlertDialog(loader);
            alertDialogClass = getAlertDialog.getReturnType();
            setItemsMethod = ReflectionUtils.findMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && method.getParameterTypes()[1].equals(CharSequence[].class));
            setMessageMethod = ReflectionUtils.findMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(CharSequence.class));
            var buttons = ReflectionUtils.findAllMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && method.getParameterTypes()[1].equals(CharSequence.class));
            setNegativeButtonMethod = buttons[0];
            setPositiveButtonMethod = buttons[1];
            isAvailable = true;
        } catch (Throwable e) {
            isAvailable = false;
            XposedBridge.log(e);
        }
    }

    public static boolean isAvailable() {
        return isAvailable;
    }

    public AlertDialogWpp(Context context) {
        if (!isAvailable()) {
            mAlertDialog = new AlertDialog.Builder(context);
            return;
        }
        try {
            mAlertDialogWpp = getAlertDialog.invoke(null, context);
            // Remove Default Message
            setMessage(null);
        } catch (Exception e) {
            XposedBridge.log(e);
        }

    }

    public AlertDialogWpp setTitle(String title) {
        if (!isAvailable()) {
            mAlertDialog.setTitle(title);
            return this;
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
        return this;
    }

    public AlertDialogWpp setMessage(String message) {
        if (!isAvailable()) {
            mAlertDialog.setMessage(message);
            return this;
        }
        try {
            setMessageMethod.invoke(mAlertDialogWpp, message);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return this;
    }

    public AlertDialogWpp setItems(CharSequence[] items, DialogInterface.OnClickListener listener){
        if (!isAvailable()) {
            mAlertDialog.setItems(items, listener);
            return this;
        }
        try {
            setItemsMethod.invoke(mAlertDialogWpp, listener, items);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return this;
    }

    public AlertDialogWpp setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (!isAvailable()) {
            mAlertDialog.setNegativeButton(text, listener);
            return this;
        }
        try {
            setNegativeButtonMethod.invoke(mAlertDialogWpp, listener, text);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return this;
    }

    public AlertDialogWpp setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (!isAvailable()) {
            mAlertDialog.setPositiveButton(text, listener);
            return this;
        }
        try {
            setPositiveButtonMethod.invoke(mAlertDialogWpp, listener, text);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return this;
    }

    public AlertDialogWpp setView(View view) {
        if (!isAvailable()) {
            mAlertDialog.setView(view);
            return this;
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setView", view);
        return this;
    }


    public Dialog create() {
        if (!isAvailable()) {
            return mAlertDialog.create();
        }
        return (Dialog) XposedHelpers.callMethod(mAlertDialogWpp, "create");
    }

    public void show() {
        if (!isAvailable()) {
            mAlertDialog.show();
            return;
        }
        create().show();
    }

}
