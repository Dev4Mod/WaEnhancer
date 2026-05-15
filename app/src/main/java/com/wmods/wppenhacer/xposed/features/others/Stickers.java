package com.wmods.wppenhacer.xposed.features.others;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class Stickers extends Feature {
    public Stickers(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("alertsticker", true)) return;

        int stickerId = Utils.getID("sticker", "id");

        XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", View.OnClickListener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                var listener = (View.OnClickListener) param.args[0];

                if (listener == null || XposedHelpers.getAdditionalInstanceField(listener, "wae_wrapped") != null) {
                    return;
                }

                var activity = WppCore.getCurrentActivity();
                if (activity == null) return;
                String actName = activity.getClass().getName();
                if (!actName.contains("Conversation") && !actName.contains("Sticker")) {
                    return;
                }

                if (XposedHelpers.getAdditionalInstanceField(view, "wae_sticker_checked") != null) {
                    return;
                }

                boolean isStickerView = view.getId() == stickerId;
                if (!isStickerView && view instanceof ViewGroup) {
                    isStickerView = view.findViewById(stickerId) != null;
                }

                if (isStickerView) {
                    param.args[0] = (View.OnClickListener) v -> {
                        var context = v.getContext();
                        var dialog = new AlertDialogWpp(context);
                        dialog.setTitle(context.getString(R.string.send_sticker));

                        LinearLayout linearLayout = new LinearLayout(context);
                        linearLayout.setOrientation(LinearLayout.VERTICAL);
                        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                        var padding = Utils.dipToPixels(16);
                        linearLayout.setPadding(padding, padding, padding, padding);

                        var stickerView = (ImageView) (v.getId() == stickerId ? v : v.findViewById(stickerId));
                        if (stickerView != null) {
                            var image = new ImageView(context);
                            var size = Utils.dipToPixels(72);
                            var params = new LinearLayout.LayoutParams(size, size);
                            params.bottomMargin = padding;
                            image.setLayoutParams(params);
                            image.setImageDrawable(stickerView.getDrawable());
                            linearLayout.addView(image);
                        }

                        TextView text = new TextView(context);
                        text.setText(context.getString(R.string.do_you_want_to_send_sticker));
                        text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        linearLayout.addView(text);

                        dialog.setView(linearLayout);
                        dialog.setPositiveButton(context.getString(R.string.send), (d, w) -> listener.onClick(v));
                        dialog.setNegativeButton(context.getString(R.string.cancel), (d, w) -> d.dismiss());
                        dialog.show();
                    };
                    XposedHelpers.setAdditionalInstanceField(param.args[0], "wae_wrapped", true);
                } else {
                    XposedHelpers.setAdditionalInstanceField(view, "wae_sticker_checked", true);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Stickers";
    }
}
