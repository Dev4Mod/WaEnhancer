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
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.xposed.core.Feature;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import de.robv.android.xposed.XposedHelpers;

public class CustomView extends Feature {

    private DrawableCache cacheImages;
    private HashMap<String, Drawable> chacheDrawables;
    private ExecutorService mThreadService;

    public CustomView(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var filter_itens = prefs.getString("css_theme", "");
        if (TextUtils.isEmpty(filter_itens)) return;
        cacheImages = new DrawableCache(Utils.getApplication(), 100 * 1024 * 1024);
        chacheDrawables = new HashMap<>();
        mThreadService = Executors.newFixedThreadPool(8);

        var sheet = CSSFactory.parseString(filter_itens, new URL("https://base.url/"));
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                View rootView = activity.getWindow().getDecorView().getRootView();
                rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> mThreadService.execute(() -> registerCssRules(activity, (ViewGroup) rootView, sheet)));
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
                    if (!(declaration.get(0) instanceof TermURI uri)) continue;
                    if (!new File(uri.getValue()).exists()) continue;
                    var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
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
                                if (width.getValue().intValue() == 100 || height.getValue().intValue() == 100) {
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
                            } else {
                                var drawable = view.getBackground();
                                if (!(drawable instanceof BitmapDrawable)) continue;
                                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                if (bitmap.getWidth() == view.getWidth() && bitmap.getHeight() == view.getHeight())
                                    continue;
                                drawable = new BitmapDrawable(view.getContext().getResources(), Bitmap.createScaledBitmap(bitmap, view.getWidth(), view.getHeight(), false));
                                view.setBackground(drawable);
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
                        var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
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
                        if (!new File(uri.getValue()).exists()) continue;
                        var draw = cacheImages.getDrawable(uri.getValue(), view.getWidth(), view.getHeight());
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
                            // Log the exception if needed
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
        private final LruCache<String, CachedDrawable> drawableCache;
        private final Context context;

        public DrawableCache(Context context, int maxSize) {
            this.context = context.getApplicationContext();
            drawableCache = new LruCache<>(maxSize);
        }

        private Drawable loadDrawableFromFile(String filePath, int reqWidth, int reqHeight) {
            File file = new File(filePath);

            if (!file.exists()) {
                Log.e("DrawableCache", "File not found: " + filePath);
                return new ColorDrawable(0);  // Return transparent drawable
            }

            // First pass to get image dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            // Calculate the inSampleSize based on required dimensions
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Second pass to actually load the bitmap
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            if (bitmap == null) {
                Log.e("DrawableCache", "Failed to decode file: " + filePath);
                return new ColorDrawable(0);  // Return transparent drawable
            }

            return new BitmapDrawable(context.getResources(), bitmap);
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            if (options == null) {
                throw new IllegalArgumentException("Options cannot be null");
            }

            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                try {
                    // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                    // height and width larger than the requested height and width.
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                } catch (ArithmeticException e) {
                    // Handle the exception if division by zero occurs
                    return 1;
                }
            }

            return inSampleSize;
        }

        public Drawable getDrawable(String key, int width, int height) {
            File file = new File(key);
            long lastModified = file.lastModified();

            // Check if the cached drawable exists and is up-to-date
            CachedDrawable cachedDrawable = drawableCache.get(key);
            if (cachedDrawable != null && cachedDrawable.lastModified == lastModified) {
                return cachedDrawable.drawable;
            }

            // Try loading the drawable from cache
            Drawable cachedDrawableFromFile = loadDrawableFromCache(key, lastModified);
            if (cachedDrawableFromFile != null) {
                cachedDrawable = new CachedDrawable(cachedDrawableFromFile, lastModified);
                drawableCache.put(key, cachedDrawable);
                return cachedDrawableFromFile;
            }

            // Load drawable from original file
            Drawable drawable = loadDrawableFromFile(key, width, height);
            // Save the drawable to cache directory
            saveDrawableToCache(key, (BitmapDrawable) drawable, lastModified);

            // Update the cache
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
                drawable.getBitmap().compress(Bitmap.CompressFormat.JPEG, 80, out);
                metaOut.writeLong(lastModified);
                Log.d("DrawableCache", "Saved drawable to cache: " + cacheFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e("DrawableCache", "Failed to save drawable to cache", e);
            }
        }

        private Drawable loadDrawableFromCache(String key, long originalLastModified) {
            File cacheDir = context.getCacheDir();
            File cacheFile = new File(cacheDir, getCacheFileName(key));
            File metadataFile = new File(cacheDir, getCacheFileName(key) + ".meta");

            if (!cacheFile.exists() || !metadataFile.exists()) {
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

        private String getCacheFileName(String key) {
            // Create a unique file name based on the original file path
            return String.valueOf(key.hashCode());
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
