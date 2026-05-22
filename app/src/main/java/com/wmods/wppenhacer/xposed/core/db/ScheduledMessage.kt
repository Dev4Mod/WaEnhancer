import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScheduledMessageWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        val jid = inputData.getString("jid") ?: return Result.failure()
        val text = inputData.getString("message") ?: return Result.failure()
        
        val intent = Intent("com.wmods.wppenhacer.SEND_SCHEDULED_MSG").apply {
            setPackage("com.whatsapp")
            putExtra("jid", jid)
            putExtra("message", text)
        }
        applicationContext.sendBroadcast(intent)
        
        return Result.success()
    }
}