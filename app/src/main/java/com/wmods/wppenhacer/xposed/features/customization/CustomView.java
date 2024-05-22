package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import java.util.concurrent.TimeUnit;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CombinedSelector;
import cz.vutbr.web.css.RuleBlock;
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

    public CustomView(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        cacheImages = new DrawableCache();
        var filter_itens = prefs.getString("css_theme", null);

        if (TextUtils.isEmpty(filter_itens)) return;

        var cssFactory = CSSFactory.parseString(filter_itens, new URL("https://base.url/"));
        XposedHelpers.findAndHookMethod(FrameLayout.class, "onMeasure", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (ViewGroup) param.thisObject;
                if (view.getId() == android.R.id.content && (view.getTag() == null || view.getTag() != "attachCSS")) {
                    view.getViewTreeObserver().addOnGlobalLayoutListener(() -> setCssRule(view, cssFactory));
                    setCssRule(view, cssFactory);
                    view.setTag("attachCSS");
                }
            }
        });

    }

    private void setCssRule(Object object, StyleSheet sheet) {
        for (RuleBlock<?> rule : sheet) {
            var currentView = (View) object;
            var ruleSet = (RuleSet) rule;
            for (var selector : ruleSet.getSelectors()) {
                var resultViews = new ArrayList<View>();
                captureSelector(currentView, selector, 0, resultViews);
                if (ruleSet.getSelectors().length == 0 || ruleSet.isEmpty() || resultViews.isEmpty())
                    continue;
                for (var view : resultViews) {
                    for (var declaration : ruleSet) {
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
                                if (!(view instanceof TextView textView)) return;
                                var value = (TermLength) declaration.get(0);
                                textView.setTextSize(getRealValue(value));
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
                                    imageView.setImageDrawable(draw);
                                } else {
                                    view.setBackground(draw);
                                }
                            }
                            case "background-size" -> {
                                if (declaration.get(0) instanceof TermLength width) {
                                    var height = (TermLength) declaration.get(1);
                                    if (view instanceof ImageView imageView) {
                                        var drawable = imageView.getDrawable();
                                        if (drawable == null) continue;
                                        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width), getRealValue(height), false);
                                        drawable = new BitmapDrawable(view.getContext().getResources(), resizedBitmap);
                                        imageView.setImageDrawable(drawable);
                                    } else {
                                        var drawable = view.getBackground();
                                        if (drawable == null) continue;
                                        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getRealValue(width), getRealValue(height), false);
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
                                if (declaration.get(0) instanceof TermColor uri){
                                    view.setBackground(new ColorDrawable(uri.getValue().getRGB()));
                                    continue;
                                }
                                var value = declaration.get(0).toString().trim();
                                switch (value) {
                                    case "none" -> view.setBackground(null);
                                    case "transparent" -> view.setBackgroundColor(0);
                                }
                            }
                            case "foreground" -> {
                                var value = declaration.get(0).toString().trim();
                                switch (value) {
                                    case "none" -> view.setForeground(null);
                                    case "transparent" -> view.setForeground(new ColorDrawable(0));
                                }
                            }
                            case "width" -> {
                                var value = (TermLength) declaration.get(0);
                                view.getLayoutParams().width = getRealValue(value);
                                view.requestLayout();
                            }
                            case "height" -> {
                                var value = (TermLength) declaration.get(0);
                                view.getLayoutParams().height = getRealValue(value)
                                ;
                            }
                            case "left" -> {
                                var value = (TermLength) declaration.get(0);
                                var layoutParams = view.getLayoutParams();
                                if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                                    rParams.addRule(RelativeLayout.ALIGN_LEFT, getRealValue(value));
                                } else if (layoutParams instanceof FrameLayout.LayoutParams fParams) {
                                    fParams.leftMargin = getRealValue(value);
                                }
                            }
                            case "right" -> {
                                var value = (TermLength) declaration.get(0);
                                var layoutParams = view.getLayoutParams();
                                if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                                    rParams.addRule(RelativeLayout.ALIGN_RIGHT, getRealValue(value));
                                } else if (layoutParams instanceof FrameLayout.LayoutParams fParams) {
                                    fParams.rightMargin = getRealValue(value);
                                }
                            }
                            case "top" -> {
                                var value = (TermLength) declaration.get(0);
                                var layoutParams = view.getLayoutParams();
                                if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                                    rParams.addRule(RelativeLayout.ALIGN_TOP, getRealValue(value));
                                } else if (layoutParams instanceof FrameLayout.LayoutParams fParams) {
                                    fParams.topMargin = getRealValue(value);
                                }
                            }
                            case "bottom" -> {
                                var value = (TermLength) declaration.get(0);
                                var layoutParams = view.getLayoutParams();
                                if (layoutParams instanceof RelativeLayout.LayoutParams rParams) {
                                    rParams.addRule(RelativeLayout.ALIGN_BOTTOM, getRealValue(value));
                                } else if (layoutParams instanceof FrameLayout.LayoutParams fParams) {
                                    fParams.bottomMargin = getRealValue(value);
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private int getRealValue(TermLength value) {
        if (value.getUnit() == TermNumeric.Unit.px) {
            return Utils.dipToPixels(value.getValue().intValue());
        }
        return value.getValue().intValue();
    }

    private void captureSelector(View currentView, CombinedSelector selector, int position, ArrayList<View> resultViews) {
        if (selector.size() == position) return;
        var selectorItem = selector.get(position);
        if (selectorItem.getIDName() != null) {
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
            var view = currentView.findViewById(id);
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

    public class DrawableCache {
        private LoadingCache<String, Drawable> drawableCache;

        public DrawableCache() {
            drawableCache = CacheBuilder.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(30, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                        @Override
                        public Drawable load(String key) throws Exception {
                            return loadDrawableFromFile(key);
                        }
                    });
        }

        private Drawable loadDrawableFromFile(String filePath) {
            File file = new File(filePath);
            if (!file.exists()) return null;
            return Drawable.createFromPath(file.getAbsolutePath());
        }

        public Drawable getDrawable(String key) {
            return drawableCache.getUnchecked(key);
        }

        public void invalidateCache() {
            drawableCache.invalidateAll();
        }
    }

}
