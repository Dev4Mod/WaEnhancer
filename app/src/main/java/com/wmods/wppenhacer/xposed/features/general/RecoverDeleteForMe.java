package com.wmods.wppenhacer.xposed.features.general;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class RecoverDeleteForMe extends Feature {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public RecoverDeleteForMe(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        // We use a new preference key or reuse an existing one.
        // Since user request didn't specify, I will stick to "enable_deleted_messages_recovery"
        // But to make it work immediately for testing, I might default to true or add it to preferences xml.
        // For now, let's assume it's always on if the module is enabled (or check a pref).
        // I'll check "enable_deleted_messages"
        // if (!prefs.getBoolean("enable_deleted_messages", true)) return; 
        
        try {
            Class<?> cms = Unobfuscator.loadCoreMessageStore(classLoader);
            Class<?> fMessage = Unobfuscator.loadFMessageClass(classLoader);
            
            XposedBridge.log("WAE: RecoverDeleteForMe Scanning class: " + cms.getName());

            for (Method m : cms.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                // XposedBridge.log("WAE: Checking method " + m.getName() + " params: " + Arrays.toString(params));
                
                // Heuristic 1: method(Collection<FMessage>, int) or (Collection, boolean)
                if (params.length == 2 && Collection.class.isAssignableFrom(params[0]) && (params[1] == int.class || params[1] == boolean.class)) {
                     hookMethod(m, cms, fMessage);
                }
                // Heuristic 2: method(List) - likely deleteMessages(List)
                else if (params.length == 1 && Collection.class.isAssignableFrom(params[0])) {
                     hookMethod(m, cms, fMessage);
                }
                // Heuristic 3: method(Object) - likely deleteMessage(FMessage)
                else if (params.length == 1 && params[0].getName().startsWith("X.")) {
                     hookMethod(m, cms, fMessage);
                }
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: RecoverDeleteForMe init failed: " + e);
        }
    }

    private void hookMethod(Method m, Class<?> cms, Class<?> fMessage) {
        XposedBridge.log("WAE: Hooking candidate method (Broad): " + m.getName() + " params: " + Arrays.toString(m.getParameterTypes()));
        XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("WAE: Hooked method " + m.getName() + " called");
                Object arg0 = param.args[0];

                if (arg0 instanceof Collection) {
                    Collection<?> c = (Collection<?>) arg0;
                    if (c != null && !c.isEmpty()) {
                        Object first = c.iterator().next();
                        if (fMessage.isInstance(first)) {
                            XposedBridge.log("WAE: Saving " + c.size() + " messages from " + m.getName());
                            saveMessages(c);
                        } else {
                             XposedBridge.log("WAE: First item is not FMessage in " + m.getName() + ": " + first.getClass().getName());
                        }
                    }
                } else if (fMessage.isInstance(arg0)) {
                 XposedBridge.log("WAE: Saving 1 message from " + m.getName());
                 saveMessages(Collections.singletonList(arg0));
            } else {
                 XposedBridge.log("WAE: arg0 is NOT FMessage instance in " + m.getName() + ". Class: " + arg0.getClass().getName() + ". Forcing save attempt.");
                 
                 // Dump fields to find the key or FMessage
                 try {
                     for (java.lang.reflect.Field f : arg0.getClass().getDeclaredFields()) {
                         f.setAccessible(true);
                         Object val = f.get(arg0);
                         XposedBridge.log("WAE: Field " + f.getName() + " (" + f.getType().getName() + ") = " + val);
                     }
                 } catch (Exception e) {
                     XposedBridge.log("WAE: Failed to dump fields: " + e);
                 }

                 saveMessages(Collections.singletonList(arg0));
            }
        }
    });
    }


    private void saveMessages(Collection<?> messages) {
        executorService.submit(() -> {
            try {
                DelMessageStore store = DelMessageStore.getInstance(Utils.getApplication());
                for (Object msg : messages) {
                     XposedBridge.log("WAE: Processing message: " + msg.getClass().getName());
                     FMessageWpp fMessage = new FMessageWpp(msg);
                     
                     FMessageWpp.Key key = fMessage.getKey();
                     if (key == null) {
                         XposedBridge.log("WAE: Key is null, skipping");
                         continue;
                     }
                     XposedBridge.log("WAE: Key found: " + key.toString());

                     DeletedMessage dm = new DeletedMessage();
                     dm.setKeyId(key.messageID);
                     
                     if (key.remoteJid != null) {
                         dm.setChatJid(key.remoteJid.getPhoneRawString());
                         dm.setSenderJid(key.isFromMe ? "Me" : key.remoteJid.getPhoneRawString());
                         
                         if (key.remoteJid.isGroup()) {
                             FMessageWpp.UserJid participant = fMessage.getUserJid();
                             if (participant != null) {
                                  dm.setSenderJid(participant.getPhoneRawString());
                             }
                         }
                     }
                     
                     dm.setTimestamp(System.currentTimeMillis()); 
                     
                     String text = fMessage.getMessageStr();
                     XposedBridge.log("WAE: Message Text: " + text);
                     dm.setTextContent(text);

                     try {
                        int mediaType = fMessage.getMediaType();
                        dm.setMediaType(mediaType);
                     } catch (Exception e) {
                        XposedBridge.log("WAE: Failed to get MediaType (ignoring)");
                        dm.setMediaType(-1);
                     }
                     
                     try {
                         if (fMessage.isMediaFile()) {
                             File media = fMessage.getMediaFile();
                             if (media != null && media.exists()) {
                                 dm.setMediaPath(media.getAbsolutePath());
                             }
                         }
                     } catch (Exception e) {
                         XposedBridge.log("WAE: Failed to get MediaFile (ignoring)");
                     }
                     
                     store.insertDeletedMessage(dm);
                     XposedBridge.log("WAE: DeletedMessage inserted into DB");
                }
            } catch (Exception e) {
                XposedBridge.log("WAE: Error saving deleted messages: " + e);
            }
        });
    }

    @Override
    public String getPluginName() {
        return "Recover Delete For Me";
    }

    public static void restoreMessage(android.content.Context context, DeletedMessage message) {
        try {
            if (message.getTextContent() != null && !message.getTextContent().isEmpty()) {
                String jid = message.getChatJid();
                if (jid.contains("@")) {
                    jid = jid.substring(0, jid.indexOf("@"));
                }
                WppCore.sendMessage(jid, message.getTextContent());
                android.widget.Toast.makeText(context, "Message content restored as new message", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(context, "Media restore not supported yet", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: Restore failed: " + e);
            android.widget.Toast.makeText(context, "Restore failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
