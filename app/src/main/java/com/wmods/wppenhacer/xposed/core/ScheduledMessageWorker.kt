import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wmods.wppenhacer.BuildConfig

class ScheduledMessageWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val jid = inputData.getString("jid") ?: return Result.failure()
        val text = inputData.getString("message") ?: return Result.failure()
        val isBusiness = inputData.getBoolean("is_business", false)

        val targetPackage = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"

        val intent = Intent("com.wmods.wppenhacer.SEND_SCHEDULED_MSG").apply {
            setPackage(targetPackage)
            putExtra("jid", jid)
            putExtra("message", text)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        return try {
            applicationContext.sendBroadcast(intent)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}