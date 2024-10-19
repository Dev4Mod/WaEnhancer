package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import com.wmods.wppenhacer.adapter.MessageAdapter;
import com.wmods.wppenhacer.views.NoScrollListView;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShowEditMessage extends Feature {

    public ShowEditMessage(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("antieditmessages", false)) return;

        var onStartMethod = Unobfuscator.loadAntiRevokeOnStartMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onStartMethod));

        var onMessageEdit = Unobfuscator.loadMessageEditMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onMessageEdit));

        var getEditMessage = Unobfuscator.loadGetEditMessageMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getEditMessage));

        var editMessageShowMethod = Unobfuscator.loadEditMessageShowMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(editMessageShowMethod));

        var editMessageViewField = Unobfuscator.loadEditMessageViewField(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(editMessageViewField));

        XposedBridge.hookMethod(onMessageEdit, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // for 21.xx, getEditMessage is static
                var editMessage = Modifier.isStatic(getEditMessage.getModifiers())
                        ? getEditMessage.invoke(null, param.args[0])
                        : getEditMessage.invoke(param.args[0]);
                if (editMessage == null) return;
                long timestamp = XposedHelpers.getLongField(editMessage, "A00");
                var fMessage = new FMessageWpp(param.args[0]);
                long id = fMessage.getRowId();
                var origMessage = MessageStore.getInstance().getCurrentMessageByID(id);
                String newMessage = fMessage.getMessageStr();
                if (newMessage == null) {
                    var methods = ReflectionUtils.findAllMethodsUsingFilter(param.args[0].getClass(), method -> method.getReturnType() == String.class && ReflectionUtils.isOverridden(method));
                    for (var method : methods) {
                        newMessage = (String) method.invoke(param.args[0]);
                        if (newMessage != null) break;
                    }
                    if (newMessage == null) return;
                }
                try {
                    var message = MessageHistory.getInstance().getMessages(id);
                    if (message == null) {
                        MessageHistory.getInstance().insertMessage(id, origMessage, 0);
                    }
                    MessageHistory.getInstance().insertMessage(id, newMessage, timestamp);
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        });


        XposedBridge.hookMethod(editMessageShowMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var textView = (TextView) editMessageViewField.get(param.thisObject);
                if (textView != null && !textView.getText().toString().contains("\uD83D\uDCDD")) {
                    textView.getPaint().setUnderlineText(true);
                    textView.append("\uD83D\uDCDD");
                    textView.setOnClickListener((v) -> {
                        try {
                            var messageObj = XposedHelpers.callMethod(param.thisObject, "getFMessage");
                            var fMesage = new FMessageWpp(messageObj);
                            long id = fMesage.getRowId();
                            var messages = MessageHistory.getInstance().getMessages(id);
                            if (messages == null) {
                                messages = new ArrayList<>();
                            }
                            showBottomDialog(messages);
                        } catch (Exception exception0) {
                            logDebug(exception0);
                        }
                    });
                }
            }
        });

    }

    @SuppressLint("SetTextI18n")
    private void showBottomDialog(ArrayList<MessageHistory.MessageItem> messages) {
        Objects.requireNonNull(WppCore.getCurrentConversation()).runOnUiThread(() -> {
            var ctx = (Context) WppCore.getCurrentConversation();

            var dialog = WppCore.createBottomDialog(ctx);
            // NestedScrollView
            NestedScrollView nestedScrollView0 = new NestedScrollView(ctx, null);
            nestedScrollView0.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            nestedScrollView0.setFillViewport(true);
            nestedScrollView0.setFitsSystemWindows(true);
            // Main Layout
            LinearLayout linearLayout = new LinearLayout(ctx);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            linearLayout.setFitsSystemWindows(true);
            linearLayout.setMinimumHeight(layoutParams.height = Utils.getApplication().getResources().getDisplayMetrics().heightPixels / 4);
            linearLayout.setLayoutParams(layoutParams);
            int dip = Utils.dipToPixels(20);
            linearLayout.setPadding(dip, dip, dip, 0);
            var bg = DesignUtils.createDrawable("rc_dialog_bg", DesignUtils.getPrimarySurfaceColor());
            linearLayout.setBackground(bg);

            // Title View
            TextView titleView = new TextView(ctx);
            LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams1.weight = 1.0f;
            layoutParams1.setMargins(0, 0, 0, Utils.dipToPixels(10));
            titleView.setLayoutParams(layoutParams1);
            titleView.setTextSize(16.0f);
            titleView.setTextColor(DesignUtils.getPrimaryTextColor());
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setText(ResId.string.edited_history);

            // List View
            var adapter = new MessageAdapter(ctx, messages);
            ListView listView = new NoScrollListView(ctx);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            layoutParams2.weight = 1.0f;
            listView.setLayoutParams(layoutParams2);
            listView.setAdapter(adapter);
            ImageView imageView0 = new ImageView(ctx);
            LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(Utils.dipToPixels(70), Utils.dipToPixels(8));
            layoutParams4.gravity = 17;
            layoutParams4.setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5));
            var bg2 = DesignUtils.createDrawable("rc_dotline_dialog", Color.BLACK);
            imageView0.setBackground(DesignUtils.alphaDrawable(bg2, DesignUtils.getPrimaryTextColor(), 33));
            imageView0.setLayoutParams(layoutParams4);
            // Button View
            Button okButton = new Button(ctx);
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams3.setMargins(0, Utils.dipToPixels(10), 0, Utils.dipToPixels(10));
            layoutParams3.gravity = 80;
            okButton.setLayoutParams(layoutParams3);
            okButton.setGravity(17);
            var drawable = DesignUtils.createDrawable("selector_bg", Color.BLACK);
            okButton.setBackground(DesignUtils.alphaDrawable(drawable, DesignUtils.getPrimaryTextColor(), 25));
            okButton.setText("OK");
            okButton.setOnClickListener((View view) -> dialog.dismissDialog());
            linearLayout.addView(imageView0);
            linearLayout.addView(titleView);
            linearLayout.addView(listView);
            linearLayout.addView(okButton);
            nestedScrollView0.addView(linearLayout);
            dialog.setContentView(nestedScrollView0);
            dialog.setCanceledOnTouchOutside(true);
            dialog.showDialog();
        });
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Show Edit Message";
    }

}
