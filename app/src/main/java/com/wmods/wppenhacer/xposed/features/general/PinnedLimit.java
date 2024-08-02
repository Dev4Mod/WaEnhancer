package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PinnedLimit extends Feature {

    public PinnedLimit(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    @SuppressLint("DiscouragedApi")
    public void doHook() throws Throwable {
        var pinnedLimitMethod = Unobfuscator.loadPinnedLimitMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(pinnedLimitMethod));
        var pinnedSetMethod = Unobfuscator.loadPinnedHashSetMethod(classLoader);
        var pinnedInChatMethod = Unobfuscator.loadPinnedInChatMethod(classLoader);

        // increase pinned limit in chat to 60
        XposedBridge.hookMethod(pinnedInChatMethod, XC_MethodReplacement.returnConstant(60));

        // isso cria um LinkedHashSet modificado para retorna 0 caso a lista de fixados seja inferior a 60.
        XposedBridge.hookMethod(pinnedSetMethod, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var pinnedset = (Set) param.getResult();
                PinnedLinkedHashSet<Object> pinnedMod;

                if (!(pinnedset instanceof PinnedLinkedHashSet)) {
                    pinnedMod = new PinnedLinkedHashSet<>();
                    pinnedMod.addAll(pinnedset);
                    var setField = ReflectionUtils.getFieldByType(pinnedSetMethod.getDeclaringClass(), Set.class);
                    XposedHelpers.setObjectField(param.thisObject, setField.getName(), pinnedMod);
                    param.setResult(pinnedMod);
                } else {
                    pinnedMod = (PinnedLinkedHashSet<Object>) pinnedset;
                }
                pinnedMod.setLimit(prefs.getBoolean("pinnedlimit", false) ? 60 : 3);
            }
        });


        // Sempre que vai fixar varias conversas de uma vez, é verificado antes se a quantidade de conversas é maior que 3, somando a lista de fixados atual com o HashSet das novas que serão fixadas.
        // Esse gancho verifica se o botao de fixar foi clicado e limita o tamanho da Hashset para 1
        var idPin = Utils.getID("menuitem_conversations_pin", "id");
        XposedBridge.hookMethod(pinnedLimitMethod, new XC_MethodHook() {
            private Unhook hooked;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("pinnedlimit", false)) return;
                if (param.args.length > 0 && param.args[0] instanceof MenuItem menuItem) {
                    if (menuItem.getItemId() != idPin) return;
                    hooked = XposedHelpers.findAndHookMethod(HashSet.class, "size", XC_MethodReplacement.returnConstant(1));
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (hooked != null) hooked.unhook();
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Pinned Limit";
    }


    private static class PinnedLinkedHashSet<T> extends java.util.LinkedHashSet<T> {

        private int limit;


        @Override
        public int size() {
            if (super.size() >= limit) {
                return 3;
            }
            return 0;
        }

        public void setLimit(int i) {
            this.limit = i;
        }
    }

}
