package com.wmods.wppenhacer.xposed.features.general;

import static com.wmods.wppenhacer.xposed.features.general.MenuStatus.menuStatuses;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;

import java.lang.reflect.Field;

import de.robv.android.xposed.XSharedPreferences;

public class DeleteStatus extends Feature {


    public DeleteStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var fragmentloader = Unobfuscator.loadFragmentLoader(classLoader);
        var showDialogStatus = Unobfuscator.loadShowDialogStatusMethod(classLoader);
        Class<?> StatusDeleteDialogFragmentClass = classLoader.loadClass("com.whatsapp.status.StatusDeleteDialogFragment");
        Field fieldBundle = ReflectionUtils.getFieldByType(fragmentloader, Bundle.class);

        var item = new MenuStatus.MenuItemStatus() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                if (menu.findItem(ResId.string.delete_for_me) != null) return null;
                if (fMessage.getKey().isFromMe) return null;
                return menu.add(0, ResId.string.delete_for_me, 0, ResId.string.delete_for_me);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessage) {
                try {
                    var status = StatusDeleteDialogFragmentClass.newInstance();
                    var key = fMessage.getKey();
                    var bundle = getBundle(key);
                    WppCore.setPrivBoolean(key.messageID + "_delpass", true);
                    fieldBundle.set(status, bundle);
                    showDialogStatus.invoke(null, status, fragmentInstance);
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        };
        menuStatuses.add(item);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Delete Status";
    }

    @NonNull
    private static Bundle getBundle(FMessageWpp.Key key) {
        var bundle = new Bundle();
        bundle.putString("fMessageKeyJid", WppCore.getRawString(key.remoteJid));
        bundle.putBoolean("fMessageKeyFromMe", key.isFromMe);
        bundle.putString("fMessageKeyId", key.messageID);
        return bundle;
    }
}
