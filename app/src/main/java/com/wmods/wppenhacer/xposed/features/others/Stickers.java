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
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
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

        XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", View.OnClickListener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                View.OnClickListener originalListener = (View.OnClickListener) param.args[0];

                if (originalListener == null || originalListener.getClass().getName().contains("com.wmods.wppenhacer")) {
                    return;
                }

                // Identify sticker view by its specific ID efficiently
                int stickerId = Utils.getID("sticker", "id");
                if (view.getId() == stickerId || (view instanceof ViewGroup && view.findViewById(stickerId) != null)) {
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
                        dialog.setPositiveButton(context.getString(R.string.send), (d, w) -> originalListener.onClick(v));
                        dialog.setNegativeButton(context.getString(R.string.cancel), null);
                        dialog.show();
                    };
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
