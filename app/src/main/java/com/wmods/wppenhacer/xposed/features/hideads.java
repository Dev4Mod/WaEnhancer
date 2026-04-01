package com.wmods.wppenhacer.xposed.features.customization;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import android.os.SystemClock;

import com.wmods.wppenhacer.xposed.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideAds extends Feature {

    public HideAds(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_ads", false)) return;

        XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                CharSequence text = (CharSequence) param.args[0];
                if (text != null) {
                    String s = text.toString().trim();
                    if (s.equalsIgnoreCase("Bersponsor") || s.equalsIgnoreCase("Sponsored") || s.equalsIgnoreCase("Promosi")) {
                        TextView tv = (TextView) param.thisObject;
                        tv.setTag("META_AD_DETECTED");
                        executeMultiScheme(tv);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if ("META_AD_DETECTED".equals(view.getTag())) {
                    param.args[0] = View.GONE;
                }
            }
        });

        XposedHelpers.findAndHookMethod(View.class, "onMeasure", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if ("META_AD_DETECTED".equals(view.getTag())) {
                    param.args[0] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                    param.args[1] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                    param.setResult(null);
                }
            }
        });
    }

    private void executeMultiScheme(View view) {
        try {
            View current = view;
            boolean isFullScreen = false;

            for (int i = 0; i < 8; i++) {
                if (current.getParent() instanceof View) {
                    current = (View) current.getParent();
                    current.setTag("META_AD_DETECTED");
                    
                    if (current.getHeight() > 1000) {
                        isFullScreen = true;
                    }

                    current.setVisibility(View.GONE);
                    ViewGroup.LayoutParams params = current.getLayoutParams();
                    if (params != null) {
                        params.height = 0;
                        params.width = 0;
                        if (params instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, 0, 0);
                        }
                        current.setLayoutParams(params);
                    }
                } else {
                    break;
                }
            }

            if (isFullScreen && current != null) {
                long downTime = SystemClock.uptimeMillis();
                long eventTime = SystemClock.uptimeMillis() + 100;
                float x = current.getWidth() - 10.0f; 
                float y = current.getHeight() / 2.0f;

                MotionEvent motionEventDown = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent motionEventUp = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);

                current.dispatchTouchEvent(motionEventDown);
                current.dispatchTouchEvent(motionEventUp);
                
                motionEventDown.recycle();
                motionEventUp.recycle();
            }

        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Ads";
    }
}
