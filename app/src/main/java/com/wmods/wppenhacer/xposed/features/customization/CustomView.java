package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CombinedSelector;
import cz.vutbr.web.css.Declaration;
import cz.vutbr.web.css.RuleSet;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFloatValue;
import cz.vutbr.web.css.TermLength;
import cz.vutbr.web.css.TermNumeric;
import cz.vutbr.web.css.TermURI;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomView extends Feature {

    private DrawableCache cacheImages;
    private ExecutorService mThreadService;

    public CustomView(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var filter_itens = prefs.getString("css_theme", null);
        if (TextUtils.isEmpty(filter_itens)) return;

        cacheImages = new DrawableCache();
        mThreadService = Executors.newFixedThreadPool(8);

        var sheet = CSSFactory.parseString(filter_itens, new URL("https://base.url/"));
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                var content = (ViewGroup) activity.findViewById(android.R.id.content);
                content.getViewTreeObserver().addOnGlobalLayoutListener(() -> mThreadService.execute(() -> registerCssRules(activity, content, sheet)));
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
                        var clazz = XposedHelpers.findClass(className, loader);
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
            mThreadService.submit(() -> {
                view.post(() -> setRuleInView(ruleItem, view));
            });
        }
    }

    private void setRuleInView(RuleItem ruleItem, View view) {
        for (var declaration : ruleItem.rule) {
            var property = declaration.getProperty();
            switch (property) {
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
                    var uri = (TermURI) declaration.get(0);
                    if (!new File(uri.getValue()).exists()) continue;
                    var draw = cacheImages.getDrawable(uri.getValue());
                    if (view instanceof ImageView imageView) {
                        if (draw == imageView.getDrawable()) continue;
                        imageView.setImageDrawable(draw);
                    } else {
                        if (draw == view.getBackground()) continue;
                        view.setBackground(draw);
                    }
                }
                case "background-size" -> {
                    if (declaration.get(0) instanceof TermLength width) {
                        var height = (TermLength) declaration.get(1);
                        if (view instanceof ImageView imageView) {
                            if (width.isPercentage() || height.isPercentage()) {
                                if (width.getValue() == 100 || height.getValue() == 100) {
                                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    continue;
                                }
                            }
                            var drawable = imageView.getDrawable();
                            if (drawable == null) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            if (getRealValue(width, imageView.getWidth()) == bitmap.getWidth() && getRealValue(height, imageView.getHeight()) == bitmap.getHeight())
                                continue;
                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width, imageView.getWidth()), getRealValue(height, imageView.getHeight()), false);
                            drawable = new BitmapDrawable(view.getContext().getResources(), resizedBitmap);
                            imageView.setImageDrawable(drawable);
                        } else {
                            var drawable = view.getBackground();
                            if (drawable == null) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            if (getRealValue(width, 0) == bitmap.getWidth() && getRealValue(height, 0) == bitmap.getHeight())
                                continue;
                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width, 0), getRealValue(height, 0), false);
                            drawable = new BitmapDrawable(view.getContext().getResources(), resizedBitmap);
                            view.setBackground(drawable);
                        }
                    } else {
                        var value = declaration.get(0).toString().trim();
                        if (value.equals("cover")) {
                            if (view instanceof ImageView imageView) {
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            }
                        }
                    }
                }
                case "background" -> {

                    if (declaration.get(0) instanceof TermColor color) {
                        view.setBackground(new ColorDrawable(color.getValue().getRGB()));
                        continue;
                    }

                    if (declaration.get(0) instanceof TermURI uri) {
                        if (!new File(uri.getValue()).exists()) continue;
                        var draw = cacheImages.getDrawable(uri.getValue());
                        view.setBackground(draw);
                        continue;
                    }

                    var value = declaration.get(0).toString().trim();
                    if (value.equals("none")) {
                        view.setBackground(null);
                    }
                }
                case "foreground" -> {
                    if (declaration.get(0) instanceof TermColor color) {
                        view.setBackground(new ColorDrawable(color.getValue().getRGB()));
                        continue;
                    }

                    if (declaration.get(0) instanceof TermURI uri) {
                        if (!new File(uri.getValue()).exists()) continue;
                        var draw = cacheImages.getDrawable(uri.getValue());
                        view.setBackground(draw);
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
                    view.getLayoutParams().height = getRealValue(value, 0)
                    ;
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
                    if (!(view instanceof ImageView imageView)) continue;
                    var mode = declaration.get(0).toString().trim();
                    if (mode.equals("none")) {
                        imageView.clearColorFilter();
                        continue;
                    }
                    var color = (TermColor) declaration.get(1);
                    try {
                        var pMode = PorterDuff.Mode.valueOf(mode);
                        imageView.setColorFilter(color.getValue().getRGB(), pMode);
                    } catch (Exception ignored) {
                    }
                }
                case "color-tint" -> {
                    if (!(view instanceof ImageView imageView)) continue;
                    if (declaration.get(0) instanceof TermColor color) {
                        if (declaration.size() == 1) {
                            imageView.setImageTintList(ColorStateList.valueOf(color.getValue().getRGB()));
                        } else if (declaration.size() == 3) {
                            ColorStateList themeColorStateList = getColorStateList(declaration);
                            imageView.setImageTintList(themeColorStateList);
                        }
                    } else {
                        var value = declaration.get(0).toString().trim();
                        switch (value) {
                            case "none" -> imageView.setImageTintList(null);
                            case "transparent" ->
                                    imageView.setImageTintList(ColorStateList.valueOf(0));
                        }
                    }
                }
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
//            log("Search Element:" + name[0] + " in " + viewGroup);
            var itemCount = 0;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                var itemView = viewGroup.getChildAt(i);
//                log("Item: " + itemView);
                if (ReflectionUtils.isClassSimpleNameString(itemView.getClass(), name[0])) {
//                    log("Found: " + itemView);
                    if (name.length > 1 && name[1].startsWith("nth-child")) {
                        var startIndex = name[1].indexOf("(") + 1;
                        var endIndex = name[1].indexOf(")");
                        var index = Integer.parseInt(name[1].substring(startIndex, endIndex)) - 1;
                        if (index != itemCount++) continue;
                    }
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

    private boolean isWidgetString(String view) {
        return XposedHelpers.findClassIfExists("android.widget." + view, null) != null;
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Custom View";
    }

    public static class DrawableCache {
        private final LoadingCache<String, Drawable> drawableCache;

        public DrawableCache() {
            drawableCache = CacheBuilder.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(30, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                        @NonNull
                        @Override
                        public Drawable load(@NonNull String key) throws Exception {
                            return Objects.requireNonNull(loadDrawableFromFile(key));
                        }
                    });
        }

        private Drawable loadDrawableFromFile(String filePath) {
            File file = new File(filePath);
            if (!file.exists()) return new ColorDrawable(0);
            return Drawable.createFromPath(file.getAbsolutePath());
        }

        public Drawable getDrawable(String key) {
            return drawableCache.getUnchecked(key);
        }

//        public void invalidateCache() {
//            drawableCache.invalidateAll();
//        }
    }

    public static class RuleItem {
        public CombinedSelector selector;
        public RuleSet rule;

        public RuleItem(CombinedSelector selectorItem, RuleSet ruleSet) {
            this.selector = selectorItem;
            this.rule = ruleSet;
        }
    }

}
