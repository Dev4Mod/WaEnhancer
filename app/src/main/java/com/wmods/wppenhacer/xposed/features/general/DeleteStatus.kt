package com.wmods.wppenhacer.xposed.features.general;


import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.util.List;

import de.robv.android.xposed.XSharedPreferences;

public class DeleteStatus extends Feature {


    public DeleteStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var StatusPlaybackActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackActivity");

        var item = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageWppList, int currentIndex) {
                if (menu.findItem(R.string.delete_for_me) != null) return null;
                var fMessage = fMessageWppList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                return menu.add(0, R.string.delete_for_me, 0, R.string.delete_for_me);
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageWppList, int currentIndex) {
                try {
                    var key = fMessageWppList.get(currentIndex).getKey();
                    MessageStore.getInstance().deleteStatusByMessageKey(key.messageID);
                    var activity = WppCore.getCurrentActivity();
                    if (activity != null && StatusPlaybackActivityClass.isInstance(activity)) {
                        activity.recreate();
                    }
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        };
        MenuStatusListener.getMenuStatuses().add(item);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Delete Status";
    }
}