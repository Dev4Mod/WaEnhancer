package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import lombok.Setter;

public class PinnedLimit extends Feature {

    public PinnedLimit(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    @SuppressLint("DiscouragedApi")
    public void doHook() throws Throwable {

        var pinnedSetMethod = Unobfuscator.loadPinnedHashSetMethod(classLoader);
        var pinnedInChatMethod = Unobfuscator.loadPinnedInChatMethod(classLoader);


        // increase pinned limit in chat to 60
        XposedBridge.hookMethod(pinnedInChatMethod, XC_MethodReplacement.returnConstant(60));

        // Fix bug in initialCapacity of LinkedHashSet
        XposedHelpers.findAndHookConstructor(LinkedHashSet.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if ((int) param.args[0] < 0) {
                    param.args[0] = Math.abs((int) param.args[0]);
                }
            }
        });

        // This creates a modified linkedhashMap to return 0 if the fixed list is less than 60.
        XposedBridge.hookMethod(pinnedSetMethod, new XC_MethodHook() {

            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var map = (Map) param.getResult();
                PinnedLinkedHashMap<Object> pinnedMod;
                if (!(map instanceof PinnedLinkedHashMap)) {
                    pinnedMod = new PinnedLinkedHashMap<>();
                    pinnedMod.putAll(map);
                    param.setResult(pinnedMod);
                } else {
                    pinnedMod = (PinnedLinkedHashMap<Object>) map;
                }
                pinnedMod.setLimit(prefs.getBoolean("pinnedlimit", false) ? 60 : 3);
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Pinned Limit";
    }


    @Setter
    private static class PinnedLinkedHashMap<T> extends LinkedHashMap<T, T> {

        @Override
        public int size() {
            if (super.size() >= limit) {
                return super.size();
            }
            return -this.limit;
        }

        private int limit;

        @NonNull
        @Override
        public Set<T> keySet() {
            return new PinnedKeySet<>(this, super.keySet());
        }

        static class PinnedKeySet<T> implements Set<T> {

            private final Set<T> set;
            private final PinnedLinkedHashMap<T> pinnedKeySet;

            public PinnedKeySet(PinnedLinkedHashMap<T> pinnedKeySet, Set<T> set) {
                this.pinnedKeySet = pinnedKeySet;
                this.set = set;
            }

            @Override
            public int size() {
                return pinnedKeySet.size();
            }

            @Override
            public boolean isEmpty() {
                return set.isEmpty();
            }

            @Override
            public boolean contains(@Nullable Object o) {
                return set.contains(o);
            }

            @NonNull
            @Override
            public Iterator<T> iterator() {
                return set.iterator();
            }

            @NonNull
            @Override
            public Object[] toArray() {
                return set.toArray();
            }

            @NonNull
            @Override
            public <T1> T1[] toArray(@NonNull T1[] a) {
                return set.toArray(a);
            }

            @Override
            public boolean add(@Nullable T t) {
                return set.add(t);
            }

            @Override
            public boolean remove(@Nullable Object o) {
                return set.remove(o);
            }

            @Override
            public boolean containsAll(@NonNull Collection<?> c) {
                return set.containsAll(c);
            }

            @Override
            public boolean addAll(@NonNull Collection<? extends T> c) {
                return false;
            }

            @Override
            public boolean retainAll(@NonNull Collection<?> c) {
                return set.retainAll(c);
            }

            @Override
            public boolean removeAll(@NonNull Collection<?> c) {
                return set.removeAll(c);
            }


            @Override
            public void clear() {
                set.clear();
            }

            @NonNull
            @Override
            public Spliterator<T> spliterator() {
                return set.spliterator();
            }
        }
    }

}
