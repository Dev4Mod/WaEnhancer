import androidx.annotation.NonNull;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class AutoReply extends Feature {
    public AutoReply(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("enable_auto_reply", false)) return;

        Class<?> fMessageClass = classLoader.loadClass("com.whatsapp.protocol.FMessage");
        Method sendMessageMethod = Unobfuscator.loadSendMessageMethod(classLoader);

        XposedBridge.hookAllConstructors(fMessageClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                FMessageWpp fmsg = new FMessageWpp(param.thisObject);
                
                if (fmsg.isFromMe()) return;

                String jid = fmsg.getJid();
                String text = fmsg.getText();
                
                if (text == null) return;

                String targetType = prefs.getString("auto_reply_target_type", "contact");
                boolean isGroup = jid != null && jid.contains("@g.us");
                
                if (targetType.equals("contact") && isGroup) return;
                if (targetType.equals("group") && !isGroup) return;

                String keyword = prefs.getString("auto_reply_keyword", "").toLowerCase();
                String mode = prefs.getString("auto_reply_match_mode", "equals");
                String currentText = text.toLowerCase().trim();

                boolean isMatch = mode.equals("equals") ? currentText.equals(keyword.trim()) : currentText.contains(keyword);

                if (isMatch) {
                    String replyText = prefs.getString("auto_reply_response", "Pesan Otomatis");
                    try {
                        XposedHelpers.callStaticMethod(sendMessageMethod.getDeclaringClass(), sendMessageMethod.getName(), fmsg.getJidObject(), replyText);
                    } catch (Exception e) {
                        XposedBridge.log("AutoReply Error: " + e.getMessage());
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Auto Reply";
    }
}