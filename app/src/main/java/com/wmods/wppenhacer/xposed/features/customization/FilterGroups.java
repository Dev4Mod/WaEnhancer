package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FilterGroups extends Feature {
    private Method methodSetFilter;
    private Object mFilterInstance;
    private Object mConversationFragment;
    private Method methodInitFilter;
    private TextView tabConversas;
    private TextView tabGrupos;

    public FilterGroups(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("filtergroups", false) || prefs.getBoolean("separategroups", false))
            return;
        if (Utils.getApplication().getPackageName().equals(FeatureLoader.PACKAGE_BUSINESS))
            return; // Business is not supported

        var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(classLoader);
        methodSetFilter = ReflectionUtils.findMethodUsingFilter(filterAdaperClass, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(int.class));

        XposedBridge.hookAllConstructors(filterAdaperClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mFilterInstance = param.thisObject;
            }
        });

        var cFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);

        XposedBridge.hookAllConstructors(cFrag, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mConversationFragment = param.thisObject;
            }
        });

        methodInitFilter = Unobfuscator.getFilterInitMethod(classLoader);

        var filterView = Unobfuscator.getFilterView(classLoader);

        XposedHelpers.findAndHookConstructor(filterView, android.content.Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                setSetupSeparate((ViewGroup) param.thisObject);
            }
        });

    }

    @SuppressLint("ResourceType")
    private void setSetupSeparate(ViewGroup view) {
        var context = view.getContext();
        if (view.findViewById(0x1235555) != null) return;
        var container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setId(0x1235555);
        var filter = view.getChildAt(0);
        view.removeView(filter);

        // Criação do LinearLayout principal (container)
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dipToPixels(48));
        params.leftMargin = Utils.dipToPixels(20);
        params.rightMargin = Utils.dipToPixels(20);
        params.bottomMargin = Utils.dipToPixels(5);
        mainLayout.setLayoutParams(params);

        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT); // Cor do fundo da borda
        borderDrawable.setStroke(Utils.dipToPixels(2), DesignUtils.getUnSeenColor()); // Defina a cor e a largura da borda
        borderDrawable.setCornerRadius(Utils.dipToPixels(50.0f));
        mainLayout.setBackground(borderDrawable);

        // Criação do Layout de Abas
        LinearLayout tabLayout = new LinearLayout(context);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.addView(tabLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Criação das TextViews para abas
        tabConversas = createTab(context, UnobfuscatorCache.getInstance().getString("chats"), 0);
        tabConversas.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabConversas.setOnClickListener(v -> updateContent(0));

        tabGrupos = createTab(context, UnobfuscatorCache.getInstance().getString("groups"), 1);
        tabGrupos.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabGrupos.setOnClickListener(v -> updateContent(1));

        // Adicionar TextViews ao Layout de Abas
        tabLayout.addView(tabConversas);
        tabLayout.addView(tabGrupos);

        // Inicialize com a primeira aba selecionada
        updateContent(0);
        new Handler(Looper.getMainLooper()).postDelayed(() -> setFilter(0), 500);

        // Definir o layout principal como a visualização de conteúdo
        container.addView(mainLayout);
        container.addView(filter);
        view.addView(container, 0);
    }

    private TextView createTab(Context context, String text, int left) {
        TextView tab = new TextView(context);
        tab.setText(text);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(32, 16, 32, 16);
        tab.setTextColor(DesignUtils.getPrimaryTextColor());
        setDrawableSelected(tab, Color.TRANSPARENT, DesignUtils.getPrimaryTextColor(), left);
        return tab;
    }

    private void setDrawableSelected(View view, int colorBackground, int colorStroke, int left) {
        // Definição dos cantos arredondados
        float border = Utils.dipToPixels(50.0f);
        float[] rects = left == 0 ? new float[]{border, border, 0, 0, 0, 0, border, border} : new float[]{0, 0, border, border, border, border, 0, 0};

        // Criação da forma do fundo
        ShapeDrawable shape = new ShapeDrawable(new RoundRectShape(rects, null, null));
        shape.getPaint().setColor(colorBackground);
        shape.setAlpha(120);

        // Criação da borda
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT); // Cor do fundo da borda
        borderDrawable.setStroke(Utils.dipToPixels(2), colorStroke); // Defina a cor e a largura da borda
        borderDrawable.setCornerRadii(rects); // Definindo os cantos arredondados para a borda

        // Combinando o fundo com a borda
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{borderDrawable, shape});
        layerDrawable.setLayerInset(1, Utils.dipToPixels(2), Utils.dipToPixels(2), Utils.dipToPixels(2), Utils.dipToPixels(2)); // Definindo a borda ao redor do fundo
        view.setBackground(layerDrawable);
    }

    private void updateContent(int position) {
        if (position == 0) {
            setDrawableSelected(tabConversas, DesignUtils.getUnSeenColor(), DesignUtils.getPrimaryTextColor(), 0);
            setDrawableSelected(tabGrupos, Color.TRANSPARENT, DesignUtils.getPrimaryTextColor(), 1);
            setFilter(position);
        } else {
            setDrawableSelected(tabConversas, Color.TRANSPARENT, DesignUtils.getPrimaryTextColor(), 0);
            setDrawableSelected(tabGrupos, DesignUtils.getUnSeenColor(), DesignUtils.getPrimaryTextColor(), 1);
            setFilter(position);
        }
    }

    private void setFilter(int position) {
        try {
            ReflectionUtils.callMethod(methodInitFilter, null, mConversationFragment);
            if (mFilterInstance == null) return;
            var listField = ReflectionUtils.getFieldByType(mFilterInstance.getClass(), List.class);
            var list = (List<Object>) ReflectionUtils.getObjectField(listField, mFilterInstance);
            if (list == null) return;
            var name = position == 0 ? "CONTACTS_FILTER" : "GROUP_FILTER";
            Object result = null;
            for (var item : list) {
                for (var field : item.getClass().getFields()) {
                    if (Objects.equals(XposedHelpers.getObjectField(item, field.getName()), name)) {
                        result = item;
                        break;
                    }
                }
            }
            if (result == null) return;
            var index = list.indexOf(result);
            ReflectionUtils.callMethod(methodSetFilter, mFilterInstance, index);
        } catch (Exception e) {
            logDebug(e);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return null;
    }
}
