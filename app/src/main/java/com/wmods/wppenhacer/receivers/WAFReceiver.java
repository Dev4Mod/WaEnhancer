package com.wmods.wppenhacer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class WAFReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.wmods.wppenhacer.SEND_SCHEDULED_MSG".equals(intent.getAction())) {
            String jid = intent.getStringExtra("jid");
            String message = intent.getStringExtra("message");
            
            try {
                ClassLoader loader = context.getClassLoader();
                Method sendMethod = Unobfuscator.loadSendMessageMethod(loader);
                Object jidObj = WppCore.createUserJid(jid);
                
                XposedHelpers.callStaticMethod(
                    sendMethod.getDeclaringClass(), 
                    sendMethod.getName(), 
                    jidObj, 
                    message
                );
            } catch (Exception e) {
                XposedBridge.log("Schedule Send Error: " + e.getMessage());
            }
        }
    }
}