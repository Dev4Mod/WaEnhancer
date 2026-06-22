package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.RuleSet;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFloatValue;
import cz.vutbr.web.css.TermFunction;
import cz.vutbr.web.css.TermLength;
import cz.vutbr.web.css.TermURI;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomView extends Feature {

    private DrawableCache cacheImages;
    private static File themeDir;
    private final HashMap<String, Drawable> chacheDrawables = new HashMap<>();
    private Properties properties;
    private final WeakHashMap<View, Boolean> processedViews = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> forcedVisibilityMap = new WeakHashMap<>();
    private final WeakHashMap<View, Drawable> forcedBackgroundMap = new WeakHashMap<>();
    private final WeakHashMap<View, Drawable> forcedDrawableMap = new WeakHashMap<>();
    private HashMap<Integer, ArrayList<CachedRuleItem>> mapIds;
    private HashMap<Integer, ArrayList<CachedRuleItem>> leafMapIds;
    private final HashMap<String, Class<?>> resolvedClasses = new HashMap<>();
    private final HashMap<String, Boolean> widgetClassCache = new HashMap<>();

    public CustomView(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    private static void changeDPI(Activity activity, XSharedPreferences prefs, Properties properties) {
        String dpiStr = null;
        if (!Objects.equals(prefs.getString("change_dpi", "0"), "0")) {
            dpiStr = prefs.getString("change_dpi", "0");
        } else if (properties.getProperty("change_dpi") != null) {
            dpiStr = properties.getProperty("change_dpi");
        }
        if (dpiStr == null) return;
        int dpi = 0;
        try {
            dpi = Integer.parseInt(dpiStr);
        } catch (NumberFormatException e) {
            XposedBridge.log("Error parsing dpi: " + e.getMessage());
        }
        if (dpi != 0) {
            var res = activity.getResources();
            DisplayMetrics runningMetrics = res.getDisplayMetrics();
            DisplayMetrics newMetrics;
            if (runningMetrics != null) {
                newMetrics = new DisplayMetrics();
                newMetrics.setTo(runningMetrics);
            } else {
                newMetrics = res.getDisplayMetrics();
            }
            newMetrics.density = dpi / 160f;
            newMetrics.densityDpi = dpi;
            res.getDisplayMetrics().setTo(newMetrics);
        }
    }

    @Override
    public void doHook() throws Throwable {
        if (prefs.getBoolean("lite_mode", false)) return;

        var filter_itens = prefs.getString("css_theme", "");
        var folder_theme = prefs.getString("folder_theme", "");
        var custom_css = prefs.getString("custom_css", "");

        if ((TextUtils.isEmpty(filter_itens) && TextUtils.isEmpty(folder_theme) && TextUtils.isEmpty(custom_css))
                || !prefs.getBoolean("custom_filters", true))
            return;

        properties = Utils.getProperties(prefs, "custom_css", "custom_filters");

        WppCore.addListenerActivity((activity1, type) -> {
            if (type != WppCore.ActivityChangeState.ChangeType.CREATED) return;
            changeDPI(activity1, prefs, properties);
        });

        hookDrawableViews();

        themeDir = new File(ThemePreference.rootDirectory, folder_theme);
        String cssContent = filter_itens + "\n" + custom_css;
        cacheImages = new DrawableCache(Utils.getApplication(), 100 * 1024 * 1024);

        String waVersion = "";
        try {
            waVersion = Utils.getApplication().getPackageManager()
                    .getPackageInfo(Utils.getApplication().getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        String hash = hashContent(cssContent + waVersion);
        File cacheDir = Utils.getApplication().getCacheDir();
        File cacheFile = new File(cacheDir, "cv_rules_" + hash + ".cache");

        boolean loaded = false;
        if (cacheFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
                //noinspection unchecked
                mapIds = (HashMap<Integer, ArrayList<CachedRuleItem>>) ois.readObject();
                //noinspection unchecked
                leafMapIds = (HashMap<Integer, ArrayList<CachedRuleItem>>) ois.readObject();
                loaded = true;
                log("CustomView: loaded compiled rules from disk cache");
            } catch (Exception e) {
                log("CustomView: cache load failed – " + e.getMessage());
                mapIds = null;
                leafMapIds = null;
            }
        }

        if (!loaded) {
            var sheet = CSSFactory.parseString(cssContent, new URL("https://base.url/"));
            mapIds = new HashMap<>();
            leafMapIds = new HashMap<>();
            buildRuleMaps(sheet);

            // Delete stale cache files
            File[] old = cacheDir.listFiles((d, n) -> n.startsWith("cv_rules_") && n.endsWith(".cache"));
            if (old != null) {
                for (File f : old) {
                    if (!f.equals(cacheFile)) f.delete();
                }
            }
            // Persist asynchronously
            final HashMap<Integer, ArrayList<CachedRuleItem>> mCopy = mapIds;
            final HashMap<Integer, ArrayList<CachedRuleItem>> lCopy = leafMapIds;
            new Thread(() -> {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                    oos.writeObject(mCopy);
                    oos.writeObject(lCopy);
                } catch (Exception e) {
                    log("CustomView: cache save failed – " + e.getMessage());
                }
            }, "cv-cache-writer").start();
        }

        registerHooks();
    }


    private String hashContent(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] h = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }


    private void buildRuleMaps(StyleSheet sheet) {
        for (var selector : sheet) {
            var ruleSet = (RuleSet) selector;
            for (var selectorItem : ruleSet.getSelectors()) {
                String targetClassName = null;
                ArrayList<SelectorPart> parts = new ArrayList<>();

                for (int i = 0; i < selectorItem.size(); i++) {
                    var item = selectorItem.get(i);
                    SelectorPart part = new SelectorPart();
                    if (item.getClassName() != null) {
                        part.className = item.getClassName().replaceAll("_", ".").trim();
                        if (i == 0) targetClassName = part.className;
                    } else if (item.getIDName() != null) {
                        part.idName = item.getIDName().trim();
                        if (part.idName.contains("android_")) {
                            try {
                                part.resolvedId = android.R.id.class.getField(part.idName.substring(8)).getInt(null);
                            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                            }
                        } else {
                            part.resolvedId = Utils.getID(part.idName, "id");
                        }
                    } else {
                        String typeStr = item.toString();
                        String[] split = typeStr.split(":", 2);
                        part.typeSelector = split[0].trim();
                        if (split.length > 1) part.pseudoClass = split[1].trim();
                    }
                    parts.add(part);
                }

                String name;
                if (parts.get(0).className != null && parts.size() > 1) {
                    name = parts.get(1).idName;
                } else {
                    name = parts.get(0).idName;
                }
                if (name == null) continue;

                int id = 0;
                for (SelectorPart p : parts) {
                    if (name.equals(p.idName)) {
                        id = p.resolvedId;
                        break;
                    }
                }
                if (id <= 0) continue;

                // Serialize declarations
                ArrayList<SerialDeclaration> decls = new ArrayList<>();
                for (var declaration : ruleSet) {
                    SerialDeclaration sd = new SerialDeclaration();
                    sd.property = declaration.getProperty();
                    sd.terms = new ArrayList<>();
                    for (int i = 0; i < declaration.size(); i++) {
                        sd.terms.add(toSerialTerm(declaration.get(i)));
                    }
                    decls.add(sd);
                }

                var ruleItem = new CachedRuleItem(parts, decls, targetClassName);
                var list = mapIds.getOrDefault(id, new ArrayList<>());
                list.add(ruleItem);
                mapIds.put(id, list);

                String leafName = null;
                for (int i = selectorItem.size() - 1; i >= 0; i--) {
                    var leafItem = selectorItem.get(i);
                    if (leafItem.getIDName() != null) {
                        leafName = leafItem.getIDName().trim();
                        break;
                    }
                }
                if (leafName != null && !leafName.equals(name)) {
                    int leafId = 0;
                    for (SelectorPart p : parts) {
                        if (leafName.equals(p.idName)) {
                            leafId = p.resolvedId;
                            break;
                        }
                    }
                    if (leafId > 0) {
                        var leafList = leafMapIds.getOrDefault(leafId, new ArrayList<>());
                        leafList.add(ruleItem);
                        leafMapIds.put(leafId, leafList);
                    }
                }
            }
        }
    }


    private static SerialTerm toSerialTerm(Term<?> term) {
        SerialTerm st = new SerialTerm();
        if (term instanceof TermFunction.LinearGradient lg) {
            st.type = SerialTerm.LINEAR_GRADIENT;
            st.strValue = lg.toString();
            st.gradientAngle = lg.getAngle().getValue();
            var stops = lg.getColorStops();
            st.gradientColors = new int[stops.size()];
            st.gradientPositions = new float[stops.size()];
            for (int i = 0; i < stops.size(); i++) {
                st.gradientColors[i] = stops.get(i).getColor().getValue().getRGB();
                st.gradientPositions[i] = stops.get(i).getLength().getValue() / 100f;
            }
        } else if (term instanceof TermFunction tf) {
            st.type = SerialTerm.FUNCTION;
            st.strValue = tf.getFunctionName();
            var values = tf.getValues(true);
            st.args = new ArrayList<>();
            for (var v : values) st.args.add(toSerialTerm(v));
        } else if (term instanceof TermColor tc) {
            st.type = SerialTerm.COLOR;
            st.colorRgb = tc.getValue().getRGB();
        } else if (term instanceof TermLength tl) {
            st.type = SerialTerm.LENGTH;
            st.numValue = tl.getValue();
            st.percentage = tl.isPercentage();
            st.unitName = tl.getUnit() != null ? tl.getUnit().toString() : null;
        } else if (term instanceof TermFloatValue tf) {
            st.type = SerialTerm.FLOAT_VAL;
            st.numValue = tf.getValue();
        } else if (term instanceof TermURI tu) {
            st.type = SerialTerm.URI;
            st.strValue = tu.getValue();
        } else {
            st.type = SerialTerm.STRING;
            st.strValue = term.toString();
        }
        return st;
    }


    private void registerHooks() {
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                applyRulesForView((View) param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(View.class, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                processedViews.remove((View) param.thisObject);
            }
        });

        final int VISIBILITY_MASK = 0x0000000C;
        XposedHelpers.findAndHookMethod(View.class, "setFlags", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (((int) param.args[1] & VISIBILITY_MASK) == 0) return;
                var view = (View) param.thisObject;
                var forced = forcedVisibilityMap.get(view);
                if (forced == null) return;
                param.args[0] = ((int) param.args[0] & ~VISIBILITY_MASK) | forced;
            }
        });
    }


    private void applyRulesForView(View view) {
        int id = view.getId();
        applyRules(view, mapIds.get(id));

        var leafList = leafMapIds.get(id);
        if (leafList != null) {
            for (var item : leafList) {
                try {
                    Class<?> activityClass = resolveClass(item.targetActivityClassName);
                    if (activityClass != null && !activityClass.isInstance(WppCore.getCurrentActivity()))
                        continue;
                    var rootView = findSelectorRoot(view, item.selector);
                    if (rootView == null) continue;
                    var resultViews = new ArrayList<View>();
                    captureSelector(rootView, item.selector, 0, resultViews);
                    for (var targetView : resultViews) {
                        if (targetView == null) continue;
                        if (processedViews.containsKey(targetView)) continue;
                        setRuleInView(item, targetView);
                        processedViews.put(targetView, Boolean.TRUE);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void applyRules(View startView, ArrayList<CachedRuleItem> rules) {
        if (rules == null) return;
        for (var item : rules) {
            try {
                Class<?> activityClass = resolveClass(item.targetActivityClassName);
                if (activityClass != null && !activityClass.isInstance(WppCore.getCurrentActivity()))
                    continue;
                var resultViews = new ArrayList<View>();
                captureSelector(startView, item.selector, 0, resultViews);
                for (var targetView : resultViews) {
                    if (targetView == null) continue;
                    if (processedViews.containsKey(targetView)) continue;
                    setRuleInView(item, targetView);
                    processedViews.put(targetView, Boolean.TRUE);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Nullable
    private View findSelectorRoot(View leafView, ArrayList<SelectorPart> selector) {
        for (var part : selector) {
            if (part.idName == null) continue;
            int rootId = part.resolvedId;
            if (rootId <= 0) continue;
            var root = leafView.getRootView();
            if (root.getId() == rootId) return root;
            return root.findViewById(rootId);
        }
        return null;
    }

    private void hookDrawableViews() {
        XposedHelpers.findAndHookMethod(View.class, "setBackground", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                var newDrawable = (Drawable) param.args[0];
                var forced = forcedBackgroundMap.get(view);
                if (forced == null) return;
                if (newDrawable != forced) param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var view = (ImageView) param.thisObject;
                var newDrawable = (Drawable) param.args[0];
                var forced = forcedDrawableMap.get(view);
                if (forced == null) return;
                if (newDrawable != forced) param.setResult(null);
            }
        });
    }

    private void setRuleInView(CachedRuleItem ruleItem, View view) {
        for (var declaration : ruleItem.declarations) {
            var property = declaration.property;
            var terms = declaration.terms;
            switch (property) {
                case "parent" -> {
                    var value = terms.get(0).strValue.trim();
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
                    if (terms.size() != 2) continue;
                    int color = terms.get(0).colorRgb;
                    int colorNew = terms.get(1).colorRgb;
                    HashMap<String, String> colors = new HashMap<>();
                    colors.put(IColors.toString(color), IColors.toString(colorNew));
                    replaceColors(view, colors);
                    if (view instanceof ImageView imageView) {
                        var drawable = imageView.getDrawable();
                        if (drawable == null) continue;
                        drawable.setTint(colorNew);
                        view.postInvalidate();
                    }
                }
                case "display" -> {
                    var value = terms.get(0).strValue;
                    switch (value) {
                        case "none" -> {
                            forcedVisibilityMap.put(view, View.GONE);
                            view.setVisibility(View.GONE);
                        }
                        case "block" -> {
                            forcedVisibilityMap.put(view, View.VISIBLE);
                            view.setVisibility(View.VISIBLE);
                        }
                        case "invisible" -> {
                            forcedVisibilityMap.put(view, View.INVISIBLE);
                            view.setVisibility(View.INVISIBLE);
                        }
                    }
                }
                case "font-size" -> {
                    if (!(view instanceof TextView textView)) continue;
                    textView.setTextSize(getRealValue(terms.get(0), 0));
                }
                case "color" -> {
                    if (!(view instanceof TextView textView)) continue;
                    textView.setTextColor(terms.get(0).colorRgb);
                }
                case "alpha", "opacity" -> view.setAlpha(terms.get(0).numValue);
                case "background-image" -> {
                    if (terms.get(0).type != SerialTerm.URI) continue;
                    var draw = cacheImages.getDrawable(terms.get(0).strValue, view.getWidth(), view.getHeight());
                    if (draw == null) continue;
                    if (forcedBackgroundMap.containsKey(view) || forcedDrawableMap.containsKey(view))
                        continue;
                    setHookedDrawable(view, draw);
                }
                case "background-size" -> {
                    if (terms.get(0).type == SerialTerm.LENGTH) {
                        var widthTerm = terms.get(0);
                        var heightTerm = terms.get(1);
                        if (view instanceof ImageView imageView) {
                            if (widthTerm.percentage || heightTerm.percentage) {
                                if ((int) widthTerm.numValue == 100 || (int) heightTerm.numValue == 100) {
                                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    continue;
                                }
                            }
                            var drawable = imageView.getDrawable();
                            if (!(drawable instanceof BitmapDrawable)) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            Bitmap resized = Bitmap.createScaledBitmap(bitmap,
                                    getRealValue(widthTerm, imageView.getWidth()),
                                    getRealValue(heightTerm, imageView.getHeight()), false);
                            setHookedDrawable(imageView, new BitmapDrawable(view.getContext().getResources(), resized));
                        } else {
                            var drawable = view.getBackground();
                            if (!(drawable instanceof BitmapDrawable)) continue;
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            Bitmap resized = Bitmap.createScaledBitmap(bitmap,
                                    getRealValue(widthTerm, 0), getRealValue(heightTerm, 0), false);
                            setHookedDrawable(view, new BitmapDrawable(view.getContext().getResources(), resized));
                        }
                    } else {
                        var value = terms.get(0).strValue.trim();
                        if (value.equals("cover")) {
                            if (view instanceof ImageView imageView) {
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            } else {
                                if (view.getWidth() < 1 || view.getHeight() < 1) {
                                    view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                        @Override
                                        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                            int w = right - left, h = bottom - top;
                                            if (w < 1 || h < 1) return;
                                            v.removeOnLayoutChangeListener(this);
                                            var bg = v.getBackground();
                                            if (!(bg instanceof BitmapDrawable)) return;
                                            var bmp = ((BitmapDrawable) bg).getBitmap();
                                            setHookedDrawable(v, new BitmapDrawable(v.getContext().getResources(),
                                                    Bitmap.createScaledBitmap(bmp, w, h, true)));
                                        }
                                    });
                                    continue;
                                }
                                var drawable = view.getBackground();
                                if (!(drawable instanceof BitmapDrawable)) continue;
                                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                setHookedDrawable(view, new BitmapDrawable(view.getContext().getResources(),
                                        Bitmap.createScaledBitmap(bitmap, view.getWidth(), view.getHeight(), true)));
                            }
                        }
                    }
                }
                case "background" -> {
                    var t0 = terms.get(0);
                    if (t0.type == SerialTerm.COLOR) {
                        view.setBackgroundColor(t0.colorRgb);
                        continue;
                    }
                    if (t0.type == SerialTerm.URI) {
                        var draw = cacheImages.getDrawable(t0.strValue, view.getWidth(), view.getHeight());
                        if (draw == null) continue;
                        setHookedDrawable(view, draw);
                        continue;
                    }
                    if (t0.type == SerialTerm.STRING && "none".equals(t0.strValue)) {
                        forcedBackgroundMap.remove(view);
                        view.setBackground(null);
                    } else {
                        setBackgroundModel(view, t0);
                    }
                }
                case "foreground" -> {
                    var t0 = terms.get(0);
                    if (t0.type == SerialTerm.COLOR) {
                        view.setBackground(new ColorDrawable(t0.colorRgb));
                        continue;
                    }
                    if (t0.type == SerialTerm.URI) {
                        var draw = cacheImages.getDrawable(t0.strValue, view.getWidth(), view.getHeight());
                        if (draw == null) continue;
                        view.setForeground(draw);
                        continue;
                    }
                    if (t0.type == SerialTerm.STRING && "none".equals(t0.strValue)) {
                        view.setForeground(null);
                    }
                }
                case "width" -> {
                    view.getLayoutParams().width = getRealValue(terms.get(0), 0);
                    view.requestLayout();
                }
                case "height" -> {
                    view.getLayoutParams().height = getRealValue(terms.get(0), 0);
                }
                case "left" -> {
                    var lp = view.getLayoutParams();
                    if (lp instanceof RelativeLayout.LayoutParams rp)
                        rp.addRule(RelativeLayout.ALIGN_LEFT, getRealValue(terms.get(0), 0));
                    else if (lp instanceof ViewGroup.MarginLayoutParams mp)
                        mp.leftMargin = getRealValue(terms.get(0), 0);
                }
                case "right" -> {
                    var lp = view.getLayoutParams();
                    if (lp instanceof RelativeLayout.LayoutParams rp)
                        rp.addRule(RelativeLayout.ALIGN_RIGHT, getRealValue(terms.get(0), 0));
                    else if (lp instanceof ViewGroup.MarginLayoutParams mp)
                        mp.rightMargin = getRealValue(terms.get(0), 0);
                }
                case "top" -> {
                    var lp = view.getLayoutParams();
                    if (lp instanceof RelativeLayout.LayoutParams rp)
                        rp.addRule(RelativeLayout.ALIGN_TOP, getRealValue(terms.get(0), 0));
                    else if (lp instanceof ViewGroup.MarginLayoutParams mp)
                        mp.topMargin = getRealValue(terms.get(0), 0);
                }
                case "bottom" -> {
                    var lp = view.getLayoutParams();
                    if (lp instanceof RelativeLayout.LayoutParams rp)
                        rp.addRule(RelativeLayout.ALIGN_BOTTOM, getRealValue(terms.get(0), 0));
                    else if (lp instanceof ViewGroup.MarginLayoutParams mp)
                        mp.bottomMargin = getRealValue(terms.get(0), 0);
                }
                case "color-filter" -> {
                    var mode = terms.get(0).strValue.trim();
                    if (mode.equals("none")) {
                        if (view instanceof ImageView iv) iv.clearColorFilter();
                        else {
                            var d = view.getBackground();
                            if (d != null) d.clearColorFilter();
                        }
                    } else {
                        if (terms.size() < 2 || terms.get(1).type != SerialTerm.COLOR) continue;
                        try {
                            var pMode = PorterDuff.Mode.valueOf(mode);
                            if (view instanceof ImageView iv)
                                iv.setColorFilter(terms.get(1).colorRgb, pMode);
                            else {
                                var d = view.getBackground();
                                if (d != null) d.setColorFilter(terms.get(1).colorRgb, pMode);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "color-tint" -> {
                    if (terms.get(0).type == SerialTerm.COLOR) {
                        if (view instanceof ImageView iv) {
                            ColorStateList csl = terms.size() == 1
                                    ? ColorStateList.valueOf(terms.get(0).colorRgb)
                                    : getColorStateList(terms);
                            iv.setImageTintList(csl);
                        } else {
                            var drawable = view.getBackground();
                            if (drawable != null) {
                                ColorStateList csl = terms.size() == 1
                                        ? ColorStateList.valueOf(terms.get(0).colorRgb)
                                        : getColorStateList(terms);
                                drawable.setTintList(csl);
                            }
                        }
                    } else {
                        var value = terms.get(0).strValue.trim();
                        if (value.equals("none")) {
                            if (view instanceof ImageView iv) iv.setImageTintList(null);
                            else {
                                var d = view.getBackground();
                                if (d != null) d.setTintList(null);
                            }
                        }
                    }
                }
                case "font-weight" -> {
                    if (!(view instanceof TextView tv)) continue;
                    String value = terms.get(0).strValue;
                    Typeface cur = tv.getTypeface();
                    if ("bold".equals(value) || "700".equals(value) || "800".equals(value) || "900".equals(value))
                        tv.setTypeface(cur, Typeface.BOLD);
                    else if ("normal".equals(value) || "400".equals(value))
                        tv.setTypeface(Typeface.create(cur, Typeface.NORMAL));
                }
                case "font-style" -> {
                    if (!(view instanceof TextView tv)) continue;
                    String value = terms.get(0).strValue;
                    Typeface cur = tv.getTypeface();
                    if ("italic".equals(value)) tv.setTypeface(cur, Typeface.ITALIC);
                    else if ("normal".equals(value))
                        tv.setTypeface(Typeface.create(cur, Typeface.NORMAL));
                }
                case "text-decoration" -> {
                    if (!(view instanceof TextView tv)) continue;
                    String value = terms.get(0).strValue;
                    if (value.contains("underline"))
                        tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    if (value.contains("line-through"))
                        tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    if (value.contains("none"))
                        tv.setPaintFlags(tv.getPaintFlags() & (~Paint.UNDERLINE_TEXT_FLAG) & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }
                case "text-transform" -> {
                    if (!(view instanceof TextView tv)) continue;
                    switch (terms.get(0).strValue) {
                        case "uppercase" -> tv.setAllCaps(true);
                        case "lowercase" -> {
                            tv.setAllCaps(false);
                            tv.setText(tv.getText().toString().toLowerCase());
                        }
                        case "none" -> tv.setAllCaps(false);
                    }
                }
                case "text-align" -> {
                    if (!(view instanceof TextView tv)) continue;
                    switch (terms.get(0).strValue) {
                        case "center" -> tv.setGravity(Gravity.CENTER);
                        case "right", "end" -> tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                        case "left", "start" ->
                                tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    }
                }
                case "box-shadow" -> {
                    for (var term : terms) {
                        if (term.type == SerialTerm.LENGTH) {
                            float val = getExactValue(term, 0);
                            if (val > 0) view.setElevation(val);
                            break;
                        }
                    }
                }
                case "transform" -> {
                    for (var term : terms) {
                        if (term.type != SerialTerm.FUNCTION) continue;
                        var args = term.args;
                        if ("rotate".equals(term.strValue) && args != null && !args.isEmpty()) {
                            var arg = args.get(0);
                            if (arg.type == SerialTerm.LENGTH && "deg".equals(arg.unitName)) {
                                view.setRotation(arg.numValue);
                            }
                        } else if ("scale".equals(term.strValue) && args != null) {
                            if (args.size() >= 1) {
                                float sx = numericFromTerm(args.get(0));
                                view.setScaleX(sx);
                                view.setScaleY(sx);
                            }
                            if (args.size() >= 2) {
                                view.setScaleY(numericFromTerm(args.get(1)));
                            }
                        }
                    }
                }
                case "margin" -> {
                    if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params))
                        continue;
                    int l = params.leftMargin, t = params.topMargin,
                            r = params.rightMargin, b = params.bottomMargin;
                    if (terms.size() == 1) {
                        l = t = r = b = getExactValue(terms.get(0), view.getWidth());
                    } else if (terms.size() == 2) {
                        t = b = getExactValue(terms.get(0), view.getHeight());
                        l = r = getExactValue(terms.get(1), view.getWidth());
                    } else if (terms.size() == 4) {
                        t = getExactValue(terms.get(0), view.getHeight());
                        r = getExactValue(terms.get(1), view.getWidth());
                        b = getExactValue(terms.get(2), view.getHeight());
                        l = getExactValue(terms.get(3), view.getWidth());
                    }
                    params.setMargins(l, t, r, b);
                    view.requestLayout();
                }
                case "margin-left" -> {
                    if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams p))
                        continue;
                    p.leftMargin = getExactValue(terms.get(0), view.getWidth());
                    view.requestLayout();
                }
                case "margin-top" -> {
                    if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams p))
                        continue;
                    p.topMargin = getExactValue(terms.get(0), view.getHeight());
                    view.requestLayout();
                }
                case "margin-right" -> {
                    if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams p))
                        continue;
                    p.rightMargin = getExactValue(terms.get(0), view.getWidth());
                    view.requestLayout();
                }
                case "margin-bottom" -> {
                    if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams p))
                        continue;
                    p.bottomMargin = getExactValue(terms.get(0), view.getHeight());
                    view.requestLayout();
                }
                case "padding" -> {
                    int l = view.getPaddingLeft(), t = view.getPaddingTop(),
                            r = view.getPaddingRight(), b = view.getPaddingBottom();
                    if (terms.size() == 1) {
                        l = t = r = b = getExactValue(terms.get(0), view.getWidth());
                    } else if (terms.size() == 2) {
                        t = b = getExactValue(terms.get(0), view.getHeight());
                        l = r = getExactValue(terms.get(1), view.getWidth());
                    } else if (terms.size() == 4) {
                        t = getExactValue(terms.get(0), view.getHeight());
                        r = getExactValue(terms.get(1), view.getWidth());
                        b = getExactValue(terms.get(2), view.getHeight());
                        l = getExactValue(terms.get(3), view.getWidth());
                    }
                    view.setPadding(l, t, r, b);
                }
                case "padding-left" -> view.setPadding(
                        getExactValue(terms.get(0), view.getWidth()),
                        view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
                case "padding-top" -> view.setPadding(
                        view.getPaddingLeft(),
                        getExactValue(terms.get(0), view.getHeight()),
                        view.getPaddingRight(), view.getPaddingBottom());
                case "padding-right" -> view.setPadding(
                        view.getPaddingLeft(), view.getPaddingTop(),
                        getExactValue(terms.get(0), view.getWidth()),
                        view.getPaddingBottom());
                case "padding-bottom" -> view.setPadding(
                        view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                        getExactValue(terms.get(0), view.getHeight()));
            }
        }
    }

    private void setHookedDrawable(View view, Drawable draw) {
        if (view instanceof ImageView imageView) {
            forcedDrawableMap.put(view, draw);
            imageView.setImageDrawable(draw);
        } else {
            forcedBackgroundMap.put(view, draw);
            view.setBackground(draw);
        }
    }

    private void setBackgroundModel(View view, SerialTerm term) {
        if (term.type == SerialTerm.LINEAR_GRADIENT) {
            try {
                var gradientDrawable = chacheDrawables.get(term.strValue);
                if (gradientDrawable == null) {
                    gradientDrawable = GradientDrawableParser.parseGradient(
                            term.gradientAngle, term.gradientColors, term.gradientPositions,
                            view.getWidth(), view.getHeight());
                    chacheDrawables.put(term.strValue, gradientDrawable);
                }
                forcedBackgroundMap.put(view, gradientDrawable);
                view.setBackground(gradientDrawable);
            } catch (Exception e) {
                log("Error parsing gradient: " + e.getMessage());
            }
        }
    }

    @NonNull
    private static ColorStateList getColorStateList(List<SerialTerm> terms) {
        int def = terms.get(0).colorRgb;
        int pressed = terms.get(1).colorRgb;
        int disabled = terms.get(2).colorRgb;
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_focused, android.R.attr.state_pressed},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{pressed, def, pressed, disabled, def}
        );
    }

    private int getRealValue(SerialTerm pValue, int size) {
        int value;
        if ("px".equals(pValue.unitName)) {
            value = Utils.dipToPixels(pValue.numValue);
        } else if (pValue.percentage) {
            value = size * (int) pValue.numValue / 100;
        } else {
            value = (int) pValue.numValue;
        }
        return value > 0 ? value : 1;
    }

    private int getExactValue(SerialTerm pValue, int size) {
        if ("px".equals(pValue.unitName)) return Utils.dipToPixels(pValue.numValue);
        if (pValue.percentage) return size * (int) pValue.numValue / 100;
        return (int) pValue.numValue;
    }

    private float numericFromTerm(SerialTerm t) {
        if (t.type == SerialTerm.FLOAT_VAL || t.type == SerialTerm.LENGTH) return t.numValue;
        try {
            return Float.parseFloat(t.strValue);
        } catch (Exception e) {
            return 1f;
        }
    }


    private void captureSelector(View currentView, ArrayList<SelectorPart> selector,
                                 int position, ArrayList<View> resultViews) {
        if (selector.size() == position) return;
        var part = selector.get(position);
        if (part.className != null) {
            captureSelector(currentView, selector, position + 1, resultViews);
        } else if (part.idName != null) {
            int id = part.resolvedId;
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
            String typeName = part.typeSelector;
            String pseudo = part.pseudoClass;
            var itemCount = new int[]{0};
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                var itemView = viewGroup.getChildAt(i);
                if (ReflectionUtils.isClassSimpleNameString(itemView.getClass(), typeName)) {
                    if (pseudo != null && checkAttribute(itemView, itemCount, pseudo)) continue;
                    if (selector.size() == position + 1) {
                        resultViews.add(itemView);
                    } else {
                        captureSelector(itemView, selector, position + 1, resultViews);
                    }
                } else if (isWidgetString(typeName) && itemView instanceof ViewGroup vg) {
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        captureSelector(vg.getChildAt(j), selector, position, resultViews);
                    }
                }
            }
        }
    }

    private boolean checkAttribute(View itemView, int[] itemCount, String name) {
        if (name.startsWith("nth-child")) {
            int index = Integer.parseInt(name.substring(name.indexOf('(') + 1, name.indexOf(')'))) - 1;
            return index != itemCount[0]++;
        } else if (name.startsWith("contains")) {
            String contains = name.substring(name.indexOf('(') + 1, name.indexOf(')'));
            if (itemView instanceof TextView tv) return !tv.getText().toString().contains(contains);
            return !itemView.toString().contains(contains);
        }
        return false;
    }

    private boolean isWidgetString(String view) {
        return widgetClassCache.computeIfAbsent(view,
                name -> XposedHelpers.findClassIfExists("android.widget." + name, null) != null);
    }

    private Class<?> resolveClass(String className) {
        if (className == null) return null;
        return resolvedClasses.computeIfAbsent(className,
                name -> XposedHelpers.findClassIfExists(name, classLoader));
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
                    try (var parcelFile = WppCore.getClientBridge().openFile(filePath, false)) {
                        bitmap = BitmapFactory.decodeStream(new FileInputStream(parcelFile.getFileDescriptor()));
                    }
                } else {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
                var newHeight = reqHeight < 1 ? bitmap.getHeight() : Math.min(bitmap.getHeight(), reqHeight);
                var newWidth = reqWidth < 1 ? bitmap.getWidth() : Math.min(bitmap.getWidth(), reqWidth);
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
            String key = file.getAbsolutePath();

            CachedDrawable cachedDrawable = drawableCache.get(key);

            if (cachedDrawable != null) {
                if (System.currentTimeMillis() - cachedDrawable.lastCheckTime < 2000) {
                    return cachedDrawable.drawable;
                }
            }

            if (!file.exists()) return null;

            long lastModified = file.lastModified();
            if (cachedDrawable != null) {
                cachedDrawable.lastCheckTime = System.currentTimeMillis();
                if (cachedDrawable.lastModified == lastModified) return cachedDrawable.drawable;
            }

            Drawable cached = loadDrawableFromCache(key, lastModified);
            if (cached != null) {
                cachedDrawable = new CachedDrawable(cached, lastModified);
                drawableCache.put(key, cachedDrawable);
                return cached;
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
            if (!cacheLocation.exists()) cacheLocation.mkdirs();
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
                if (cachedLastModified != originalLastModified) return null;
                Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (bitmap != null) return new BitmapDrawable(context.getResources(), bitmap);
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
            long lastCheckTime;

            CachedDrawable(Drawable drawable, long lastModified) {
                this.drawable = drawable;
                this.lastModified = lastModified;
                this.lastCheckTime = System.currentTimeMillis();
            }
        }
    }


    static class SelectorPart implements Serializable {
        String idName;
        int resolvedId;
        String className;
        String typeSelector;
        String pseudoClass;
    }

    static class SerialTerm implements Serializable {
        static final byte COLOR = 1;
        static final byte LENGTH = 2;
        static final byte URI = 3;
        static final byte FLOAT_VAL = 4;
        static final byte STRING = 5;
        static final byte LINEAR_GRADIENT = 6;
        static final byte FUNCTION = 7;

        byte type;
        int colorRgb;
        float numValue;
        boolean percentage;
        String unitName;
        String strValue;
        ArrayList<SerialTerm> args;
        float gradientAngle;
        int[] gradientColors;
        float[] gradientPositions;
    }

    static class SerialDeclaration implements Serializable {
        String property;
        ArrayList<SerialTerm> terms;
    }

    static class CachedRuleItem implements Serializable {
        ArrayList<SelectorPart> selector;
        ArrayList<SerialDeclaration> declarations;
        String targetActivityClassName;

        CachedRuleItem(ArrayList<SelectorPart> selector,
                       ArrayList<SerialDeclaration> declarations,
                       String targetActivityClassName) {
            this.selector = selector;
            this.declarations = declarations;
            this.targetActivityClassName = targetActivityClassName;
        }
    }


    public static class GradientDrawableParser {

        public static BitmapDrawable parseGradient(float angle, int[] colors, float[] positions,
                                                   int width, int height) {
            LinearGradient lg = createLinearGradient(angle, colors, positions, width, height);
            ShapeDrawable sd = new ShapeDrawable(new RectShape());
            sd.setIntrinsicWidth(width);
            sd.setIntrinsicHeight(height);
            sd.getPaint().setShader(lg);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            sd.setBounds(0, 0, width, height);
            sd.draw(new Canvas(bitmap));
            return new BitmapDrawable(Utils.getApplication().getResources(), bitmap);
        }

        private static LinearGradient createLinearGradient(float angle, int[] colors,
                                                           float[] positions, int width, int height) {
            double radians = Math.toRadians(angle);
            float x0 = (float) (0.5 * width + 0.5 * width * Math.cos(radians - Math.PI / 2));
            float y0 = (float) (0.5 * height + 0.5 * height * Math.sin(radians - Math.PI / 2));
            float x1 = (float) (0.5 * width + 0.5 * width * Math.cos(radians + Math.PI / 2));
            float y1 = (float) (0.5 * height + 0.5 * height * Math.sin(radians + Math.PI / 2));
            return new LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP);
        }
    }
}
