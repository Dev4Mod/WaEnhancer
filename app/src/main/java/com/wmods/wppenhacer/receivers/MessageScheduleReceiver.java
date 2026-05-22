import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MessageScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String jid = intent.getStringExtra("jid");
        String message = intent.getStringExtra("message");

        // Kirim broadcast ke modul Xposed yang berjalan di proses WhatsApp
        Intent xposedIntent = new Intent("com.wmods.wppenhacer.SEND_SCHEDULED_MESSAGE");
        xposedIntent.putExtra("jid", jid);
        xposedIntent.putExtra("message", message);
        context.sendBroadcast(xposedIntent);
    }
}