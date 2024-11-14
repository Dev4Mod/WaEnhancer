package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.wmods.wppenhacer.preference.ThemePreference;
import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CombinedSelector;
import cz.vutbr.web.css.Declaration;
import cz.vutbr.web.css.RuleSet;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFloatValue;
import cz.vutbr.web.css.TermFunction;
import cz.vutbr.web.css.TermLength;
import cz.vutbr.web.css.TermNumeric;
import cz.vutbr.web.css.TermURI;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomView extends Feature {

    private DrawableCache cacheImages;
    private static File themeDir;
    private final HashMap<String, Drawable> chacheDrawables = new HashMap<>();
    private final HashMap<String, DocumentFile> chacheUris = new HashMap<>();

    public CustomView(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var filter_itens = prefs.getString("css_theme", "");
        var folder_theme = prefs.getString("folder_theme", "");
        var custom_css = prefs.getString("custom_css", "");

        if ((TextUtils.isEmpty(filter_itens) && TextUtils.isEmpty(folder_theme) && TextUtils.isEmpty(custom_css)) || !prefs.getBoolean("custom_filters", true))
            return;

        hookDrawableViews();

        themeDir = new File(ThemePreference.rootDirectory, folder_theme);
        filter_itens += "\n" + custom_css;
        cacheImages = new DrawableCache(Utils.getApplication(), 100 * 1024 * 1024);
        var sheet = CSSFactory.parseString(filter_itens, new URL("https://base.url/"));

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                View rootView = activity.getWindow().getDecorView().getRootView();
                rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> CompletableFuture.runAsync(() -> registerCssRules(activity, (ViewGroup) rootView, sheet)));
            }
        });

    }

    private void hookDrawableViews() {
        XposedHelpers.findAndHookMethod(View.class, "setBackground", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                var newDrawable = (Drawable) param.args[0];
                var hookedBackground = XposedHelpers.getAdditionalInstanceField(view, "mHookedBackground");
                if (ReflectionUtils.isCalledFromClass(CustomView.class)) {
                    if (hookedBackground == null || view.getBackground() != newDrawable) {
                        XposedHelpers.setAdditionalInstanceField(view, "mHookedBackground", newDrawable);
                        return;
                    }
                } else if (hookedBackground == null) return;
                param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (ImageView) param.thisObject;
                var newDrawable = (Drawable) param.args[0];
                var mHookedDrawable = XposedHelpers.getAdditionalInstanceField(view, "mHookedDrawable");
                if (ReflectionUtils.isCalledFromClass(CustomView.class)) {
                    if (mHookedDrawable == null || view.getDrawable() != newDrawable) {
                        XposedHelpers.setAdditionalInstanceField(view, "mHookedDrawable", newDrawable);
                        return;
                    }
                } else if (mHookedDrawable == null) return;
                param.setResult(null);
            }
        });

    }


    private void registerCssRules(Activity activity, ViewGroup currenView, StyleSheet sheet) {
        try {
            for (var selector : sheet) {
                var ruleSet = (RuleSet) selector;
                for (var selectorItem : ruleSet.getSelectors()) {
                    var item = selectorItem.get(0);
                    String className;
                    String name;
                    if ((className = item.getClassName()) != null) {
                        className = className.replaceAll("_", ".").trim();
                        var clazz = XposedHelpers.findClass(className, classLoader);
                        if (clazz == null || !clazz.isInstance(activity)) continue;
                        name = selectorItem.get(1).getIDName().trim();
                    } else {
                        name = selectorItem.get(0).getIDName().trim();
                    }
                    int id = 0;
                    if (name.contains("android_")) {
                        try {
                            id = android.R.id.class.getField(name.substring(8)).getInt(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
                        }
                    } else {
                        id = Utils.getID(name, "id");
                    }
                    if (id <= 0) continue;
                    var view = currenView.findViewById(id);
                    if (view == null || !view.isShown() || view.getVisibility() != View.VISIBLE || !view.isAttachedToWindow())
                        continue;
                    var ruleItem = new RuleItem(selectorItem, ruleSet);
                    setCssRule(view, ruleItem);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void setCssRule(View currentView, RuleItem ruleItem) {
        var resultViews = new ArrayList<View>();
        captureSelector(currentView, ruleItem.selector, 0, resultViews);
        if (ruleItem.rule.getSelectors().length == 0 || ruleItem.rule.isEmpty() || resultViews.isEmpty())
            return;
        for (var view : resultViews) {
            if (view == null || !view.isAttachedToWindow())
                continue;
            CompletableFuture.runAsync(() -> view.post(() -> {
                try {
                    setRuleInView(ruleItem, view);
                } catch (Throwable e) {
                    log(e);
                }
            }), Utils.getExecutor());
        }
    }

    private void setRuleInView(RuleItem ruleItem, View view) {
        for (var declaration : ruleItem.rule) {
            var property = declaration.getProperty();
            switch (property) {
                case "parent" -> {
                    var value = declaration.get(0).toString().trim();
                    var parent = view.getRootView();
                    if (!"root".equals(value)) {
                        parent = parent.findViewById(Utils.getID(value, "id"));
                    }
                    if (parent instanceof ViewGroup parentView) {
                        var oldParent = (View) view.getParent();
                        if (oldParent.getTag() != "relative") {
                            ((ViewGroup) view.getParent()).removeView(view);
                            var frameLayout = new FrameLayout(parentView.getContext());
                            frameLayout.setTag("relative");
                            var params = new FrameLayout.LayoutParams(view.getLayoutParams());
                            parentView.addView(frameLayout, 0, params);
                            frameLayout.addView(view);
                            view = frameLayout;
                        } else if (oldParent.getTag() == "relative") {
                            view = oldParent;
                        }
                    }
                }
                case "background-color" -> {
                    if (declaration.size() != 2) continue;
                    var color = (TermColor) declaration.get(0);
                    var colorNew = (TermColor) declaration.get(1);
                    var colorValue = IColors.toString(color.getValue().getRGB());
                    var colorNewValue = IColors.toString(colorNew.getValue().getRGB());
                    HashMap<String, String> colors = new HashMap<>();
                    colors.put(colorValue, colorNewValue);
                    replaceColors(view, colors);
                    if (view instanceof ImageView imageView) {
                        var drawable = imageView.getDrawable();
                        if (drawable == null) continue;
                        drawable.setTint(colorNew.getValue().getRGB());
                        view.postInvalidate();
                    }
                }
                case "display" -> {
                    var value = declaration.get(0).toString();
                    switch (value) {
                        case "none" -> view.setVisibility(View.GONE);
                        case "block" -> view.setVisibility(View.VISIBLE);
                        case "invisible" -> view.setVisibility(View.INVISIBLE);
                    }
                }
                case "font-size" -> {
                    if (!(view instanceof TextView textView)) continue;
                    var value = (TermLength) declaration.get(0);
                    textView.setTextSize(getRealValue(value, 0));
                }
                case "color" -> {
                    if (!(view instanceof TextView textView)) continue;
                    var value = (TermColor) declaration.get(0);
                    textView.setTextColor(value.getValue().getRGB());
                }
                case "alpha" -> {
                    var value = (TermFloatValue) declaration.get(0);
                    view.setAlpha(value.getValue());
                }
                case "background-image" -> {
                    if (!(declaration.get(0) instanceof TermURI uri)) continue;
                    var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
                    if (draw == null) continue;
                    if (XposedHelpers.getAdditionalInstanceField(view, "mHookedBackground") != null || XposedHelpers.getAdditionalInstanceField(view, "mHookedDrawable") != null)
                        continue;
                    setHookedDrawable(view, draw);
                }
                case "background-size" -> {
                    if (declaration.get(0) instanceof TermLength width) {
                        var height = (TermLength) declaration.get(1);
                        if (view instanceof ImageView imageView) {
                            if (width.isPercentage() || height.isPercentage()) {
                                if (width.getValue().intValue() == 100 || height.getValue().intValue() == 100) {
                                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    continue;
                                }
                            }
                            var drawable = imageView.getDrawable();
                            if (!(drawable instanceof BitmapDrawable)) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            var widthObj = XposedHelpers.getAdditionalInstanceField(view, "mWidth");
                            var heightObj = XposedHelpers.getAdditionalInstanceField(view, "mHeight");
                            if (widthObj != null && heightObj != null) {
                                if (getRealValue(width, imageView.getWidth()) == (int) widthObj && getRealValue(height, imageView.getHeight()) == (int) heightObj)
                                    continue;
                            }
                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width, imageView.getWidth()), getRealValue(height, imageView.getHeight()), false);
                            var resizeDrawable = new BitmapDrawable(view.getContext().getResources(), resizedBitmap);
                            XposedHelpers.setAdditionalInstanceField(view, "mHeight", getRealValue(height, imageView.getHeight()));
                            XposedHelpers.setAdditionalInstanceField(view, "mWidth", getRealValue(width, imageView.getWidth()));
                            setHookedDrawable(imageView, resizeDrawable);
                        } else {
                            var drawable = view.getBackground();
                            if (!(drawable instanceof BitmapDrawable)) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            var widthObj = XposedHelpers.getAdditionalInstanceField(view, "mWidth");
                            var heightObj = XposedHelpers.getAdditionalInstanceField(view, "mHeight");
                            if (widthObj != null && heightObj != null) {
                                if (getRealValue(width, view.getWidth()) == (int) widthObj && getRealValue(height, view.getHeight()) == (int) heightObj)
                                    continue;
                            }
                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width, 0), getRealValue(height, 0), false);
                            var resizeDrawable = new BitmapDrawable(view.getContext().getResources(), resizedBitmap);
                            XposedHelpers.setAdditionalInstanceField(view, "mHeight", getRealValue(height, 0));
                            XposedHelpers.setAdditionalInstanceField(view, "mWidth", getRealValue(width, 0));
                            view.setBackground(resizeDrawable);
                        }
                    } else {
                        var value = declaration.get(0).toString().trim();
                        if (value.equals("cover")) {
                            if (view instanceof ImageView imageView) {
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            } else {
                                var drawable = view.getBackground();
                                if (!(drawable instanceof BitmapDrawable)) continue;
                                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                var widthObj = XposedHelpers.getAdditionalInstanceField(view, "mWidth");
                                var heightObj = XposedHelpers.getAdditionalInstanceField(view, "mHeight");
                                if (widthObj != null && heightObj != null) {
                                    if ((int) widthObj == view.getWidth() && (int) heightObj == view.getHeight())
                                        continue;
                                }
                                var resizeDrawable = new BitmapDrawable(view.getContext().getResources(), Bitmap.createScaledBitmap(bitmap, view.getWidth(), view.getHeight(), true));
                                XposedHelpers.setAdditionalInstanceField(view, "mHeight", view.getHeight());
                                XposedHelpers.setAdditionalInstanceField(view, "mWidth", view.getWidth());
                                view.setBackground(resizeDrawable);
                            }
                        }
                    }
                }
                case "background" -> {
                    if (declaration.get(0) instanceof TermColor color) {
                        view.setBackgroundColor(color.getValue().getRGB());
                        continue;
                    }
                    if (declaration.get(0) instanceof TermURI uri) {
                        var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
                        if (draw == null) continue;
                        view.setBackground(draw);
                        continue;
                    }
                    var value = declaration.get(0).toString().trim();
                    if (value.equals("none")) {
                        view.setBackground(null);
                    } else {
                        setBackgroundModel(view, declaration.get(0));
                    }

                }
                case "foreground" -> {
                    if (declaration.get(0) instanceof TermColor color) {
                        view.setBackground(new ColorDrawable(color.getValue().getRGB()));
                        continue;
                    }
                    if (declaration.get(0) instanceof TermURI uri) {
                        var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
                        if (draw == null) continue;
                        view.setForeground(draw);
                        continue;
                    }
                    var value = declaration.get(0).toString().trim();
                    if (value.equals("none")) {
                        view.setForeground(null);
                    }
                }
                case "width" -> {
                    var value = (TermLength) declaration.get(0);
                    view.getLayoutParams().width = getRealValue(value, 0);
                    view.requestLayout();
                }
                case "height" -> {
                    var value = (TermLength) declaration.get(0);
                    view.getLayoutParams().height = getRealValue(value, 0);
                }
                case "left" -> {
                    var value = (TermLength) declaration.get(0);
                    var layoutParams = view.getLayoutParams();
                    if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                        rParams.addRule(RelativeLayout.ALIGN_LEFT, getRealValue(value, 0));
                    } else if (layoutParams instanceof ViewGroup.MarginLayoutParams fParams) {
                        fParams.leftMargin = getRealValue(value, 0);
                    }
                }
                case "right" -> {
                    var value = (TermLength) declaration.get(0);
                    var layoutParams = view.getLayoutParams();
                    if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                        rParams.addRule(RelativeLayout.ALIGN_RIGHT, getRealValue(value, 0));
                    } else if (layoutParams instanceof ViewGroup.MarginLayoutParams fParams) {
                        fParams.rightMargin = getRealValue(value, 0);
                    }
                }
                case "top" -> {
                    var value = (TermLength) declaration.get(0);
                    var layoutParams = view.getLayoutParams();
                    if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                        rParams.addRule(RelativeLayout.ALIGN_TOP, getRealValue(value, 0));
                    } else if (layoutParams instanceof ViewGroup.MarginLayoutParams fParams) {
                        fParams.topMargin = getRealValue(value, 0);
                    }
                }
                case "bottom" -> {
                    var value = (TermLength) declaration.get(0);
                    var layoutParams = view.getLayoutParams();
                    if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                        rParams.addRule(RelativeLayout.ALIGN_BOTTOM, getRealValue(value, 0));
                    } else if (layoutParams instanceof ViewGroup.MarginLayoutParams fParams) {
                        fParams.bottomMargin = getRealValue(value, 0);
                    }
                }
                case "color-filter" -> {
                    var mode = declaration.get(0).toString().trim();
                    if (mode.equals("none")) {
                        if (view instanceof ImageView imageView) {
                            imageView.clearColorFilter();
                        } else {
                            var drawable = view.getBackground();
                            if (drawable != null) {
                                drawable.clearColorFilter();
                            }
                        }
                    } else {
                        if (!(declaration.get(1) instanceof TermColor color)) continue;
                        try {
                            var pMode = PorterDuff.Mode.valueOf(mode);
                            if (view instanceof ImageView imageView) {
                                imageView.setColorFilter(color.getValue().getRGB(), pMode);
                            } else {
                                var drawable = view.getBackground();
                                if (drawable != null) {
                                    drawable.setColorFilter(color.getValue().getRGB(), pMode);
                                }
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "color-tint" -> {
                    if (declaration.get(0) instanceof TermColor color) {
                        if (view instanceof ImageView imageView) {
                            ColorStateList colorStateList = declaration.size() == 1 ? ColorStateList.valueOf(color.getValue().getRGB()) : getColorStateList(declaration);
                            imageView.setImageTintList(colorStateList);
                        } else {
                            var drawable = view.getBackground();
                            if (drawable != null) {
                                ColorStateList colorStateList = declaration.size() == 1 ? ColorStateList.valueOf(color.getValue().getRGB()) : getColorStateList(declaration);
                                drawable.setTintList(colorStateList);
                            }
                        }
                    } else {
                        var value = declaration.get(0).toString().trim();
                        if (value.equals("none")) {
                            if (view instanceof ImageView imageView) {
                                imageView.setImageTintList(null);
                            } else {
                                var drawable = view.getBackground();
                                if (drawable != null) {
                                    drawable.setTintList(null);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void setHookedDrawable(View view, Drawable draw) {
        if (view instanceof ImageView imageView) {
            imageView.setImageDrawable(draw);
        } else {
            view.setBackground(draw);
        }
    }

    private void setBackgroundModel(View view, Term<?> value) {
        if (value instanceof TermFunction.LinearGradient gradient) {
            try {
                var gradientDrawable = chacheDrawables.get(value.toString());
                if (gradientDrawable == null) {
                    gradientDrawable = GradientDrawableParser.parseGradient(gradient, view.getWidth(), view.getHeight());
                    chacheDrawables.put(value.toString(), gradientDrawable);
                }
                view.setBackground(gradientDrawable);
            } catch (Exception e) {
                log("Error parsing gradient: " + e.getMessage());
            }
        }
    }


    @NonNull
    private static ColorStateList getColorStateList(Declaration declaration) {
        var defaultFontColor = ((TermColor) declaration.get(0)).getValue().getRGB();
        var pressedFontColor = ((TermColor) declaration.get(1)).getValue().getRGB();
        var disabledFontColor = ((TermColor) declaration.get(2)).getValue().getRGB();

        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_focused, android.R.attr.state_pressed},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{} // this should be empty to make default color as we want
                },
                new int[]{
                        pressedFontColor,
                        defaultFontColor,
                        pressedFontColor,
                        disabledFontColor,
                        defaultFontColor
                }
        );
    }

    private int getRealValue(TermLength value, int size) {
        if (value.getUnit() == TermNumeric.Unit.px) {
            return Utils.dipToPixels(value.getValue().intValue());
        } else if (value.isPercentage()) {
            return size * value.getValue().intValue() / 100;
        }
        return value.getValue().intValue();
    }

    private void captureSelector(View currentView, CombinedSelector selector, int position, ArrayList<View> resultViews) {
        if (selector.size() == position) return;
        var selectorItem = selector.get(position);
        if (selectorItem.getClassName() != null) {
            captureSelector(currentView, selector, position + 1, resultViews);
        } else if (selectorItem.getIDName() != null) {
            var name = selectorItem.getIDName().trim();
            int id = 0;
            if (name.contains("android_")) {
                try {
                    id = android.R.id.class.getField(name.substring(8)).getInt(null);
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                }
            } else {
                id = Utils.getID(name, "id");
            }
            if (id <= 0) return;
            View view = currentView.getId() == id ? currentView : currentView.findViewById(id);
            if (view == null) return;
            if (selector.size() == position + 1) {
                resultViews.add(view);
            } else {
                captureSelector(view, selector, position + 1, resultViews);
            }
        } else {
            if (!(currentView instanceof ViewGroup viewGroup)) return;
            var name = Arrays.stream(selectorItem.toString().split(":")).map(String::trim).toArray(String[]::new);
            var itemCount = new int[]{0};
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                var itemView = viewGroup.getChildAt(i);
                if (ReflectionUtils.isClassSimpleNameString(itemView.getClass(), name[0])) {
                    if (name.length > 1)
                        if (checkAttribute(itemView, itemCount, name[1])) continue;
                    if (selector.size() == position + 1) {
                        resultViews.add(itemView);
                    } else {
                        captureSelector(itemView, selector, position + 1, resultViews);
                    }
                } else if (isWidgetString(name[0]) && itemView instanceof ViewGroup viewGroup1) {
                    for (int j = 0; j < viewGroup1.getChildCount(); j++) {
                        var childView = viewGroup1.getChildAt(j);
                        captureSelector(childView, selector, position, resultViews);
                    }
                }
            }
        }
    }

    private boolean checkAttribute(View itemView, int[] itemCount, String name) {
        if (name.startsWith("nth-child")) {
            var startIndex = name.indexOf("(") + 1;
            var endIndex = name.indexOf(")");
            var index = Integer.parseInt(name.substring(startIndex, endIndex)) - 1;
            return index != itemCount[0]++;
        } else if (name.startsWith("contains")) {
            var startIndex = name.indexOf("(") + 1;
            var endIndex = name.indexOf(")");
            var contains = name.substring(startIndex, endIndex);
            if (itemView instanceof TextView textView) {
                return !textView.getText().toString().contains(contains);
            } else {
                return !itemView.toString().contains(contains);
            }
        }
        return false;
    }

    private boolean isWidgetString(String view) {
        return XposedHelpers.findClassIfExists("android.widget." + view, null) != null;
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Custom View";
    }


    public class DrawableCache {
        private final LruCache<String, CachedDrawable> drawableCache;
        private final Context context;

        public DrawableCache(Context context, int maxSize) {
            this.context = context.getApplicationContext();
            drawableCache = new LruCache<>(maxSize);
        }

        private Drawable loadDrawableFromFile(String filePath, int reqWidth, int reqHeight) {
            try {
                File file = new File(filePath);
                Bitmap bitmap;
                if (!file.canRead()) {
                    var parcelFile = WppCore.getClientBridge().openFile(filePath, false);
                    bitmap = BitmapFactory.decodeStream(new FileInputStream(parcelFile.getFileDescriptor()));
                } else {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
                var newHeight = reqHeight < 10 ? bitmap.getHeight() : Math.min(bitmap.getHeight(), reqHeight);
                var newWidth = reqWidth < 10 ? bitmap.getWidth() : Math.min(bitmap.getWidth(), reqWidth);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                return new BitmapDrawable(context.getResources(), bitmap);
            } catch (Exception e) {
                XposedBridge.log(e);
                return null;
            }
        }


        @Nullable
        public Drawable getDrawable(String filePath, int width, int height) {
            File file = filePath.startsWith("/") ? new File(filePath) : new File(themeDir, filePath);

            if (!file.exists()) {
                return null;
            }
            String key = file.getAbsolutePath();
            long lastModified = file.lastModified();
            CachedDrawable cachedDrawable = drawableCache.get(key);
            if (cachedDrawable != null && cachedDrawable.lastModified == lastModified) {
                return cachedDrawable.drawable;
            }
            Drawable cachedDrawableFromFile = loadDrawableFromCache(key, lastModified);
            if (cachedDrawableFromFile != null) {
                cachedDrawable = new CachedDrawable(cachedDrawableFromFile, lastModified);
                drawableCache.put(key, cachedDrawable);
                return cachedDrawableFromFile;
            }
            Drawable drawable = loadDrawableFromFile(key, width, height);
            if (drawable == null) return null;
            saveDrawableToCache(key, (BitmapDrawable) drawable, lastModified);
            cachedDrawable = new CachedDrawable(drawable, lastModified);
            drawableCache.put(key, cachedDrawable);
            return drawable;
        }

        private void saveDrawableToCache(String key, BitmapDrawable drawable, long lastModified) {
            File cacheDir = context.getCacheDir();
            File cacheLocation = new File(cacheDir, "drawable_cache");
            if (!cacheLocation.exists()) {
                cacheLocation.mkdirs();
            }
            File cacheFile = new File(cacheLocation, getCacheFileName(key));
            File metadataFile = new File(cacheLocation, getCacheFileName(key) + ".meta");

            try (OutputStream out = new FileOutputStream(cacheFile);
                 ObjectOutputStream metaOut = new ObjectOutputStream(new FileOutputStream(metadataFile))) {
                drawable.getBitmap().compress(Bitmap.CompressFormat.PNG, 80, out);
                metaOut.writeLong(lastModified);
                Log.d("DrawableCache", "Saved drawable to cache: " + cacheFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e("DrawableCache", "Failed to save drawable to cache", e);
            }
        }

        private Drawable loadDrawableFromCache(String key, long originalLastModified) {
            File cacheDir = context.getCacheDir();
            File cacheLocation = new File(cacheDir, "drawable_cache");
            File cacheFile = new File(cacheLocation, getCacheFileName(key));
            File metadataFile = new File(cacheLocation, getCacheFileName(key) + ".meta");

            if (!cacheFile.exists() || !metadataFile.exists()) {
                log("Drawable not found in cache: " + cacheFile.getAbsolutePath());
                return null;
            }

            try (ObjectInputStream metaIn = new ObjectInputStream(new FileInputStream(metadataFile))) {
                long cachedLastModified = metaIn.readLong();
                if (cachedLastModified != originalLastModified) {
                    return null;
                }

                Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (bitmap != null) {
                    return new BitmapDrawable(context.getResources(), bitmap);
                }
            } catch (IOException e) {
                Log.e("DrawableCache", "Failed to load drawable from cache", e);
            }

            return null;
        }

        private String getCacheFileName(String input) {
            return String.valueOf(Objects.hash(input));
        }

        private static class CachedDrawable {
            Drawable drawable;
            long lastModified;

            CachedDrawable(Drawable drawable, long lastModified) {
                this.drawable = drawable;
                this.lastModified = lastModified;
            }
        }
    }

    // Create a unique file name based


    public static class RuleItem {
        public CombinedSelector selector;
        public RuleSet rule;

        public RuleItem(CombinedSelector selectorItem, RuleSet ruleSet) {
            this.selector = selectorItem;
            this.rule = ruleSet;
        }
    }

    public static class GradientDrawableParser {

        public static BitmapDrawable parseGradient(TermFunction.LinearGradient cssGradient, int width, int height) {

            int[] colors = new int[cssGradient.getColorStops().size()];
            float[] positions = new float[cssGradient.getColorStops().size()];
            float angle = cssGradient.getAngle().getValue();

            for (int i = cssGradient.getColorStops().size() - 1; i >= 0; i--) {
                colors[i] = cssGradient.getColorStops().get(i).getColor().getValue().getRGB();
                positions[i] = cssGradient.getColorStops().get(i).getLength().getValue() / 100f;
            }

            // Create LinearGradient with the extracted parameters
            LinearGradient linearGradient = createLinearGradient(angle, colors, positions, width, height);
            // Create ShapeDrawable and set the shader
            ShapeDrawable shapeDrawable = new ShapeDrawable(new RectShape());
            shapeDrawable.setIntrinsicWidth(width);
            shapeDrawable.setIntrinsicHeight(height);
            shapeDrawable.getPaint().setShader(linearGradient);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            shapeDrawable.setBounds(0, 0, width, height);
            shapeDrawable.draw(new Canvas(bitmap));
            return new BitmapDrawable(Utils.getApplication().getResources(), bitmap);
        }

        private static LinearGradient createLinearGradient(float angle, int[] colors, float[] positions, int width, int height) {
            float x0, y0, x1, y1;
            double radians = Math.toRadians(angle);

            // Calculate the start and end points based on the angle and dimensions
            x0 = (float) (0.5 * width + 0.5 * width * Math.cos(radians - Math.PI / 2));
            y0 = (float) (0.5 * height + 0.5 * height * Math.sin(radians - Math.PI / 2));
            x1 = (float) (0.5 * width + 0.5 * width * Math.cos(radians + Math.PI / 2));
            y1 = (float) (0.5 * height + 0.5 * height * Math.sin(radians + Math.PI / 2));

            return new LinearGradient(
                    x0, y0, x1, y1,
                    colors, positions,
                    Shader.TileMode.CLAMP);
        }


    }

}
