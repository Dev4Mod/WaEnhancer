package com.wmods.wppenhacer.xposed.features.others;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Stickers extends Feature {
    public Stickers(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("alertsticker", false)) return;
        var sendStickerMethod = Unobfuscator.loadSendStickerMethod(classLoader);
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
                        var view = (View) param.thisObject;
                        if (view.findViewById(Utils.getID("sticker", "id")) == null) return;

                        param.args[0] = (View.OnClickListener) v -> {
                            var context = view.getContext();
                            var dialog = new AlertDialogWpp(view.getContext());
                            dialog.setTitle(context.getString(ResId.string.send_sticker));

                            var stickerView = (ImageView) view.findViewById(Utils.getID("sticker", "id"));
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

    @NonNull
    @Override
    public String getPluginName() {
        return "Stickers";
    }
}
